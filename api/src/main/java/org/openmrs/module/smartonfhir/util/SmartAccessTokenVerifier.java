/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;

/**
 * Verifies the SMART access tokens presented on FHIR requests.
 * <p>
 * What it checks, and why each matters:
 * <ul>
 * <li><strong>Signature</strong>, against the authorization server's published keys. Without it any
 * self-issued token would be accepted.</li>
 * <li><strong>{@code iss}</strong>, exactly. A valid token from a different authorization server is
 * still not a token for this server.</li>
 * <li><strong>{@code aud}</strong>, against this FHIR server's base URL. SMART App Launch 2.x
 * requires it: an app may hold a legitimate token for another FHIR server, and without this check
 * that token would be replayable here.</li>
 * <li><strong>{@code exp}</strong>, required rather than merely honoured when present, so a token
 * without an expiry is rejected instead of lasting forever.</li>
 * </ul>
 * Asymmetric signatures only. The authorization server signs access tokens with a key it alone
 * holds, so accepting an HMAC algorithm here would mean accepting a token signed with a secret this
 * module also knows.
 */
@Slf4j
public class SmartAccessTokenVerifier {

	private static final Set<JWSAlgorithm> PERMITTED_ALGORITHMS = new HashSet<>(
	        Arrays.asList(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512, JWSAlgorithm.ES256, JWSAlgorithm.ES384,
	            JWSAlgorithm.ES512, JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512));

	private final SmartOAuth2Config config;

	private final ConfigurableJWTProcessor<SecurityContext> processor;

	/**
	 * @param config the configured issuer and audience
	 * @param keySource where to obtain the authorization server's signing keys. Injected rather than
	 *            built here so that tests can supply a fixed key set instead of reaching the network.
	 */
	public SmartAccessTokenVerifier(SmartOAuth2Config config, JWKSource<SecurityContext> keySource) {
		this.config = config;
		this.processor = new DefaultJWTProcessor<>();

		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(PERMITTED_ALGORITHMS, keySource));

		// exp is required. nimbus only enforces claims named here as required, so leaving it out would
		// let a token with no expiry through.
		List<String> required = Arrays.asList("exp", "iss", "aud");

		// Only the issuer is matched exactly. The audience is handled by the requiredAudience argument
		// below, which asks whether this server is *among* the audiences, as RFC 7519 defines the check.
		// Matching aud exactly would reject a legitimately multi-audience token -- which Keycloak issues
		// routinely, often including "account" -- without making anything safer.
		JWTClaimsSet expected = new JWTClaimsSet.Builder().issuer(config.getIssuer()).build();

		DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(config.getAudience(),
		        expected, new HashSet<>(required));
		claimsVerifier.setMaxClockSkew(config.getAllowedClockSkewSeconds());
		processor.setJWTClaimsSetVerifier(claimsVerifier);
	}

	/**
	 * Verifies a bearer token and extracts what the FHIR layer needs from it.
	 *
	 * @return the token's details, or null if it is not acceptable. The reason is logged; callers
	 *         should answer with a generic {@code invalid_token} rather than relaying it, so that a
	 *         caller cannot probe for which check failed.
	 */
	public SmartAccessToken verify(String bearerToken) {
		if (bearerToken == null || bearerToken.trim().isEmpty()) {
			return null;
		}

		final JWTClaimsSet claims;
		try {
			claims = processor.process(bearerToken.trim(), null);
		}
		catch (Exception e) {
			// nimbus reports signature, issuer, audience, expiry and algorithm failures alike as
			// exceptions; none of the detail is safe to hand back to the caller.
			log.warn("Rejected a SMART access token: {}", e.getMessage());
			return null;
		}

		String username = claimAsString(claims, config.getUsernameClaim());

		if (username == null || username.trim().isEmpty()) {
			// Nearly always a client that was never granted the scope carrying this claim. Its launch
			// succeeds, so the failure surfaces as a 401 on every FHIR call with nothing to connect it
			// to a scope. Naming the remedy here is the only place anyone will find it.
			log.warn(
			    "A SMART access token passed verification but carries no '{}' claim, so it names no OpenMRS user. "
			            + "Grant this client the authorization server scope that emits that claim (the 'profile' scope "
			            + "in the OpenMRS realm), or set usernameClaim in smart-oauth2.json to a claim it does emit.",
			    config.getUsernameClaim());
			return null;
		}

		return new SmartAccessToken(username.trim(), claimAsString(claims, "patient"), claimAsString(claims, "encounter"),
		        scopesFrom(claims));
	}

	private String claimAsString(JWTClaimsSet claims, String name) {
		Object value = claims.getClaim(name);
		return value == null ? null : value.toString();
	}

	private Set<String> scopesFrom(JWTClaimsSet claims) {
		String scope = claimAsString(claims, "scope");

		if (scope == null || scope.trim().isEmpty()) {
			return Collections.emptySet();
		}

		return new HashSet<>(Arrays.asList(scope.trim().split("\\s+")));
	}

	/**
	 * What a verified SMART access token tells us: which OpenMRS user is acting, the launch context the
	 * authorization server granted, and the scopes it was granted with.
	 */
	public static final class SmartAccessToken {

		private final String username;

		private final String patient;

		private final String encounter;

		private final Set<String> scopes;

		SmartAccessToken(String username, String patient, String encounter, Set<String> scopes) {
			this.username = username;
			this.patient = patient;
			this.encounter = encounter;
			this.scopes = Collections.unmodifiableSet(scopes);
		}

		public String getUsername() {
			return username;
		}

		public String getPatient() {
			return patient;
		}

		public String getEncounter() {
			return encounter;
		}

		public Set<String> getScopes() {
			return scopes;
		}

		public boolean hasScope(String scope) {
			return scopes.contains(scope);
		}
	}
}

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

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
 * Verifies SMART access tokens: signature, {@code iss}, {@code aud} and a required {@code exp}.
 * Asymmetric algorithms only, so no token this module could itself sign is ever accepted.
 */
@Slf4j
public class SmartAccessTokenVerifier {

	private static final Set<JWSAlgorithm> ASYMMETRIC_ALGORITHMS = new HashSet<>(
	        Arrays.asList(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512, JWSAlgorithm.ES256, JWSAlgorithm.ES384,
	            JWSAlgorithm.ES512, JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512));

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private final SmartOAuth2Config config;

	private final ConfigurableJWTProcessor<SecurityContext> processor;

	/**
	 * @param config the configured issuer, audience and accepted algorithms
	 * @param keySource where to obtain the authorization server's signing keys. Injected rather than
	 *            built here so that tests can supply a fixed key set instead of reaching the network.
	 */
	public SmartAccessTokenVerifier(SmartOAuth2Config config, JWKSource<SecurityContext> keySource) {
		this.config = config;
		this.processor = new DefaultJWTProcessor<>();

		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(permittedAlgorithms(config), keySource));

		// nimbus enforces only the claims named here, so omitting exp would admit an endless token.
		List<String> required = Arrays.asList("exp", "iss", "aud");

		// aud is checked for membership per RFC 7519, since Keycloak issues multi-audience tokens.
		JWTClaimsSet expected = new JWTClaimsSet.Builder().issuer(config.getIssuer()).build();

		DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(config.getAudience(),
		        expected, new HashSet<>(required));
		claimsVerifier.setMaxClockSkew(config.getAllowedClockSkewSeconds());
		processor.setJWTClaimsSetVerifier(claimsVerifier);
	}

	/**
	 * Verifies a bearer token and extracts what the FHIR layer needs from it.
	 *
	 * @return its details, or null if unacceptable. Answer {@code invalid_token} rather than relaying
	 *         the logged reason, so a caller cannot probe for which check failed.
	 */
	public SmartAccessToken verify(String bearerToken) {
		if (bearerToken == null || bearerToken.isBlank()) {
			return null;
		}

		final JWTClaimsSet claims;
		try {
			claims = processor.process(bearerToken, null);
		}
		catch (Exception e) {
			// nimbus reports every failure as an exception, and none of the detail is safe to return.
			log.warn("Rejected a SMART access token: {}", e.getMessage(), e);
			return null;
		}

		String username = claimAsString(claims, config.getUsernameClaim());

		if (username == null) {
			// Nearly always a client never granted the scope that carries this claim.
			log.warn(
			    "A SMART access token passed verification but carries no '{}' claim, so it names no OpenMRS user. "
			            + "Grant this client the scope that emits that claim, or configure a claim it does emit.",
			    config.getUsernameClaim());
			return null;
		}

		return new SmartAccessToken(username, claimAsString(claims, "patient"), claimAsString(claims, "encounter"),
		        scopesFrom(claims));
	}

	/** Only asymmetric algorithms are honoured; anything else configured is ignored and logged. */
	private static Set<JWSAlgorithm> permittedAlgorithms(SmartOAuth2Config config) {
		String configured = config.getSignatureAlgorithms();

		if (configured == null || configured.isBlank()) {
			return ASYMMETRIC_ALGORITHMS;
		}

		Set<JWSAlgorithm> permitted = WHITESPACE.splitAsStream(configured.trim()).map(JWSAlgorithm::parse)
		        .filter(ASYMMETRIC_ALGORITHMS::contains).collect(toUnmodifiableSet());

		if (permitted.isEmpty()) {
			log.warn("None of the configured signature algorithms ({}) are asymmetric; falling back to all of them",
			    configured);
			return ASYMMETRIC_ALGORITHMS;
		}

		return permitted;
	}

	/** Bare strings only; a structured claim here is not something to coerce with toString(). */
	private String claimAsString(JWTClaimsSet claims, String name) {
		Object value = claims.getClaim(name);

		if (!(value instanceof String)) {
			return null;
		}

		String text = ((String) value).trim();
		return text.isEmpty() ? null : text;
	}

	private Set<String> scopesFrom(JWTClaimsSet claims) {
		String scope = claimAsString(claims, "scope");
		return scope == null ? Set.of() : WHITESPACE.splitAsStream(scope).collect(toUnmodifiableSet());
	}

	/**
	 * What a verified SMART access token tells us: which OpenMRS user is acting, the launch context the
	 * authorization server granted, and the scopes it was granted with.
	 */
	public record SmartAccessToken(String username, String patient, String encounter, Set<String> scopes) {

		public SmartAccessToken {
			scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
		}

		public boolean hasScope(String scope) {
			return scopes.contains(scope);
		}
	}
}

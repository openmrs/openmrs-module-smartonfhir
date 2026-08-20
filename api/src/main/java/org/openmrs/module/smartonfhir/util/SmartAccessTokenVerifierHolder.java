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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;

/**
 * Builds the {@link SmartAccessTokenVerifier} once and hands it out.
 * <p>
 * Construction is deferred to first use rather than done at module start, because it needs the
 * authorization server to be reachable: an OpenMRS that cannot start because Keycloak is not up yet
 * would be a poor trade. A failure here leaves SMART unavailable and everything else working.
 * <p>
 * Key retrieval is cached and rate-limited by nimbus's {@code JWKSourceBuilder} defaults. That matters:
 * a verifier that fetched JWKS per request would add a network round trip to every FHIR call, and would
 * turn a slow authorization server into slow FHIR responses. Cached, the common path is a signature
 * check against keys already in memory, and a key rotation is picked up on the next refresh.
 */
@Slf4j
public class SmartAccessTokenVerifierHolder {

	private static final int DISCOVERY_TIMEOUT_MILLIS = 10_000;

	private static volatile SmartAccessTokenVerifier verifier;

	private static volatile boolean attempted = false;

	private static volatile String resolvedJwksUri;

	/**
	 * @return the verifier, or null if SMART is unconfigured or the authorization server's keys could not
	 *         be located. Callers must refuse the request in that case rather than accept it unverified.
	 */
	public static SmartAccessTokenVerifier getVerifier() {
		if (!attempted) {
			synchronized (SmartAccessTokenVerifierHolder.class) {
				if (!attempted) {
					build();

					// Only latched once there is something to keep. It used to latch either way, so a single
					// transient failure to reach the authorization server -- a slow start, a DNS blip --
					// left this null and answered invalid_token to every FHIR request for the life of the
					// JVM, with nothing in production able to reset it. Now a failed attempt is retried by
					// the next request.
					attempted = verifier != null;
				}
			}
		}

		return verifier;
	}

	/** Discards the built verifier so the next call rebuilds it. For tests and for config changes. */
	public static synchronized void reset() {
		verifier = null;
		resolvedJwksUri = null;
		attempted = false;
	}

	/**
	 * Where token signing keys are actually being fetched from, so the discovery document advertises
	 * the same location the verifier uses rather than a second guess at it.
	 *
	 * @return the JWKS URI, or null if it could not be determined
	 */
	public static String getResolvedJwksUri() {
		getVerifier();
		return resolvedJwksUri;
	}

	private static void build() {
		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		if (config == null) {
			log.warn("SMART on FHIR is not configured, so access tokens cannot be verified");
			return;
		}

		String jwksUri = config.getJwksUri();

		if (jwksUri == null || jwksUri.trim().isEmpty()) {
			jwksUri = discoverJwksUri(config.getIssuer());
		}

		if (jwksUri == null) {
			log.error("Could not determine the authorization server's JWKS location. Set 'jwks-uri' in "
			        + "{} to avoid relying on discovery.", SmartOAuth2ConfigHolder.CONFIG_FILE_NAME);
			return;
		}

		try {
			JWKSource<SecurityContext> keySource = JWKSourceBuilder.create(new URL(jwksUri.trim())).retrying(true).build();
			verifier = new SmartAccessTokenVerifier(config, keySource);
			resolvedJwksUri = jwksUri.trim();
			log.info("SMART access tokens will be verified against {}", jwksUri);
		}
		catch (Exception e) {
			log.error("Could not build a token verifier from {}", jwksUri, e);
		}
	}

	/**
	 * Reads {@code jwks_uri} from the issuer's OpenID Connect discovery document, so that a deployment
	 * need only state the issuer. Keycloak publishes it, as does any conforming provider.
	 */
	private static String discoverJwksUri(String issuer) {
		final String discoveryUrl = issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration";

		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(discoveryUrl).openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(DISCOVERY_TIMEOUT_MILLIS);
			connection.setReadTimeout(DISCOVERY_TIMEOUT_MILLIS);
			connection.setRequestProperty("Accept", "application/json");

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				log.error("{} answered HTTP {}", discoveryUrl, connection.getResponseCode());
				return null;
			}

			try (InputStream in = connection.getInputStream()) {
				JsonNode discovered = new ObjectMapper().readTree(in);
				JsonNode jwksUri = discovered.get("jwks_uri");

				if (jwksUri == null || jwksUri.asText().isEmpty()) {
					log.error("{} advertises no jwks_uri", discoveryUrl);
					return null;
				}

				return jwksUri.asText();
			}
		}
		catch (Exception e) {
			log.error("Could not read {}", discoveryUrl, e);
			return null;
		}
	}
}

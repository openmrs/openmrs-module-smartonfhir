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
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;

/**
 * Builds the {@link SmartAccessTokenVerifier} on first use, because it needs the authorization
 * server reachable; a failure leaves SMART unavailable and is retried by the next request.
 */
@Slf4j
public class SmartAccessTokenVerifierHolder {

	private static volatile SmartAccessTokenVerifier verifier;

	private static volatile String resolvedJwksUri;

	/**
	 * @return the verifier, or null if SMART is unconfigured or the authorization server's keys could
	 *         not be located. Callers must refuse the request in that case rather than accept it
	 *         unverified.
	 */
	public static SmartAccessTokenVerifier getVerifier() {
		SmartAccessTokenVerifier built = verifier;

		if (built == null) {
			synchronized (SmartAccessTokenVerifierHolder.class) {
				built = verifier;

				if (built == null) {
					build();
					built = verifier;
				}
			}
		}

		return built;
	}

	/** Discards the built verifier so the next call rebuilds it. For tests and for config changes. */
	public static synchronized void reset() {
		verifier = null;
		resolvedJwksUri = null;
	}

	/**
	 * Where token signing keys are actually being fetched from, so the discovery document advertises
	 * the same location the verifier uses.
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

		if (jwksUri == null || jwksUri.isBlank()) {
			jwksUri = discoverJwksUri(config);
		}

		if (jwksUri == null) {
			log.error("Could not determine the authorization server's JWKS location. Set 'jwks_uri' in "
			        + "{} to avoid relying on discovery.",
			    SmartOAuth2ConfigHolder.CONFIG_FILE_NAME);
			return;
		}

		try {
			JWKSourceBuilder<SecurityContext> keys = JWKSourceBuilder.create(new URL(jwksUri.trim())).retrying(true);

			if (config.getJwksCacheSeconds() > 0) {
				keys.cache(config.getJwksCacheSeconds() * 1000L, JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT);
			}

			JWKSource<SecurityContext> keySource = keys.build();
			verifier = new SmartAccessTokenVerifier(config, keySource);
			resolvedJwksUri = jwksUri.trim();
			log.info("SMART access tokens will be verified against {}", jwksUri);
		}
		catch (Exception e) {
			log.error("Could not build a token verifier from {}", jwksUri, e);
		}
	}

	/**
	 * Reads {@code jwks_uri} from the issuer's discovery document, so only the issuer need be stated.
	 */
	private static String discoverJwksUri(SmartOAuth2Config config) {
		String issuer = config.getIssuer();

		if (issuer == null || !isHttpUrl(issuer)) {
			log.error("The configured issuer '{}' is not an http or https URL, so it cannot be discovered", issuer);
			return null;
		}

		// Only the final slash is ever there to strip.
		String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		final String discoveryUrl = base + "/.well-known/openid-configuration";
		final int timeout = config.getDiscoveryTimeoutSeconds() * 1000;

		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(discoveryUrl).openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(timeout);
			connection.setReadTimeout(timeout);
			connection.setRequestProperty("Accept", "application/json");

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				log.error("{} answered HTTP {}", discoveryUrl, connection.getResponseCode());
				return null;
			}

			try (InputStream in = connection.getInputStream()) {
				JsonNode jwksUri = new ObjectMapper().readTree(in).get("jwks_uri");

				if (jwksUri == null || jwksUri.asText().isBlank()) {
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
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static boolean isHttpUrl(String value) {
		String scheme = value.toLowerCase(Locale.ROOT);
		return scheme.startsWith("http://") || scheme.startsWith("https://");
	}
}

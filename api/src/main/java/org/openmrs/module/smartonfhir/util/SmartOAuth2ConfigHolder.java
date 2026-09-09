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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;

/**
 * Loads {@link SmartOAuth2Config} from the {@code smart.*} runtime properties. There is
 * deliberately no default: which authorization server to trust is a deployment decision.
 */
@Slf4j
public class SmartOAuth2ConfigHolder {

	private static volatile SmartOAuth2Config config;

	private static volatile boolean loadAttempted = false;

	/**
	 * @return the configuration, or null if none could be loaded. Callers must treat null as "SMART
	 *         support is not configured" and refuse to serve, rather than falling back to anything.
	 */
	public static SmartOAuth2Config getConfig() {
		if (!loadAttempted) {
			synchronized (SmartOAuth2ConfigHolder.class) {
				if (!loadAttempted) {
					load();
					// Latched on success only, so a transient read failure is retried rather than final.
					loadAttempted = config != null;
				}
			}
		}

		return config;
	}

	/** Discards the cached configuration so the next read reloads it. */
	public static synchronized void reset() {
		config = null;
		loadAttempted = false;
	}

	/**
	 * The reference application's image turns {@code OMRS_EXTRA_SMART_ISSUER} into
	 * {@code smart.issuer}, so a container is configured from its environment.
	 */
	public static final String ISSUER_PROPERTY = "smart.issuer";

	public static final String AUDIENCE_PROPERTY = "smart.audience";

	public static final String JWKS_URI_PROPERTY = "smart.jwks.uri";

	public static final String ADVERTISED_JWKS_URI_PROPERTY = "smart.advertised.jwks.uri";

	public static final String USERNAME_CLAIM_PROPERTY = "smart.username.claim";

	public static final String CLOCK_SKEW_PROPERTY = "smart.allowed.clock.skew.seconds";

	public static final String AUTHORIZATION_ENDPOINT_PROPERTY = "smart.authorization.endpoint";

	public static final String TOKEN_ENDPOINT_PROPERTY = "smart.token.endpoint";

	public static final String INTROSPECTION_ENDPOINT_PROPERTY = "smart.introspection.endpoint";

	public static final String REVOCATION_ENDPOINT_PROPERTY = "smart.revocation.endpoint";

	public static final String REGISTRATION_ENDPOINT_PROPERTY = "smart.registration.endpoint";

	public static final String END_SESSION_ENDPOINT_PROPERTY = "smart.end.session.endpoint";

	public static final String SIGNATURE_ALGORITHMS_PROPERTY = "smart.signature.algorithms";

	public static final String DISCOVERY_TIMEOUT_PROPERTY = "smart.discovery.timeout.seconds";

	public static final String JWKS_CACHE_PROPERTY = "smart.jwks.cache.seconds";

	private static void load() {
		final SmartOAuth2Config candidate = new SmartOAuth2Config();
		final List<String> applied = applyRuntimeProperties(candidate);

		if (!candidate.isUsable()) {
			if (applied.isEmpty()) {
				log.warn("SMART on FHIR is not configured: set at least {} and {} in the runtime properties. Until "
				        + "they exist, SMART endpoints will refuse requests rather than trust an unconfigured "
				        + "authorization server.",
				    ISSUER_PROPERTY, AUDIENCE_PROPERTY);
			} else {
				// An issuer without an audience would accept a token minted for another FHIR server.
				log.error("SMART on FHIR has {}, and needs both. Set the missing one as {} or {}.", describe(candidate),
				    ISSUER_PROPERTY, AUDIENCE_PROPERTY);
			}

			return;
		}

		config = candidate;

		log.info("SMART on FHIR configured for issuer {} and audience {}, from {}", candidate.getIssuer(),
		    candidate.getAudience(), String.join(", ", applied));
	}

	/**
	 * Reads every key from the runtime properties, leaving the model's own defaults where one is
	 * absent.
	 *
	 * @return the names of the properties that were applied, for logging
	 */
	private static List<String> applyRuntimeProperties(SmartOAuth2Config target) {
		final Properties properties;

		try {
			properties = Context.getRuntimeProperties();
		}
		catch (Exception e) {
			// Reached before the runtime properties exist, so the next lookup tries again.
			return Collections.emptyList();
		}

		if (properties == null) {
			return Collections.emptyList();
		}

		final List<String> applied = new ArrayList<>();

		final String issuer = trimmed(properties.getProperty(ISSUER_PROPERTY));
		if (issuer != null) {
			target.setIssuer(issuer);
			applied.add(ISSUER_PROPERTY);
		}

		final String audience = trimmed(properties.getProperty(AUDIENCE_PROPERTY));
		if (audience != null) {
			target.setAudience(audience);
			applied.add(AUDIENCE_PROPERTY);
		}

		final String jwksUri = trimmed(properties.getProperty(JWKS_URI_PROPERTY));
		if (jwksUri != null) {
			target.setJwksUri(jwksUri);
			applied.add(JWKS_URI_PROPERTY);
		}

		final String advertisedJwksUri = trimmed(properties.getProperty(ADVERTISED_JWKS_URI_PROPERTY));
		if (advertisedJwksUri != null) {
			target.setAdvertisedJwksUri(advertisedJwksUri);
			applied.add(ADVERTISED_JWKS_URI_PROPERTY);
		}

		final String usernameClaim = trimmed(properties.getProperty(USERNAME_CLAIM_PROPERTY));
		if (usernameClaim != null) {
			target.setUsernameClaim(usernameClaim);
			applied.add(USERNAME_CLAIM_PROPERTY);
		}

		final String algorithms = trimmed(properties.getProperty(SIGNATURE_ALGORITHMS_PROPERTY));
		if (algorithms != null) {
			target.setSignatureAlgorithms(algorithms);
			applied.add(SIGNATURE_ALGORITHMS_PROPERTY);
		}

		// Introspection is never derived, so a property is the only way to advertise one at all.
		applyEndpoints(properties, target, applied);
		applyClockSkew(properties, target, applied);
		applySeconds(properties, DISCOVERY_TIMEOUT_PROPERTY, target::setDiscoveryTimeoutSeconds,
		    target::getDiscoveryTimeoutSeconds, applied);
		applySeconds(properties, JWKS_CACHE_PROPERTY, target::setJwksCacheSeconds, target::getJwksCacheSeconds, applied);

		return applied;
	}

	private static void applyEndpoints(Properties properties, SmartOAuth2Config target, List<String> applied) {
		final String authorization = trimmed(properties.getProperty(AUTHORIZATION_ENDPOINT_PROPERTY));
		if (authorization != null) {
			target.setAuthorizationEndpoint(authorization);
			applied.add(AUTHORIZATION_ENDPOINT_PROPERTY);
		}

		final String token = trimmed(properties.getProperty(TOKEN_ENDPOINT_PROPERTY));
		if (token != null) {
			target.setTokenEndpoint(token);
			applied.add(TOKEN_ENDPOINT_PROPERTY);
		}

		final String introspection = trimmed(properties.getProperty(INTROSPECTION_ENDPOINT_PROPERTY));
		if (introspection != null) {
			target.setIntrospectionEndpoint(introspection);
			applied.add(INTROSPECTION_ENDPOINT_PROPERTY);
		}

		final String revocation = trimmed(properties.getProperty(REVOCATION_ENDPOINT_PROPERTY));
		if (revocation != null) {
			target.setRevocationEndpoint(revocation);
			applied.add(REVOCATION_ENDPOINT_PROPERTY);
		}

		final String registration = trimmed(properties.getProperty(REGISTRATION_ENDPOINT_PROPERTY));
		if (registration != null) {
			target.setRegistrationEndpoint(registration);
			applied.add(REGISTRATION_ENDPOINT_PROPERTY);
		}

		final String endSession = trimmed(properties.getProperty(END_SESSION_ENDPOINT_PROPERTY));
		if (endSession != null) {
			target.setEndSessionEndpoint(endSession);
			applied.add(END_SESSION_ENDPOINT_PROPERTY);
		}
	}

	/**
	 * A value that is not a number is refused rather than coerced, so a deployment cannot believe it
	 * widened the window in which tokens are accepted when it did not.
	 */
	private static void applyClockSkew(Properties properties, SmartOAuth2Config target, List<String> applied) {
		applySeconds(properties, CLOCK_SKEW_PROPERTY, target::setAllowedClockSkewSeconds, target::getAllowedClockSkewSeconds,
		    applied);
	}

	/**
	 * Refused rather than coerced, for the same reason: a default that silently stands is a surprise.
	 */
	private static void applySeconds(Properties properties, String property, IntConsumer setter, IntSupplier currentValue,
	        List<String> applied) {
		final String seconds = trimmed(properties.getProperty(property));

		if (seconds == null) {
			return;
		}

		try {
			setter.accept(Integer.parseInt(seconds));
			applied.add(property);
		}
		catch (NumberFormatException e) {
			log.error("Ignoring {}={}: it is not a whole number of seconds, so the default of {} stands", property, seconds,
			    currentValue.getAsInt());
		}
	}

	private static String describe(SmartOAuth2Config config) {
		if (config.getIssuer() != null) {
			return "an issuer but no audience";
		}

		return config.getAudience() != null ? "an audience but no issuer" : "neither an issuer nor an audience";
	}

	private static String trimmed(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

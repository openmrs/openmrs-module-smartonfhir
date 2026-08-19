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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.util.OpenmrsUtil;

/**
 * Loads {@link SmartOAuth2Config} from {@code config/smart-oauth2.json}, with individual keys
 * overridable by runtime properties so a container can configure it from its environment.
 */
@Slf4j
public class SmartOAuth2ConfigHolder {

	public static final String CONFIG_FILE_NAME = "smart-oauth2.json";

	private static final ObjectMapper objectMapper = new ObjectMapper();

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
	 * Runtime properties that override their corresponding key in the file.
	 * <p>
	 * The reference application image turns {@code OMRS_CONFIG_SMART_ISSUER} into {@code smart.issuer}
	 * and so on, so a container is configured through its environment rather than by writing a JSON
	 * file into the application data directory. Each property replaces only the key it names: the file
	 * is still the way to express what these five do not cover, and setting one no longer discards the
	 * rest of the file.
	 */
	public static final String ISSUER_PROPERTY = "smart.issuer";

	public static final String AUDIENCE_PROPERTY = "smart.audience";

	public static final String JWKS_URI_PROPERTY = "smart.jwks.uri";

	public static final String ADVERTISED_JWKS_URI_PROPERTY = "smart.advertised.jwks.uri";

	public static final String USERNAME_CLAIM_PROPERTY = "smart.username.claim";

	private static void load() {
		final File file = configFile();
		final boolean fileExists = file.canRead();

		SmartOAuth2Config candidate = fileExists ? readFile(file) : new SmartOAuth2Config();

		if (candidate == null) {
			// The file is there but unreadable or malformed; readFile has already said which.
			return;
		}

		final List<String> overridden = applyRuntimeProperties(candidate);

		if (!candidate.isUsable()) {
			if (!fileExists && overridden.isEmpty()) {
				log.warn("SMART on FHIR is not configured: expected {}, or {} and {} as runtime properties. Until one "
				        + "exists, SMART endpoints will refuse requests rather than trust an unconfigured "
				        + "authorization server.",
				    file.getAbsolutePath(), ISSUER_PROPERTY, AUDIENCE_PROPERTY);
			} else {
				// Half-configured is the dangerous case: an issuer without an audience would accept a
				// token minted for another FHIR server. Naming both sources matters because either
				// could have supplied the missing half.
				log.error(
				    "SMART on FHIR has {} after reading {} and the runtime properties, and needs both. Set the "
				            + "missing one in that file or as {} / {}.",
				    describe(candidate), file.getAbsolutePath(), ISSUER_PROPERTY, AUDIENCE_PROPERTY);
			}

			return;
		}

		config = candidate;

		if (overridden.isEmpty()) {
			log.info("SMART on FHIR configured for issuer {} and audience {}", candidate.getIssuer(),
			    candidate.getAudience());
		} else {
			// Which keys came from where, so that a property quietly overriding a file is visible in
			// the log rather than something an operator has to infer from behaviour.
			log.info("SMART on FHIR configured for issuer {} and audience {}; {} from runtime properties{}",
			    candidate.getIssuer(), candidate.getAudience(), String.join(", ", overridden),
			    fileExists ? " over " + CONFIG_FILE_NAME : "");
		}
	}

	private static SmartOAuth2Config readFile(File file) {
		try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
			return objectMapper.readValue(in, SmartOAuth2Config.class);
		}
		catch (IOException e) {
			log.error("Could not read {}", file.getAbsolutePath(), e);
			return null;
		}
	}

	/**
	 * Overrides the keys the runtime properties set, leaving every other key as the file left it.
	 *
	 * @return the names of the properties that were applied, for logging
	 */
	private static List<String> applyRuntimeProperties(SmartOAuth2Config target) {
		final Properties properties;

		try {
			properties = Context.getRuntimeProperties();
		}
		catch (Exception e) {
			// Reached before the runtime properties exist; the file alone can still serve.
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

		return applied;
	}

	private static String describe(SmartOAuth2Config config) {
		if (config.getIssuer() != null) {
			return "an issuer but no audience";
		}

		return config.getAudience() != null ? "an audience but no issuer" : "neither an issuer nor an audience";
	}

	private static String trimmed(String value) {
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static File configFile() {
		return Paths.get(OpenmrsUtil.getApplicationDataDirectory(), "config", CONFIG_FILE_NAME).toFile();
	}
}

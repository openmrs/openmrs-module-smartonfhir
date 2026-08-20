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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.util.OpenmrsUtil;

/**
 * Loads {@link SmartOAuth2Config} from {@code {application data
 * directory}/config/smart-oauth2.json}.
 * <p>
 * There is deliberately no classpath fallback and no built-in default. Which authorization server
 * to trust is a deployment decision, and a shipped default would mean a module that appears
 * configured while trusting something the deployment never chose.
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
					// Only latch on success. Latching either way meant one transient failure to
					// read the file -- a mount not ready, a moment's bad permissions -- left
					// SMART unconfigured for the life of the JVM, with nothing able to reset it.
					loadAttempted = config != null;
				}
			}
		}

		return config;
	}

	/**
	 * Discards the cached configuration so the next read reloads it. Intended for tests and for picking
	 * up an edited file without a restart.
	 */
	public static synchronized void reset() {
		config = null;
		loadAttempted = false;
	}

	private static void load() {
		final File file = configFile();

		if (!file.canRead()) {
			log.warn("SMART on FHIR is not configured: expected {}. Until it exists, SMART endpoints will refuse "
			        + "requests rather than trust an unconfigured authorization server.",
			    file.getAbsolutePath());
			return;
		}

		try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
			SmartOAuth2Config loaded = objectMapper.readValue(in, SmartOAuth2Config.class);

			if (!loaded.isUsable()) {
				log.error("{} must set both 'issuer' and 'audience'; ignoring it", file.getAbsolutePath());
				return;
			}

			config = loaded;
			log.info("SMART on FHIR configured for issuer {} and audience {}", loaded.getIssuer(), loaded.getAudience());
		}
		catch (IOException e) {
			log.error("Could not read {}", file.getAbsolutePath(), e);
		}
	}

	private static File configFile() {
		return Paths.get(OpenmrsUtil.getApplicationDataDirectory(), "config", CONFIG_FILE_NAME).toFile();
	}
}

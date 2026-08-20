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
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.model.SmartSecretKey;
import org.openmrs.util.OpenmrsUtil;

@Slf4j
public class SmartSecretKeyHolder {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private static volatile byte[] secretKey = null;

	public static byte[] getSecretKey() {
		if (secretKey == null) {
			synchronized (SmartSecretKeyHolder.class) {
				if (secretKey == null) {
					loadSecretKey();
				}
			}
		}

		return secretKey;
	}

	private static void loadSecretKey() {
		final File file = Paths.get(OpenmrsUtil.getApplicationDataDirectory(), "config", "smart-secret-key.json").toFile();

		if (!file.canRead()) {
			log.warn("No SMART launch secret at {}. The launch handshake with the authorization server cannot be "
			        + "verified until one exists, and launches will be refused.",
			    file.getAbsolutePath());
			return;
		}

		try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
			String encoded = objectMapper.readValue(in, SmartSecretKey.class).getSmartSharedSecretKey();

			if (encoded == null || encoded.trim().isEmpty()) {
				log.error("{} does not set 'smart-shared-secret-key'", file.getAbsolutePath());
				return;
			}

			secretKey = Base64.getDecoder().decode(encoded.trim());
		}
		catch (IOException e) {
			log.error("Could not read {}", file.getAbsolutePath(), e);
		}
		catch (IllegalArgumentException e) {
			log.error("The value of 'smart-shared-secret-key' in {} is not valid base64", file.getAbsolutePath(), e);
		}
	}
}

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

import java.util.Base64;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;

@Slf4j
public class SmartSecretKeyHolder {

	/**
	 * The runtime property the secret is read from, in preference to the configuration file, so a
	 * container can pass it in the environment as {@code OMRS_EXTRA_SMART_LAUNCH_SECRET}.
	 */
	public static final String SECRET_RUNTIME_PROPERTY = "smart.launch.secret";

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
		if (loadFromRuntimeProperty()) {
			return;
		}

		log.warn(
		    "No SMART launch secret: set {} in the runtime properties. The launch handshake with the "
		            + "authorization server cannot be verified until one exists, and launches will be refused.",
		    SECRET_RUNTIME_PROPERTY);
	}

	/**
	 * Reads the secret from {@link #SECRET_RUNTIME_PROPERTY}. A property that is set but unusable still
	 * counts as set, so the error is reported rather than hidden by a fallback to the file.
	 */
	private static boolean loadFromRuntimeProperty() {
		String encoded;

		try {
			encoded = Context.getRuntimeProperties().getProperty(SECRET_RUNTIME_PROPERTY);
		}
		catch (Exception e) {
			// Reached before the runtime properties are available, which the file path can still serve.
			return false;
		}

		if (encoded == null || encoded.isBlank()) {
			return false;
		}

		try {
			secretKey = Base64.getDecoder().decode(encoded.trim());
		}
		catch (IllegalArgumentException e) {
			log.error("The runtime property {} is not valid base64, so no launch can be verified", SECRET_RUNTIME_PROPERTY,
			    e);
		}

		return true;
	}
}

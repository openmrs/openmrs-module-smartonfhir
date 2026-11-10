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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openmrs.util.OpenmrsUtil;

class SmartAccessTokenVerifierHolderTest {

	private static final String UNDISCOVERABLE = "{\"issuer\":\"not-a-url\",\"audience\":\"https://openmrs.example.org\"}";

	private static final String WITH_KEYS = "{\"issuer\":\"not-a-url\",\"audience\":\"https://openmrs.example.org\","
	        + "\"jwks_uri\":\"http://127.0.0.1:1/certs\"}";

	@TempDir
	Path appData;

	private String previousAppDataDirectory;

	@BeforeEach
	void useATemporaryApplicationDataDirectory() {
		previousAppDataDirectory = OpenmrsUtil.getApplicationDataDirectory();
		OpenmrsUtil.setApplicationDataDirectory(appData.toString());
		SmartOAuth2ConfigHolder.reset();
		SmartAccessTokenVerifierHolder.reset();
	}

	@AfterEach
	void restore() {
		OpenmrsUtil.setApplicationDataDirectory(previousAppDataDirectory);
		SmartOAuth2ConfigHolder.reset();
		SmartAccessTokenVerifierHolder.reset();
	}

	@Test
	void shouldNotRetryAFailedBuildOnTheNextRequest() throws Exception {
		writeConfig(UNDISCOVERABLE);
		assertNull(SmartAccessTokenVerifierHolder.getVerifier());

		// Now buildable, but the failure still stands, so this request does not wait on another attempt.
		writeConfig(WITH_KEYS);
		assertNull(SmartAccessTokenVerifierHolder.getVerifier());
	}

	@Test
	void shouldBuildOnceTheFailureIsCleared() throws Exception {
		writeConfig(UNDISCOVERABLE);
		assertNull(SmartAccessTokenVerifierHolder.getVerifier());

		writeConfig(WITH_KEYS);
		SmartAccessTokenVerifierHolder.reset();
		assertNotNull(SmartAccessTokenVerifierHolder.getVerifier());
	}

	@Test
	void shouldRefuseAJwksLocationThatIsNotHttp() throws Exception {
		writeConfig("{\"issuer\":\"not-a-url\",\"audience\":\"https://openmrs.example.org\","
		        + "\"jwks_uri\":\"file:///etc/certs.json\"}");

		assertNull(SmartAccessTokenVerifierHolder.getVerifier());
	}

	@ValueSource(ints = { 1, 44 })
	@ParameterizedTest(name = "still builds with jwks_cache_seconds {0}")
	void shouldRaiseACacheLifetimeTooShortForTheKeyCache(int seconds) throws Exception {
		writeConfig("{\"issuer\":\"not-a-url\",\"audience\":\"https://openmrs.example.org\","
		        + "\"jwks_uri\":\"http://127.0.0.1:1/certs\",\"jwks_cache_seconds\":" + seconds + "}");

		assertNotNull(SmartAccessTokenVerifierHolder.getVerifier());
	}

	private void writeConfig(String json) throws Exception {
		File config = appData.resolve("config").toFile();
		assertTrue(config.mkdirs() || config.isDirectory());
		Files.write(config.toPath().resolve(SmartOAuth2ConfigHolder.CONFIG_FILE_NAME),
		    json.getBytes(StandardCharsets.UTF_8));
		SmartOAuth2ConfigHolder.reset();
	}
}

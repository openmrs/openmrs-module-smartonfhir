/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SmartSecretKeyTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void shouldReadTheSecretUnderItsKey() throws Exception {
		assertEquals("abc",
		    objectMapper.readValue("{\"smart_shared_secret_key\":\"abc\"}", SmartSecretKey.class).getSmartSharedSecretKey());
	}

	@Test
	public void shouldReadASecretFileWrittenForTheHyphenatedKey() throws Exception {
		assertEquals("abc",
		    objectMapper.readValue("{\"smart-shared-secret-key\":\"abc\"}", SmartSecretKey.class).getSmartSharedSecretKey());
	}
}

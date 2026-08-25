/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.openmrs.module.smartonfhir.util.PrivilegeConstants;

/**
 * A privilege the service demands but config.xml never creates is one nobody can hold, so the
 * constants and the module configuration have to name the same strings.
 */
public class PrivilegeConstantsTest {

	@Test
	public void everyConstantIsDeclaredInConfigXml() throws IOException {
		String config = new String(Files.readAllBytes(Paths.get("src/main/resources/config.xml")), StandardCharsets.UTF_8);

		for (String privilege : new String[] { PrivilegeConstants.GET_SMART_APPS, PrivilegeConstants.MANAGE_SMART_APPS }) {
			assertTrue(config.contains("<name>" + privilege + "</name>"),
			    "config.xml declares no privilege named '" + privilege + "', so nothing can grant it");
		}
	}
}

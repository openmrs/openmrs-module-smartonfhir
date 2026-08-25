/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.smart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.authentication.ConfigurableAuthenticationScheme;
import org.openmrs.module.authentication.web.WebAuthenticationScheme;

/**
 * This scheme is reached through {@code Context.authenticate}, never through a login page.
 */
public class SmartBearerTokenAuthenticationSchemeTest {

	/**
	 * Extending {@code WebAuthenticationScheme} makes the module's filter redirect to a null URL, which
	 * every deployment then had to work around with {@code authentication.whiteList=/*}.
	 */
	@Test
	@DisplayName("is not an interactive scheme, so the module's filter leaves requests alone")
	public void shouldNotBeAWebAuthenticationScheme() {
		SmartBearerTokenAuthenticationScheme scheme = new SmartBearerTokenAuthenticationScheme();

		// Asked of the class, so this still compiles and fails if the base class is put back.
		assertFalse(WebAuthenticationScheme.class.isAssignableFrom(SmartBearerTokenAuthenticationScheme.class),
		    "extending WebAuthenticationScheme makes the module's filter gatekeep every URL again");
		// Still configurable, which is how the module passes it a scheme id and its config.* properties.
		assertTrue(scheme instanceof ConfigurableAuthenticationScheme);
	}

	@Test
	@DisplayName("keeps the scheme id it was configured with")
	public void shouldKeepItsSchemeId() {
		SmartBearerTokenAuthenticationScheme scheme = new SmartBearerTokenAuthenticationScheme();

		scheme.configure("smartbearer", new Properties());

		assertEquals("smartbearer", scheme.getSchemeId());
	}
}

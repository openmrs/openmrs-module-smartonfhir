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
	 * The regression this guards is expensive and quiet. The authentication module's filter is mapped
	 * to every URL, but collects credentials, redirects to a login page and consults
	 * {@code authentication.whiteList} only when the active scheme is a
	 * {@code WebAuthenticationScheme}; for anything else it passes the request down the chain. This
	 * scheme used to extend that base and return nulls, which does not mean "carry on": the filter fell
	 * through to {@code sendRedirect(null)} and the OpenMRS root became a redirect loop, so every
	 * deployment had to set {@code authentication.whiteList=/*} and switch the module's gatekeeping off
	 * wholesale. Re-adding the base class would bring that back without failing anything else.
	 */
	@Test
	@DisplayName("is not an interactive scheme, so the module's filter leaves requests alone")
	public void shouldNotBeAWebAuthenticationScheme() {
		SmartBearerTokenAuthenticationScheme scheme = new SmartBearerTokenAuthenticationScheme();

		// Asked of the class rather than the instance, so this still compiles -- and fails -- if the
		// base class is put back.
		assertFalse(WebAuthenticationScheme.class.isAssignableFrom(SmartBearerTokenAuthenticationScheme.class),
		    "extending WebAuthenticationScheme makes the module's filter gatekeep every URL again");
		// Still configurable, which is how the authentication module passes it a scheme id and its
		// config.* properties after instantiating it from authentication.scheme.<id>.type.
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

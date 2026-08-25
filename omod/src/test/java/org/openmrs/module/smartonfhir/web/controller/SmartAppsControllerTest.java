/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.util.SmartAppRegistry;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.v1_0.controller.jupiter.RestControllerTestUtils;

public class SmartAppsControllerTest extends RestControllerTestUtils {

	private static final String URI = "smartonfhir/apps";

	private Properties previousRuntimeProperties;

	@BeforeEach
	public void isolateTheRuntimeProperties() {
		previousRuntimeProperties = Context.getRuntimeProperties();
		Context.setRuntimeProperties(new Properties());
		SmartAppRegistry.reset();
	}

	@AfterEach
	public void restore() {
		Context.setRuntimeProperties(previousRuntimeProperties == null ? new Properties() : previousRuntimeProperties);
		SmartAppRegistry.reset();
	}

	@Test
	public void shouldListTheDeclaredAppsWithoutTheirAddresses() throws Exception {
		declare("smart.app.vitals.name", "Vitals");
		declare("smart.app.vitals.launchurl", "https://vitals.example.org/launch.html");
		declare("smart.app.vitals.clientid", "vitals-review");

		SimpleObject body = deserialize(handle(newGetRequest(URI)));

		List<Map<String, Object>> apps = body.get("apps");
		assertThat(apps, hasSize(1));
		assertThat(apps.get(0), hasKey("name"));
		// A chart screen launches by id, so the address and client id are of no use to it.
		assertThat(apps.get(0), not(hasKey("launchUrl")));
		assertThat(apps.get(0), not(hasKey("clientId")));
	}

	/** The point of the report: the deployment that has to fix the configuration can see it. */
	@Test
	public void shouldReportRefusalsToAnAdministrator() throws Exception {
		declare("smart.app.orphan.name", "Declared but unlaunchable");

		SimpleObject body = deserialize(handle(newGetRequest(URI)));

		List<String> problems = body.get("problems");
		assertThat(problems, hasSize(1));
	}

	/** Which apps exist is not public: it describes what a deployment has integrated with. */
	@Test
	public void shouldRefuseAnUnauthenticatedCaller() {
		Context.logout();

		assertThrows(APIAuthenticationException.class, () -> handle(newGetRequest(URI)));
	}

	private void declare(String key, String value) {
		Properties properties = Context.getRuntimeProperties();
		properties.setProperty(key, value);
		Context.setRuntimeProperties(properties);
		SmartAppRegistry.reset();
	}
}

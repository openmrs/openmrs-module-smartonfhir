/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.servlet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.util.SmartAppRegistry;
import org.openmrs.util.PrivilegeConstants;

/**
 * What a chart screen is handed, and what it is not: the app list needs authentication, and the
 * refusal report needs more, since those messages name the property keys a deployment set.
 */
@ExtendWith(MockitoExtension.class)
public class SmartAppsServletTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

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
	public void doGet_shouldRefuseAnUnauthenticatedCaller() throws Exception {
		try (MockedStatic<Context> context = Mockito.mockStatic(Context.class)) {
			context.when(Context::isAuthenticated).thenReturn(false);

			new SmartAppsServlet().doGet(request, response);
		}

		verify(response).sendError(anyInt(), anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void doGet_shouldListTheDeclaredAppsWithoutTheirAddresses() throws Exception {
		declare("smart.app.vitals.name", "Vitals");
		declare("smart.app.vitals.launchurl", "https://vitals.example.org/launch.html");
		declare("smart.app.vitals.clientid", "vitals-review");

		Map<String, Object> body = serve(false);

		List<Map<String, String>> apps = (List<Map<String, String>>) body.get("apps");
		assertThat(apps, hasSize(1));
		assertThat(apps.get(0), hasKey("name"));
		// A chart screen launches by id, so the address and client id are of no use to it.
		assertThat(apps.get(0), not(hasKey("launchUrl")));
		assertThat(apps.get(0), not(hasKey("clientId")));
	}

	@Test
	public void doGet_shouldNotReportRefusalsToAUserWithoutAdministrativeAccess() throws Exception {
		declare("smart.app.orphan.name", "Declared but unlaunchable");

		assertThat(serve(false), not(hasKey("problems")));
	}

	/** The point of the report: the deployment that has to fix the configuration can see it. */
	@Test
	@SuppressWarnings("unchecked")
	public void doGet_shouldReportRefusalsToAnAdministrator() throws Exception {
		declare("smart.app.orphan.name", "Declared but unlaunchable");

		Map<String, Object> body = serve(true);

		List<String> problems = (List<String>) body.get("problems");
		assertThat(problems, hasSize(1));
		assertThat(problems.get(0), containsString("'orphan'"));
	}

	private void declare(String key, String value) {
		Properties properties = Context.getRuntimeProperties();
		properties.setProperty(key, value);
		Context.setRuntimeProperties(properties);
		SmartAppRegistry.reset();
	}

	/**
	 * Serves the app list to an authenticated caller who does or does not hold the privilege the
	 * refusal report is gated on. The registry is left unmocked, since that is what is worth asserting.
	 */
	private Map<String, Object> serve(boolean administrator) throws Exception {
		StringWriter written = new StringWriter();
		Properties declared = Context.getRuntimeProperties();

		when(response.getWriter()).thenReturn(new PrintWriter(written));

		try (MockedStatic<Context> context = Mockito.mockStatic(Context.class)) {
			context.when(Context::isAuthenticated).thenReturn(true);
			context.when(Context::getRuntimeProperties).thenReturn(declared);
			context.when(() -> Context.hasPrivilege(PrivilegeConstants.VIEW_ADMIN_FUNCTIONS)).thenReturn(administrator);

			new SmartAppsServlet().doGet(request, response);
		}

		return MAPPER.readValue(written.toString(), Map.class);
	}
}

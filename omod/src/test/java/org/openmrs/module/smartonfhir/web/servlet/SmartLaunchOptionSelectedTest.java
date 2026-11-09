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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.module.smartonfhir.util.SmartOAuth2ConfigHolder;
import org.openmrs.module.smartonfhir.web.filter.AuthenticationByPassFilter;

/**
 * A session the bypass filter opened for a launch must not outlive that launch, however it ends.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SmartLaunchOptionSelectedTest {

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private HttpSession session;

	private MockedStatic<Context> context;

	private MockedStatic<SmartOAuth2ConfigHolder> oauth2Config;

	@BeforeEach
	public void setUp() throws Exception {
		context = mockStatic(Context.class);
		context.when(Context::isAuthenticated).thenReturn(true);

		SmartOAuth2Config config = new SmartOAuth2Config();
		config.setIssuer("https://kc.example.org/realms/openmrs");
		oauth2Config = mockStatic(SmartOAuth2ConfigHolder.class);
		oauth2Config.when(SmartOAuth2ConfigHolder::getConfig).thenReturn(config);

		when(request.getSession(false)).thenReturn(session);
		when(request.getParameter("patientId")).thenReturn("patient-uuid");
		when(request.getParameter("token"))
		        .thenReturn(URLEncoder.encode("https://evil.example/cb?t={APP_TOKEN}", StandardCharsets.UTF_8.name()));
	}

	@AfterEach
	public void restore() {
		context.close();
		oauth2Config.close();
	}

	@Test
	public void doGet_shouldEndTheBypassSessionWhenTheLaunchIsRefused() throws Exception {
		when(session.getAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS)).thenReturn(true);

		new SmartLaunchOptionSelected().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		context.verify(Context::logout);
		verify(session).removeAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS);
	}

	@Test
	public void doGet_shouldRefuseABracketThatBrowsersReadAsPartOfUserinfo() throws Exception {
		when(request.getParameter("token")).thenReturn(
		    URLEncoder.encode("https://kc.example.org[@evil.example/cb?t={APP_TOKEN}", StandardCharsets.UTF_8.name()));

		new SmartLaunchOptionSelected().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldLeaveASessionTheClinicianOpenedThemselves() throws Exception {
		when(session.getAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS)).thenReturn(null);

		new SmartLaunchOptionSelected().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		context.verify(Context::logout, never());
	}
}

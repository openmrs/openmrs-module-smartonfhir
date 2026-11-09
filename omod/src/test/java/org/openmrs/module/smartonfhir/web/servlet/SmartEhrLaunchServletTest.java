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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartOAuth2ConfigHolder;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;

/**
 * The launch this servlet emits is what the access confirmation later redeems for the clinician.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SmartEhrLaunchServletTest {

	private static final String APP_LAUNCH_URL = "https://app.example.org/launch";

	private static final String CONFIRM_TARGET = "https://kc.example.org/realms/openmrs/login-actions/action-token?t=";

	private static final byte[] SECRET = new byte[32];

	static {
		Arrays.fill(SECRET, (byte) 7);
	}

	@Mock
	private HttpServletRequest launchRequest;

	@Mock
	private HttpServletResponse launchResponse;

	@Mock
	private HttpServletRequest confirmRequest;

	@Mock
	private HttpServletResponse confirmResponse;

	private MockedStatic<Context> context;

	private MockedStatic<SmartSecretKeyHolder> secret;

	private MockedStatic<SmartOAuth2ConfigHolder> oauth2Config;

	@BeforeEach
	public void signInAClinician() {
		User clinician = new User();
		clinician.setUsername("clinician");

		IServerAddressStrategy addressStrategy = mock(IServerAddressStrategy.class);
		when(addressStrategy.determineServerBase(any(), any())).thenReturn("https://ehr.example.org/ws/fhir2/R4");

		context = mockStatic(Context.class);
		context.when(Context::getAuthenticatedUser).thenReturn(clinician);
		context.when(() -> Context.getRegisteredComponent("openmrsFhirAddressStrategy", IServerAddressStrategy.class))
		        .thenReturn(addressStrategy);

		secret = mockStatic(SmartSecretKeyHolder.class);
		secret.when(SmartSecretKeyHolder::getSecretKey).thenReturn(SECRET);

		SmartOAuth2Config config = new SmartOAuth2Config();
		config.setIssuer("https://kc.example.org/realms/openmrs");
		oauth2Config = mockStatic(SmartOAuth2ConfigHolder.class);
		oauth2Config.when(SmartOAuth2ConfigHolder::getConfig).thenReturn(config);

		when(launchRequest.getParameter("launchUrl")).thenReturn(APP_LAUNCH_URL);
		when(launchResponse.encodeRedirectURL(anyString())).thenAnswer(i -> i.getArgument(0));
	}

	@AfterEach
	public void restore() {
		context.close();
		secret.close();
		oauth2Config.close();
	}

	@Test
	public void doGet_shouldEmitALaunchTheAccessConfirmationRedeemsForTheSameClinician() throws Exception {
		when(launchRequest.getParameter("launchContext")).thenReturn("patient");
		when(launchRequest.getParameter("patientId")).thenReturn("patient-uuid");
		when(launchRequest.getParameter("visitId")).thenReturn("visit-uuid");

		String launch = launch();

		when(confirmRequest.getParameter("launch")).thenReturn(launch);
		when(confirmRequest.getParameter("token"))
		        .thenReturn(URLEncoder.encode(CONFIRM_TARGET + "{APP_TOKEN}", StandardCharsets.UTF_8.name()));

		new SmartAccessConfirmation().doGet(confirmRequest, confirmResponse);

		ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
		verify(confirmResponse).sendRedirect(redirect.capture());
		verify(confirmResponse, never()).sendError(anyInt(), anyString());

		String appToken = URLDecoder.decode(redirect.getValue().substring(CONFIRM_TARGET.length()),
		    StandardCharsets.UTF_8.name());
		JWTClaimsSet claims = SmartLaunchTokens.verify(appToken, SECRET);
		assertNotNull(claims);
		assertEquals("clinician", claims.getSubject());
		assertEquals("patient-uuid", claims.getClaim(SmartAccessConfirmation.PATIENT_NAME));
		assertEquals("visit-uuid", claims.getClaim(SmartAccessConfirmation.VISIT_NAME));
		assertNotEquals("patient-uuid", launch);
	}

	@Test
	public void doGet_shouldRefuseALaunchWithNoAuthenticatedUser() throws Exception {
		context.when(Context::getAuthenticatedUser).thenReturn(null);
		when(launchRequest.getParameter("patientId")).thenReturn("patient-uuid");

		new SmartEhrLaunchServlet().doGet(launchRequest, launchResponse);

		verify(launchResponse).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		verify(launchResponse, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseAnEncounterLaunchWithNoVisit() throws Exception {
		when(launchRequest.getParameter("launchContext")).thenReturn("encounter");
		when(launchRequest.getParameter("patientId")).thenReturn("patient-uuid");

		new SmartEhrLaunchServlet().doGet(launchRequest, launchResponse);

		verify(launchResponse).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(launchResponse, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseALaunchForAUserWithNoIdentity() throws Exception {
		context.when(Context::getAuthenticatedUser).thenReturn(new User());
		when(launchRequest.getParameter("patientId")).thenReturn("patient-uuid");

		new SmartEhrLaunchServlet().doGet(launchRequest, launchResponse);

		verify(launchResponse).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), anyString());
		verify(launchResponse, never()).sendRedirect(anyString());
	}

	private String launch() throws Exception {
		new SmartEhrLaunchServlet().doGet(launchRequest, launchResponse);

		ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
		verify(launchResponse).sendRedirect(redirect.capture());
		String prefix = APP_LAUNCH_URL + "?iss=https://ehr.example.org/ws/fhir2/R4&launch=";
		assertTrue(redirect.getValue().startsWith(prefix), redirect.getValue());

		return URLDecoder.decode(redirect.getValue().substring(prefix.length()), StandardCharsets.UTF_8.name());
	}
}

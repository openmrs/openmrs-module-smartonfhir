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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.openmrs.module.smartonfhir.util.SmartLaunchContextService;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartOAuth2ConfigHolder;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;

/** The token this servlet signs names the clinician, so where it is sent decides who can use it. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SmartAccessConfirmationTest {

	private static final byte[] SECRET = new byte[32];

	static {
		Arrays.fill(SECRET, (byte) 7);
	}

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	private MockedStatic<Context> context;

	private MockedStatic<SmartSecretKeyHolder> secret;

	private MockedStatic<SmartOAuth2ConfigHolder> oauth2Config;

	@BeforeEach
	public void signInAClinician() {
		User clinician = new User();
		clinician.setUsername("clinician");

		context = mockStatic(Context.class);
		context.when(Context::getAuthenticatedUser).thenReturn(clinician);

		secret = mockStatic(SmartSecretKeyHolder.class);
		secret.when(SmartSecretKeyHolder::getSecretKey).thenReturn(SECRET);

		SmartOAuth2Config config = new SmartOAuth2Config();
		config.setIssuer("https://kc.example.org/realms/openmrs");
		oauth2Config = mockStatic(SmartOAuth2ConfigHolder.class);
		oauth2Config.when(SmartOAuth2ConfigHolder::getConfig).thenReturn(config);
	}

	@AfterEach
	public void restore() {
		context.close();
		secret.close();
		oauth2Config.close();
	}

	@Test
	public void doGet_shouldRefuseToSendTheClinicianTokenOutsideTheAuthorizationServer() throws Exception {
		givenLaunch("https://evil.example/cb?t={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseAHostThatOnlyStartsWithTheIssuers() throws Exception {
		givenLaunch("https://kc.example.org.evil.example/cb?t={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseABackslashBeforeUserinfoThatBrowsersReadAsAPathSeparator() throws Exception {
		givenLaunch("https://evil.example\\@kc.example.org/realms/openmrs/x?t={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseATargetCarryingUserinfo() throws Exception {
		givenLaunch("https://evil.example@kc.example.org/realms/openmrs/x?t={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseABracketThatBrowsersReadAsPartOfUserinfo() throws Exception {
		givenLaunch("https://kc.example.org[@evil.example/cb?t={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldRefuseAForeignTargetBeforeAnyoneIsSignedIn() throws Exception {
		context.when(Context::getAuthenticatedUser).thenReturn(null);
		when(request.getParameter("token")).thenReturn(encode("https://evil.example/cb?t={APP_TOKEN}"));

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldSendTheClinicianTokenToTheAuthorizationServer() throws Exception {
		String target = "https://kc.example.org/realms/openmrs/login-actions/action-token?t=";
		givenLaunch(target + "{APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
		verify(response).sendRedirect(redirect.capture());
		verify(response, never()).sendError(anyInt(), anyString());
		assertTrue(redirect.getValue().startsWith(target), redirect.getValue());

		String appToken = URLDecoder.decode(redirect.getValue().substring(target.length()), StandardCharsets.UTF_8.name());
		JWTClaimsSet claims = SmartLaunchTokens.verify(appToken, SECRET);
		assertNotNull(claims);
		assertEquals("clinician", claims.getSubject());
		assertEquals("patient-uuid", claims.getClaim(SmartAccessConfirmation.PATIENT_NAME));
	}

	@Test
	public void doGet_shouldSendTheClinicianTokenToAnIssuerOnAnExplicitPort() throws Exception {
		givenIssuer("http://localhost:8180/realms/openmrs");
		givenLaunch("http://localhost:8180/realms/openmrs/login-actions/authenticate?client_id=x&app_token={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendRedirect(anyString());
		verify(response, never()).sendError(anyInt(), anyString());
	}

	@Test
	public void doGet_shouldRefuseTheIssuersHostOnAnotherPort() throws Exception {
		givenIssuer("http://localhost:8180/realms/openmrs");
		givenLaunch("http://localhost/realms/openmrs/login-actions/authenticate?app_token={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldTreatAnIssuerSpellingOutTheDefaultPortAsTheSameOrigin() throws Exception {
		givenIssuer("https://kc.example.org:443/realms/openmrs/");
		givenLaunch("https://kc.example.org/realms/openmrs/login-actions/authenticate?app_token={APP_TOKEN}");

		new SmartAccessConfirmation().doGet(request, response);

		verify(response).sendRedirect(anyString());
		verify(response, never()).sendError(anyInt(), anyString());
	}

	private void givenIssuer(String issuer) {
		SmartOAuth2Config config = new SmartOAuth2Config();
		config.setIssuer(issuer);
		oauth2Config.when(SmartOAuth2ConfigHolder::getConfig).thenReturn(config);
	}

	private void givenLaunch(String target) throws Exception {
		String handle = new SmartLaunchContextService().issue("clinician", "patient-uuid", null);
		when(request.getParameter("launch")).thenReturn(handle);
		when(request.getParameter("token")).thenReturn(encode(target));
	}

	private static String encode(String target) throws Exception {
		return URLEncoder.encode(target, StandardCharsets.UTF_8.name());
	}
}

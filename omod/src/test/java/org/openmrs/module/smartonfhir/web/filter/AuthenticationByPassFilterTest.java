/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Credentials;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AuthenticationByPassFilterTest {

	private static final byte[] SECRET = new byte[32];

	private static final byte[] SOMEONE_ELSES_SECRET = new byte[32];

	static {
		Arrays.fill(SECRET, (byte) 7);
		Arrays.fill(SOMEONE_ELSES_SECRET, (byte) 9);
	}

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private HttpSession session;

	@Mock
	private FilterChain chain;

	private MockedStatic<Context> context;

	private MockedStatic<SmartSecretKeyHolder> secret;

	private final AuthenticationByPassFilter filter = new AuthenticationByPassFilter();

	@BeforeEach
	public void setUp() {
		FilterConfig config = mock(FilterConfig.class);
		when(config.getInitParameter("validUrls")).thenReturn("/ms/smartLaunchOptionSelected");
		filter.init(config);

		context = mockStatic(Context.class);
		context.when(Context::getUserContext).thenReturn(mock(UserContext.class));
		context.when(Context::getLocationService).thenReturn(mock(LocationService.class));

		secret = mockStatic(SmartSecretKeyHolder.class);
		secret.when(SmartSecretKeyHolder::getSecretKey).thenReturn(SECRET);

		when(request.getContextPath()).thenReturn("/openmrs");
		when(request.getRequestURI()).thenReturn("/openmrs/ms/smartLaunchOptionSelected");
		when(request.getSession()).thenReturn(session);
		when(request.getSession(false)).thenReturn(session);
	}

	@AfterEach
	public void restore() {
		context.close();
		secret.close();
	}

	@Test
	public void doFilter_shouldSignInTheUserASignedLaunchTokenNames() throws Exception {
		givenUserToken(SmartLaunchTokens.sign(userToken().subject("clinician").build(), SECRET));

		filter.doFilter(request, response, chain);

		ArgumentCaptor<Credentials> credentials = ArgumentCaptor.forClass(Credentials.class);
		context.verify(() -> Context.authenticate(credentials.capture()));
		assertEquals("clinician", credentials.getValue().getClientName());
		verify(session).setAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS, true);
		verify(chain).doFilter(request, response);
	}

	@Test
	public void doFilter_shouldRefuseALaunchTokenThatNamesNoUser() throws Exception {
		givenUserToken(SmartLaunchTokens.sign(userToken().claim("patient", "p").build(), SECRET));

		filter.doFilter(request, response, chain);

		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		context.verify(() -> Context.authenticate(any(Credentials.class)), never());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void doFilter_shouldRefuseAUserTokenSignedWithAnotherSecret() throws Exception {
		givenUserToken(SmartLaunchTokens.sign(userToken().subject("clinician").build(), SOMEONE_ELSES_SECRET));

		filter.doFilter(request, response, chain);

		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		context.verify(() -> Context.authenticate(any(Credentials.class)), never());
	}

	@Test
	public void doFilter_shouldRefuseATokenSignedWithTheLaunchSecretThatIsNotAUserToken() throws Exception {
		givenUserToken(SmartLaunchTokens
		        .sign(new JWTClaimsSet.Builder().subject("clinician").claim("patient", "patient-uuid").build(), SECRET));

		filter.doFilter(request, response, chain);

		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		context.verify(() -> Context.authenticate(any(Credentials.class)), never());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void doFilter_shouldEndTheBypassSessionOnAPathOffTheList() throws Exception {
		when(request.getRequestURI()).thenReturn("/openmrs/smartonfhir/somethingElse");
		when(session.getAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS)).thenReturn(true);

		filter.doFilter(request, response, chain);

		context.verify(Context::logout);
		verify(session).removeAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS);
		verify(chain).doFilter(request, response);
	}

	private static JWTClaimsSet.Builder userToken() {
		return new JWTClaimsSet.Builder().claim("typ", "smart-username-token");
	}

	/** Wraps the user token in an outer token whose signature the filter does not check. */
	private void givenUserToken(String userToken) throws Exception {
		String outer = SmartLaunchTokens.sign(new JWTClaimsSet.Builder().claim("user", userToken).build(),
		    SOMEONE_ELSES_SECRET);
		String target = "https://kc.example.org/realms/openmrs/login-actions/action-token?key=" + outer + "&t={APP_TOKEN}";
		when(request.getParameter("token")).thenReturn(URLEncoder.encode(target, StandardCharsets.UTF_8.name()));
	}
}

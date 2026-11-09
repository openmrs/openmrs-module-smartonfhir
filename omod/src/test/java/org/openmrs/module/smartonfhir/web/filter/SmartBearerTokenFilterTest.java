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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Set;

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
import org.openmrs.api.context.Credentials;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifierHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SmartBearerTokenFilterTest {

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private FilterChain chain;

	@Mock
	private SmartAccessTokenVerifier verifier;

	private MockedStatic<Context> context;

	private MockedStatic<SmartAccessTokenVerifierHolder> holder;

	private final SmartBearerTokenFilter filter = new SmartBearerTokenFilter();

	@BeforeEach
	public void setUp() {
		context = mockStatic(Context.class);
		holder = mockStatic(SmartAccessTokenVerifierHolder.class);
		holder.when(SmartAccessTokenVerifierHolder::getVerifier).thenReturn(verifier);
		when(request.getHeader(SmartBearerTokenFilter.AUTHORIZATION_HEADER)).thenReturn("Bearer the-token");
	}

	@AfterEach
	public void restore() {
		context.close();
		holder.close();
	}

	@Test
	public void doFilter_shouldLeaveARequestWithoutABearerTokenToTheRestOfTheChain() throws Exception {
		when(request.getHeader(SmartBearerTokenFilter.AUTHORIZATION_HEADER)).thenReturn("Basic YWRtaW46QWRtaW4xMjM=");

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		holder.verify(SmartAccessTokenVerifierHolder::getVerifier, never());
	}

	@Test
	public void doFilter_shouldChallengeAnInvalidToken() throws Exception {
		when(verifier.verify("the-token")).thenReturn(null);

		filter.doFilter(request, response, chain);

		verify(response).setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void doFilter_shouldRefuseRatherThanPassThroughWhenNoVerifierCanBeBuilt() throws Exception {
		holder.when(SmartAccessTokenVerifierHolder::getVerifier).thenReturn(null);

		filter.doFilter(request, response, chain);

		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void doFilter_shouldIgnoreATokenOnAnAlreadyAuthenticatedRequest() throws Exception {
		context.when(Context::isAuthenticated).thenReturn(true);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		holder.verify(SmartAccessTokenVerifierHolder::getVerifier, never());
		context.verify(Context::logout, never());
	}

	@Test
	public void doFilter_shouldEndTheSessionEvenWhenTheChainThrows() throws Exception {
		when(verifier.verify("the-token")).thenReturn(new SmartAccessToken("clinician", null, null, Set.of()));
		context.when(Context::isAuthenticated).thenReturn(false, true);
		doThrow(new ServletException("downstream")).when(chain).doFilter(request, response);

		assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));

		context.verify(() -> Context.authenticate(any(Credentials.class)));
		context.verify(Context::logout);
	}

	@Test
	public void doFilter_shouldRefuseAVerifiedTokenWhoseUserCannotSignIn() throws Exception {
		when(verifier.verify("the-token")).thenReturn(new SmartAccessToken("stranger", null, null, Set.of()));
		context.when(() -> Context.authenticate(any(Credentials.class))).thenThrow(new RuntimeException("no such user"));

		filter.doFilter(request, response, chain);

		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		verify(chain, never()).doFilter(any(), any());
	}
}

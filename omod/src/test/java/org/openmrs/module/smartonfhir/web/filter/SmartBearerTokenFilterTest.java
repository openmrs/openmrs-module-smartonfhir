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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openmrs.User;
import org.openmrs.UserSessionListener.Event;
import org.openmrs.UserSessionListener.Status;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Authenticated;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Credentials;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.authentication.AuthenticationUserSessionListener;
import org.openmrs.module.authentication.UserLogin;
import org.openmrs.module.authentication.UserLoginTracker;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifierHolder;
import org.openmrs.module.smartonfhir.web.smart.SmartBearerTokenAuthenticationScheme;

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

	private final AtomicBoolean signedIn = new AtomicBoolean();

	private final AuthenticationUserSessionListener listener = new AuthenticationUserSessionListener();

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

	/**
	 * Core's logout swaps in a fresh context; the stubs here do the same, so a restore is observable.
	 */
	@Test
	public void doFilter_shouldSignInOnAContextOfItsOwnAndHandTheSessionsBack() throws Exception {
		UserContext sessionContext = new UserContext(null);
		AtomicReference<UserContext> current = givenAThreadContext(sessionContext);
		AtomicReference<UserContext> signedInOn = new AtomicReference<>();
		AtomicReference<UserContext> servedOn = new AtomicReference<>();
		when(verifier.verify("the-token")).thenReturn(new SmartAccessToken("clinician", null, null, Set.of()));
		context.when(() -> Context.authenticate(any(Credentials.class))).thenAnswer(invocation -> {
			signedInOn.set(current.get());
			return null;
		});
		doAnswer(invocation -> {
			servedOn.set(current.get());
			return null;
		}).when(chain).doFilter(request, response);

		filter.doFilter(request, response, chain);

		assertNotNull(signedInOn.get());
		assertNotSame(sessionContext, signedInOn.get(), "requests sharing a session must not share a login");
		assertSame(signedInOn.get(), servedOn.get());
		assertSame(sessionContext, current.get(), "the session's own context must be back on the thread");
	}

	@Test
	public void doFilter_shouldRecordTheLoginAndLogoutOnOneRecord() throws Exception {
		givenAThreadContext(new UserContext(null));
		givenTheAuthenticationModulesListener();
		when(verifier.verify("the-token")).thenReturn(new SmartAccessToken("clinician", null, null, Set.of()));
		// The module's own filter, further down the chain, puts its login on the thread and takes it off.
		doAnswer(invocation -> {
			UserLoginTracker.setLoginOnThread(new UserLogin());
			UserLoginTracker.removeLoginFromThread();
			return null;
		}).when(chain).doFilter(request, response);
		int activeBefore = UserLoginTracker.getActiveLogins().size();

		filter.doFilter(request, response, chain);

		assertEquals(activeBefore, UserLoginTracker.getActiveLogins().size(), "the logout must close the login it follows");
		assertNull(UserLoginTracker.getLoginOnThread());
	}

	@Test
	public void doFilter_shouldNotRecordItsLoginOnAnotherRequestsRecord() throws Exception {
		givenAThreadContext(new UserContext(null));
		givenTheAuthenticationModulesListener();
		when(verifier.verify("the-token")).thenReturn(new SmartAccessToken("clinician", null, null, Set.of()));
		UserService users = mock(UserService.class);
		when(users.getUserByUsername("clinician")).thenReturn(new User());
		context.when(Context::getUserService).thenReturn(users);
		SmartBearerTokenAuthenticationScheme scheme = new SmartBearerTokenAuthenticationScheme();
		scheme.configure("smartBearer", new Properties());
		context.when(() -> Context.authenticate(any(Credentials.class))).thenAnswer(invocation -> {
			Authenticated authenticated = scheme.authenticate(invocation.getArgument(0));
			signedIn.set(true);
			listener.loggedInOrOut(authenticated.getUser(), Event.LOGIN, Status.SUCCESS);
			return authenticated;
		});
		UserLogin anotherRequests = new UserLogin();
		anotherRequests.setUser(new User());
		UserLoginTracker.setLoginOnThread(anotherRequests);

		try {
			filter.doFilter(request, response, chain);
		}
		finally {
			UserLoginTracker.removeLoginFromThread();
		}

		verify(chain).doFilter(request, response);
		verify(response, never()).sendError(anyInt(), anyString());
		assertTrue(anotherRequests.getEvents().isEmpty(), "another request's login record was written to");
	}

	@Test
	public void doFilter_shouldRefuseAVerifiedTokenWhoseUserCannotSignIn() throws Exception {
		when(verifier.verify("the-token")).thenReturn(new SmartAccessToken("stranger", null, null, Set.of()));
		context.when(() -> Context.authenticate(any(Credentials.class))).thenThrow(new RuntimeException("no such user"));

		filter.doFilter(request, response, chain);

		verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
		verify(chain, never()).doFilter(any(), any());
	}

	private AtomicReference<UserContext> givenAThreadContext(UserContext sessionContext) {
		AtomicReference<UserContext> current = new AtomicReference<>(sessionContext);
		context.when(Context::getUserContext).thenAnswer(invocation -> current.get());
		context.when(() -> Context.setUserContext(any())).thenAnswer(invocation -> {
			current.set(invocation.getArgument(0));
			return null;
		});
		context.when(Context::isAuthenticated).thenAnswer(invocation -> signedIn.get());
		context.when(Context::logout).thenAnswer(invocation -> {
			signedIn.set(false);
			current.set(new UserContext(null));
			return null;
		});
		return current;
	}

	/**
	 * Reports logins and logouts to the module's listener, as core does on every authenticate and
	 * logout.
	 */
	private void givenTheAuthenticationModulesListener() {
		User clinician = new User();
		context.when(() -> Context.authenticate(any(Credentials.class))).thenAnswer(invocation -> {
			signedIn.set(true);
			listener.loggedInOrOut(clinician, Event.LOGIN, Status.SUCCESS);
			return null;
		});
		context.when(Context::logout).thenAnswer(invocation -> {
			signedIn.set(false);
			listener.loggedInOrOut(clinician, Event.LOGOUT, Status.SUCCESS);
			return null;
		});
	}
}

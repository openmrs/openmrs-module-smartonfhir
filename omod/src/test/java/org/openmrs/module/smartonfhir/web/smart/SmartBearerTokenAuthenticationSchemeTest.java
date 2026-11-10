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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Authenticated;
import org.openmrs.api.context.AuthenticationScheme;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.UsernamePasswordCredentials;
import org.openmrs.api.db.ContextDAO;
import org.openmrs.module.authentication.AuthenticationConfig;
import org.openmrs.module.authentication.AuthenticationEvent;
import org.openmrs.module.authentication.ConfigurableAuthenticationScheme;
import org.openmrs.module.authentication.UserLogin;
import org.openmrs.module.authentication.UserLoginTracker;
import org.openmrs.module.authentication.web.WebAuthenticationScheme;
import org.openmrs.module.smartonfhir.auth.SmartBearerCredentials;
import org.openmrs.module.smartonfhir.auth.SmartTokenCredentials;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;
import org.openmrs.util.PrivilegeConstants;

/**
 * This scheme is reached through {@code Context.authenticate}, never through a login page.
 */
public class SmartBearerTokenAuthenticationSchemeTest {

	private final User passwordUser = new User();

	@BeforeEach
	public void givenThePlatformsPasswordCheck() {
		ContextDAO dao = mock(ContextDAO.class);
		when(dao.authenticate("admin", "Admin123")).thenReturn(passwordUser);
		Context.setDAO(dao);
	}

	@AfterEach
	public void restore() {
		Context.setDAO(null);
	}

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

	@Test
	@DisplayName("signs in the user a verified access token names")
	public void shouldSignInTheUserABearerTokenNames() {
		User clinician = new User();

		try (MockedStatic<Context> context = givenUser("clinician", clinician)) {
			Authenticated authenticated = configured(new Properties()).authenticate(bearer("clinician"));

			assertSame(clinician, authenticated.getUser());
			context.verify(() -> Context.removeProxyPrivilege(PrivilegeConstants.GET_USERS));
		}
	}

	@Test
	@DisplayName("refuses a verified access token naming nobody OpenMRS knows")
	public void shouldRefuseABearerTokenNamingAnUnknownUser() {
		try (MockedStatic<Context> context = givenUser("clinician", null)) {
			assertThrows(ContextAuthenticationException.class,
			    () -> configured(new Properties()).authenticate(bearer("clinician")));
		}
	}

	@Test
	@DisplayName("refuses, and drops its proxy privilege, when the user lookup fails")
	public void shouldRefuseWhenTheUserLookupFails() {
		UserService users = mock(UserService.class);
		when(users.getUserByUsername("clinician")).thenThrow(new RuntimeException("database down"));

		try (MockedStatic<Context> context = mockStatic(Context.class)) {
			context.when(Context::getUserService).thenReturn(users);

			assertThrows(ContextAuthenticationException.class,
			    () -> configured(new Properties()).authenticate(bearer("clinician")));
			context.verify(() -> Context.removeProxyPrivilege(PrivilegeConstants.GET_USERS));
		}
	}

	@Test
	@DisplayName("signs in the user a launch token names, so a standalone launch reaches patient selection")
	public void shouldSignInTheUserALaunchTokenNames() {
		User clinician = new User();

		try (MockedStatic<Context> context = givenUser("clinician", clinician)) {
			Authenticated authenticated = configured(new Properties()).authenticate(new SmartTokenCredentials("clinician"));

			assertSame(clinician, authenticated.getUser());
		}
	}

	@Test
	@DisplayName("records a SMART sign-in on the login the module's filter put on the thread")
	public void shouldRecordTheOutcomeOnTheThreadsLogin() {
		UserLogin login = new UserLogin();
		UserLoginTracker.setLoginOnThread(login);

		try (MockedStatic<Context> context = givenUser("clinician", new User())) {
			configured(new Properties()).authenticate(bearer("clinician"));

			assertTrue(login.containsEvent(AuthenticationEvent.AUTHENTICATION_SUCCEEDED));
		}
		finally {
			UserLoginTracker.removeLoginFromThread();
		}
	}

	@Test
	@DisplayName("records a refused SMART sign-in on the login the module's filter put on the thread")
	public void shouldRecordARefusalOnTheThreadsLogin() {
		UserLogin login = new UserLogin();
		UserLoginTracker.setLoginOnThread(login);

		try (MockedStatic<Context> context = givenUser("clinician", null)) {
			assertThrows(ContextAuthenticationException.class,
			    () -> configured(new Properties()).authenticate(bearer("clinician")));

			assertTrue(login.containsEvent(AuthenticationEvent.AUTHENTICATION_FAILED));
		}
		finally {
			UserLoginTracker.removeLoginFromThread();
		}
	}

	@Test
	@DisplayName("hands a password to username/password when no delegate is configured")
	public void shouldDelegateAPasswordToUsernamePasswordByDefault() {
		assertSame(passwordUser, configured(new Properties()).authenticate(password()).getUser());
	}

	@Test
	@DisplayName("hands a password to the configured delegate")
	public void shouldDelegateAPasswordToTheConfiguredScheme() {
		AuthenticationScheme delegate = mock(AuthenticationScheme.class);
		Authenticated delegated = mock(Authenticated.class);
		when(delegate.authenticate(any())).thenReturn(delegated);

		try (MockedStatic<AuthenticationConfig> config = mockStatic(AuthenticationConfig.class)) {
			config.when(() -> AuthenticationConfig.getAuthenticationScheme("other")).thenReturn(delegate);

			assertSame(delegated, configured(delegateTo("other")).authenticate(password()));
		}
	}

	@Test
	@DisplayName("falls back to username/password when the delegate has no type configured")
	public void shouldFallBackWhenTheDelegateHasNoType() {
		try (MockedStatic<AuthenticationConfig> config = mockStatic(AuthenticationConfig.class)) {
			config.when(() -> AuthenticationConfig.getAuthenticationScheme("untyped")).thenReturn(null);

			assertSame(passwordUser, configured(delegateTo("untyped")).authenticate(password()).getUser());
		}
	}

	@Test
	@DisplayName("falls back to username/password when the delegate cannot be loaded")
	public void shouldFallBackWhenTheDelegateCannotBeLoaded() {
		try (MockedStatic<AuthenticationConfig> config = mockStatic(AuthenticationConfig.class)) {
			config.when(() -> AuthenticationConfig.getAuthenticationScheme("broken"))
			        .thenThrow(new IllegalStateException("no such class"));

			assertSame(passwordUser, configured(delegateTo("broken")).authenticate(password()).getUser());
		}
	}

	private static SmartBearerTokenAuthenticationScheme configured(Properties config) {
		SmartBearerTokenAuthenticationScheme scheme = new SmartBearerTokenAuthenticationScheme();
		scheme.configure("smartBearer", config);
		return scheme;
	}

	private static Properties delegateTo(String schemeId) {
		Properties config = new Properties();
		config.setProperty(SmartBearerTokenAuthenticationScheme.CONFIG_DELEGATE, schemeId);
		return config;
	}

	private static SmartBearerCredentials bearer(String username) {
		return new SmartBearerCredentials("smartBearer", new SmartAccessToken(username, null, null, Set.of()));
	}

	private static UsernamePasswordCredentials password() {
		return new UsernamePasswordCredentials("admin", "Admin123");
	}

	private static MockedStatic<Context> givenUser(String username, User user) {
		UserService users = mock(UserService.class);
		when(users.getUserByUsername(username)).thenReturn(user);
		MockedStatic<Context> context = mockStatic(Context.class);
		context.when(Context::getUserService).thenReturn(users);
		return context;
	}
}

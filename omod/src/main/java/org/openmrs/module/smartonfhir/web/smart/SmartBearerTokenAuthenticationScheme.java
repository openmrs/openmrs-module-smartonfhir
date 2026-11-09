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

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.User;
import org.openmrs.api.context.Authenticated;
import org.openmrs.api.context.AuthenticationScheme;
import org.openmrs.api.context.BasicAuthenticated;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.Credentials;
import org.openmrs.api.context.UsernamePasswordAuthenticationScheme;
import org.openmrs.module.authentication.ConfigurableAuthenticationScheme;
import org.openmrs.module.authentication.UserLogin;
import org.openmrs.module.authentication.UserLoginTracker;
import org.openmrs.module.smartonfhir.auth.SmartBearerCredentials;
import org.openmrs.module.smartonfhir.auth.SmartTokenCredentials;
import org.openmrs.util.PrivilegeConstants;

/**
 * Authenticates FHIR requests presenting a SMART access token, and delegates everything else. It is
 * neither a Spring component nor a {@code WebAuthenticationScheme}, as either would disable it.
 */
@Slf4j
public class SmartBearerTokenAuthenticationScheme implements ConfigurableAuthenticationScheme {

	/** Scheme id to hand non-bearer credentials to; the platform's username/password one by default. */
	public static final String CONFIG_DELEGATE = "delegate";

	private String delegateSchemeId;

	private String schemeId;

	private volatile AuthenticationScheme delegate;

	@Override
	public String getSchemeId() {
		return schemeId;
	}

	@Override
	public void configure(String schemeId, Properties config) {
		this.schemeId = schemeId;
		this.delegateSchemeId = config.getProperty(CONFIG_DELEGATE);
	}

	/**
	 * Maps a username onto an OpenMRS user without a password, which is sound only because both
	 * {@link SmartBearerCredentials} and {@link SmartTokenCredentials} require a verified token.
	 */
	private Authenticated authenticateAsNamedUser(String username, String schemeName) {
		User user = findUser(username);

		if (user == null) {
			// The authorization server knows this person and OpenMRS does not, so no privileges apply.
			log.warn("A verified SMART token named '{}', which is not an OpenMRS user", username);
			throw new ContextAuthenticationException("Invalid credentials");
		}

		log.debug("Authenticated '{}' from a verified SMART token", user.getUsername());

		return new BasicAuthenticated(user, schemeName);
	}

	/**
	 * The one entry point. Bearer credentials cannot be forged into existence here, because
	 * {@link SmartBearerCredentials} can only be built from an already-verified token.
	 */
	@Override
	public Authenticated authenticate(Credentials credentials) throws ContextAuthenticationException {
		if (credentials instanceof SmartBearerCredentials) {
			return recorded(credentials);
		}

		// Nothing else handles these, so a standalone launch would stall at patient selection.
		if (credentials instanceof SmartTokenCredentials) {
			return recorded(credentials);
		}

		return delegateAuthenticate(credentials);
	}

	/**
	 * Authenticates a SMART credential and reports the outcome to the authentication module's audit
	 * trail, but only when its filter has put a login on the thread.
	 */
	private Authenticated recorded(Credentials credentials) throws ContextAuthenticationException {
		final UserLogin login = UserLoginTracker.getLoginOnThread();

		try {
			Authenticated authenticated = authenticateAsNamedUser(credentials.getClientName(),
			    credentials.getAuthenticationScheme());

			if (login != null) {
				login.authenticationSuccessful(schemeId, authenticated);
			}

			return authenticated;
		}
		catch (Exception e) {
			if (login != null) {
				login.authenticationFailed(schemeId);
			}

			throw e;
		}
	}

	/**
	 * Looks up the OpenMRS user a verified token names. {@code getUserByUsername} needs the Get Users
	 * privilege, which nobody has yet, so it runs under a proxy privilege dropped in a finally block.
	 */
	private User findUser(String username) {
		Context.addProxyPrivilege(PrivilegeConstants.GET_USERS);
		try {
			return Context.getUserService().getUserByUsername(username);
		}
		catch (Exception e) {
			log.error("Could not look up the OpenMRS user named '{}'", username, e);
			return null;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_USERS);
		}
	}

	/** The scheme that handles everything this one does not; username/password by default. */
	private AuthenticationScheme resolveDelegate() {
		AuthenticationScheme resolved = delegate;

		if (resolved == null) {
			synchronized (this) {
				if (delegate == null) {
					delegate = buildDelegate();
				}
				resolved = delegate;
			}
		}

		return resolved;
	}

	private AuthenticationScheme buildDelegate() {
		if (delegateSchemeId != null && !delegateSchemeId.isBlank()) {
			try {
				return org.openmrs.module.authentication.AuthenticationConfig
				        .getAuthenticationScheme(delegateSchemeId.trim());
			}
			catch (Exception e) {
				log.error("Could not load the configured delegate scheme '{}'; falling back to username/password",
				    delegateSchemeId, e);
			}
		}

		return new UsernamePasswordAuthenticationScheme();
	}

	private Authenticated delegateAuthenticate(Credentials credentials) {
		return resolveDelegate().authenticate(credentials);
	}
}

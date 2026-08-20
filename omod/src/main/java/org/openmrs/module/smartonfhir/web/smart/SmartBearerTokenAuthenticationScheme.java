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
import org.openmrs.module.authentication.AuthenticationCredentials;
import org.openmrs.module.authentication.UserLogin;
import org.openmrs.module.authentication.web.AuthenticationSession;
import org.openmrs.module.authentication.web.WebAuthenticationScheme;
import org.openmrs.module.smartonfhir.auth.SmartBearerCredentials;
import org.openmrs.module.smartonfhir.auth.SmartTokenCredentials;
import org.openmrs.util.PrivilegeConstants;

/**
 * Authenticates FHIR requests that present a SMART access token, and stays out of the way
 * otherwise.
 * <p>
 * <strong>Why this shape.</strong> OpenMRS core permits exactly one {@code AuthenticationScheme}
 * Spring bean: {@code Context.setAuthenticationScheme()} calls
 * {@code getBean(AuthenticationScheme.class)} and, if more than one exists, logs <em>"Multiple
 * authentication schemes overrides are being provided"</em> and silently falls back to the platform
 * default, disabling every candidate. RefApp 3.7.1 already ships the authentication module, whose
 * {@code DelegatingAuthenticationScheme} is that bean. A second {@code @Component} here would
 * therefore disable both. So this class is <em>not</em> a Spring component: it is instantiated by
 * the authentication module from {@code authentication.scheme.{id}.type}, by reflection, and so
 * never competes for the bean.
 * <p>
 * <strong>Why O3 login is unaffected.</strong> The module's filter is mapped to {@code /*} and,
 * when the active scheme is a {@link WebAuthenticationScheme}, redirects unauthenticated requests
 * to {@link #getChallengeUrl}. Sending O3's app shell and REST calls to a login page would be a
 * serious regression, so this scheme returns no credentials and a <em>null</em> challenge URL for
 * any request without a bearer token; the filter treats a null challenge URL as "carry on" and
 * passes the request down the chain untouched. Non-bearer credentials are handed to the delegate,
 * which defaults to the platform's own username/password scheme — exactly what RefApp 3.7.1 uses
 * today.
 */
@Slf4j
public class SmartBearerTokenAuthenticationScheme extends WebAuthenticationScheme {

	/**
	 * Scheme id to hand non-bearer credentials to. When unset the platform's username/password scheme
	 * is used, which is what RefApp 3.7.1 authenticates with, so installing this scheme changes nothing
	 * for users logging in normally.
	 */
	public static final String CONFIG_DELEGATE = "delegate";

	private String delegateSchemeId;

	private volatile AuthenticationScheme delegate;

	@Override
	public void configure(String schemeId, Properties config) {
		super.configure(schemeId, config);
		this.delegateSchemeId = config.getProperty(CONFIG_DELEGATE);
	}

	/**
	 * Always null: this scheme never reads the request.
	 * <p>
	 * It is registered so that {@link Context#authenticate} can route SMART credentials here, not so
	 * that the authentication module's filter can drive a login. Returning credentials from here makes
	 * that filter authenticate the request itself and then issue its interactive-login success redirect
	 * -- a 302 where a FHIR client expects its data. Reading the bearer header is
	 * {@code SmartBearerTokenFilter}'s job, scoped to the FHIR paths, and it authenticates by calling
	 * {@link Context#authenticate}, which arrives at {@link #authenticate(Credentials)} below.
	 */
	@Override
	public AuthenticationCredentials getCredentials(AuthenticationSession session) {
		return null;
	}

	/**
	 * Also null, so the module's filter passes every request down the chain rather than redirecting it
	 * to a login page. Without this, O3's app shell and REST calls would be sent to one.
	 */
	@Override
	public String getChallengeUrl(AuthenticationSession session) {
		return null;
	}

	@Override
	protected Authenticated authenticate(AuthenticationCredentials credentials, UserLogin userLogin) {
		if (!(credentials instanceof SmartBearerCredentials)) {
			return delegateAuthenticate(credentials);
		}

		return authenticateAsNamedUser(credentials.getClientName(), credentials.getAuthenticationScheme());
	}

	/**
	 * Maps a username onto an OpenMRS user without a password, which is only sound because the caller
	 * has already established who the user is: {@link SmartBearerCredentials} exists only for a
	 * verified access token, and {@link SmartTokenCredentials} only for a launch token whose HMAC
	 * signature was checked against the shared secret.
	 */
	private Authenticated authenticateAsNamedUser(String username, String schemeName) {
		User user = findUser(username);

		if (user == null) {
			// The token was valid, so the authorization server knows this person; OpenMRS does not.
			// Refusing is the only safe answer: there is no user whose privileges could apply.
			log.warn("A verified SMART token named '{}', which is not an OpenMRS user", username);
			throw new ContextAuthenticationException("Invalid credentials");
		}

		log.debug("Authenticated '{}' from a verified SMART token", user.getUsername());

		return new BasicAuthenticated(user, schemeName);
	}

	/**
	 * Also reachable through {@code Context.authenticate(Credentials)}, which bypasses
	 * {@link #getCredentials}. Bearer credentials cannot be forged into existence here, because
	 * {@link SmartBearerCredentials} can only be built from an already-verified token.
	 */
	@Override
	public Authenticated authenticate(Credentials credentials) throws ContextAuthenticationException {
		if (credentials instanceof SmartBearerCredentials) {
			return super.authenticate(credentials);
		}

		// The launch handshake authenticates the user who is selecting a patient, via
		// AuthenticationByPassFilter, which verifies the launch token's signature before getting here.
		// Nothing else in the platform handles these credentials, so without this the standalone
		// launch cannot get past patient selection.
		if (credentials instanceof SmartTokenCredentials) {
			return authenticateAsNamedUser(credentials.getClientName(), credentials.getAuthenticationScheme());
		}

		return delegateAuthenticate(credentials);
	}

	@Override
	public boolean isUserConfigurationRequired(User user) {
		// Nothing for a user to set up: the authorization server owns the credential.
		return false;
	}

	/**
	 * Looks up the OpenMRS user a verified token names.
	 * <p>
	 * {@code UserService.getUserByUsername} is {@code @Authorized("Get Users")}, and this runs before
	 * anyone is authenticated, so the call is refused and the user appears not to exist -- the token is
	 * then rejected as naming an unknown user, which is a confusing way to fail. A proxy privilege is
	 * the platform's own idiom for a lookup that has to happen in order to authenticate at all. It is
	 * granted around this one call and removed in a finally block, so it cannot leak into the request.
	 * <p>
	 * The lookup matches username or systemId, which matters because OpenMRS's own {@code admin}
	 * account has a null username and is identified by its systemId.
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

	/**
	 * The scheme that handles everything this one does not. Defaults to the platform's
	 * username/password scheme, so behaviour for ordinary logins is unchanged from a deployment with no
	 * scheme configured.
	 */
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
		if (delegateSchemeId != null && !delegateSchemeId.trim().isEmpty()) {
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

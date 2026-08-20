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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.auth.SmartBearerCredentials;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifierHolder;

/**
 * Authenticates FHIR requests that carry a SMART access token.
 * <p>
 * <strong>Why a filter and not only a scheme.</strong> The authentication module's filter is the
 * natural place for this, but it only invokes a scheme's {@code getCredentials} under conditions
 * that did not hold for FHIR requests in RefApp 3.7.1, so bearer tokens were never examined. This
 * filter does the part that was not happening — read the header, verify the token — and then hands
 * the resulting credentials to {@link Context#authenticate}, which routes them through the module's
 * delegating scheme to {@code SmartBearerTokenAuthenticationScheme}. Identity mapping therefore
 * still lives in one place.
 * <p>
 * <strong>Scoped to the FHIR paths.</strong> Deliberately not {@code /*}. The filter it replaces
 * was mapped to every request in the webapp, so its logic ran on every O3 REST call. Nothing
 * outside the FHIR API accepts SMART tokens, so nothing outside it needs this.
 * <p>
 * <strong>Authentication does not outlive the request.</strong> {@link Context#authenticate}
 * attaches the user to the HTTP session, which would let a client replay the resulting cookie
 * without presenting a token again — turning a bearer credential into an ambient one. The session
 * is therefore discarded once the request completes, so each FHIR call stands on its own token.
 */
@Slf4j
public class SmartBearerTokenFilter implements Filter {

	public static final String AUTHORIZATION_HEADER = "Authorization";

	public static final String BEARER_PREFIX = "Bearer ";

	/** Request attributes the FHIR layer can read the granted launch context from. */
	public static final String ATTRIBUTE_PATIENT = "org.openmrs.module.smartonfhir.patient";

	public static final String ATTRIBUTE_ENCOUNTER = "org.openmrs.module.smartonfhir.encounter";

	public static final String ATTRIBUTE_SCOPES = "org.openmrs.module.smartonfhir.scopes";

	@Override
	public void init(FilterConfig filterConfig) {
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		if (!(req instanceof HttpServletRequest) || !(res instanceof HttpServletResponse)) {
			chain.doFilter(req, res);
			return;
		}

		final HttpServletRequest request = (HttpServletRequest) req;
		final HttpServletResponse response = (HttpServletResponse) res;
		final String bearerToken = bearerTokenFrom(request);

		if (bearerToken == null) {
			// No SMART token offered. Basic auth and session cookies are still handled by the rest
			// of the chain exactly as before.
			chain.doFilter(req, res);
			return;
		}

		if (Context.isAuthenticated()) {
			// Already authenticated by something earlier in the chain -- a session cookie, or basic auth --
			// so that identity is left alone. The token is not verified and no launch context is set, which
			// means an app believing itself confined to a granted patient is in fact running with the cookie
			// user's full privileges. Logged, because nothing else would say so.
			log.warn("A SMART access token was presented on an already-authenticated request; it was not used, "
			        + "and no launch context is available to this request");
			chain.doFilter(req, res);
			return;
		}

		final SmartAccessTokenVerifier verifier = SmartAccessTokenVerifierHolder.getVerifier();

		if (verifier == null) {
			log.error("A SMART access token was presented but SMART on FHIR is not configured");
			unauthorized(response, "invalid_token");
			return;
		}

		final SmartAccessToken token = verifier.verify(bearerToken);

		if (token == null) {
			// The verifier logged the reason. The response deliberately does not say which check
			// failed, so a caller cannot use the error to probe.
			unauthorized(response, "invalid_token");
			return;
		}

		// Any exception, not only ContextAuthenticationException. UserContext.authenticate sets the user
		// before it sets location and locale, and those two calls sit outside its own try block: an
		// APIException from either -- APIAuthenticationException is a sibling, not a subclass -- escaped
		// this catch with the context already logged in, leaving a session minted from a bearer token and
		// replayable without one.
		try {
			Context.authenticate(new SmartBearerCredentials(SmartBearerCredentials.SCHEME_ID, token));
		}
		catch (Exception e) {
			log.warn("A valid SMART access token named '{}', who could not be authenticated in OpenMRS", token.getUsername(),
			    e);
			endBearerSession();
			unauthorized(response, "invalid_token");
			return;
		}

		try {
			request.setAttribute(ATTRIBUTE_PATIENT, token.getPatient());
			request.setAttribute(ATTRIBUTE_ENCOUNTER, token.getEncounter());
			request.setAttribute(ATTRIBUTE_SCOPES, token.getScopes());

			chain.doFilter(req, res);
		}
		finally {
			// Keeps bearer authentication per-request: see the class comment. Asks the context what it
			// actually holds, rather than trusting a local flag that was set after authenticate() returned
			// and so was false on exactly the paths where a session had already been established.
			try {
				endBearerSession();
			}
			catch (Exception e) {
				log.warn("Could not close the session opened for a SMART access token", e);
			}
		}
	}

	/**
	 * A bearer challenge, as OAuth 2 requires. The previous implementation answered a bare 401 with no
	 * {@code WWW-Authenticate}, leaving a client unable to tell that refreshing its token was the
	 * remedy.
	 */
	/** Ends a session this filter created, if the context in fact holds one. */
	private void endBearerSession() {
		if (Context.isAuthenticated()) {
			Context.logout();
		}
	}

	private void unauthorized(HttpServletResponse response, String error) throws IOException {
		if (response.isCommitted()) {
			return;
		}

		response.setHeader("WWW-Authenticate", "Bearer error=\"" + error + "\"");
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
	}

	private String bearerTokenFrom(HttpServletRequest request) {
		final String header = request.getHeader(AUTHORIZATION_HEADER);

		if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return null;
		}

		final String token = header.substring(BEARER_PREFIX.length()).trim();

		return token.isEmpty() ? null : token;
	}

	@Override
	public void destroy() {
	}
}

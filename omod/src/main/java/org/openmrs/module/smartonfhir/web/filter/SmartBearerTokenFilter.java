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
 * Authenticates FHIR requests carrying a SMART access token, routing the credentials through
 * {@link Context#authenticate}. The session it opens is discarded when the request completes.
 */
@Slf4j
public class SmartBearerTokenFilter implements Filter {

	/** Must match the scheme id this module is registered under in {@code authentication.scheme}. */
	private static final String SCHEME_ID = "smartBearer";

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
			// No SMART token offered, so basic auth and session cookies are left to the rest of the chain.
			chain.doFilter(req, res);
			return;
		}

		if (Context.isAuthenticated()) {
			// An earlier identity wins, so the app runs with that user's privileges and no launch context.
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
			// The verifier logged the reason; the response withholds it so a caller cannot probe.
			unauthorized(response, "invalid_token");
			return;
		}

		// Any exception: authenticate() can leave the context logged in while a later one escapes.
		try {
			Context.authenticate(new SmartBearerCredentials(SCHEME_ID, token));
		}
		catch (Exception e) {
			log.warn("A valid SMART access token named '{}', who could not be authenticated in OpenMRS", token.username(),
			    e);
			endBearerSession();
			unauthorized(response, "invalid_token");
			return;
		}

		try {
			request.setAttribute(ATTRIBUTE_PATIENT, token.patient());
			request.setAttribute(ATTRIBUTE_ENCOUNTER, token.encounter());
			request.setAttribute(ATTRIBUTE_SCOPES, token.scopes());

			chain.doFilter(req, res);
		}
		finally {
			// Asks the context what it holds rather than trusting a flag set after authenticate() returned.
			try {
				endBearerSession();
			}
			catch (Exception e) {
				log.warn("Could not close the session opened for a SMART access token", e);
			}
		}
	}

	/** Ends a session this filter created, if the context in fact holds one. */
	private void endBearerSession() {
		if (Context.isAuthenticated()) {
			Context.logout();
		}
	}

	/**
	 * A bearer challenge, as OAuth 2 requires, so a client can tell that its token needs refreshing.
	 */
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

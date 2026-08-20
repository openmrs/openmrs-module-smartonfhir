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
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.auth.SmartTokenCredentials;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;
import org.openmrs.module.smartonfhir.web.util.SmartLaunchTargets;

@Slf4j
public class AuthenticationByPassFilter implements Filter {

	public static final String SMART_AUTH_BYPASS = "SMART_AUTH_BYPASS";

	private static final String VALID_URLS_PARAM = "validUrls";

	private static final Pattern KEY_PARAM = Pattern.compile("^key=([^&]*)(?:&|$)");

	private List<String> validUrls = new ArrayList<>(0);

	@Override
	public void init(FilterConfig filterConfig) {
		String validUrlsParam = filterConfig.getInitParameter(VALID_URLS_PARAM);
		if (StringUtils.isNotBlank(validUrlsParam)) {
			validUrls = Arrays.stream(validUrlsParam.split(",")).filter(org.apache.commons.lang3.StringUtils::isNotBlank)
			        .map(it -> {
				        if (it.startsWith("/") || it.equals("*")) {
					        return it;
				        }

				        return "/" + it;
			        }).distinct().collect(Collectors.toList());
		}
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
	        throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
			Context.logout();
		}

		String pathInfo = request.getRequestURI();

		boolean isValidRequest = false;
		if (pathInfo != null) {
			final String thePathInfo = pathInfo.replaceFirst(request.getContextPath(), "");
			isValidRequest = validUrls.stream().anyMatch(url -> {
				if (url.endsWith("*")) {
					if (url.length() < 3) {
						return true;
					} else {
						String urlStart = url.substring(0, url.length() - 2);
						return thePathInfo.startsWith(urlStart);
					}
				}

				return url.equals(thePathInfo);
			});
		}

		if (!isValidRequest) {
			HttpSession session = request.getSession(false);
			if (session != null && session.getAttribute(SMART_AUTH_BYPASS) != null) {
				// Log out and drop the marker; do not invalidate. Invalidating leaves the
				// browser holding a cookie for a session that no longer exists, and the only
				// reason it did not bite here was the getSession() that used to follow it
				// minting a replacement. SmartLaunchOptionSelected ends the same session the
				// same way.
				Context.logout();
				session.removeAttribute(SMART_AUTH_BYPASS);
			}

			filterChain.doFilter(request, response);
			return;
		}

		if (!Context.isAuthenticated()) {
			final String tokenParam = request.getParameter("token");

			// Read exactly as SmartLaunchOptionSelected reads it. This used to search the
			// container-decoded parameter for "key=" while the servlet decoded once more before parsing,
			// so a doubly-encoded key was invisible here and visible there: this filter attempted no
			// authentication and the servlet proceeded anyway. It also matched any parameter merely
			// ending in "key", so ?monkey=x&key=real yielded x.
			final String key = SmartLaunchTargets.parameterFrom(SmartLaunchTargets.decodeLaunchTarget(tokenParam), "key");

			if (tokenParam != null) {
				if (key == null) {
					// Left silently to the chain before, so an operator debugging a launch that dies at the
					// picker had nothing to look at. Still fail-closed: the servlets check
					// Context.isAuthenticated() themselves.
					log.warn("A request to a launch URL carried a token with no readable key; not authenticating it");
				} else {
					{

						// The outer token is the authorization server's action token, signed with a key
						// this module does not hold, so it is read but not trusted. It carries a nested
						// token that is signed with the shared secret; that one is verified below, and it
						// is what establishes who the user is.
						JWTClaimsSet outerClaims = SmartLaunchTokens.readUnverifiedClaims(key);

						if (outerClaims == null) {
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
							return;
						}

						Object userTokenClaim = outerClaims.getClaim("user");

						if (userTokenClaim == null) {
							log.error("Could not read the user entry from the launch token");
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
							return;
						}

						JWTClaimsSet userClaims = SmartLaunchTokens.verify(userTokenClaim.toString(),
						    SmartSecretKeyHolder.getSecretKey());

						if (userClaims == null) {
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
							return;
						}

						final String username = userClaims.getSubject();

						if (username == null || username.trim().isEmpty()) {
							// Two very different situations reach here, and the message has to tell them apart or
							// it is undiagnosable. An EHR launch mints this token deliberately without a subject --
							// Keycloak does not know who the clinician is, which is the whole reason it is asking
							// OpenMRS -- so a 401 here is the flow working: the authorization server treats it as
							// attempted and falls through to a login form. A standalone launch, by contrast, sets
							// the subject from the authenticated Keycloak user, so a blank one there means
							// something upstream lost the user and is worth investigating.
							//
							// Everything logged below is what was needed and missing the one time this was seen in
							// the wild: which request, which token type, whether the subject was absent or blank,
							// and when the token was issued and expires.
							log.warn(
							    "A launch token carrying no user reached {} -- inner token type '{}', subject {}, issued {}, expires {}, issuer '{}'. "
							            + "For an EHR launch this is expected and the launch continues at the login form; for a standalone launch it is not.",
							    request.getRequestURI(), userClaims.getClaim("typ"), username == null ? "absent" : "blank",
							    userClaims.getIssueTime(), userClaims.getExpirationTime(), userClaims.getIssuer());
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
							return;
						}

						try {
							Context.authenticate(new SmartTokenCredentials(username));

							// Marked immediately, before anything that can throw. The teardown below keys off
							// this attribute, and setting it after the location lookup meant an
							// APIAuthenticationException from getDefaultLocation -- a sibling of
							// ContextAuthenticationException, so not caught here before -- left the session
							// authenticated and unmarked, which is to say authenticated forever.
							request.getSession().setAttribute(SMART_AUTH_BYPASS, true);

							Context.getUserContext().setLocation(Context.getLocationService().getDefaultLocation());
						}
						catch (Exception e) {
							log.error("Error while logging in as user {}", username, e);
							Context.logout();
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
							return;
						}
					}
				}
			}
		}

		filterChain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}
}

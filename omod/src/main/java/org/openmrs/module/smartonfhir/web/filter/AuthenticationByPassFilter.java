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
				// Not invalidated, which would leave a cookie for a session that no longer exists.
				Context.logout();
				session.removeAttribute(SMART_AUTH_BYPASS);
			}

			filterChain.doFilter(request, response);
			return;
		}

		if (!Context.isAuthenticated()) {
			final String tokenParam = request.getParameter("token");

			// Read exactly as SmartLaunchOptionSelected reads it, so the two cannot disagree.
			final String key = SmartLaunchTargets.parameterFrom(SmartLaunchTargets.decodeLaunchTarget(tokenParam), "key");

			if (tokenParam != null) {
				if (key == null) {
					// Still fail-closed, since the servlets check Context.isAuthenticated() themselves.
					log.warn("A request to a launch URL carried a token with no readable key; not authenticating it");
				} else {
					{

						// Read but not trusted; the nested token verified below names the user.
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

						if (username == null || username.isBlank()) {
							// Expected on an EHR launch, but a sign of trouble on a standalone one.
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

							// Marked before anything that can throw, since the teardown below keys off it.
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

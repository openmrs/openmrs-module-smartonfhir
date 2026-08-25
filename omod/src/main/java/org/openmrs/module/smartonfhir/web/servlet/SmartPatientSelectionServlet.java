/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;

/**
 * Where a launch lands when it needs a patient chosen. The frontend picker would bounce to its
 * login page and lose the token, so the session is established here first and the token forwarded.
 */
@Slf4j
public class SmartPatientSelectionServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	/** Overrides the default route, for a deployment whose picker is its own frontend module. */
	static final String ROUTE_PROPERTY = "smart.launch.patientSelectionRoute";

	/** The route registered by the SMART app launch frontend module. */
	private static final String DEFAULT_ROUTE = "/spa/smart/select-patient";

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String token = request.getParameter("token");

		if (token == null || token.isBlank()) {
			log.error("A patient selection was requested with no launch token");
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No launch token");
			return;
		}

		// The bypass filter authenticates from the token, so reaching here unauthenticated ends it.
		if (!Context.isAuthenticated()) {
			log.error("The launch token did not identify a user, so no patient can be selected");
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		StringBuilder target = new StringBuilder(request.getContextPath()).append(patientSelectionRoute()).append("?token=")
		        .append(URLEncoder.encode(token, StandardCharsets.UTF_8.name()));

		// Passed through only so the screen can name the app asking; it carries no authority.
		String appName = request.getParameter("appName");
		if (appName != null && !appName.isBlank()) {
			target.append("&appName=").append(URLEncoder.encode(appName, StandardCharsets.UTF_8.name()));
		}

		// Not encodeRedirectURL, which would put the session id in the address bar and every referrer.
		response.sendRedirect(target.toString());
	}

	/** Matched case-insensitively, since the reference application's image lower-cases what it maps. */
	private String patientSelectionRoute() {
		try {
			for (String name : Context.getRuntimeProperties().stringPropertyNames()) {
				if (!name.equalsIgnoreCase(ROUTE_PROPERTY)) {
					continue;
				}

				String configured = Context.getRuntimeProperties().getProperty(name);

				if (configured != null && !configured.isBlank()) {
					return configured.trim();
				}
			}
		}
		catch (Exception e) {
			log.debug("The runtime properties are not readable yet, so the default route is used", e);
		}

		return DEFAULT_ROUTE;
	}
}

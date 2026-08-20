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
 * The entry point the authorization server sends a clinician to when a launch needs a patient
 * chosen.
 * <p>
 * The screen itself is a frontend module, served by the single-page application host rather than by
 * OpenMRS. That host will not render any route until it has an authenticated session, and redirects
 * to the login page when it does not have one — which would discard the launch token in the URL and
 * end the launch. So the launch cannot land on the frontend route directly.
 * <p>
 * This servlet exists to be that landing place instead. It is mapped behind
 * {@link org.openmrs.module.smartonfhir.web.filter.AuthenticationByPassFilter}, which reads the
 * launch token and establishes the session, and it then redirects to the frontend route carrying
 * the same token. By the time the browser reaches the single-page application the session already
 * exists, so the route renders and the token is still in hand for the hand-off back to the
 * authorization server.
 */
@Slf4j
public class SmartPatientSelectionServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	/** The route registered by the SMART app launch frontend module. */
	private static final String PATIENT_SELECTION_ROUTE = "/spa/smart/select-patient";

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String token = request.getParameter("token");

		if (token == null || token.trim().isEmpty()) {
			log.error("A patient selection was requested with no launch token");
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No launch token");
			return;
		}

		// The bypass filter runs first and authenticates from the token. Reaching this point
		// unauthenticated means the token was missing, expired, or not signed with the shared
		// secret, and the launch cannot continue.
		if (!Context.isAuthenticated()) {
			log.error("The launch token did not identify a user, so no patient can be selected");
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		StringBuilder target = new StringBuilder(request.getContextPath()).append(PATIENT_SELECTION_ROUTE).append("?token=")
		        .append(URLEncoder.encode(token, StandardCharsets.UTF_8.name()));

		// Passed through only so the screen can name the app that is asking. It is the authorization
		// server's own description of the client and carries no authority.
		String appName = request.getParameter("appName");
		if (appName != null && !appName.trim().isEmpty()) {
			target.append("&appName=").append(URLEncoder.encode(appName, StandardCharsets.UTF_8.name()));
		}

		// Deliberately not encodeRedirectURL: the session cookie is set on this very response, so the
		// browser has it before it follows the redirect. Encoding would instead append the session id
		// to the path, putting it in the address bar, browser history and any referrer the launched app
		// sees — and the single-page application needs cookies regardless.
		response.sendRedirect(target.toString());
	}
}

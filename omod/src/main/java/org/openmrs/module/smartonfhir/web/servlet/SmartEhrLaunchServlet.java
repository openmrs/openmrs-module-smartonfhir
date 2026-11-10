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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.util.FhirBaseAddressStrategy;
import org.openmrs.module.smartonfhir.util.SmartLaunchContextService;

/**
 * Starts an EHR launch: a redirect to the app's launch URL with {@code iss} and a single-use
 * {@code launch} handle bound to the clinician who started it.
 */
public class SmartEhrLaunchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		final User user = Context.getAuthenticatedUser();

		if (user == null) {
			resp.sendError(HttpStatus.SC_UNAUTHORIZED, "A launch must be started by an authenticated user");
			return;
		}

		final String patientId = req.getParameter("patientId");
		final String visitId = req.getParameter("visitId");
		final String launchContext = StringUtils.defaultIfBlank(req.getParameter("launchContext"), "patient");
		final String contextId = "encounter".equals(launchContext) ? visitId : patientId;

		if (StringUtils.isBlank(contextId)) {
			resp.sendError(HttpStatus.SC_BAD_REQUEST,
			    "encounter".equals(launchContext) ? "A visitId must be provided" : "A patientId must be provided");
			return;
		}

		String url = new FhirBaseAddressStrategy().getBaseSmartLaunchAddress(req);

		if (StringUtils.isBlank(url)) {
			resp.sendError(HttpStatus.SC_BAD_REQUEST, "A url must be provided");
			return;
		}

		// Opaque and single-use, so it neither discloses the context nor can be forged from a uuid.
		final String launchHandle = new SmartLaunchContextService().issue(SmartLaunchContextService.identify(user),
		    patientId, visitId);

		if (launchHandle == null) {
			resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Could not issue a launch handle");
			return;
		}

		url = url + URLEncoder.encode(launchHandle, StandardCharsets.UTF_8.name());

		resp.sendRedirect(resp.encodeRedirectURL(url));
	}
}

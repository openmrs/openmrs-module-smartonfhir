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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.openmrs.module.smartonfhir.util.SmartAppRegistry;
import org.openmrs.module.smartonfhir.util.SmartLaunchContextService;
import org.openmrs.module.smartonfhir.web.util.FhirBaseAddressStrategy;

/**
 * Starts an EHR launch: a redirect to the app's launch URL with {@code iss} and a single-use
 * {@code launch} handle. The address comes from the registry, and the context is resolved first.
 */
@Slf4j
public class SmartEhrLaunchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		final User user = Context.getAuthenticatedUser();

		// Checked first, so an unauthenticated caller cannot learn which app ids are registered.
		if (user == null) {
			resp.sendError(HttpStatus.SC_UNAUTHORIZED, "A launch must be started by an authenticated user");
			return;
		}

		final String appId = req.getParameter("appId");
		final String patientId = req.getParameter("patientId");
		final String visitId = req.getParameter("visitId");

		if (StringUtils.isBlank(appId)) {
			resp.sendError(HttpStatus.SC_BAD_REQUEST, "An appId must be provided");
			return;
		}

		final SmartApp app = SmartAppRegistry.getApp(appId);

		if (app == null) {
			// Refused rather than launched: an unregistered app is one this deployment has not permitted.
			log.error("Refused a launch for '{}', which is not in the app registry", appId);
			resp.sendError(HttpStatus.SC_NOT_FOUND, "No such app");
			return;
		}

		final String launchContext = StringUtils.defaultIfBlank(app.getLaunchContext(), "patient");
		final String contextId = "encounter".equals(launchContext) ? visitId : patientId;

		if (StringUtils.isBlank(contextId)) {
			resp.sendError(HttpStatus.SC_BAD_REQUEST,
			    "encounter".equals(launchContext) ? "A visitId must be provided" : "A patientId must be provided");
			return;
		}

		// A handle for a context that does not exist is redeemable but names nothing.
		if (!contextExists(launchContext, patientId, visitId, resp)) {
			return;
		}

		final String issuer = new FhirBaseAddressStrategy().getFhirBaseUrl(req);

		if (StringUtils.isBlank(issuer)) {
			resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Could not determine the FHIR base URL");
			return;
		}

		// Opaque and single-use, so it neither discloses the context nor can be forged from a uuid.
		final String launchHandle = new SmartLaunchContextService().issue(SmartLaunchContextService.identify(user),
		    patientId, visitId);

		final String separator = app.getLaunchUrl().contains("?") ? "&" : "?";
		final String target = app.getLaunchUrl() + separator + "iss="
		        + URLEncoder.encode(issuer, StandardCharsets.UTF_8.name()) + "&launch="
		        + URLEncoder.encode(launchHandle, StandardCharsets.UTF_8.name());

		resp.sendRedirect(resp.encodeRedirectURL(target));
	}

	/** Whether this launch's context can be read; answers the response itself when it cannot. */
	private boolean contextExists(String launchContext, String patientId, String visitId, HttpServletResponse resp)
	        throws IOException {
		final boolean forEncounter = "encounter".equals(launchContext);
		final String uuid = forEncounter ? visitId : patientId;
		final String what = forEncounter ? "visit" : "patient";

		try {
			Object context = forEncounter ? Context.getVisitService().getVisitByUuid(uuid)
			        : Context.getPatientService().getPatientByUuid(uuid);

			if (context == null) {
				log.error("Refused a launch: no {} with uuid {}", what, uuid);
				resp.sendError(HttpStatus.SC_BAD_REQUEST, "No such " + what);
				return false;
			}
		}
		catch (APIAuthenticationException e) {
			// A launch grants the app the clinician's own access, which they do not have here.
			log.error("Refused a launch: not permitted to read the {} {}", what, uuid, e);
			resp.sendError(HttpStatus.SC_FORBIDDEN, "Not permitted to launch for this " + what);
			return false;
		}

		return true;
	}
}

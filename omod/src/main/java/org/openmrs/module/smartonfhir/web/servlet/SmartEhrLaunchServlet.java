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
 * Starts an EHR launch: the clinician is in OpenMRS looking at a patient, and opens a SMART app for
 * them.
 * <p>
 * Answers with the launch notification the specification defines — a redirect to the app's own
 * launch URL carrying {@code iss} and {@code launch}. The {@code launch} value is an opaque,
 * single-use handle bound to the clinician who started the launch; the app hands it back to the
 * authorization server, which redeems it through OpenMRS to learn who and which patient.
 * <p>
 * The app is named by id and its address is read from the registry. It used to be taken from a
 * {@code launchUrl} request parameter, which made this an open redirector: anyone who could reach
 * this servlet could have a launch handle delivered to a host of their choosing.
 * <p>
 * The context is resolved before a handle is issued, so a launch names something that exists.
 */
@Slf4j
public class SmartEhrLaunchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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

		final User user = Context.getAuthenticatedUser();

		if (user == null) {
			resp.sendError(HttpStatus.SC_UNAUTHORIZED, "A launch must be started by an authenticated user");
			return;
		}

		// A handle is a credential, and one issued for a context that does not exist is redeemable but
		// names nothing: the app is sent on its way, asks for a patient it was told about, and is told
		// there is none. The chart only ever names ids it is displaying, so a miss here is a hand-made
		// URL rather than something a clinician did.
		if (!contextExists(launchContext, patientId, visitId, resp)) {
			return;
		}

		final String issuer = new FhirBaseAddressStrategy().getFhirBaseUrl(req);

		if (StringUtils.isBlank(issuer)) {
			resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Could not determine the FHIR base URL");
			return;
		}

		// The handle is opaque and single-use. It used to be the patient or visit uuid, which both
		// disclosed the context it stands for and let anyone holding a uuid forge a launch.
		final String launchHandle = new SmartLaunchContextService().issue(SmartLaunchContextService.identify(user),
		    patientId, visitId);

		final String separator = app.getLaunchUrl().contains("?") ? "&" : "?";
		final String target = app.getLaunchUrl() + separator + "iss="
		        + URLEncoder.encode(issuer, StandardCharsets.UTF_8.name()) + "&launch="
		        + URLEncoder.encode(launchHandle, StandardCharsets.UTF_8.name());

		resp.sendRedirect(resp.encodeRedirectURL(target));
	}

	/**
	 * Whether the patient or visit this launch is for can be read. Answers the response itself when it
	 * cannot, so the caller only has to stop.
	 */
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
				resp.sendError(HttpStatus.SC_NOT_FOUND, "No such " + what);
				return false;
			}
		}
		catch (APIAuthenticationException e) {
			// Reading the context is a privileged operation, and this is the one path where a clinician
			// who may start a launch might not be allowed to see what it is for. Refusing is the answer:
			// a launch grants the app the clinician's own access, which they do not have here.
			log.error("Refused a launch: not permitted to read the {} {}", what, uuid);
			resp.sendError(HttpStatus.SC_FORBIDDEN, "Not permitted to launch for this " + what);
			return false;
		}

		return true;
	}
}

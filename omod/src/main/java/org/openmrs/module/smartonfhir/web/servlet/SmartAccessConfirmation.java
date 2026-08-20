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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.nimbusds.jwt.JWTClaimsSet;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartSession;
import org.openmrs.module.smartonfhir.util.SmartLaunchContextService;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;

public class SmartAccessConfirmation extends HttpServlet {

	public static final String PATIENT_NAME = "patient";

	public static final String VISIT_NAME = "visit";

	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = req.getParameter("token");
		String launchId = req.getParameter("launch");

		if (token == null) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String decodedUrl = URLDecoder.decode(token, StandardCharsets.UTF_8.name());
		User user = Context.getAuthenticatedUser();

		if (user == null) {
			// Not logged in yet: hand control back with no app token so the flow can
			// prompt for credentials.
			res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", ""));
			return;
		}

		// Single use, and only by the user the handle was issued to.
		SmartSession smartSession = new SmartLaunchContextService().redeem(launchId,
		    SmartLaunchContextService.identify(user));

		if (smartSession == null) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown launch");
			return;
		}

		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(SmartLaunchContextService.identify(user));

		if (smartSession.getPatientUuid() != null) {
			claims.claim(PATIENT_NAME, smartSession.getPatientUuid());
		}
		if (smartSession.getVisitUuid() != null) {
			claims.claim(VISIT_NAME, smartSession.getVisitUuid());
		}

		String appToken = SmartLaunchTokens.sign(claims.build(), SmartSecretKeyHolder.getSecretKey());

		if (appToken == null) {
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not issue the launch token");
			return;
		}

		String encodedToken = URLEncoder.encode(appToken, StandardCharsets.UTF_8.name());

		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	}
}

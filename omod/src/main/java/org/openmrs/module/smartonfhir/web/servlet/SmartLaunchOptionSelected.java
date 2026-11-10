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

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;
import org.openmrs.module.smartonfhir.web.filter.AuthenticationByPassFilter;
import org.openmrs.module.smartonfhir.web.util.SmartLaunchTargets;

@Slf4j
public class SmartLaunchOptionSelected extends HttpServlet {

	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		// On every outcome, so a failed or refused launch does not leave the bypass session behind.
		try {
			selectLaunchContext(req, res);
		}
		finally {
			AuthenticationByPassFilter.endBypassSession(req);
		}
	}

	private void selectLaunchContext(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = getParameter(req, "token");
		String patientId = getParameter(req, "patientId");
		String visitId = getParameter(req, "visitId");

		// Checked before anything is decoded, so a missing parameter is a 400 rather than a 500.
		if (token == null || (patientId == null && visitId == null)) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "A token and a patient or visit are required");
			return;
		}

		// Checked here, because the bypass filter in front never requires authentication.
		if (!Context.isAuthenticated()) {
			log.error("Refused to sign launch context for an unauthenticated request");
			res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		final String decodedUrl = SmartLaunchTargets.decodeLaunchTarget(token);

		// The signed token goes to this address, so it must be the authorization server's own.
		if (!SmartLaunchTargets.isAuthorizationServerAddress(decodedUrl)) {
			log.error("Refused to send launch context to an address that is not the authorization server");
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "The launch target is not the authorization server");
			return;
		}

		final String launchTypeString = getLaunchTypeString(SmartLaunchTargets.parameterFrom(decodedUrl, "key"));

		if (launchTypeString == null) {
			res.sendError(HttpServletResponse.SC_FORBIDDEN, "The launch token names no scope");
			return;
		}

		if (launchTypeString.contains("encounter") && visitId == null) {
			// Only a standalone launch reaches this, and its visit picker went with the RefApp 2.x UI.
			log.error("An encounter launch was requested, but there is no visit-selection screen to send the user to");
			res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Encounter launch is not supported");
			return;
		}

		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();

		if (patientId != null) {
			claims.claim("patient", patientId);
		}
		if (visitId != null) {
			claims.claim("visit", visitId);
		}

		String appToken = SmartLaunchTokens.sign(claims.build(), SmartSecretKeyHolder.getSecretKey());

		if (appToken == null) {
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not issue the launch token");
			return;
		}

		String encodedToken = URLEncoder.encode(appToken, StandardCharsets.UTF_8.name());

		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	}

	private String getParameter(HttpServletRequest request, String parameter) {
		String result = request.getParameter(parameter);
		if (result == null || result.isEmpty()) {
			return null;
		}

		return result;
	}

	/**
	 * Which launch context the app asked for. The action token's signature is not checked, and nothing
	 * security-relevant rests on the answer: it only decides whether a visit is asked for.
	 */
	private String getLaunchTypeString(String key) {
		JWTClaimsSet claims = SmartLaunchTokens.readUnverifiedClaims(key);

		if (claims == null) {
			return null;
		}

		Object launchType = claims.getClaim("launchType");

		return launchType == null ? null : launchType.toString();
	}

}

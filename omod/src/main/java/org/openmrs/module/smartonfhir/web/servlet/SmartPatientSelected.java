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
import java.util.HashMap;
import java.util.Map;

import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;

public class SmartPatientSelected extends HttpServlet {
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = req.getParameter("token");
		String patientId = req.getParameter("patientId");
		
		if (token == null || patientId == null) {
			// this simulates what the controller would do if required parameteres are missing
			res.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		Map<String, String> claims = new HashMap<String, String>();
		claims.put("patient", patientId);
		
		JsonWebToken tokenSentBack = new JsonWebToken();
		for (Map.Entry<String, String> entry : claims.entrySet()) {
			String decodedValue = URLDecoder.decode(entry.getValue(), "UTF-8");
			tokenSentBack.setOtherClaims(entry.getKey(), decodedValue);
		}
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).none();
		String encodedToken = URLEncoder.encode(appToken, "UTF-8");
		
		String decodedUrl = URLDecoder.decode(token, "UTF-8");
		System.out.println(decodedUrl.replace("{APP_TOKEN}", encodedToken));
		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	}
}

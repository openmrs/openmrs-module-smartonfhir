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

import static org.openmrs.module.smartonfhir.web.servlet.SmartAccessConfirmation.PATIENT_NAME;
import static org.openmrs.module.smartonfhir.web.servlet.SmartAccessConfirmation.VISIT_NAME;

import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.keycloak.crypto.*;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;

public class SmartVisitSelected extends HttpServlet {
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String patientId = req.getParameter("patientId");
		String visitId = req.getParameter("visitId");
		String token = req.getParameter("token");
		
		if (token == null || patientId == null) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		JsonWebToken tokenSentBack = new JsonWebToken();
		tokenSentBack.setOtherClaims("patient", patientId);
		
		if (patientId != null) {
			tokenSentBack.setOtherClaims(PATIENT_NAME, patientId);
		}
		if (visitId != null) {
			tokenSentBack.setOtherClaims(VISIT_NAME, visitId);
		}
		
		SecretKeySpec hmacSecretKeySpec = new SecretKeySpec(SmartSecretKeyHolder.getSecretKey(), JavaAlgorithm.HS256);
		KeyWrapper keyWrapper = new KeyWrapper();
		keyWrapper.setAlgorithm(Algorithm.HS256);
		keyWrapper.setSecretKey(hmacSecretKeySpec);
		SignatureSignerContext signer = new MacSignatureSignerContext(keyWrapper);
		
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).sign(signer);
		String encodedToken = URLEncoder.encode(appToken, StandardCharsets.UTF_8.name());
		
		String decodedUrl = URLDecoder.decode(token, StandardCharsets.UTF_8.name());
		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	}
}

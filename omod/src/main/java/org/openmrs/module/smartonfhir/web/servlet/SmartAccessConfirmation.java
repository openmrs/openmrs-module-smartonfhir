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

import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.MacSignatureSignerContext;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartSession;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;
import org.openmrs.module.smartonfhir.util.SmartSessionCache;

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
		JsonWebToken tokenSentBack = new JsonWebToken();
		String decodedUrl = URLDecoder.decode(token, StandardCharsets.UTF_8.name());
		User user = Context.getAuthenticatedUser();
		
		if (user == null) {
			res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", ""));
			return;
		}
		
		SmartSessionCache smartSessionCache = new SmartSessionCache();
		SmartSession smartSession = smartSessionCache.get(launchId);
		
		if (smartSession.getPatientUuid() != null) {
			tokenSentBack.setOtherClaims(PATIENT_NAME, smartSession.getPatientUuid());
		}
		if (smartSession.getVisitUuid() != null) {
			tokenSentBack.setOtherClaims(VISIT_NAME, smartSession.getVisitUuid());
		}
		
		tokenSentBack.setSubject(user.getUsername());
		
		SecretKeySpec secretKeySpec = new SecretKeySpec(SmartSecretKeyHolder.getSecretKey(), JavaAlgorithm.HS256);
		KeyWrapper keyWrapper = new KeyWrapper();
		keyWrapper.setAlgorithm(Algorithm.HS256);
		keyWrapper.setSecretKey(secretKeySpec);
		SignatureSignerContext signer = new MacSignatureSignerContext(keyWrapper);
		
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).sign(signer);
		String encodedToken = URLEncoder.encode(appToken, StandardCharsets.UTF_8.name());
		
		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	}
}

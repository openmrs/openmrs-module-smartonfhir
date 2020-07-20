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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.MacSignatureSignerContext;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;

public class SmartPatientSelected extends HttpServlet {
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = req.getParameter("token");
		System.out.println("token " + token);
		String patientId = req.getParameter("patientId");
		System.out.println("patientId " + patientId);
		String state = req.getParameter("state");
		System.out.println("state " + state);
		
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
		
		// create token signer
		String secretKey = "dbc3ad18-cce2-4793-a0d1-5fbbc733bd56";
		SecretKeySpec hmacSecretKeySpec = new SecretKeySpec(secretKey.getBytes(),
		        JavaAlgorithm.getJavaAlgorithm("HmacSHA256"));
		KeyWrapper keyWrapper = new KeyWrapper();
		keyWrapper.setAlgorithm("HmacSHA256");
		keyWrapper.setSecretKey(hmacSecretKeySpec);
		SignatureSignerContext signer = new MacSignatureSignerContext(keyWrapper);
		
		// sign and encode launch context token
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).sign(signer);//.hmac256(hmacSecretKeySpec);
		String encodedToken = URLEncoder.encode(appToken, "UTF-8");
		
		String decodedUrl = URLDecoder.decode(token, "UTF-8");
		String finalToken = decodedUrl.replace("{APP_TOKEN}", encodedToken);
		String[] tokenParts = finalToken.split("\\.");
		System.out.println(new String(Base64.getDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8));
		//		System.out.println(decodedUrl.replace("{APP_TOKEN}", encodedToken));
		res.sendRedirect("http://127.0.0.1:9090/?code=" + finalToken + "&state=" + state);
	}
}

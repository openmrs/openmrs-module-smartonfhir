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
import javax.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.MacSignatureSignerContext;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.web.SmartSecretKey;
import org.openmrs.module.smartonfhir.web.filter.AuthenticationByPassFilter;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class SmartPatientSelected extends HttpServlet {
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	private SmartSecretKey smartSecretKey;
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = req.getParameter("token");
		String patientId = req.getParameter("patientId");
		
		if (token == null || patientId == null) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		JsonWebToken tokenSentBack = new JsonWebToken();
		tokenSentBack.setOtherClaims("patient", patientId);
		
		SecretKeySpec hmacSecretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(getSecretKey()), "HmacSHA256");
		KeyWrapper keyWrapper = new KeyWrapper();
		keyWrapper.setAlgorithm(Algorithm.HS256);
		keyWrapper.setSecretKey(hmacSecretKeySpec);
		SignatureSignerContext signer = new MacSignatureSignerContext(keyWrapper);
		
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).sign(signer);
		String encodedToken = URLEncoder.encode(appToken, "UTF-8");
		
		String decodedUrl = URLDecoder.decode(token, "UTF-8");
		decodedUrl = decodedUrl + "&client_id=" + req.getParameter("client_id") + "&tab_id=" + req.getParameter("tab_id")
		        + "&execution=" + req.getParameter("execution") + "&app-token=" + req.getParameter("app-token");
		
		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
		
		Boolean usedBypassAuth = (Boolean) req.getAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS);
		if (usedBypassAuth != null && usedBypassAuth) {
			HttpSession session = req.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			Context.logout();
		}
	}
	
	private String getSecretKey() throws IOException {
		final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource resource = resolver.getResource(
		    OpenmrsUtil.getDirectoryInApplicationDataDirectory("config") + File.separator + "smart-secret-key.json");
		if (resource != null) {
			resource = resolver.getResource("classpath:smart-secret-key.json");
			
			InputStream secretKeyStream = resource.getInputStream();
			
			smartSecretKey = new SmartSecretKey();
			smartSecretKey = objectMapper.readValue(secretKeyStream, SmartSecretKey.class);
		}
		
		return smartSecretKey.getSmartSharedSecretKey();
	}
}

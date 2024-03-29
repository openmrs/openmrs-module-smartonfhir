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
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.JavaAlgorithm;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.MacSignatureSignerContext;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class SmartLaunchOptionSelected extends HttpServlet {
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = getParameter(req, "token");
		String patientId = getParameter(req, "patientId");
		String visitId = getParameter(req, "visitId");
		String decodedUrl = URLDecoder.decode(token, StandardCharsets.UTF_8.name());
		
		String jwtKeyToken = null;
		try {
			jwtKeyToken = getParameterFromStringUrl(decodedUrl, "key");
		}
		catch (URISyntaxException e) {
			log.error("Verification exception while trying to determine launchType", e);
			return;
		}
		
		String launchTypeString = null;
		
		try {
			launchTypeString = getLaunchTypeString(jwtKeyToken);
		}
		catch (VerificationException e) {
			log.error("Error while extracting the launch type from token", e);
			return;
		}
		
		if (launchTypeString == null) {
			res.sendError(HttpServletResponse.SC_FORBIDDEN, "Couldn't found scope in Token");
			return;
		}
		
		if (launchTypeString.contains("encounter") && visitId == null) {
			res.sendRedirect(res.encodeRedirectURL(
			    req.getContextPath() + "/smartonfhir/findVisit.page?app=smartonfhir.search.visit&patientId=" + patientId
			            + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.name())));
			return;
		}
		
		if (token == null || (patientId == null && visitId == null)) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		JsonWebToken tokenSentBack = new JsonWebToken();
		tokenSentBack.setOtherClaims("patient", patientId);
		tokenSentBack.setOtherClaims("visit", visitId);
		
		SecretKeySpec hmacSecretKeySpec = new SecretKeySpec(SmartSecretKeyHolder.getSecretKey(), JavaAlgorithm.HS256);
		KeyWrapper keyWrapper = new KeyWrapper();
		keyWrapper.setAlgorithm(Algorithm.HS256);
		keyWrapper.setSecretKey(hmacSecretKeySpec);
		SignatureSignerContext signer = new MacSignatureSignerContext(keyWrapper);
		
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).sign(signer);
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
	
	private String getParameterFromStringUrl(String url, String parameter) throws URISyntaxException {
		MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
		
		if (params.containsKey(parameter)) {
			return params.getFirst(parameter);
		}
		
		return null;
	}
	
	private String getLaunchTypeString(String key) throws VerificationException {
		JsonWebToken appToken;
		
		appToken = TokenVerifier.create(key, JsonWebToken.class).getToken();
		
		for (Map.Entry<String, Object> value : appToken.getOtherClaims().entrySet()) {
			if (value.getKey().equals("launchType")) {
				return value.getValue().toString();
			}
		}
		
		return null;
	}
	
}

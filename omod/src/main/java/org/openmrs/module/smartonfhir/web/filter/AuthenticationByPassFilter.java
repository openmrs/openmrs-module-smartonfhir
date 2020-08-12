/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AuthenticationByPassFilter implements Filter {
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
	        throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		if (!(request.getRequestURI().contains("/.well-known") || request.getRequestURI().contains("/metadata"))) {
			if (request.getParameter("token") != null) {
				
				String token = request.getParameter("token");
				
				String[] tokenPart = token.split("\\.");
				
				String decodedToken = new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8);
				
				String username = decodedToken.substring(decodedToken.indexOf("username") + 3,
				    decodedToken.lastIndexOf("\""));
				System.out.println("Username in AuthenticationByPassFilter " + username);
				System.out.println("AuthenticationByPassFilter "
				        + new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8));
			}
		}
		
		filterChain.doFilter(request, response);
	}
	
	@Override
	public void destroy() {
	}
	
	//	@Override
	//	public void doGet(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException {
	//		String token = req.getParameter("token");
	//
	//		String[] tokenPart = token.split("\\.");
	//
	//		String decodedToken = new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8);
	//
	//		String username = decodedToken.substring(decodedToken.indexOf("username") + 3, decodedToken.lastIndexOf("\""));
	//		System.out.println("Username in AuthenticationByPassFilter " + username);
	//		System.out.println(new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8));
	//
	//		if (token == null) {
	//			// this simulates what the controller would do if required parameteres are missing
	//			res.sendError(HttpServletResponse.SC_NOT_FOUND);
	//			return;
	//		}
	//
	//		//		String secret = "aSqzP4reFgWR4j94BDT1r+81QYp/NYbY9SBwXtqV1ko=";
	//		//
	//		//		//		JsonWebToken tokenSentBack = new JsonWebToken();
	//		//		SecretKeySpec hmacSecretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
	//		//
	//		//		//		for (java.util.Map.Entry<String, String[]> me : request.getParameterMap().entrySet()) {
	//		//		//			String name = me.getKey();
	//		//		//			if (! name.startsWith("_")) {
	//		//		//				String decodedValue = URLDecoder.decode(me.getValue()[0], "UTF-8");
	//		//		//				tokenSentBack.setOtherClaims(name, decodedValue);
	//		//		//			}
	//		//		//		}
	//		//
	//		//		Map<String, String> claims = new HashMap<String, String>();
	//		//		claims.put("patient", patientId);
	//		//
	//		//		JsonWebToken tokenSentBack = new JsonWebToken();
	//		//		System.out.println(patientId);
	//		//		tokenSentBack.setOtherClaims("patient", patientId);
	//		//		//		for (Map.Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
	//		//		//			String name = entry.getKey();
	//		//		//			if (!name.startsWith("_")) {
	//		//		//				String decodedValue = URLDecoder.decode(entry.getValue()[0], "UTF-8");
	//		//		//				tokenSentBack.setOtherClaims(name, decodedValue);
	//		//		//			}
	//		//		//		}
	//		//
	//		//		String appToken = new JWSBuilder().jsonContent(tokenSentBack).hmac256(hmacSecretKeySpec);
	//		//		System.out.println(appToken);
	//		//		String encodedToken = URLEncoder.encode(appToken, "UTF-8");
	//		//
	//		//		String decodedUrl = URLDecoder.decode(token, "UTF-8");
	//		//		decodedUrl = decodedUrl + "&client_id=" + req.getParameter("client_id") + "&tab_id=" + req.getParameter("tab_id")
	//		//				+ "&execution=" + req.getParameter("execution") + "&app-token=" + req.getParameter("app-token");
	//		//		System.out.println(decodedUrl);
	//		//		//		System.out.println(new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8));
	//		//		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	//	}
}

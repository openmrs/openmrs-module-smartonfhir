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
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.jose.jws.crypto.HMACProvider;
import org.keycloak.representations.JsonWebToken;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.smartonfhir.web.smart.SmartTokenCredentials;
import org.postgresql.util.Base64;

@Slf4j
public class AuthenticationByPassFilter implements Filter {
	
	@Override
	public void init(FilterConfig filterConfig) {
		
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
	        throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		
		final String tokenParam = request.getParameter("token");
		
		if (tokenParam != null) {
			//			final URIBuilder uriBuilder;
			//			try {
			//				uriBuilder = new URIBuilder(URLDecoder.decode(tokenParam, StandardCharsets.UTF_8.name()));
			//			}
			//			catch (URISyntaxException e) {
			//				log.error("Could not parse tokenParam {}", tokenParam);
			//				filterChain.doFilter(servletRequest, servletResponse);
			//				return;
			//			}
			
			//			final String token = uriBuilder.getQueryParams().stream().filter(nvp -> nvp.getName().equalsIgnoreCase("key"))
			//			        .map(NameValuePair::getValue).findFirst().orElse(null);
			
			String token = tokenParam.substring(tokenParam.indexOf("key=") + 4, tokenParam.indexOf("&"));
			
			System.out.println("key ");
			System.out.println(token);
			String[] tokenPart = token.split("\\.");
			
			System.out.println("key VALUE");
			System.out.println(new String(java.util.Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8));
			
			if (token == null) {
				log.warn("Could not find token for current request");
				filterChain.doFilter(servletRequest, servletResponse);
				return;
			}
			
			JWSInput jwsInput;
			JsonWebToken webToken;
			try {
				jwsInput = new JWSInput(token);
				webToken = jwsInput.readJsonContent(JsonWebToken.class);
			}
			catch (JWSInputException e) {
				log.error("Error while reading JWS token", e);
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
				return;
			}
			
			String userToken = (String) webToken.getOtherClaims().get("user");
			
			try {
				jwsInput = new JWSInput(userToken);
				
				if (!HMACProvider.verify(jwsInput, Base64.decode("aSqzP4reFgWR4j94BDT1r+81QYp/NYbY9SBwXtqV1ko="))) {
					log.error("Error validating user token");
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
					return;
				}
				
				webToken = jwsInput.readJsonContent(JsonWebToken.class);
			}
			catch (JWSInputException e) {
				log.error("Error while reading user token", e);
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
				return;
			}
			
			final String username = webToken.getSubject();
			
			try {
				Context.authenticate(new SmartTokenCredentials(username));
			}
			catch (ContextAuthenticationException e) {
				log.error("Error while logging in as user {}", username, e);
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
				return;
			}
			
			filterChain.doFilter(request, response);
			
			Context.logout();
			
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
		} else {
			filterChain.doFilter(request, response);
		}
	}
	
	@Override
	public void destroy() {
	}
}

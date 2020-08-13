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

import org.openmrs.api.context.Authenticated;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.smartonfhir.web.smart.SmartTokenCredentials;

public class AuthenticationByPassFilter implements Filter {
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
	        throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		if (request.getParameter("token") != null) {
			
			String token = request.getParameter("token");
			
			//			JWSInput jwsInput = null;
			//			try {
			//				jwsInput = new JWSInput(token);
			//			}
			//			catch (JWSInputException e) {
			//				e.printStackTrace();
			//			}
			
			if (true) {
				//				if (jwsInput != null && jwsInput.verify("aSqzP4reFgWR4j94BDT1r+81QYp/NYbY9SBwXtqV1ko=")) {
				String[] tokenPart = token.split("\\.");
				
				String decodedToken = new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8);
				
				String username = decodedToken.substring(decodedToken.indexOf("username") + 11,
				    decodedToken.lastIndexOf("\""));
				System.out.println("Username in AuthenticationByPassFilter " + username);
				System.out.println("AuthenticationByPassFilter "
				        + new String(Base64.getDecoder().decode(tokenPart[1]), StandardCharsets.UTF_8));
				
				Authenticated authenticated;
				try {
					authenticated = Context.authenticate(new SmartTokenCredentials(username));
				}
				catch (ContextAuthenticationException e) {
					((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
					return;
				}
			} else {
				System.out.println("AuthenticationByPassFilter Token not verified");
			}
		}
		filterChain.doFilter(request, response);
	}
	
	@Override
	public void destroy() {
	}
}

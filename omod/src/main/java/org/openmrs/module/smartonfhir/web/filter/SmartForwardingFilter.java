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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmartForwardingFilter implements Filter {
	
	@Override
	public void init(FilterConfig filterConfig) {
	}
	
	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		if (req instanceof HttpServletRequest && res instanceof HttpServletResponse) {
			HttpServletRequest request = (HttpServletRequest) req;
			if (request.getRequestURI().endsWith("/.well-known/smart-configuration")) {
				req.getRequestDispatcher("/ms/smartConfig").forward(req, res);
				return;
			}
			
			if (request.getRequestURI().endsWith("/smartPatientSelected")) {
				req.getRequestDispatcher("/ms/smartPatientSelected").forward(req, res);
				return;
			}
			
			if (request.getRequestURI().contains("/ms/smartEhrLaunchServlet")) {
				req.getRequestDispatcher("/ms/smartEhrLaunchServlet").forward(req, res);
				return;
			}
			if (request.getRequestURI().contains("/ms/smartAppSelectorServlet")) {
				req.getRequestDispatcher("/ms/smartAppSelectorServlet").forward(req, res);
				return;
			}
		}
		chain.doFilter(req, res);
	}
	
	@Override
	public void destroy() {
		
	}
}

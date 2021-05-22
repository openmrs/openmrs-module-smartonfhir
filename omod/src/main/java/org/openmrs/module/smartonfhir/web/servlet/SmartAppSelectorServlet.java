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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.openmrs.module.fhir2.web.util.OpenmrsFhirAddressStrategy;
import org.springframework.beans.factory.annotation.Autowired;

public class SmartAppSelectorServlet extends HttpServlet {
	
	@Autowired
	private OpenmrsFhirAddressStrategy openmrsFhirAddressStrategy;
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String baseURL = openmrsFhirAddressStrategy.determineServerBase(null, req);
		String smartAppLaunchURL = req.getParameter("launchUrl");
		
		String url = smartAppLaunchURL + "?iss=" + baseURL + "&launch=";
		
		if (StringUtils.isBlank(url)) {
			resp.sendError(HttpStatus.SC_BAD_REQUEST, "A url must be provided");
			return;
		}
		
		resp.sendRedirect(resp.encodeRedirectURL(url));
	}
}

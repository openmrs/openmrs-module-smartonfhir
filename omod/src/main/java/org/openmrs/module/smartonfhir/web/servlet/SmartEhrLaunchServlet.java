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

import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.openmrs.module.smartonfhir.model.SmartSession;
import org.openmrs.module.smartonfhir.util.SmartSessionCache;
import org.springframework.beans.factory.annotation.Autowired;

public class SmartEhrLaunchServlet extends HttpServlet {
	
	@Autowired
	private IServerAddressStrategy iServerAddressStrategy;
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String baseURL = iServerAddressStrategy.determineServerBase(req.getServletContext(), req);
		String patientId = req.getParameter("patientId");
		String launchUrl = req.getParameter("launchUrl");
		String visitId = req.getParameter("visitId");
		String launchContext = req.getParameter("launchContext");
		
		if (!(baseURL.contains("R4") || baseURL.contains("R3"))) {
			String fhirVersion = req.getParameter("fhirVersion");
			baseURL = baseURL + fhirVersion;
		}
		
		SmartSessionCache smartSessionCache = new SmartSessionCache();
		SmartSession smartSession = new SmartSession();
		
		smartSession.setPatientUuid(patientId);
		smartSession.setVisitUuid(visitId);
		
		String url = "";
		
		if (launchContext.equals("patient")) {
			url = launchUrl + "?iss=" + baseURL + "&launch=" + patientId;
			smartSessionCache.put(patientId, smartSession);
		}
		
		if (launchContext.equals("visit")) {
			url = launchUrl + "?iss=" + baseURL + "&launch=" + visitId;
			smartSessionCache.put(visitId, smartSession);
		}
		
		if (StringUtils.isBlank(url)) {
			resp.sendError(HttpStatus.SC_BAD_REQUEST, "A url must be provided");
			return;
		}
		
		resp.sendRedirect(resp.encodeRedirectURL(url));
	}
}

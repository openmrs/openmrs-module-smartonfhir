/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.util;

import javax.servlet.http.HttpServletRequest;

import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import org.openmrs.api.context.Context;

public class FhirBaseAddressStrategy {
	
	private static final String DEFAULT_FHIR_VERSION = "R4";
	
	public String getBaseAddress(HttpServletRequest request) {
		IServerAddressStrategy iServerAddressStrategy = Context.getRegisteredComponent("openmrsFhirAddressStrategy",
		    IServerAddressStrategy.class);
		String baseURL = iServerAddressStrategy.determineServerBase(request.getServletContext(), request);
		String smartAppLaunchURL = request.getParameter("launchUrl");
		
		if (!(baseURL.contains("R4") || baseURL.contains("R3"))) {
			String fhirVersion = request.getParameter("fhirVersion");
			if (fhirVersion == null) {
				fhirVersion = DEFAULT_FHIR_VERSION;
			}
			baseURL = baseURL + fhirVersion;
		}
		
		String url = smartAppLaunchURL + "?iss=" + baseURL + "&launch=";
		
		return url;
	}
}

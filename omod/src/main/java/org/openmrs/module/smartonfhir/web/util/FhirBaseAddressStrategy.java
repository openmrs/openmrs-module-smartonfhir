/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.util;

import javax.servlet.http.HttpServletRequest;

import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import org.openmrs.api.context.Context;

public class FhirBaseAddressStrategy {

	private static final String DEFAULT_FHIR_VERSION = "R4";

	/**
	 * This server's FHIR base URL, which an EHR launch sends to the app as {@code iss} and which the
	 * app sends back as {@code aud}. Taken from the FHIR2 module's own address strategy so the three
	 * agree.
	 * <p>
	 * This used to return the app's launch URL with {@code iss} and {@code launch=} already appended,
	 * reading the app's address from a request parameter. That made every caller an open redirector.
	 * Where the launch is sent is the app registry's business; this only answers where the FHIR API is.
	 */
	public String getFhirBaseUrl(HttpServletRequest request) {
		IServerAddressStrategy addressStrategy = Context.getRegisteredComponent("openmrsFhirAddressStrategy",
		    IServerAddressStrategy.class);
		String baseUrl = addressStrategy.determineServerBase(request.getServletContext(), request);

		if (baseUrl == null) {
			return null;
		}

		if (baseUrl.contains("R4") || baseUrl.contains("R3")) {
			return baseUrl;
		}

		String fhirVersion = request.getParameter("fhirVersion");

		return baseUrl + (fhirVersion == null ? DEFAULT_FHIR_VERSION : fhirVersion);
	}
}

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
import javax.servlet.http.HttpServletRequestWrapper;

import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import org.openmrs.api.context.Context;

public class FhirBaseAddressStrategy {

	private static final String DEFAULT_FHIR_VERSION = "R4";

	/**
	 * This server's FHIR base URL, sent to the app as {@code iss} and returned as {@code aud}. Taken
	 * from the FHIR2 module's own address strategy so the three agree.
	 */
	public String getFhirBaseUrl(HttpServletRequest request) {
		IServerAddressStrategy addressStrategy = Context.getRegisteredComponent("openmrsFhirAddressStrategy",
		    IServerAddressStrategy.class);
		String baseUrl = addressStrategy.determineServerBase(request.getServletContext(), asFhirRequest(request));

		if (baseUrl == null) {
			return null;
		}

		if (baseUrl.contains("R4") || baseUrl.contains("R3")) {
			return baseUrl;
		}

		return baseUrl + fhirVersion(request);
	}

	/**
	 * The request as it would look addressed to the FHIR API, because that is what FHIR2's address
	 * strategy expects to be handed.
	 * <p>
	 * Given the real request -- a launch servlet under {@code /ms/} -- FHIR2 cannot tell which FHIR
	 * version is being asked for, and logs
	 * {@code Could not determine FHIR version for URI ... and path ...} at ERROR on every launch before
	 * returning a base with no version on the end. The launch still worked, because the version was
	 * appended below, but every launch left an error in the log that looked like a fault and was not.
	 * Handing it a URI it can read costs nothing and keeps the base FHIR2's own to compute, so the
	 * {@code iss} an app is given still agrees with the server it will call.
	 */
	private HttpServletRequest asFhirRequest(HttpServletRequest request) {
		final String uri = request.getContextPath() + "/ws/fhir2/" + fhirVersion(request);

		return new HttpServletRequestWrapper(request) {

			@Override
			public String getRequestURI() {
				return uri;
			}
		};
	}

	private String fhirVersion(HttpServletRequest request) {
		String fhirVersion = request.getParameter("fhirVersion");

		return fhirVersion == null || fhirVersion.trim().isEmpty() ? DEFAULT_FHIR_VERSION : fhirVersion.trim();
	}
}

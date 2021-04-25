/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.page.controller;

import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.ui.framework.page.Redirect;
import org.springframework.web.bind.annotation.RequestParam;

public class SmartAppsRedirectPageController {
	
	public Redirect get(@RequestParam(value = "app") AppDescriptor app,
	        @RequestParam(required = false, value = "patientId") String patientId,
	        @RequestParam(required = false, value = "visitId") String visitId) {
		
		String launchUrl = app.getConfig().get("launchUrl").getTextValue();
		String launchType = app.getConfig().get("launchType").getTextValue();
		String launchContext = app.getConfig().get("launchContext").getTextValue();
		
		// For EHR launch
		if (launchType.equals("EHR")) {
			return new Redirect("ms/smartEhrLaunchServlet?launchUrl=" + launchUrl + "&patientId=" + patientId + "&visitId="
			        + visitId + "&launchContext=" + launchContext);
		}
		
		// For Standalone launch
		if (launchType.equals("standalone")) {
			return new Redirect("ms/smartAppSelectorServlet?launchUrl=" + launchUrl);
		}
		
		return new Redirect("ms/smartAppSelectorServlet?launchUrl=");
	}
}

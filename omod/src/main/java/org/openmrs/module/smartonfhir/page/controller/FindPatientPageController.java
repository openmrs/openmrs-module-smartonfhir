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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.helper.BreadcrumbHelper;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

@SuppressWarnings("unused")
public class FindPatientPageController {
	
	public void get(PageModel model, @RequestParam("app") AppDescriptor app, @RequestParam("token") String token,
	        @RequestParam("launchType") String launchType, UiSessionContext sessionContext, UiUtils ui)
	        throws UnsupportedEncodingException {
		model.addAttribute("afterSelectedUrl", app.getConfig().get("afterSelectedUrl").getTextValue() + "&token="
		        + URLEncoder.encode(token, StandardCharsets.UTF_8.name()) + "&launchType=" + launchType);
		model.addAttribute("heading", app.getConfig().get("heading").getTextValue());
		model.addAttribute("label", app.getConfig().get("label").getTextValue());
		model.addAttribute("showLastViewedPatients", app.getConfig().get("showLastViewedPatients").getBooleanValue());
		
		if (app.getConfig().get("registrationAppLink") == null) {
			model.addAttribute("registrationAppLink", "");
		} else {
			model.addAttribute("registrationAppLink", app.getConfig().get("registrationAppLink").getTextValue());
		}
		BreadcrumbHelper.addBreadcrumbsIfDefinedInApp(app, model, ui);
	}
}

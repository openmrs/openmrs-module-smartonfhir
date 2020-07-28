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
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.helper.BreadcrumbHelper;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class FindPatientPageController {
	
	/**
	 * This page is built to be shared across multiple apps. To use it, you must pass an "app" request
	 * parameter, which must be the id of an existing app that is an instance of
	 * coreapps.template.findPatient
	 *
	 * @param model
	 * @param app
	 * @param sessionContext
	 */
	public void get(PageModel model, @RequestParam("app") AppDescriptor app, @RequestParam("token") String token,
	        UiSessionContext sessionContext, UiUtils ui) {
		
		model.addAttribute("afterSelectedUrl", app.getConfig().get("afterSelectedUrl").getTextValue() + "&token=" + token);
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

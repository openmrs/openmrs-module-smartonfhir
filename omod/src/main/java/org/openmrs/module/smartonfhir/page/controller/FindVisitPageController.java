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

import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class FindVisitPageController {
	
	public void get(PageModel model, @RequestParam("app") AppDescriptor app,
	        @RequestParam(required = false, value = "patientId") String patientId, UiSessionContext uiSessionContext,
	        UiUtils ui) throws UnsupportedEncodingException {
		
		model.addAttribute("afterSelectedUrl",
		    app.getConfig().get("afterSelectedUrl").getTextValue() + "&patientId=" + patientId);
		model.addAttribute("heading", app.getConfig().get("heading").getTextValue());
		model.addAttribute("patientId", patientId);
	}
}

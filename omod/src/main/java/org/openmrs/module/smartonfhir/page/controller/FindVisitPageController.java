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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.CoreAppsConstants;
import org.openmrs.module.coreapps.utils.VisitTypeHelper;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class FindVisitPageController {
	
	public String get(UiSessionContext sessionContext, PageModel model, @RequestParam("app") AppDescriptor app,
	        @SpringBean AdtService service, @SpringBean("visitService") VisitService visitService,
	        @RequestParam("patientId") String patientId, @RequestParam("token") String token,
	        UiSessionContext uiSessionContext, @SpringBean("visitTypeHelper") VisitTypeHelper visitTypeHelper)
	        throws UnsupportedEncodingException {
		
		Patient patient = Context.getPatientService().getPatientByUuid(patientId);
		
		List<Visit> activeVisits = Context.getVisitService().getVisitsByPatient(patient);
		model.addAttribute("visitSummaries", activeVisits);
		
		model.addAttribute("canViewVisits", Context.hasPrivilege(CoreAppsConstants.PRIVILEGE_PATIENT_VISITS));
		
		Map<Integer, Object> visitTypesWithAttr = new HashMap<Integer, Object>();
		
		List<VisitType> allVisitTypes = visitService.getAllVisitTypes();
		for (VisitType type : allVisitTypes) {
			Map<String, Object> typeAttr = visitTypeHelper.getVisitTypeColorAndShortName(type);
			visitTypesWithAttr.put(type.getVisitTypeId(), typeAttr);
		}
		
		model.addAttribute("visitTypesWithAttr", visitTypesWithAttr);
		
		String afterSelectedUrl = app.getConfig().get("afterSelectedUrl").getTextValue();
		afterSelectedUrl = afterSelectedUrl.replace("{{patient.uuid}}", patientId).replace("{{token}}",
		    URLEncoder.encode(token, StandardCharsets.UTF_8.name()));
		model.addAttribute("afterSelectedUrl", afterSelectedUrl);
		
		return null;
	}
}

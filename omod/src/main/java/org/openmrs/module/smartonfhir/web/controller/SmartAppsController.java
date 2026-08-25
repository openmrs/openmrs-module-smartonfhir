/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.controller;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.openmrs.module.smartonfhir.util.SmartAppRegistry;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The SMART apps a clinician may launch, listed by name and id. Administrators additionally get
 * what the registry refused, so a misconfigured app is visible outside the server log.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/smartonfhir/apps")
public class SmartAppsController extends BaseRestController {

	@RequestMapping(method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	public SimpleObject apps() {
		if (!Context.isAuthenticated()) {
			throw new APIAuthenticationException("Not authenticated");
		}

		final List<SimpleObject> apps = new ArrayList<>();

		for (SmartApp app : SmartAppRegistry.getApps()) {
			SimpleObject entry = new SimpleObject().add("id", app.getId()).add("name",
			    app.getName() == null ? app.getId() : app.getName());

			if (app.getDescription() != null) {
				entry.add("description", app.getDescription());
			}

			apps.add(entry.add("launchContext", app.getLaunchContext()));
		}

		final SimpleObject body = new SimpleObject().add("apps", apps);

		if (Context.hasPrivilege(PrivilegeConstants.VIEW_ADMIN_FUNCTIONS)) {
			body.add("problems", SmartAppRegistry.getProblems());
		}

		return body;
	}
}

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.openmrs.module.smartonfhir.util.SmartAppRegistry;
import org.openmrs.util.PrivilegeConstants;

/**
 * The SMART apps a clinician may launch, listed by name and id. Administrators additionally get
 * what the registry refused, so a misconfigured app is visible outside the server log.
 */
public class SmartAppsServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// Which apps exist is not public: it describes what a deployment has integrated with.
		if (!Context.isAuthenticated()) {
			resp.sendError(HttpStatus.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		List<Map<String, String>> apps = new ArrayList<>();

		for (SmartApp app : SmartAppRegistry.getApps()) {
			Map<String, String> entry = new LinkedHashMap<>();
			entry.put("id", app.getId());
			entry.put("name", app.getName() == null ? app.getId() : app.getName());
			if (app.getDescription() != null) {
				entry.put("description", app.getDescription());
			}
			entry.put("launchContext", app.getLaunchContext());
			apps.add(entry);
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("apps", apps);

		// Administrators only, since these messages name the properties a deployment set.
		if (Context.hasPrivilege(PrivilegeConstants.VIEW_ADMIN_FUNCTIONS)) {
			body.put("problems", SmartAppRegistry.getProblems());
		}

		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.setStatus(HttpServletResponse.SC_OK);
		objectMapper.writeValue(resp.getWriter(), body);
	}
}

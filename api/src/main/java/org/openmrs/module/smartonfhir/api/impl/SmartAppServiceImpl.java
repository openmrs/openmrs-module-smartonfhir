/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.api.impl;

import java.util.List;

import lombok.Setter;
import org.openmrs.api.ValidationException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.smartonfhir.api.SmartAppService;
import org.openmrs.module.smartonfhir.api.dao.SmartAppDao;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.springframework.transaction.annotation.Transactional;

@Setter
@Transactional
public class SmartAppServiceImpl extends BaseOpenmrsService implements SmartAppService {

	private SmartAppDao dao;

	@Override
	@Transactional(readOnly = true)
	public SmartApp getSmartAppByUuid(String uuid) {
		return uuid == null || uuid.isBlank() ? null : dao.getByUuid(uuid.trim());
	}

	@Override
	@Transactional(readOnly = true)
	public List<SmartApp> getSmartApps(boolean includeRetired) {
		return dao.getAll(includeRetired);
	}

	@Override
	public SmartApp saveSmartApp(SmartApp app) {
		// A registration is what a launch is sent to, so an unusable one is refused on the way in
		// rather than listed and then failing when a clinician chooses it.
		if (app.getName() == null || app.getName().isBlank()) {
			throw new ValidationException("A SMART app needs a name");
		}

		if (app.getLaunchUrl() == null || app.getLaunchUrl().isBlank()) {
			throw new ValidationException("A SMART app needs a launch URL");
		}

		if (!isHttpUrl(app.getLaunchUrl())) {
			throw new ValidationException("A SMART app's launch URL must be an http or https URL");
		}

		if (!"patient".equals(app.getLaunchContext()) && !"encounter".equals(app.getLaunchContext())) {
			throw new ValidationException("A SMART app's launch context must be 'patient' or 'encounter'");
		}

		SmartApp existing = dao.getByName(app.getName().trim());

		if (existing != null && !existing.getUuid().equals(app.getUuid())) {
			throw new ValidationException("A SMART app named '" + app.getName().trim() + "' is already registered");
		}

		return dao.saveOrUpdate(app);
	}

	@Override
	public SmartApp retireSmartApp(SmartApp app, String reason) {
		app.setRetired(true);
		app.setRetireReason(reason);
		return dao.saveOrUpdate(app);
	}

	@Override
	public void purgeSmartApp(SmartApp app) {
		dao.delete(app);
	}

	private boolean isHttpUrl(String value) {
		String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}
}

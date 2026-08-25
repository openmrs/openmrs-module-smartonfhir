/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.api;

import java.util.List;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.openmrs.module.smartonfhir.util.PrivilegeConstants;

/**
 * The SMART apps this deployment permits to be launched. An app that is not registered cannot be
 * launched, so managing them is privileged separately from reading them.
 */
public interface SmartAppService extends OpenmrsService {

	@Authorized(PrivilegeConstants.GET_SMART_APPS)
	SmartApp getSmartAppByUuid(String uuid);

	@Authorized(PrivilegeConstants.GET_SMART_APPS)
	List<SmartApp> getSmartApps(boolean includeRetired);

	@Authorized(PrivilegeConstants.MANAGE_SMART_APPS)
	SmartApp saveSmartApp(SmartApp app);

	@Authorized(PrivilegeConstants.MANAGE_SMART_APPS)
	SmartApp retireSmartApp(SmartApp app, String reason);

	@Authorized(PrivilegeConstants.MANAGE_SMART_APPS)
	void purgeSmartApp(SmartApp app);
}

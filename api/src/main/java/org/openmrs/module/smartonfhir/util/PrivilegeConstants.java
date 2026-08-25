/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.util;

/**
 * This module's privilege names, as {@code org.openmrs.util.PrivilegeConstants} holds the
 * platform's. Each one must match a {@code <privilege>} in config.xml, which is what creates it.
 */
public class PrivilegeConstants {

	private PrivilegeConstants() {
	}

	/** Able to read the SMART apps this deployment permits to be launched. */
	public static final String GET_SMART_APPS = "Get SMART Apps";

	/** Able to register, change and retire the SMART apps that may be launched. */
	public static final String MANAGE_SMART_APPS = "Manage SMART Apps";
}

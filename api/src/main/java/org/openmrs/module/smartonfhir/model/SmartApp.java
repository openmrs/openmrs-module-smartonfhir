/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/** A SMART app this deployment permits to be launched. */
@Data
@NoArgsConstructor
public class SmartApp {

	/** How a launch names this app. Stable, and safe to put in a URL. */
	private String id;

	/** What a clinician sees when choosing the app. */
	private String name;

	/** Optional, shown alongside the name. */
	private String description;

	/** The app's launch URL, to which {@code iss} and {@code launch} are appended. */
	private String launchUrl;

	/** The app's client id at the authorization server, recorded only to identify its registration. */
	private String clientId;

	/** {@code patient} or {@code encounter}; a launch asking for anything else is refused. */
	private String launchContext = "patient";

	public SmartApp(SmartApp other) {
		this.id = other.id;
		this.name = other.name;
		this.description = other.description;
		this.launchUrl = other.launchUrl;
		this.clientId = other.clientId;
		this.launchContext = other.launchContext;
	}

	/** An entry missing either of these would be listed and then fail when chosen, so it is refused. */
	public boolean isUsable() {
		return isNotBlank(id) && isNotBlank(launchUrl);
	}

	private static boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}
}

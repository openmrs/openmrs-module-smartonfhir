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

/**
 * A SMART app this deployment permits to be launched, as declared in the runtime properties under
 * {@code smart.app.<id>.}.
 * <p>
 * The reason this exists is {@link #launchUrl}. An EHR launch has to send the browser to the app,
 * and that address used to be taken from a request parameter — so anyone who could reach the launch
 * servlet could have a single-use launch handle delivered to a host of their choosing. Recording
 * the permitted apps means the launch servlet is asked for an app by id and looks the address up,
 * rather than being told where to send the clinician.
 */
@Data
public class SmartApp {

	/** How a launch names this app. Stable, and safe to put in a URL. */
	private String id;

	/** What a clinician sees when choosing the app. */
	private String name;

	/** Optional, shown alongside the name. */
	private String description;

	/**
	 * Where the launch is sent, which the specification calls the app's launch URL. The {@code iss} and
	 * {@code launch} parameters are appended to it.
	 */
	private String launchUrl;

	/**
	 * The app's client id at the authorization server. Not used to launch — the app sends its own — but
	 * recorded so a deployment can tell which registration an entry belongs to.
	 */
	private String clientId;

	/**
	 * {@code patient} or {@code encounter}. What context this app expects to be launched with; a launch
	 * that asks for something else is refused.
	 */
	private String launchContext = "patient";

	/**
	 * An entry missing either of these cannot be launched, and is worse than absent: it would appear in
	 * a list of apps and then fail when chosen.
	 */
	public boolean isUsable() {
		return isNotBlank(id) && isNotBlank(launchUrl);
	}

	private static boolean isNotBlank(String value) {
		return value != null && !value.trim().isEmpty();
	}
}

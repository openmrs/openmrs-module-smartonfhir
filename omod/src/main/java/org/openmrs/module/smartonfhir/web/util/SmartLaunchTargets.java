/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

/** How the launch target in a {@code token} parameter is read, in one place so readers agree. */
@Slf4j
public class SmartLaunchTargets {

	private SmartLaunchTargets() {
	}

	/**
	 * The launch target as every reader of it must see it. The container decodes the parameter once,
	 * and it needs decoding once more to become a usable URL with its {@code {APP_TOKEN}} placeholder.
	 *
	 * @return the decoded target, or null if there was nothing to decode
	 */
	public static String decodeLaunchTarget(String tokenParameter) {
		if (tokenParameter == null || tokenParameter.isBlank()) {
			return null;
		}

		try {
			return URLDecoder.decode(tokenParameter, StandardCharsets.UTF_8.name());
		}
		catch (UnsupportedEncodingException e) {
			// UTF-8 is always present; this cannot happen.
			log.error("Could not decode the launch target", e);
			return null;
		}
	}

	/**
	 * Reads one query parameter out of a launch target, with a real parser rather than a substring
	 * search that would also match a parameter merely ending in the name.
	 *
	 * @return the parameter's value, or null if the target is unparseable or does not carry it
	 */
	public static String parameterFrom(String launchTarget, String name) {
		if (launchTarget == null) {
			return null;
		}

		try {
			return UriComponentsBuilder.fromUriString(launchTarget).build().getQueryParams().getFirst(name);
		}
		catch (Exception e) {
			log.error("Could not read '{}' from a launch target", name, e);
			return null;
		}
	}

}

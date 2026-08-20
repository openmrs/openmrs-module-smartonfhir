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

/**
 * How the launch target in a {@code token} parameter is read.
 * <p>
 * One place, because there used to be two that disagreed: the filter guarding the launch URLs and
 * the servlet behind it parsed the same parameter differently, and a request could be shaped so
 * that only one of them saw a launch token in it.
 */
@Slf4j
public class SmartLaunchTargets {

	private SmartLaunchTargets() {
	}

	/**
	 * The launch target as every reader of it must see it.
	 * <p>
	 * The value is a URL that has been encoded into a query parameter, so the servlet container decodes
	 * it once and it has to be decoded once more to become a usable URL -- which also restores the
	 * {@code {APP_TOKEN}} placeholder the authorization server expects back.
	 * <p>
	 * This exists because two readers disagreed. {@code AuthenticationByPassFilter} searched the
	 * container-decoded value for {@code key=}, while the servlet decoded a second time before parsing,
	 * so a doubly-encoded {@code %256Bey=} was invisible to the filter and a {@code key=} to the
	 * servlet: the filter attempted no authentication and the servlet carried on regardless. Both call
	 * this now.
	 *
	 * @return the decoded target, or null if there was nothing to decode
	 */
	public static String decodeLaunchTarget(String tokenParameter) {
		if (tokenParameter == null || tokenParameter.trim().isEmpty()) {
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
	 * Reads one query parameter out of a launch target.
	 * <p>
	 * A real query parser rather than a substring search: {@code indexOf("key=")} also matches any
	 * parameter whose name merely ends in {@code key}, so {@code ?monkey=x&key=real} yielded {@code x}.
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

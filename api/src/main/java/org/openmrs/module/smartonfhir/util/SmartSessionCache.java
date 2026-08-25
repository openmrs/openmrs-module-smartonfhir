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

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.openmrs.module.smartonfhir.model.SmartSession;

public class SmartSessionCache {

	/**
	 * Built once so concurrent launches share one cache. It lives in this JVM only, as does the
	 * {@code UserContext} a launch already depends on.
	 */
	private static final Cache<String, SmartSession> CACHE = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES)
	        .maximumSize(500).recordStats().build();

	/** Removes the entry and returns what it held, in one operation, so a handle is used once. */
	public SmartSession take(String key) {
		return key == null ? null : CACHE.asMap().remove(key);
	}

	public boolean put(String key, SmartSession value) {
		CACHE.put(key, value);
		return Boolean.TRUE;
	}

	public SmartSession get(String key) {
		try {
			return CACHE.getIfPresent(key);
		}
		catch (Exception e) {
			return null;
		}
	}

	public boolean clear(String key) {
		CACHE.invalidate(key);

		return Boolean.TRUE;
	}
}

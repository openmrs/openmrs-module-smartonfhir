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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class SmartCachingHolder {
	
	private static LoadingCache<String, String> cache;
	
	public SmartCachingHolder() {
		if (cache == null) {
			cache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(500).recordStats()
			        .build(key -> null);
		}
	}
	
	public boolean put(String key, String value) {
		cache.put(key, value);
		return Boolean.TRUE;
	}
	
	public String get(String key) {
		try {
			return cache.get(key);
		}
		catch (Exception e) {
			return null;
		}
	}
	
	public boolean clear(String key) {
		cache.invalidate(key);
		
		return Boolean.TRUE;
	}
}

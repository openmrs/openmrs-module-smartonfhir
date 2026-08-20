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
	 * Built once, eagerly, and never reassigned.
	 * <p>
	 * This was a lazily initialised non-volatile static assigned from a constructor that runs per
	 * request, so two threads racing at startup could build two caches and publish one: a handle issued
	 * into the discarded cache then redeemed as "Unknown launch". Non-volatile publication also allowed
	 * another thread to observe a partly constructed cache.
	 * <p>
	 * <strong>This cache lives in one JVM.</strong> A launch crosses several browser redirects, two of
	 * which reach OpenMRS, so behind a load balancer a handle issued by one node is unknown to another
	 * and redemption fails intermittently as "Unknown launch". That is acceptable because a clustered
	 * OpenMRS already requires sticky sessions -- {@code UserContext} lives in the HTTP session and
	 * nothing replicates it, including the marker this module sets -- so every hop of a launch lands on
	 * the node that issued the handle. What remains is a node failing mid-launch, inside the five
	 * minute window, which costs the clinician a retry.
	 * <p>
	 * The platform's own cache manager does not help here: its clustered configuration defines an
	 * {@code invalidation-cache}, which broadcasts evictions rather than sharing values, so a handle
	 * would still be invisible on the other node. Making this genuinely node-independent means either a
	 * replicated cache, which needs a JGroups transport the deployment has to configure, or dropping
	 * server-side state and signing the context into the handle itself -- which would trade away single
	 * use, since consuming a handle on redemption is what stops it being replayed.
	 */
	private static final Cache<String, SmartSession> CACHE = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES)
	        .maximumSize(500).recordStats().build();

	/**
	 * Removes the entry and returns what it held, in one operation.
	 * <p>
	 * Redemption used to be a {@code get} followed by a {@code clear}, which is single-use only when
	 * single-threaded: two concurrent redemptions of one handle could both see a session and both
	 * proceed.
	 */
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

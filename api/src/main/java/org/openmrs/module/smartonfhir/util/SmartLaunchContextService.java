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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.User;
import org.openmrs.module.smartonfhir.model.SmartSession;

/**
 * Issues and redeems the {@code launch} handles that carry EHR launch context. A handle is opaque,
 * single use, bound to the user who created it, and held in an in-memory cache.
 */
@Slf4j
public class SmartLaunchContextService {

	/** 256 bits, so the handle cannot be usefully guessed or enumerated. */
	private static final int HANDLE_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final SmartSessionCache cache;

	public SmartLaunchContextService() {
		this(new SmartSessionCache());
	}

	SmartLaunchContextService(SmartSessionCache cache) {
		this.cache = cache;
	}

	/**
	 * Stores launch context and returns the opaque handle that stands for it.
	 *
	 * @param username the user this launch belongs to; the handle is only redeemable by them
	 * @return the handle to hand to the app as the {@code launch} parameter
	 */
	public String issue(String username, String patientUuid, String visitUuid) {
		// ensure that caller has supplied a username as this indicates the launch token owner
		if (username == null || username.isBlank()) {
			log.error("Refusing to issue a SMART launch handle with no owner; the launch cannot be attributed");
			return null;
		}

		SmartSession session = new SmartSession();
		session.setPatientUuid(patientUuid);
		session.setVisitUuid(visitUuid);
		session.setUsername(username);

		String handle = newHandle();
		cache.put(handle, session);

		log.debug("Issued SMART launch handle {} for user {}", handle, username);

		return handle;
	}

	/**
	 * Redeems a handle, removing it so it cannot be used twice.
	 *
	 * @param username the authenticated user redeeming it
	 * @return the launch context, or null if the handle is unknown, expired, already used, or belongs
	 *         to somebody else
	 */
	public SmartSession redeem(String handle, String username) {
		if (handle == null || handle.isBlank()) {
			return null;
		}

		// a launch token is only usable once, so we remove it
		SmartSession session = cache.take(handle);

		if (session == null) {
			log.warn("'{}' presented a SMART launch handle that is unknown, expired, or already used", username);
			return null;
		}

		// Objects.equals, so a handle with no recorded owner refuses everyone rather than admitting everyone.
		if (!Objects.equals(session.getUsername(), username)) {
			log.warn("A SMART launch handle issued to '{}' was presented by '{}'; refusing it", session.getUsername(),
			    username);
			return null;
		}

		return session;
	}

	/**
	 * How a launch identifies the clinician who started it, since OpenMRS does not require a username.
	 *
	 * @return the username, or the systemId when there is no username, or null when the user has
	 *         neither
	 */
	public static String identify(User user) {
		if (user == null) {
			return null;
		}

		return user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : user.getSystemId();
	}

	private String newHandle() {
		byte[] bytes = new byte[HANDLE_BYTES];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}
}

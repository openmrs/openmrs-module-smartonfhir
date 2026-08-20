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

import lombok.extern.slf4j.Slf4j;
import org.openmrs.User;
import org.openmrs.module.smartonfhir.model.SmartSession;

/**
 * Issues and redeems the {@code launch} handles that carry EHR launch context.
 * <p>
 * SMART App Launch requires this handle to be opaque and unguessable: it is passed to the app in a
 * URL and exchanged for patient context. The previous implementation used the patient's own UUID as
 * the handle, which meant the value both revealed the context it was supposed to conceal and could
 * be guessed by anyone who already knew a patient UUID. Two launches for the same patient also
 * collided on the same cache key.
 * <p>
 * A handle is <strong>single use</strong>: redeeming it removes it. A launch is one exchange, so
 * allowing a second would only permit replay.
 * <p>
 * A handle is <strong>bound to the user who created it</strong>. It is minted while a clinician is
 * looking at a patient and redeemed after that same clinician authenticates at the authorization
 * server, so a mismatch means the handle has travelled to someone else and is refused.
 * <p>
 * Storage is in-process, which is deliberate. OpenMRS keeps the authenticated user in the HTTP
 * session and its web.xml is not marked distributable, so a deployment already cannot spread a
 * user's requests across nodes without affinity; a launch handle is no more node-bound than the
 * login that created it. Putting it in the database would add a write and a read to the redirect a
 * clinician is waiting through, and an expiry sweep, for no gain.
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
		// An owner is what makes a handle refusable. A blank one used to be stored as-is, and the
		// ownership check below read a null owner as "matches everybody" rather than "matches nobody":
		// a launch started by an account with no username -- which is how OpenMRS stores its own admin
		// account -- minted a handle any authenticated user could redeem. Callers pass systemId when
		// there is no username; if they pass neither there is nothing to bind to, so refuse.
		if (username == null || username.trim().isEmpty()) {
			log.error("Refusing to issue a SMART launch handle with no owner; the launch cannot be attributed");
			return null;
		}

		SmartSession session = new SmartSession();
		session.setPatientUuid(patientUuid);
		session.setVisitUuid(visitUuid);
		session.setUsername(username);

		String handle = newHandle();
		cache.put(handle, session);

		// The handle is logged, the context is not: this is enough to correlate a launch without
		// putting a patient identifier in the log.
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
		if (handle == null || handle.trim().isEmpty()) {
			return null;
		}

		// One operation, so two concurrent redemptions cannot both succeed: whichever thread removes the
		// entry gets the session and the other gets null. It also spends the handle before the ownership
		// check below, so one offered by the wrong user is not left available for another attempt.
		SmartSession session = cache.take(handle);

		if (session == null) {
			log.warn("A SMART launch handle was presented that is unknown, expired, or already used");
			return null;
		}

		// Objects.equals, so a handle with no recorded owner refuses everyone rather than admitting
		// everyone. issue() will not create one, and this is the second line of defence.
		if (!java.util.Objects.equals(session.getUsername(), username)) {
			log.warn("A SMART launch handle issued to '{}' was presented by '{}'; refusing it", session.getUsername(),
			    username);
			return null;
		}

		return session;
	}

	/**
	 * How a launch identifies the clinician who started it.
	 * <p>
	 * OpenMRS does not require a username: its own {@code admin} account has none and is identified by
	 * its systemId, and any user created without one is stored the same way. Both ends of a launch have
	 * to agree on which of the two they use, or a handle is issued under one identity and presented
	 * under the other, so both call this.
	 *
	 * @return the username, or the systemId when there is no username, or null when the user has
	 *         neither
	 */
	public static String identify(User user) {
		if (user == null) {
			return null;
		}

		return user.getUsername() != null && !user.getUsername().trim().isEmpty() ? user.getUsername() : user.getSystemId();
	}

	private String newHandle() {
		byte[] bytes = new byte[HANDLE_BYTES];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}
}

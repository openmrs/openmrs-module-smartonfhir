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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openmrs.User;
import org.openmrs.module.smartonfhir.model.SmartSession;

/**
 * The launch handle is what an app exchanges for a patient's identity, so what matters is that it
 * cannot be guessed, cannot be reused, and cannot be redeemed by anyone other than the clinician
 * who started the launch.
 */
class SmartLaunchContextServiceTest {

	private static final String CLINICIAN = "doctor";

	private static final String PATIENT = "6a1b2c3d-0000-4444-8888-abcdefabcdef";

	private static final String VISIT = "9f8e7d6c-1111-4222-9333-fedcbafedcba";

	private SmartLaunchContextService service;

	@BeforeEach
	void setUp() {
		// A fresh cache per test: the underlying Caffeine cache is static, so tests would otherwise
		// see each other's handles.
		service = new SmartLaunchContextService(new SmartSessionCache());
	}

	@Nested
	@DisplayName("the handle")
	class Handle {

		@Test
		@DisplayName("does not contain the context it stands for")
		void doesNotLeakContext() {
			String handle = service.issue(CLINICIAN, PATIENT, VISIT);

			assertFalse(handle.contains(PATIENT), "the patient uuid was the handle before; it must not be now");
			assertFalse(handle.contains(VISIT));
			assertFalse(handle.contains(CLINICIAN));
		}

		@Test
		@DisplayName("carries enough entropy that it cannot be enumerated")
		void isLongEnough() {
			String handle = service.issue(CLINICIAN, PATIENT, null);
			byte[] decoded = Base64.getUrlDecoder().decode(handle);

			assertTrue(decoded.length >= 32, "a launch handle should be at least 256 bits, got " + decoded.length * 8);
		}

		@Test
		@DisplayName("is different every time, including for the same patient")
		void isUniquePerLaunch() {
			Set<String> handles = new HashSet<>();
			for (int i = 0; i < 200; i++) {
				handles.add(service.issue(CLINICIAN, PATIENT, VISIT));
			}

			assertEquals(200, handles.size(), "two launches for the same patient used to collide on the same key");
		}

		@Test
		@DisplayName("is safe to put in a URL without escaping")
		void isUrlSafe() {
			for (int i = 0; i < 50; i++) {
				String handle = service.issue(CLINICIAN, PATIENT, null);
				assertTrue(handle.matches("[A-Za-z0-9_-]+"), "a handle goes into a query string as-is, but got: " + handle);
			}
		}
	}

	@Nested
	@DisplayName("redeeming")
	class Redeeming {

		@Test
		@DisplayName("returns the context that was stored")
		void returnsTheContext() {
			SmartSession session = service.redeem(service.issue(CLINICIAN, PATIENT, VISIT), CLINICIAN);

			assertNotNull(session);
			assertEquals(PATIENT, session.getPatientUuid());
			assertEquals(VISIT, session.getVisitUuid());
		}

		/**
		 * A launch is one exchange. Permitting a second would only allow the handle, which travels in a URL
		 * and therefore through logs and referrers, to be replayed.
		 */
		@Test
		@DisplayName("works exactly once")
		void isSingleUse() {
			String handle = service.issue(CLINICIAN, PATIENT, VISIT);

			assertNotNull(service.redeem(handle, CLINICIAN), "the first redemption should succeed");
			assertNull(service.redeem(handle, CLINICIAN), "the second must not");
		}

		@Test
		@DisplayName("is refused for a user the handle was not issued to")
		void isBoundToItsOwner() {
			String handle = service.issue(CLINICIAN, PATIENT, VISIT);

			assertNull(service.redeem(handle, "someone-else"),
			    "a handle that reaches another user must not be redeemable by them");
		}

		/**
		 * The wrong user spends the handle, rather than leaving it available. Otherwise an attacker who
		 * guessed a handle could probe usernames until one worked.
		 */
		@Test
		@DisplayName("a refused attempt still consumes the handle")
		void wrongUserConsumesTheHandle() {
			String handle = service.issue(CLINICIAN, PATIENT, VISIT);

			assertNull(service.redeem(handle, "someone-else"));
			assertNull(service.redeem(handle, CLINICIAN),
			    "the rightful owner should not be able to use a handle somebody else already tried");
		}

		@ValueSource(strings = { "", "   ", "never-issued", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
		@ParameterizedTest(name = "unknown handle [{0}]")
		void unknownHandleIsRefused(String handle) {
			assertNull(service.redeem(handle, CLINICIAN));
		}

		@Test
		@DisplayName("null handle")
		void nullHandle() {
			assertNull(service.redeem(null, CLINICIAN));
		}

		@Test
		@DisplayName("a launch with only a patient, or only a visit, round-trips")
		void partialContextRoundTrips() {
			SmartSession patientOnly = service.redeem(service.issue(CLINICIAN, PATIENT, null), CLINICIAN);
			assertEquals(PATIENT, patientOnly.getPatientUuid());
			assertNull(patientOnly.getVisitUuid());

			SmartSession visitOnly = service.redeem(service.issue(CLINICIAN, null, VISIT), CLINICIAN);
			assertEquals(VISIT, visitOnly.getVisitUuid());
			assertNull(visitOnly.getPatientUuid());
		}
	}

	@Nested
	@DisplayName("ownership")
	class Ownership {

		/**
		 * A handle with no recorded owner used to be redeemable by anybody: the check read
		 * {@code owner != null && !owner.equals(user)}, so a null owner matched everyone rather than
		 * nobody. It mattered because the owner came from {@code User.getUsername()}, and OpenMRS's own
		 * admin account has none -- so a launch started during setup or a demo minted a handle bound to
		 * nothing, which then travelled to the app in a query string.
		 */
		@Test
		@DisplayName("a handle is never issued without an owner")
		void refusesToIssueWithoutAnOwner() {
			assertNull(service.issue(null, PATIENT, VISIT));
			assertNull(service.issue("", PATIENT, VISIT));
			assertNull(service.issue("   ", PATIENT, VISIT));
		}

		@Test
		@DisplayName("identify falls back to the system id when there is no username")
		void identifyFallsBackToSystemId() {
			User withUsername = new User();
			withUsername.setUsername("doctor");
			withUsername.setSystemId("6-7");
			assertEquals("doctor", SmartLaunchContextService.identify(withUsername));

			User adminShaped = new User();
			adminShaped.setUsername(null);
			adminShaped.setSystemId("admin");
			assertEquals("admin", SmartLaunchContextService.identify(adminShaped),
			    "the account OpenMRS ships with has no username and is identified by its system id");

			assertNull(SmartLaunchContextService.identify(null));
		}
	}
}

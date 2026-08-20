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

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartApp;

/**
 * The registry decides where an EHR launch sends a clinician. Before it existed the launch servlet
 * took that address from a request parameter, so anyone who could reach it could have a single-use
 * launch handle delivered to a host of their choosing. What matters here is that an app the
 * deployment has not declared cannot be launched, that a half-declared app is refused rather than
 * half-honoured, and that a deployment can find out why something it declared did not appear.
 */
class SmartAppRegistryTest {

	private Properties previousRuntimeProperties;

	@BeforeEach
	void isolate() {
		previousRuntimeProperties = Context.getRuntimeProperties();
		Context.setRuntimeProperties(new Properties());
		SmartAppRegistry.reset();
	}

	@AfterEach
	void restore() {
		Context.setRuntimeProperties(previousRuntimeProperties == null ? new Properties() : previousRuntimeProperties);
		SmartAppRegistry.reset();
	}

	private void property(String key, String value) {
		Properties properties = Context.getRuntimeProperties();
		properties.setProperty(key, value);
		Context.setRuntimeProperties(properties);
		SmartAppRegistry.reset();
	}

	@Nested
	@DisplayName("with nothing declared")
	class Absent {

		/**
		 * The safe direction. An empty registry means a launch has nowhere to go, which is a refusal; the
		 * alternative would be falling back to an address the caller supplies.
		 */
		@Test
		@DisplayName("nothing can be launched")
		void nothingIsLaunchable() {
			assertTrue(SmartAppRegistry.getApps().isEmpty());
			assertNull(SmartAppRegistry.getApp("vitals"));
		}

		/** Properties that arrive after the first lookup are still picked up, since nothing latched. */
		@Test
		@DisplayName("an app declared after the first lookup is still picked up")
		void aLateAppIsPickedUp() {
			assertTrue(SmartAppRegistry.getApps().isEmpty());

			property("smart.app.vitals.launchurl", "https://vitals.example.org/launch");

			assertNotNull(SmartAppRegistry.getApp("vitals"));
		}
	}

	@Nested
	@DisplayName("with apps declared")
	class Present {

		@BeforeEach
		void declareTwoApps() {
			property("smart.app.vitals.name", "Vitals Review");
			property("smart.app.vitals.description", "Recent vitals against reference ranges");
			property("smart.app.vitals.clientid", "vitals-review");
			property("smart.app.vitals.launchurl", "https://vitals.example.org/launch");
			property("smart.app.growth.launchurl", "https://growth.example.org/launch");
			property("smart.app.growth.launchcontext", "encounter");
		}

		@Test
		@DisplayName("a declared app is found by id, with its address and every field")
		void declaredAppIsFound() {
			SmartApp app = SmartAppRegistry.getApp("vitals");

			assertNotNull(app);
			assertEquals("Vitals Review", app.getName());
			assertEquals("Recent vitals against reference ranges", app.getDescription());
			assertEquals("vitals-review", app.getClientId());
			assertEquals("https://vitals.example.org/launch", app.getLaunchUrl());
		}

		@Test
		@DisplayName("launch context is read when stated, and is patient when not")
		void launchContextIsRead() {
			assertEquals("encounter", SmartAppRegistry.getApp("growth").getLaunchContext());
			assertEquals("patient", SmartAppRegistry.getApp("vitals").getLaunchContext());
		}

		/** Whatever order the properties enumerate in, a clinician sees the same menu twice running. */
		@Test
		@DisplayName("apps are listed in a stable order")
		void orderIsStable() {
			assertEquals(2, SmartAppRegistry.getApps().size());
			assertEquals("growth", SmartAppRegistry.getApps().get(0).getId());
			assertEquals("vitals", SmartAppRegistry.getApps().get(1).getId());
		}

		@Test
		@DisplayName("an app that was never declared is not found")
		void undeclaredAppIsNotFound() {
			assertNull(SmartAppRegistry.getApp("something-else"));
		}

		@Test
		@DisplayName("an id is matched exactly, not by prefix or case")
		void idIsMatchedExactly() {
			assertNull(SmartAppRegistry.getApp("vital"));
			assertNull(SmartAppRegistry.getApp("vitalsx"));
			assertNull(SmartAppRegistry.getApp("VITALS"));
		}

		@Test
		@DisplayName("a blank or null id is not found")
		void blankIdIsNotFound() {
			assertNull(SmartAppRegistry.getApp(null));
			assertNull(SmartAppRegistry.getApp("   "));
		}

		/** The caller gets copies, so nothing it does can rewrite the deployment's allowlist. */
		@Test
		@DisplayName("a caller cannot edit the registry through what it is handed")
		void callersGetCopies() {
			SmartAppRegistry.getApp("vitals").setLaunchUrl("https://evil.example.org/launch");
			SmartAppRegistry.getApps().get(0).setLaunchUrl("https://evil.example.org/launch");

			assertEquals("https://vitals.example.org/launch", SmartAppRegistry.getApp("vitals").getLaunchUrl());
		}
	}

	@Nested
	@DisplayName("with something malformed")
	class Malformed {

		@Test
		@DisplayName("an app with no launch URL is dropped, and the rest still register")
		void appWithNoLaunchUrlIsDropped() {
			property("smart.app.orphan.name", "Declared but unlaunchable");
			property("smart.app.vitals.launchurl", "https://vitals.example.org/launch");

			assertNull(SmartAppRegistry.getApp("orphan"));
			assertNotNull(SmartAppRegistry.getApp("vitals"));
		}

		@Test
		@DisplayName("a field nobody recognises is refused rather than silently dropped")
		void unknownFieldIsRefused() {
			property("smart.app.vitals.launchurl", "https://vitals.example.org/launch");
			// Deliberately not "patient": that is the model default, so it could not tell an ignored
			// typo apart from an applied one.
			property("smart.app.vitals.launchcontxt", "encounter");

			// The typo is refused and the app is still registered, because refusing it outright would
			// make one misspelled variable look like a broken deployment.
			assertNotNull(SmartAppRegistry.getApp("vitals"));
			assertEquals("patient", SmartAppRegistry.getApp("vitals").getLaunchContext());
		}

		@Test
		@DisplayName("a key naming no field at all is refused")
		void keyWithNoFieldIsRefused() {
			property("smart.app.vitals", "https://vitals.example.org/launch");

			assertTrue(SmartAppRegistry.getApps().isEmpty());
		}

		@Test
		@DisplayName("a blank value declares nothing")
		void blankValueDeclaresNothing() {
			property("smart.app.vitals.launchurl", "   ");

			assertTrue(SmartAppRegistry.getApps().isEmpty());
		}
	}

	@Nested
	@DisplayName("an app")
	class Usability {

		@Test
		@DisplayName("needs both an id and a launch URL to be usable")
		void needsIdAndLaunchUrl() {
			SmartApp app = new SmartApp();
			assertFalse(app.isUsable());

			app.setId("x");
			assertFalse(app.isUsable(), "an app with no address cannot be launched");

			app.setLaunchUrl("   ");
			assertFalse(app.isUsable(), "whitespace is not an address");

			app.setLaunchUrl("https://x.example.org/launch");
			assertTrue(app.isUsable());
		}
	}
}

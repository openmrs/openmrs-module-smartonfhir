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
 * The registry decides where an EHR launch sends a clinician, so what matters is that an undeclared
 * app cannot be launched, a half-declared one is refused, and a deployment can find out why.
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

		/** An empty registry refuses every launch, rather than falling back to a caller's address. */
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

		/** Field names are case-insensitive, so a deployment may type either convention. */
		@Test
		void acceptsCamelCasedFieldNames() {
			property("smart.app.camel.launchUrl", "https://camel.example.org/launch");
			property("smart.app.camel.clientId", "camel-review");
			property("smart.app.camel.launchContext", "encounter");

			SmartApp app = SmartAppRegistry.getApp("camel");

			assertNotNull(app, "launchUrl should register the app just as launchurl does");
			assertEquals("https://camel.example.org/launch", app.getLaunchUrl());
			assertEquals("camel-review", app.getClientId());
			assertEquals("encounter", app.getLaunchContext());
			assertTrue(SmartAppRegistry.getProblems().isEmpty(), "a camel-cased field is not a problem to report");
		}

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
			// Deliberately not "patient", which is the model default and so proves nothing here.
			property("smart.app.vitals.launchcontxt", "encounter");

			// The typo is refused and the app still registered, so one bad variable is not fatal.
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

	/**
	 * A refusal that only reaches the log is indistinguishable from a module ignoring the properties.
	 */
	@Nested
	@DisplayName("the report of what was refused")
	class Problems {

		@Test
		@DisplayName("says nothing when everything declared registered")
		void silentWhenClean() {
			property("smart.app.vitals.launchurl", "https://vitals.example.org/launch");

			assertNotNull(SmartAppRegistry.getApp("vitals"));
			assertTrue(SmartAppRegistry.getProblems().isEmpty());
		}

		@Test
		@DisplayName("is empty when nothing was declared, because nothing was refused")
		void silentWhenAbsent() {
			assertTrue(SmartAppRegistry.getApps().isEmpty());
			assertTrue(SmartAppRegistry.getProblems().isEmpty());
		}

		/** The likeliest thing to go wrong when an app is registered from the environment. */
		@Test
		@DisplayName("names the misspelled property and the field it did not recognise")
		void namesAMisspelledProperty() {
			property("smart.app.vitals.launchurl", "https://vitals.example.org/launch");
			property("smart.app.vitals.launchcontxt", "encounter");

			assertEquals(1, SmartAppRegistry.getProblems().size());
			assertTrue(SmartAppRegistry.getProblems().get(0).contains("smart.app.vitals.launchcontxt"),
			    SmartAppRegistry.getProblems().toString());
			assertTrue(SmartAppRegistry.getProblems().get(0).contains("launchcontxt"),
			    SmartAppRegistry.getProblems().toString());
		}

		@Test
		@DisplayName("names the app that had nowhere to launch")
		void namesAnAppWithNowhereToGo() {
			property("smart.app.orphan.name", "Declared but unlaunchable");

			assertEquals(1, SmartAppRegistry.getProblems().size());
			assertTrue(SmartAppRegistry.getProblems().get(0).contains("'orphan'"),
			    SmartAppRegistry.getProblems().toString());
		}

		@Test
		@DisplayName("says what a key naming no field should have looked like")
		void explainsAMalformedKey() {
			property("smart.app.vitals", "https://vitals.example.org/launch");

			assertEquals(1, SmartAppRegistry.getProblems().size());
			assertTrue(SmartAppRegistry.getProblems().get(0).contains("<id>.<field>"),
			    SmartAppRegistry.getProblems().toString());
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

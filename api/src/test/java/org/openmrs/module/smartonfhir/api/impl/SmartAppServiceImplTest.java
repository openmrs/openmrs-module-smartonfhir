/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.api.ValidationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.api.SmartAppService;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

/**
 * A registration decides where a launch handle may be delivered, so an unusable one is refused on
 * the way in rather than listed and then failing when a clinician chooses it.
 */
public class SmartAppServiceImplTest extends BaseModuleContextSensitiveTest {

	private static final String DATASET = "org/openmrs/module/smartonfhir/include/SmartAppServiceImplTest-initial.xml";

	private static final String REGISTERED_UUID = "e3a1c0de-0000-4000-8000-000000000001";

	private static final String RETIRED_UUID = "e3a1c0de-0000-4000-8000-000000000002";

	private SmartAppService service;

	@BeforeEach
	public void setUp() throws Exception {
		executeDataSet(DATASET);
		service = Context.getService(SmartAppService.class);
	}

	private SmartApp usable(String name) {
		SmartApp app = new SmartApp();
		app.setName(name);
		app.setLaunchUrl("https://growth.example.org/launch.html");
		app.setLaunchContext("patient");
		return app;
	}

	/** Reloads from the database rather than the session, so a test asserts what was persisted. */
	private SmartApp reload(String uuid) {
		Context.flushSession();
		Context.clearSession();
		return service.getSmartAppByUuid(uuid);
	}

	private List<String> names(List<SmartApp> apps) {
		return apps.stream().map(SmartApp::getName).collect(Collectors.toList());
	}

	@Test
	public void getSmartAppByUuid_shouldReturnEveryFieldOfTheRegisteredApp() {
		SmartApp app = service.getSmartAppByUuid(REGISTERED_UUID);

		assertNotNull(app, "the dataset registers this app, so it must be found");
		assertEquals(REGISTERED_UUID, app.getUuid());
		assertEquals(Integer.valueOf(1), app.getId());
		assertEquals("Vitals Review", app.getName());
		assertEquals("Trends and reference-range flagging", app.getDescription());
		assertEquals("https://vitals.example.org/launch.html", app.getLaunchUrl());
		assertEquals("vitals-review", app.getClientId());
		assertEquals("patient", app.getLaunchContext());
		assertFalse(app.getRetired(), "a live app must not read as retired");
		assertNotNull(app.getCreator());
		assertNotNull(app.getDateCreated());
	}

	@Test
	public void getSmartAppByUuid_shouldReturnNullForAnythingThatNamesNoApp() {
		assertNull(service.getSmartAppByUuid(null));
		assertNull(service.getSmartAppByUuid(""));
		assertNull(service.getSmartAppByUuid("   "));
		assertNull(service.getSmartAppByUuid("not-a-registered-uuid"));
	}

	/** A retired app is kept for the record but must not be offered for launching. */
	@Test
	public void getSmartApps_shouldExcludeRetiredAppsUnlessAsked() {
		assertEquals(Arrays.asList("Vitals Review"), names(service.getSmartApps(false)));
		// Ordered by name, so a chart menu and a log read the same way twice running.
		assertEquals(Arrays.asList("Retired App", "Vitals Review"), names(service.getSmartApps(true)));
	}

	@Test
	public void saveSmartApp_shouldRegisterAUsableAppAndStampIt() {
		SmartApp saved = service.saveSmartApp(usable("Growth Chart"));

		assertNotNull(saved.getUuid(), "a registration must be addressable");
		assertNotNull(saved.getId());

		SmartApp persisted = reload(saved.getUuid());
		assertNotNull(persisted, "it must survive a flush, not only the session");
		assertEquals("Growth Chart", persisted.getName());
		assertEquals("https://growth.example.org/launch.html", persisted.getLaunchUrl());
		assertEquals("patient", persisted.getLaunchContext());
		assertFalse(persisted.getRetired());
		assertNotNull(persisted.getCreator());
		assertNotNull(persisted.getDateCreated());
		assertEquals(Arrays.asList("Growth Chart", "Vitals Review"), names(service.getSmartApps(false)));
	}

	@Test
	public void saveSmartApp_shouldRefuseAnAppWithNoName() {
		SmartApp app = usable("   ");

		ValidationException thrown = assertThrows(ValidationException.class, () -> service.saveSmartApp(app));

		assertTrue(thrown.getMessage().contains("name"), "the message should name the problem: " + thrown.getMessage());
		assertNothingWasAdded();
	}

	@Test
	public void saveSmartApp_shouldRefuseAnAppWithNoLaunchUrl() {
		SmartApp app = usable("Growth Chart");
		app.setLaunchUrl(null);

		ValidationException thrown = assertThrows(ValidationException.class, () -> service.saveSmartApp(app));

		assertTrue(thrown.getMessage().contains("launch URL"), thrown.getMessage());
		assertNothingWasAdded();
	}

	@Test
	public void saveSmartApp_shouldRefuseALaunchUrlABrowserCannotBeSentTo() {
		SmartApp app = usable("Growth Chart");
		app.setLaunchUrl("javascript:alert(1)");

		ValidationException thrown = assertThrows(ValidationException.class, () -> service.saveSmartApp(app));

		assertTrue(thrown.getMessage().contains("http or https"), thrown.getMessage());
		assertNothingWasAdded();
	}

	@Test
	public void saveSmartApp_shouldRefuseALaunchContextNothingCanEstablish() {
		SmartApp app = usable("Growth Chart");
		app.setLaunchContext("population");

		ValidationException thrown = assertThrows(ValidationException.class, () -> service.saveSmartApp(app));

		assertTrue(thrown.getMessage().contains("launch context"), thrown.getMessage());
		assertNothingWasAdded();
	}

	/** Two apps of one name are indistinguishable in a chart menu. */
	@Test
	public void saveSmartApp_shouldRefuseANameAnotherAppHolds() {
		ValidationException thrown = assertThrows(ValidationException.class,
		    () -> service.saveSmartApp(usable("Vitals Review")));

		assertTrue(thrown.getMessage().contains("already registered"), thrown.getMessage());
		assertNothingWasAdded();
		// The app that held the name is untouched, not overwritten by the attempt.
		assertEquals("https://vitals.example.org/launch.html", service.getSmartAppByUuid(REGISTERED_UUID).getLaunchUrl());
	}

	@Test
	public void saveSmartApp_shouldLetAnAppKeepItsOwnNameWhenEdited() {
		SmartApp app = service.getSmartAppByUuid(REGISTERED_UUID);
		app.setDescription("edited");
		app.setClientId("edited-client");
		service.saveSmartApp(app);

		SmartApp persisted = reload(REGISTERED_UUID);
		assertEquals("edited", persisted.getDescription());
		assertEquals("edited-client", persisted.getClientId());
		assertEquals("Vitals Review", persisted.getName());
		assertEquals(2, service.getSmartApps(true).size(), "an edit, not a second registration");
	}

	@Test
	public void retireSmartApp_shouldTakeItOutOfTheLaunchableListButKeepTheRecord() {
		service.retireSmartApp(service.getSmartAppByUuid(REGISTERED_UUID), "replaced");

		SmartApp persisted = reload(REGISTERED_UUID);
		assertNotNull(persisted, "retiring keeps the record");
		assertTrue(persisted.getRetired());
		assertEquals("replaced", persisted.getRetireReason());
		assertNotNull(persisted.getRetiredBy());
		assertNotNull(persisted.getDateRetired());
		assertTrue(service.getSmartApps(false).isEmpty(), "a retired app must not be launchable");
		assertEquals(2, service.getSmartApps(true).size());
	}

	@Test
	public void purgeSmartApp_shouldRemoveTheRecordEntirelyAndLeaveTheOthers() {
		service.purgeSmartApp(service.getSmartAppByUuid(RETIRED_UUID));

		assertNull(reload(RETIRED_UUID));
		assertEquals(1, service.getSmartApps(true).size());
		assertNotNull(service.getSmartAppByUuid(REGISTERED_UUID), "purging one must not touch another");
	}

	/** A refusal must leave the registry exactly as it was, not half-written. */
	private void assertNothingWasAdded() {
		Context.clearSession();
		assertEquals(Arrays.asList("Retired App", "Vitals Review"), names(service.getSmartApps(true)));
	}
}

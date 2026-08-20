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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartApp;

/**
 * The SMART apps this deployment permits to be launched, declared in the runtime properties as
 * {@code smart.app.<id>.<field>}: <pre>
 * smart.app.vitals.name          = Vitals Review
 * smart.app.vitals.launchurl     = https://vitals.example.org/launch.html
 * smart.app.vitals.clientid      = vitals-review
 * smart.app.vitals.launchcontext = patient
 * </pre>
 * <p>
 * An EHR launch names an app by id and the launch address is looked up here, so an app this
 * deployment has not declared cannot be launched at all. That is the point: the address used to
 * come from a request parameter, which made the launch endpoint an open redirector for single-use
 * launch handles.
 * <p>
 * Runtime properties rather than a file of its own, because that is where OpenMRS keeps server-side
 * configuration -- the same file {@code InitializationFilter} writes at setup -- and because every
 * deployment already has a way to set them. The reference application's image turns
 * {@code OMRS_EXTRA_SMART_APP_VITALS_LAUNCHURL} into {@code smart.app.vitals.launchurl}, so an app
 * can be registered with environment variables and nothing else. Global properties would have been
 * the other candidate and are deliberately not used: those are editable through the administration
 * UI, and a launch allowlist that the web tier can rewrite gives back much of what looking the
 * address up was for.
 * <p>
 * The field names are lower case and unpunctuated -- {@code launchurl}, not {@code launchUrl} --
 * because that image lower-cases the variables it maps, so a camel-cased key would arrive as
 * something no reader is looking for.
 * <p>
 * Read once, on first use. OpenMRS fills the runtime properties in at startup and never re-reads
 * them, so registering an app takes a restart; nothing this class could do would change that.
 */
@Slf4j
public class SmartAppRegistry {

	static final String APP_PROPERTY_PREFIX = "smart.app.";

	private static volatile Map<String, SmartApp> apps;

	private static volatile boolean loadAttempted = false;

	/**
	 * @return the registered apps, by id, as copies. Never null.
	 *         <p>
	 *         Copies, because this used to hand out the stored entries themselves: any caller could do
	 *         {@code getApps().get(0).setLaunchUrl("https://evil/")} and rewrite the deployment's
	 *         allowlist process-wide. That is a poor property for the type whose whole purpose is
	 *         deciding where a launch may be sent.
	 */
	public static List<SmartApp> getApps() {
		List<SmartApp> copies = new ArrayList<>();

		for (SmartApp app : registry().values()) {
			copies.add(copyOf(app));
		}

		return copies;
	}

	private static SmartApp copyOf(SmartApp app) {
		SmartApp copy = new SmartApp();
		copy.setId(app.getId());
		copy.setName(app.getName());
		copy.setDescription(app.getDescription());
		copy.setLaunchUrl(app.getLaunchUrl());
		copy.setClientId(app.getClientId());
		copy.setLaunchContext(app.getLaunchContext());

		return copy;
	}

	/** @return the app with this id as a copy, or null if no such app is registered. */
	public static SmartApp getApp(String id) {
		if (id == null || id.trim().isEmpty()) {
			return null;
		}

		SmartApp app = registry().get(id.trim());

		return app == null ? null : copyOf(app);
	}

	/** Discards what was loaded, so the next read builds the registry again. For tests. */
	public static synchronized void reset() {
		apps = null;
		loadAttempted = false;
	}

	private static Map<String, SmartApp> registry() {
		if (!loadAttempted) {
			synchronized (SmartAppRegistry.class) {
				if (!loadAttempted) {
					load();
					// Only latch on success, as SmartOAuth2ConfigHolder does: this runs before the
					// runtime properties exist in some startup orders, and latching then would leave
					// nothing launchable until a restart.
					loadAttempted = apps != null;
				}
			}
		}

		return apps == null ? Collections.emptyMap() : apps;
	}

	private static void load() {
		final Properties properties = runtimeProperties();

		if (properties == null) {
			return;
		}

		// Sorted by id, so the app list and the log below read in a stable order rather than however
		// the properties happened to enumerate.
		final Map<String, SmartApp> byId = new TreeMap<>();

		for (String key : properties.stringPropertyNames()) {
			final String lower = key.toLowerCase();

			if (!lower.startsWith(APP_PROPERTY_PREFIX)) {
				continue;
			}

			final String remainder = lower.substring(APP_PROPERTY_PREFIX.length());
			final int dot = remainder.lastIndexOf('.');

			if (dot <= 0 || dot == remainder.length() - 1) {
				log.error("Ignoring runtime property '{}': expected {}<id>.<field>", key, APP_PROPERTY_PREFIX);
				continue;
			}

			final String id = remainder.substring(0, dot);
			final String field = remainder.substring(dot + 1);
			final String value = properties.getProperty(key);

			if (value == null || value.trim().isEmpty()) {
				continue;
			}

			final SmartApp app = byId.computeIfAbsent(id, newId -> {
				SmartApp created = new SmartApp();
				created.setId(newId);
				return created;
			});

			if (!apply(app, field, value.trim())) {
				log.error("Ignoring runtime property '{}': '{}' is not a field of a SMART app registration", key, field);
			}
		}

		// An app can be declared field by field and still be missing the one field that matters.
		byId.values().removeIf(app -> {
			if (app.isUsable()) {
				return false;
			}

			// Listing it would show a clinician an app that fails when chosen.
			log.error("Ignoring the app '{}': it has no launchUrl, so a launch would have nowhere to go", app.getId());
			return true;
		});

		apps = new LinkedHashMap<>(byId);

		if (byId.isEmpty()) {
			log.info("No SMART app is registered, so none can be launched. Set {}<id>.launchurl to register one.",
			    APP_PROPERTY_PREFIX);
		} else {
			log.info("Registered {} SMART app(s) from the runtime properties: {}", byId.size(), byId.keySet());
		}
	}

	private static Properties runtimeProperties() {
		try {
			return Context.getRuntimeProperties();
		}
		catch (Exception e) {
			// Reached before the runtime properties exist; the next lookup tries again.
			log.debug("The runtime properties are not readable yet, so no SMART app is registered", e);
			return null;
		}
	}

	private static boolean apply(SmartApp app, String field, String value) {
		switch (field) {
			case "name":
				app.setName(value);
				return true;
			case "description":
				app.setDescription(value);
				return true;
			case "launchurl":
				app.setLaunchUrl(value);
				return true;
			case "clientid":
				app.setClientId(value);
				return true;
			case "launchcontext":
				app.setLaunchContext(value);
				return true;
			default:
				return false;
		}
	}
}

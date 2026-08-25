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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartApp;

/**
 * The SMART apps this deployment permits to be launched, as {@code smart.app.<id>.<field>}. Field
 * names are case-insensitive, and are read once, so registering an app needs a restart.
 */
@Slf4j
public class SmartAppRegistry {

	static final String APP_PROPERTY_PREFIX = "smart.app.";

	private static volatile Map<String, SmartApp> apps;

	private static volatile boolean loadAttempted = false;

	/** Why the last load refused what it refused, for whoever has to make the configuration work. */
	private static volatile List<String> problems = Collections.emptyList();

	/** @return copies of the registered apps, so no caller can rewrite the allowlist. Never null. */
	public static List<SmartApp> getApps() {
		final Map<String, SmartApp> registry = registry();
		final List<SmartApp> copies = new ArrayList<>(registry.size());

		for (SmartApp app : registry.values()) {
			copies.add(new SmartApp(app));
		}

		return copies;
	}

	/** @return the app with this id as a copy, or null if no such app is registered. */
	public static SmartApp getApp(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}

		SmartApp app = registry().get(id.trim());

		return app == null ? null : new SmartApp(app);
	}

	/**
	 * @return what the last load refused and why, so a misspelled property is visible rather than only
	 *         logged. Empty when everything declared was registered.
	 */
	public static List<String> getProblems() {
		registry();

		return problems;
	}

	/** Discards what was loaded, so the next read builds the registry again. For tests. */
	public static synchronized void reset() {
		apps = null;
		loadAttempted = false;
		problems = Collections.emptyList();
	}

	private static Map<String, SmartApp> registry() {
		if (!loadAttempted) {
			synchronized (SmartAppRegistry.class) {
				if (!loadAttempted) {
					load();
					// Latched on success only; some startup orders precede the runtime properties.
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

		// Sorted by id, so the app list and the log below read in a stable order.
		final Map<String, SmartApp> byId = new TreeMap<>();
		final List<String> found = new ArrayList<>();

		for (String key : properties.stringPropertyNames()) {
			final String lower = key.toLowerCase(Locale.ROOT);

			if (!lower.startsWith(APP_PROPERTY_PREFIX)) {
				continue;
			}

			final String remainder = lower.substring(APP_PROPERTY_PREFIX.length());
			final int dot = remainder.lastIndexOf('.');

			if (dot <= 0 || dot == remainder.length() - 1) {
				refused(found,
				    String.format("Ignoring runtime property '%s': expected %s<id>.<field>", key, APP_PROPERTY_PREFIX));
				continue;
			}

			final String id = remainder.substring(0, dot);
			final String field = remainder.substring(dot + 1);
			final String value = properties.getProperty(key);

			if (value == null || value.isBlank()) {
				continue;
			}

			final SmartApp app = byId.computeIfAbsent(id, newId -> {
				SmartApp created = new SmartApp();
				created.setId(newId);
				return created;
			});

			if (!apply(app, field, value.trim())) {
				refused(found, String.format(
				    "Ignoring runtime property '%s': '%s' is not a field of a SMART app registration", key, field));
			}
		}

		// An app can be declared field by field and still be missing the one field that matters.
		byId.values().removeIf(app -> {
			if (app.isUsable()) {
				return false;
			}

			// Listing it would show a clinician an app that fails when chosen.
			refused(found, String.format("Ignoring the app '%s': it has no launchUrl, so a launch would have nowhere to go",
			    app.getId()));
			return true;
		});

		apps = new LinkedHashMap<>(byId);
		problems = Collections.unmodifiableList(found);

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

	/** Logs what was refused, as before, and keeps it for {@link #getProblems()}. */
	private static void refused(List<String> found, String message) {
		log.error(message);
		found.add(message);
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

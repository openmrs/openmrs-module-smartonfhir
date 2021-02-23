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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.web.KeycloakConfig;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Slf4j
public class KeycloakConfigHolder {
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	private static volatile KeycloakConfig keycloakConfig;
	
	public static KeycloakConfig getKeycloakConfig() {
		if (keycloakConfig == null) {
			synchronized (KeycloakConfigHolder.class) {
				if (keycloakConfig == null) {
					loadKeycloakConfig();
				}
			}
		}
		
		return keycloakConfig;
	}
	
	@SneakyThrows
	private static void loadKeycloakConfig() {
		final File f = Paths.get(OpenmrsUtil.getApplicationDataDirectory(), "config", "smart-keycloak.json").toFile();
		
		if (f.canRead()) {
			try (InputStream keycloakConfigStream = new BufferedInputStream(new FileInputStream(f))) {
				keycloakConfig = objectMapper.readValue(keycloakConfigStream, KeycloakConfig.class);
				return;
			}
			catch (FileNotFoundException e) {
				log.error("Could not load file [{}]", f.getPath(), e);
			}
		}
		
		final ResourceLoader resolver = new PathMatchingResourcePatternResolver();
		final Resource resource = resolver.getResource("classpath:smart-keycloak.json");
		
		if (resource != null && resource.isReadable()) {
			try (InputStream keycloakConfigStream = resource.getInputStream()) {
				keycloakConfig = objectMapper.readValue(keycloakConfigStream, KeycloakConfig.class);
			}
		}
	}
	
}

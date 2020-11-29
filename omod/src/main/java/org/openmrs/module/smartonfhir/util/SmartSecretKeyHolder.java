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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.web.SmartSecretKey;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Slf4j
public class SmartSecretKeyHolder {
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	private static volatile byte[] secretKey = null;
	
	public static byte[] getSecretKey() {
		if (secretKey == null) {
			synchronized (SmartSecretKeyHolder.class) {
				if (secretKey == null) {
					loadSecretKey();
				}
			}
		}
		
		return secretKey;
	}
	
	@SneakyThrows
	private static void loadSecretKey() {
		final Base64.Decoder decoder = Base64.getDecoder();
		final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		
		Resource resource = resolver
		        .getResource(OpenmrsUtil.getDirectoryInApplicationDataDirectory("config").getAbsolutePath() + File.separator
		                + "smart-secret-key.json");
		
		if (resource != null && resource.isReadable()) {
			try (InputStream secretKeyStream = resource.getInputStream()) {
				secretKey = decoder
				        .decode(objectMapper.readValue(secretKeyStream, SmartSecretKey.class).getSmartSharedSecretKey());
				return;
			}
			catch (FileNotFoundException e) {
				log.error("Could not load file [{}]", resource.getFilename(), e);
			}
		}
		
		resource = resolver.getResource("classpath:smart-secret-key.json");
		
		if (resource != null && resource.isReadable()) {
			try (InputStream secretKeyStream = resource.getInputStream()) {
				secretKey = decoder
				        .decode(objectMapper.readValue(secretKeyStream, SmartSecretKey.class).getSmartSharedSecretKey());
			}
		}
	}
}

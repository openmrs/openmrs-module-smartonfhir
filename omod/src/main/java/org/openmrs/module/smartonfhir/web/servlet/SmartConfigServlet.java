/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.web.KeycloakConfig;
import org.openmrs.module.smartonfhir.web.SmartConformance;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Slf4j
public class SmartConfigServlet extends HttpServlet {
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	private SmartConformance smartConformance;
	
	private KeycloakConfig keycloakConfig;
	
	@Override
	public void init() throws ServletException {
		try {
			this.keycloakConfig = getKeycloakConfig();
		}
		catch (IOException e) {
			throw new ServletException("Error creating SmartConfigServlet", e);
		}
		
		smartConformance = new SmartConformance();
		smartConformance.setAuthorizationEndpoint(
		    keycloakConfig.getAuthServerUrl() + "realms/" + keycloakConfig.getRealm() + "/protocol/openid-connect/auth");
		smartConformance.setTokenEndpoint(
		    keycloakConfig.getAuthServerUrl() + "realms/" + keycloakConfig.getRealm() + "/protocol/openid-connect/token");
		smartConformance.setTokenEndpointAuthMethodsSupported(new String[] { "client_secret_basic" });
		smartConformance.setScopesSupported(new String[] { "openid", "profile", "launch", "launch/patient", "patient/*.*" });
		smartConformance.setResponseTypesSupported(new String[] { "code", "code id_token", "id_token", "refresh_token" });
		smartConformance.setIntrospectionEndpoint(keycloakConfig.getAuthServerUrl() + "realms/" + keycloakConfig.getRealm()
		        + "/protocol/openid-connect/token/introspect");
		smartConformance.setCapabilities(new String[] { "launch-standalone", "launch-ehr", "client-public",
		        "client-confidential-symmetric", "context-ehr-patient", "sso-openid-connect" });
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		res.setContentType("application/json");
		res.setCharacterEncoding("UTF-8");
		res.setStatus(200);
		objectMapper.writerFor(SmartConformance.class).writeValue(res.getWriter(), smartConformance);
	}
	
	private KeycloakConfig getKeycloakConfig() throws IOException {
		final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource resource = resolver
		        .getResource(OpenmrsUtil.getDirectoryInApplicationDataDirectory("config").getAbsolutePath() + File.separator
		                + "smart-keycloak.json");
		
		if (resource != null && resource.isReadable()) {
			try (InputStream keycloakConfigStream = resource.getInputStream()) {
				keycloakConfig = objectMapper.readValue(keycloakConfigStream, KeycloakConfig.class);
				return keycloakConfig;
			}
			catch (FileNotFoundException e) {
				log.error("Could not load file [{}]", resource.getFilename(), e);
			}
		}
		
		resource = resolver.getResource("classpath:smart-keycloak.json");
		
		if (resource != null && resource.isReadable()) {
			try (InputStream keycloakConfigStream = resource.getInputStream()) {
				keycloakConfig = objectMapper.readValue(keycloakConfigStream, KeycloakConfig.class);
			}
		}
		
		return keycloakConfig;
	}
}

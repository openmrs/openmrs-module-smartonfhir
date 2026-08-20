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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openmrs.module.smartonfhir.util.SmartOAuth2ConfigHolder;
import org.openmrs.util.OpenmrsUtil;

/**
 * The discovery document is a contract. An app reads it and believes it, so what matters here is
 * the JSON a client actually receives rather than the bean behind it.
 * <p>
 * Introspection was advertised for a while by deriving Keycloak's conventional path, which meant
 * every app could discover an endpoint that answers a public client {@code 403
 * {"error":"invalid_request","error_description":"Client not allowed."}}. That is the overclaim
 * these tests hold shut: stated in configuration, advertised; not stated, absent.
 */
@ExtendWith(MockitoExtension.class)
public class SmartConfigServletTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	public Path appData;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	private String previousAppDataDirectory;

	@BeforeEach
	public void useATemporaryApplicationDataDirectory() {
		previousAppDataDirectory = OpenmrsUtil.getApplicationDataDirectory();
		OpenmrsUtil.setApplicationDataDirectory(appData.toString());
		SmartOAuth2ConfigHolder.reset();
	}

	@AfterEach
	public void restore() {
		OpenmrsUtil.setApplicationDataDirectory(previousAppDataDirectory);
		SmartOAuth2ConfigHolder.reset();
	}

	@Test
	public void doGet_shouldNotAdvertiseIntrospectionWhenTheDeploymentHasNotStatedIt() throws Exception {
		Map<String, Object> document = serve("");

		assertThat(document, not(hasKey("introspection_endpoint")));
		// The endpoints this server does stand behind are still there, so the assertion above is about
		// introspection rather than about a document that failed to build.
		assertThat((String) document.get("token_endpoint"),
		    is("https://kc.example.org/realms/openmrs/protocol/openid-connect/token"));
	}

	@Test
	public void doGet_shouldAdvertiseIntrospectionWhenTheDeploymentStatesIt() throws Exception {
		Map<String, Object> document = serve(
		    ",\"introspection-endpoint\":\"https://kc.example.org/realms/openmrs/protocol/openid-connect/token/introspect\"");

		assertThat((String) document.get("introspection_endpoint"),
		    is("https://kc.example.org/realms/openmrs/protocol/openid-connect/token/introspect"));
	}

	/**
	 * Serves the discovery document from a configuration holding nothing but the required fields plus
	 * whatever {@code extraConfig} adds. {@code advertised-jwks-uri} is stated so that building the
	 * document never reaches key discovery, which would otherwise put a network call in a unit test.
	 */
	private Map<String, Object> serve(String extraConfig) throws Exception {
		File config = appData.resolve("config").toFile();
		assertTrue(config.mkdirs() || config.isDirectory());
		Files.write(config.toPath().resolve(SmartOAuth2ConfigHolder.CONFIG_FILE_NAME),
		    ("{\"issuer\":\"https://kc.example.org/realms/openmrs\","
		            + "\"audience\":\"https://openmrs.example.org/openmrs/ws/fhir2/R4\","
		            + "\"advertised-jwks-uri\":\"https://kc.example.org/realms/openmrs/protocol/openid-connect/certs\""
		            + extraConfig + "}").getBytes(StandardCharsets.UTF_8));
		SmartOAuth2ConfigHolder.reset();

		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		SmartConfigServlet servlet = new SmartConfigServlet();
		servlet.init();
		servlet.doGet(request, response);

		return MAPPER.readValue(body.toString(), Map.class);
	}
}

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
 * The discovery document is a contract, so these assert the JSON a client receives. An endpoint
 * stated in configuration is advertised; one that is not stated stays absent.
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
		// A stated endpoint is still there, so the assertion above is not about a document that failed.
		assertThat((String) document.get("token_endpoint"),
		    is("https://kc.example.org/realms/openmrs/protocol/openid-connect/token"));
	}

	@Test
	public void doGet_shouldAdvertiseIntrospectionWhenTheDeploymentStatesIt() throws Exception {
		Map<String, Object> document = serve(
		    ",\"introspection_endpoint\":\"https://kc.example.org/realms/openmrs/protocol/openid-connect/token/introspect\"");

		assertThat((String) document.get("introspection_endpoint"),
		    is("https://kc.example.org/realms/openmrs/protocol/openid-connect/token/introspect"));
	}

	/**
	 * Serves the document from the required fields plus whatever {@code extraConfig} adds, with the
	 * advertised JWKS URI stated so building it never reaches the network.
	 */
	private Map<String, Object> serve(String extraConfig) throws Exception {
		File config = appData.resolve("config").toFile();
		assertTrue(config.mkdirs() || config.isDirectory());
		Files.write(config.toPath().resolve(SmartOAuth2ConfigHolder.CONFIG_FILE_NAME),
		    ("{\"issuer\":\"https://kc.example.org/realms/openmrs\","
		            + "\"audience\":\"https://openmrs.example.org/openmrs/ws/fhir2/R4\","
		            + "\"advertised_jwks_uri\":\"https://kc.example.org/realms/openmrs/protocol/openid-connect/certs\""
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

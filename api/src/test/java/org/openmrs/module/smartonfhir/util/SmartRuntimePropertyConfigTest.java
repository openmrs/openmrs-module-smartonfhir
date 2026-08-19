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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.util.OpenmrsUtil;

/**
 * Configuring this module through runtime properties, which is how a container does it: the
 * reference application image turns {@code OMRS_CONFIG_SMART_ISSUER} into {@code smart.issuer}, so
 * a distribution passes its environment rather than writing JSON into the application data
 * directory first.
 * <p>
 * The file remains supported, so what matters here is which wins and what happens when a deployment
 * half-configures either one.
 */
public class SmartRuntimePropertyConfigTest {

	private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

	@TempDir
	public Path appData;

	private String previousAppDataDirectory;

	private Properties previousRuntimeProperties;

	@BeforeEach
	public void isolateConfiguration() {
		previousAppDataDirectory = OpenmrsUtil.getApplicationDataDirectory();
		previousRuntimeProperties = Context.getRuntimeProperties();
		OpenmrsUtil.setApplicationDataDirectory(appData.toString());
		Context.setRuntimeProperties(new Properties());
		SmartOAuth2ConfigHolder.reset();
	}

	@AfterEach
	public void restore() {
		OpenmrsUtil.setApplicationDataDirectory(previousAppDataDirectory);
		Context.setRuntimeProperties(previousRuntimeProperties == null ? new Properties() : previousRuntimeProperties);
		SmartOAuth2ConfigHolder.reset();
	}

	private void runtimeProperty(String key, String value) {
		Properties properties = Context.getRuntimeProperties();
		properties.setProperty(key, value);
		Context.setRuntimeProperties(properties);
		SmartOAuth2ConfigHolder.reset();
	}

	private void writeConfigFile(String json) throws Exception {
		File config = appData.resolve("config").toFile();
		assertTrue(config.mkdirs() || config.isDirectory());
		Files.write(config.toPath().resolve(SmartOAuth2ConfigHolder.CONFIG_FILE_NAME),
		    json.getBytes(StandardCharsets.UTF_8));
		SmartOAuth2ConfigHolder.reset();
	}

	@Test
	public void getConfig_shouldBuildFromRuntimePropertiesWithNoFilePresent() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://ehr.example.org/openmrs/ws/fhir2/R4");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertThat(config, notNullValue());
		assertThat(config.getIssuer(), is("https://kc.example.org/realms/openmrs"));
		assertThat(config.getAudience(), is("https://ehr.example.org/openmrs/ws/fhir2/R4"));
	}

	/**
	 * An issuer without an audience would let this module accept a token minted for another FHIR
	 * server, so a half-configured environment must not produce a usable configuration.
	 */
	@Test
	public void getConfig_shouldRefuseAnIssuerWithNoAudience() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");

		assertThat(SmartOAuth2ConfigHolder.getConfig(), nullValue());
	}

	/**
	 * The environment is what an operator edits in a container, so it wins key by key over the file.
	 */
	@Test
	public void getConfig_shouldOverrideTheFilesValueWithARuntimeProperty() throws Exception {
		writeConfigFile(
		    "{\"issuer\":\"https://from-file.example.org\",\"audience\":\"https://from-file.example.org/fhir\"}");
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://from-env.example.org");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://from-env.example.org/fhir");

		assertThat(SmartOAuth2ConfigHolder.getConfig().getIssuer(), is("https://from-env.example.org"));
	}

	@Test
	public void getConfig_shouldFallBackToTheFileWhenNoPropertyIsSet() throws Exception {
		writeConfigFile(
		    "{\"issuer\":\"https://from-file.example.org\",\"audience\":\"https://from-file.example.org/fhir\"}");

		assertThat(SmartOAuth2ConfigHolder.getConfig().getIssuer(), is("https://from-file.example.org"));
	}

	@Test
	public void getConfig_shouldCarryTheOptionalPropertiesThrough() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://ehr.example.org/openmrs/ws/fhir2/R4");
		runtimeProperty(SmartOAuth2ConfigHolder.JWKS_URI_PROPERTY, "http://keycloak:8080/realms/openmrs/certs");
		runtimeProperty(SmartOAuth2ConfigHolder.ADVERTISED_JWKS_URI_PROPERTY, "https://kc.example.org/realms/openmrs/certs");
		runtimeProperty(SmartOAuth2ConfigHolder.USERNAME_CLAIM_PROPERTY, "sub");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertThat(config.getJwksUri(), is("http://keycloak:8080/realms/openmrs/certs"));
		assertThat(config.getAdvertisedJwksUri(), is("https://kc.example.org/realms/openmrs/certs"));
		assertThat(config.getUsernameClaim(), is("sub"));
	}

	/** Blank is how an unset environment variable arrives, and it must not count as configuration. */
	@Test
	public void getConfig_shouldTreatBlankPropertiesAsAbsent() throws Exception {
		writeConfigFile(
		    "{\"issuer\":\"https://from-file.example.org\",\"audience\":\"https://from-file.example.org/fhir\"}");
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "   ");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "");

		assertThat(SmartOAuth2ConfigHolder.getConfig().getIssuer(), is("https://from-file.example.org"));
	}

	/**
	 * The point of layering: a property replaces the key it names and nothing else, so the settings no
	 * property covers survive. Overriding the issuer used to discard the whole file, taking the clock
	 * skew and every explicit endpoint with it.
	 */
	@Test
	public void getConfig_shouldKeepTheFilesOtherKeysWhenAPropertyOverridesOne() throws Exception {
		writeConfigFile("{\"issuer\":\"https://from-file.example.org\","
		        + "\"audience\":\"https://from-file.example.org/fhir\"," + "\"allowed-clock-skew-seconds\":90,"
		        + "\"introspection-endpoint\":\"https://from-file.example.org/introspect\"}");
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://from-env.example.org");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertThat(config.getIssuer(), is("https://from-env.example.org"));
		assertThat(config.getAudience(), is("https://from-file.example.org/fhir"));
		assertThat(config.getAllowedClockSkewSeconds(), is(90));
		assertThat(config.getIntrospectionEndpoint(), is("https://from-file.example.org/introspect"));
	}

	/**
	 * Either source may supply either half. A file naming only the issuer was unusable on its own, and
	 * an environment naming only the audience was ignored; together they are a configuration.
	 */
	@Test
	public void getConfig_shouldCompleteAFileMissingItsAudienceFromTheEnvironment() throws Exception {
		writeConfigFile("{\"issuer\":\"https://from-file.example.org\"}");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://from-env.example.org/fhir");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertThat(config, notNullValue());
		assertThat(config.getIssuer(), is("https://from-file.example.org"));
		assertThat(config.getAudience(), is("https://from-env.example.org/fhir"));
	}

	/** A file that names neither half is still not a configuration, whatever else it sets. */
	@Test
	public void getConfig_shouldRefuseAFileWithNeitherIssuerNorAudience() throws Exception {
		writeConfigFile("{\"allowed-clock-skew-seconds\":90}");

		assertThat(SmartOAuth2ConfigHolder.getConfig(), nullValue());
	}
}

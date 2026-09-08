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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.LoggerFactory;

/**
 * Configuring this module through runtime properties, which is how a container does it. The file
 * remains supported, so what matters is which wins and what a half-configured deployment gets.
 */
public class SmartRuntimePropertyConfigTest {

	private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

	@TempDir
	public Path appData;

	private String previousAppDataDirectory;

	private Properties previousRuntimeProperties;

	/**
	 * OpenMRS configures log4j from the application data directory, so initialising it reenters
	 * OpenmrsUtil. Reaching OpenmrsUtil first leaves its own logger unassigned for that reentrant call.
	 */
	@BeforeAll
	public static void initialiseLoggingBeforeOpenmrsUtil() {
		LoggerFactory.getLogger(SmartRuntimePropertyConfigTest.class);
	}

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

	@Test
	public void getConfig_shouldBuildFromRuntimePropertiesWithNoFilePresent() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://ehr.example.org/openmrs/ws/fhir2/R4");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertThat(config, notNullValue());
		assertThat(config.getIssuer(), is("https://kc.example.org/realms/openmrs"));
		assertThat(config.getAudience(), is("https://ehr.example.org/openmrs/ws/fhir2/R4"));
	}

	/** An issuer without an audience would accept a token minted for another FHIR server. */
	@Test
	public void getConfig_shouldRefuseAnIssuerWithNoAudience() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");

		assertThat(SmartOAuth2ConfigHolder.getConfig(), nullValue());
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
	public void getConfig_shouldTreatBlankPropertiesAsAbsent() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "   ");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "");

		assertThat(SmartOAuth2ConfigHolder.getConfig(), nullValue());
	}

	/** The endpoints and the clock skew were the reason a file existed at all. */
	@Test
	public void getConfig_shouldTakeTheEndpointsAndClockSkewFromProperties() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://openmrs.example.org/ws/fhir2/R4");
		runtimeProperty(SmartOAuth2ConfigHolder.AUTHORIZATION_ENDPOINT_PROPERTY, "https://kc.example.org/auth");
		runtimeProperty(SmartOAuth2ConfigHolder.TOKEN_ENDPOINT_PROPERTY, "https://kc.example.org/token");
		runtimeProperty(SmartOAuth2ConfigHolder.INTROSPECTION_ENDPOINT_PROPERTY, "https://kc.example.org/introspect");
		runtimeProperty(SmartOAuth2ConfigHolder.REVOCATION_ENDPOINT_PROPERTY, "https://kc.example.org/revoke");
		runtimeProperty(SmartOAuth2ConfigHolder.REGISTRATION_ENDPOINT_PROPERTY, "https://kc.example.org/register");
		runtimeProperty(SmartOAuth2ConfigHolder.END_SESSION_ENDPOINT_PROPERTY, "https://kc.example.org/logout");
		runtimeProperty(SmartOAuth2ConfigHolder.CLOCK_SKEW_PROPERTY, "90");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertThat(config.getAuthorizationEndpoint(), is("https://kc.example.org/auth"));
		assertThat(config.getTokenEndpoint(), is("https://kc.example.org/token"));
		assertThat(config.getIntrospectionEndpoint(), is("https://kc.example.org/introspect"));
		assertThat(config.getRevocationEndpoint(), is("https://kc.example.org/revoke"));
		assertThat(config.getRegistrationEndpoint(), is("https://kc.example.org/register"));
		assertThat(config.getEndSessionEndpoint(), is("https://kc.example.org/logout"));
		assertThat(config.getAllowedClockSkewSeconds(), is(90));
	}

	/**
	 * The review asked for these three to be configurable, so each has to be reachable from a property
	 * rather than only settable on the model.
	 */
	@Test
	public void getConfig_shouldReadTheVerifierSettingsFromProperties() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://openmrs.example.org/ws/fhir2/R4");
		runtimeProperty(SmartOAuth2ConfigHolder.SIGNATURE_ALGORITHMS_PROPERTY, "ES256 ES384");
		runtimeProperty(SmartOAuth2ConfigHolder.DISCOVERY_TIMEOUT_PROPERTY, "3");
		runtimeProperty(SmartOAuth2ConfigHolder.JWKS_CACHE_PROPERTY, "600");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertNotNull(config);
		assertEquals("ES256 ES384", config.getSignatureAlgorithms());
		assertEquals(3, config.getDiscoveryTimeoutSeconds());
		assertEquals(600, config.getJwksCacheSeconds());
	}

	/** Refused rather than coerced, as the clock skew is, so a default cannot stand in silence. */
	@Test
	public void getConfig_shouldRefuseATimeoutThatIsNotANumber() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://openmrs.example.org/ws/fhir2/R4");
		runtimeProperty(SmartOAuth2ConfigHolder.DISCOVERY_TIMEOUT_PROPERTY, "ten");
		runtimeProperty(SmartOAuth2ConfigHolder.JWKS_CACHE_PROPERTY, "a while");

		SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		assertEquals(10, config.getDiscoveryTimeoutSeconds(), "the default must stand");
		assertEquals(0, config.getJwksCacheSeconds(), "the default must stand");
	}

	/** Coercing this would leave a deployment believing it had widened the window it accepts. */
	@Test
	public void getConfig_shouldRefuseAClockSkewThatIsNotANumber() {
		runtimeProperty(SmartOAuth2ConfigHolder.ISSUER_PROPERTY, "https://kc.example.org/realms/openmrs");
		runtimeProperty(SmartOAuth2ConfigHolder.AUDIENCE_PROPERTY, "https://openmrs.example.org/ws/fhir2/R4");
		runtimeProperty(SmartOAuth2ConfigHolder.CLOCK_SKEW_PROPERTY, "a minute or so");

		assertThat(SmartOAuth2ConfigHolder.getConfig().getAllowedClockSkewSeconds(), is(10));
	}
}

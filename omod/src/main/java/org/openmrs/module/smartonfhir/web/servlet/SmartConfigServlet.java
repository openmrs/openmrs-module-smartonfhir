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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.smartonfhir.model.SmartConformance;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifierHolder;
import org.openmrs.module.smartonfhir.util.SmartOAuth2ConfigHolder;

@Slf4j
public class SmartConfigServlet extends HttpServlet {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Only what this server can actually do, each walked end to end. The {@code permission-*} entries
	 * are absent because granted scopes are not enforced, and neither encounter context is exercised.
	 */
	private static final String[] CAPABILITIES = new String[] { "launch-ehr", "launch-standalone", "client-public",
	        "client-confidential-symmetric", "context-ehr-patient", "context-standalone-patient", "sso-openid-connect" };

	private SmartConformance smartConformance;

	@Override
	public void init() {
		final SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		if (config == null) {
			log.error("SMART on FHIR is not configured; the discovery document will not be served");
			return;
		}

		smartConformance = buildConformance(config);
	}

	/**
	 * Builds the SMART discovery document from the configured authorization server. Endpoints left
	 * unstated are derived from the issuer using Keycloak's conventional paths.
	 */
	private SmartConformance buildConformance(SmartOAuth2Config config) {
		// Only the final slash is ever there to strip.
		final String configured = config.getIssuer();
		final String issuer = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;

		SmartConformance conformance = new SmartConformance();
		conformance.setAuthorizationEndpoint(
		    orDerived(config.getAuthorizationEndpoint(), issuer, "/protocol/openid-connect/auth"));
		conformance.setTokenEndpoint(orDerived(config.getTokenEndpoint(), issuer, "/protocol/openid-connect/token"));
		// Stated only, never derived: introspection needs a confidential client.
		conformance.setIntrospectionEndpoint(config.getIntrospectionEndpoint());
		conformance
		        .setRevocationEndpoint(orDerived(config.getRevocationEndpoint(), issuer, "/protocol/openid-connect/revoke"));
		// Without this, logging out of OpenMRS leaves the authorization server's session intact.
		conformance
		        .setEndSessionEndpoint(orDerived(config.getEndSessionEndpoint(), issuer, "/protocol/openid-connect/logout"));
		conformance.setRegistrationEndpoint(config.getRegistrationEndpoint());
		conformance.setTokenEndpointAuthMethodsSupported(new String[] { "client_secret_basic", "private_key_jwt" });
		conformance.setIssuer(issuer);
		// What an app is told, which need not be where we fetch keys from.
		conformance.setJwksUri(config.getAdvertisedJwksUri() != null && !config.getAdvertisedJwksUri().isBlank()
		        ? config.getAdvertisedJwksUri().trim()
		        : SmartAccessTokenVerifierHolder.getResolvedJwksUri());
		conformance.setGrantTypesSupported(new String[] { "authorization_code", "refresh_token" });
		// SMART App Launch 2.x mandates S256 and forbids plain, so only S256 is offered.
		conformance.setCodeChallengeMethodsSupported(new String[] { "S256" });
		// Only scopes the authorization server will grant; Keycloak refuses the wildcard forms.
		conformance.setScopesSupported(new String[] { "openid", "profile", "fhirUser", "launch", "launch/patient",
		        "launch/encounter", "patient/Patient.rs", "patient/Observation.rs", "patient/Condition.rs",
		        "patient/Encounter.rs", "offline_access" });
		conformance.setResponseTypesSupported(new String[] { "code" });
		conformance.setCapabilities(CAPABILITIES);

		return conformance;
	}

	private String orDerived(String configured, String issuer, String path) {
		return configured != null && !configured.isBlank() ? configured : issuer + path;
	}

	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		if (smartConformance == null) {
			// Refuse rather than advertise endpoints nobody configured.
			res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SMART on FHIR is not configured");
			return;
		}

		res.setContentType("application/json");
		res.setCharacterEncoding("UTF-8");
		res.setStatus(200);
		objectMapper.writerFor(SmartConformance.class).writeValue(res.getWriter(), smartConformance);
	}
}

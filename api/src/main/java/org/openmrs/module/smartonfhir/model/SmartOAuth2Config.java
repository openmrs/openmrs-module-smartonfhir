/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * How this module reaches its authorization server, read from {@code {application data
 * directory}/config/smart-oauth2.json}.
 * <p>
 * This replaces the Keycloak adapter's {@code keycloak.json}. Keycloak's Java adapters were removed
 * after Keycloak 25 and the adapter configuration format went with them, so the settings this
 * module needs are now stated directly and in provider-neutral terms.
 * <p>
 * Only {@code issuer} and {@code audience} are required. The endpoints are optional: they exist so
 * a deployment can override what would otherwise be read from the issuer's OpenID Connect discovery
 * document.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartOAuth2Config {

	/**
	 * The authorization server's issuer identifier, for example
	 * {@code https://keycloak.example.org/realms/openmrs}. Access tokens whose {@code iss} does not
	 * match this are rejected.
	 */
	@JsonProperty(value = "issuer", required = true)
	private String issuer;

	/**
	 * This FHIR server's base URL, as an app names it in the SMART {@code aud} parameter, for example
	 * {@code https://openmrs.example.org/openmrs/ws/fhir2/R4}. Access tokens must carry it in
	 * {@code aud}; SMART App Launch 2.x requires the check, and without it a token minted for another
	 * FHIR server can be replayed here.
	 */
	@JsonProperty(value = "audience", required = true)
	private String audience;

	/**
	 * Where the token signing keys are published. Defaults to the {@code jwks_uri} advertised by the
	 * issuer's discovery document.
	 */
	@JsonProperty("jwks-uri")
	private String jwksUri;

	/**
	 * The JWKS location to publish in the SMART discovery document, when it differs from the one this
	 * module fetches keys from.
	 * <p>
	 * They differ whenever the authorization server is reachable by two names. This module fetches keys
	 * server to server and may use an internal address; an app reads the discovery document from
	 * outside and must be given one it can resolve. Publishing the internal address produces a
	 * discovery document that looks complete and is unusable.
	 */
	@JsonProperty("advertised-jwks-uri")
	private String advertisedJwksUri;

	@JsonProperty("authorization-endpoint")
	private String authorizationEndpoint;

	@JsonProperty("token-endpoint")
	private String tokenEndpoint;

	@JsonProperty("introspection-endpoint")
	private String introspectionEndpoint;

	@JsonProperty("revocation-endpoint")
	private String revocationEndpoint;

	@JsonProperty("registration-endpoint")
	private String registrationEndpoint;

	@JsonProperty("end-session-endpoint")
	private String endSessionEndpoint;

	/**
	 * The claim naming the OpenMRS user. Defaults to {@code preferred_username}, which is what
	 * Keycloak's profile scope emits.
	 */
	@JsonProperty("username-claim")
	private String usernameClaim = "preferred_username";

	/**
	 * Seconds of clock skew tolerated when checking {@code exp} and {@code nbf}.
	 */
	@JsonProperty("allowed-clock-skew-seconds")
	private int allowedClockSkewSeconds = 30;

	public boolean isUsable() {
		return issuer != null && !issuer.trim().isEmpty() && audience != null && !audience.trim().isEmpty();
	}
}

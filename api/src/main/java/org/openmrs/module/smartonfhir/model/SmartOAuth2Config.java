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
 * How this module reaches its authorization server. Only {@code issuer} and {@code audience} are
 * required; everything else defaults, and the endpoints come from OpenID Connect discovery.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartOAuth2Config {

	/** The authorization server's issuer identifier; a token whose {@code iss} differs is rejected. */
	@JsonProperty(value = "issuer", required = true)
	private String issuer;

	/** This FHIR server's base URL, which a token must name in {@code aud} to be accepted here. */
	@JsonProperty(value = "audience", required = true)
	private String audience;

	/**
	 * Where token signing keys are fetched from. Defaults to the issuer's advertised {@code jwks_uri}.
	 */
	@JsonProperty("jwks_uri")
	private String jwksUri;

	/** The JWKS location to publish, for when apps reach the authorization server by another name. */
	@JsonProperty("advertised_jwks_uri")
	private String advertisedJwksUri;

	@JsonProperty("authorization_endpoint")
	private String authorizationEndpoint;

	@JsonProperty("token_endpoint")
	private String tokenEndpoint;

	@JsonProperty("introspection_endpoint")
	private String introspectionEndpoint;

	@JsonProperty("revocation_endpoint")
	private String revocationEndpoint;

	@JsonProperty("registration_endpoint")
	private String registrationEndpoint;

	@JsonProperty("end_session_endpoint")
	private String endSessionEndpoint;

	/** The claim naming the OpenMRS user; Keycloak's profile scope emits the default. */
	@JsonProperty("username_claim")
	private String usernameClaim = "preferred_username";

	/** Seconds of clock skew tolerated when checking {@code exp} and {@code nbf}. */
	@JsonProperty("allowed_clock_skew_seconds")
	private int allowedClockSkewSeconds = 10;

	/** Signature algorithms accepted on access tokens, space separated. Asymmetric only. */
	@JsonProperty("signature_algorithms")
	private String signatureAlgorithms = "RS256 RS384 RS512 ES256 ES384 ES512 PS256 PS384 PS512";

	/** Seconds to wait on the issuer's discovery document before giving up. */
	@JsonProperty("discovery_timeout_seconds")
	private int discoveryTimeoutSeconds = 10;

	/** Seconds to cache the authorization server's signing keys. Zero keeps the nimbus default. */
	@JsonProperty("jwks_cache_seconds")
	private int jwksCacheSeconds = 0;

	public boolean isUsable() {
		return issuer != null && !issuer.isBlank() && audience != null && !audience.isBlank();
	}
}

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

import lombok.Data;

/**
 * How this module reaches its authorization server. Only {@code issuer} and {@code audience} are
 * required; the endpoints otherwise come from OpenID Connect discovery.
 */
@Data
public class SmartOAuth2Config {

	/** The authorization server's issuer identifier; a token whose {@code iss} differs is rejected. */
	private String issuer;

	/** This FHIR server's base URL, which a token must name in {@code aud} to be accepted here. */
	private String audience;

	/**
	 * Where token signing keys are fetched from. Defaults to the issuer's advertised {@code jwks_uri}.
	 */
	private String jwksUri;

	/** The JWKS location to publish, for when apps reach the authorization server by another name. */
	private String advertisedJwksUri;

	private String authorizationEndpoint;

	private String tokenEndpoint;

	private String introspectionEndpoint;

	private String revocationEndpoint;

	private String registrationEndpoint;

	private String endSessionEndpoint;

	/** The claim naming the OpenMRS user; Keycloak's profile scope emits the default. */
	private String usernameClaim = "preferred_username";

	/** Seconds of clock skew tolerated when checking {@code exp} and {@code nbf}. */
	private int allowedClockSkewSeconds = 10;

	/** Signature algorithms accepted on access tokens, space separated. Asymmetric only. */
	private String signatureAlgorithms = "RS256 RS384 RS512 ES256 ES384 ES512 PS256 PS384 PS512";

	/** Seconds to wait on the issuer's discovery document before giving up. */
	private int discoveryTimeoutSeconds = 10;

	/** Seconds to cache the authorization server's signing keys. Zero keeps the nimbus default. */
	private int jwksCacheSeconds = 0;

	public boolean isUsable() {
		return issuer != null && !issuer.isBlank() && audience != null && !audience.isBlank();
	}
}

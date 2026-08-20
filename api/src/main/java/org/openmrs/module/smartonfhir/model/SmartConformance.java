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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SmartConformance {

	@JsonProperty(value = "authorization_endpoint", required = true)
	private String authorizationEndpoint;

	@JsonProperty(value = "token_endpoint", required = true)
	private String tokenEndpoint;

	@JsonProperty("token_endpoint_auth_methods_supported")
	private String[] tokenEndpointAuthMethodsSupported;

	@JsonProperty("registration_endpoint")
	private String registrationEndpoint;

	@JsonProperty("scopes_supported")
	private String[] scopesSupported;

	@JsonProperty("response_types_supported")
	private String[] responseTypesSupported;

	@JsonProperty("management_endpoint")
	private String managementEndpoint;

	@JsonProperty("introspection_endpoint")
	private String introspectionEndpoint;

	@JsonProperty("revocation_endpoint")
	private String revocationEndpoint;

	/**
	 * Where an app sends the clinician to end their session with the authorization server.
	 * <p>
	 * Without this an app has no discoverable way to log anybody out, and ending an OpenMRS session
	 * alone leaves the authorization server's own session intact: the next launch in that browser is
	 * then granted silently, as the previous clinician.
	 */
	@JsonProperty("end_session_endpoint")
	private String endSessionEndpoint;

	@JsonProperty(value = "capabilities", required = true)
	private String[] capabilities;

	/**
	 * REQUIRED by SMART App Launch 2.x. Its absence is a conformance failure in its own right, and an
	 * app has no way to discover which authorization server issued the tokens it is validating.
	 */
	@JsonProperty(value = "issuer", required = true)
	private String issuer;

	/** REQUIRED by SMART App Launch 2.x, so an app can verify token signatures itself. */
	@JsonProperty(value = "jwks_uri", required = true)
	private String jwksUri;

	@JsonProperty(value = "grant_types_supported", required = true)
	private String[] grantTypesSupported;

	/**
	 * REQUIRED by SMART App Launch 2.x, which mandates S256 and forbids {@code plain}. Advertising it
	 * is how an app knows PKCE is available at all.
	 */
	@JsonProperty(value = "code_challenge_methods_supported", required = true)
	private String[] codeChallengeMethodsSupported;
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class KeycloakConfig {
	
	@JsonProperty(value = "realm", required = true)
	private String realm;
	
	@JsonProperty(value = "auth-server-url", required = true)
	private String authServerUrl;
	
	@JsonProperty(value = "ssl-required", required = false)
	private String sslRequired;
	
	@JsonProperty(value = "resource", required = false)
	private String resource;
	
	@JsonProperty(value = "public-client", required = false)
	private String publicClient;
	
	@JsonProperty(value = "confidential-port", required = false)
	private int confidentialPort;
}

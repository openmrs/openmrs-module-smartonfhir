/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.auth;

import java.util.Set;

import org.openmrs.module.authentication.AuthenticationCredentials;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;

/**
 * An already-verified SMART access token, in the form the authentication module expects. Built only
 * from a {@link SmartAccessToken}, so no credential exists without a token behind it.
 */
public class SmartBearerCredentials implements AuthenticationCredentials {

	private static final long serialVersionUID = 1L;

	private final String schemeId;

	private final String username;

	private final String patient;

	private final String encounter;

	private final Set<String> scopes;

	public SmartBearerCredentials(String schemeId, SmartAccessToken token) {
		this.schemeId = schemeId;
		this.username = token.username();
		this.patient = token.patient();
		this.encounter = token.encounter();
		this.scopes = token.scopes();
	}

	@Override
	public String getAuthenticationScheme() {
		return schemeId;
	}

	@Override
	public String getClientName() {
		return username;
	}

	public String getPatient() {
		return patient;
	}

	public String getEncounter() {
		return encounter;
	}

	public Set<String> getScopes() {
		return scopes;
	}
}

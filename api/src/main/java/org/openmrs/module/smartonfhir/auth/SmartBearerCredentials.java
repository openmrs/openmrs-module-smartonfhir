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

import java.util.Collections;
import java.util.Set;

import org.openmrs.module.authentication.AuthenticationCredentials;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;

/**
 * The result of an already-verified SMART access token, in the form the authentication module
 * expects.
 * <p>
 * Constructed only from a {@link SmartAccessToken}, which exists only if
 * {@link org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier} accepted the token. There is
 * deliberately no constructor taking a bare username: that would make it possible to mint
 * credentials for any user without presenting a token.
 * <p>
 * The raw token is not retained. Nothing downstream needs it, and holding it would put a bearer
 * credential into the HTTP session the authentication module stores these in.
 */
public class SmartBearerCredentials implements AuthenticationCredentials {

	private static final long serialVersionUID = 1L;

	/**
	 * The scheme these credentials are handled by. Must match the scheme id configured in
	 * authentication.scheme, so that whichever route produced the credentials reaches the same handler.
	 */
	public static final String SCHEME_ID = "smartBearer";

	private final String schemeId;

	private final String username;

	private final String patient;

	private final String encounter;

	private final Set<String> scopes;

	public SmartBearerCredentials(String schemeId, SmartAccessToken token) {
		this.schemeId = schemeId;
		this.username = token.getUsername();
		this.patient = token.getPatient();
		this.encounter = token.getEncounter();
		this.scopes = Collections.unmodifiableSet(token.getScopes());
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

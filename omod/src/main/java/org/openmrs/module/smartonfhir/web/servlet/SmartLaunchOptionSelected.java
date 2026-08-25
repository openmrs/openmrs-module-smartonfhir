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
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.module.smartonfhir.util.SmartLaunchTokens;
import org.openmrs.module.smartonfhir.util.SmartOAuth2ConfigHolder;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;
import org.openmrs.module.smartonfhir.web.filter.AuthenticationByPassFilter;
import org.openmrs.module.smartonfhir.web.util.SmartLaunchTargets;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class SmartLaunchOptionSelected extends HttpServlet {

	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String token = getParameter(req, "token");
		String patientId = getParameter(req, "patientId");
		String visitId = getParameter(req, "visitId");

		// Checked before anything is decoded, so a missing parameter is a 400 rather than a 500.
		if (token == null || (patientId == null && visitId == null)) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "A token and a patient or visit are required");
			return;
		}

		// Checked here, because the bypass filter in front never requires authentication.
		if (!Context.isAuthenticated()) {
			log.error("Refused to sign launch context for an unauthenticated request");
			res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		final String decodedUrl = SmartLaunchTargets.decodeLaunchTarget(token);

		// The signed token goes to this address, so it must be the authorization server's own.
		if (!isAuthorizationServerAddress(decodedUrl)) {
			log.error("Refused to send launch context to an address that is not the authorization server");
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "The launch target is not the authorization server");
			return;
		}

		final String launchTypeString = getLaunchTypeString(SmartLaunchTargets.parameterFrom(decodedUrl, "key"));

		if (launchTypeString == null) {
			res.sendError(HttpServletResponse.SC_FORBIDDEN, "The launch token names no scope");
			return;
		}

		if (launchTypeString.contains("encounter") && visitId == null) {
			// Only a standalone launch reaches this, and its visit picker went with the RefApp 2.x UI.
			log.error("An encounter launch was requested, but there is no visit-selection screen to send the user to");
			res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Encounter launch is not supported");
			return;
		}

		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();

		if (patientId != null) {
			claims.claim("patient", patientId);
		}
		if (visitId != null) {
			claims.claim("visit", visitId);
		}

		String appToken = SmartLaunchTokens.sign(claims.build(), SmartSecretKeyHolder.getSecretKey());

		if (appToken == null) {
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not issue the launch token");
			return;
		}

		String encodedToken = URLEncoder.encode(appToken, StandardCharsets.UTF_8.name());

		endSessionIfItExistedOnlyForThisLaunch(req);

		res.sendRedirect(decodedUrl.replace("{APP_TOKEN}", encodedToken));
	}

	/**
	 * Ends the OpenMRS session the launch token created, now the hand-off is done. Only a session the
	 * bypass filter made is ended; a clinician who was already signed in keeps theirs.
	 */
	private void endSessionIfItExistedOnlyForThisLaunch(HttpServletRequest req) {
		HttpSession session = req.getSession(false);

		if (session == null || session.getAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS) == null) {
			return;
		}

		// The container is left alone: a cookie for a dead session breaks the frontend's session poll.
		Context.logout();
		session.removeAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS);
	}

	/**
	 * Whether a launch target belongs to the configured authorization server, compared on origin so a
	 * host merely starting with the issuer's cannot pass.
	 */
	private boolean isAuthorizationServerAddress(String target) {
		final SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		if (target == null || config == null || config.getIssuer() == null) {
			return false;
		}

		// UriComponentsBuilder rather than java.net.URI, which rejects the {APP_TOKEN} placeholder.
		try {
			UriComponents candidate = UriComponentsBuilder.fromUriString(target).build();
			UriComponents issuer = UriComponentsBuilder.fromUriString(config.getIssuer()).build();

			return candidate.getScheme() != null && candidate.getScheme().equalsIgnoreCase(issuer.getScheme())
			        && candidate.getHost() != null && candidate.getHost().equalsIgnoreCase(issuer.getHost())
			        && candidate.getPort() == issuer.getPort();
		}
		catch (Exception e) {
			log.error("A launch target could not be parsed", e);
			return false;
		}
	}

	private String getParameter(HttpServletRequest request, String parameter) {
		String result = request.getParameter(parameter);
		if (result == null || result.isEmpty()) {
			return null;
		}

		return result;
	}

	private String getParameterFromStringUrl(String url, String parameter) throws URISyntaxException {
		MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();

		if (params.containsKey(parameter)) {
			return params.getFirst(parameter);
		}

		return null;
	}

	/**
	 * Which launch context the app asked for. The action token's signature is not checked, and nothing
	 * security-relevant rests on the answer: it only decides whether a visit is asked for.
	 */
	private String getLaunchTypeString(String key) {
		JWTClaimsSet claims = SmartLaunchTokens.readUnverifiedClaims(key);

		if (claims == null) {
			return null;
		}

		Object launchType = claims.getClaim("launchType");

		return launchType == null ? null : launchType.toString();
	}

}

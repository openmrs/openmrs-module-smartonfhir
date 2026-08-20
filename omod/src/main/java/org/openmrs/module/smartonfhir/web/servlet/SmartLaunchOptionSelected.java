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

		// Before anything is decoded: this used to run URLDecoder on the parameter twenty-five lines
		// above the null check, so a request without one answered 500 instead of 400.
		if (token == null || (patientId == null && visitId == null)) {
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "A token and a patient or visit are required");
			return;
		}

		// This endpoint signs launch context with the secret shared with the authorization server, and
		// hands the result to whatever address the token names. Unauthenticated, that is an oracle: any
		// caller could obtain a token asserting any patient, delivered to a URL of their choosing. The
		// bypass filter in front of this never *requires* authentication -- a request it cannot read a
		// launch token from simply passes through -- so the check has to be here.
		if (!Context.isAuthenticated()) {
			log.error("Refused to sign launch context for an unauthenticated request");
			res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		final String decodedUrl = SmartLaunchTargets.decodeLaunchTarget(token);

		// The signed token goes to this address, so it must be the authorization server's own. Without
		// this, the address came from the request and the token could be delivered anywhere.
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
			// This is the standalone path -- the patient picker sends the clinician here -- so a visit has
			// to be chosen, and the screen that chose one was a RefApp 2.x page removed with the rest of
			// that UI: it could not have rendered in a distribution without uiframework. Refused plainly
			// rather than redirected to a page that no longer exists, until a replacement exists. An EHR
			// launch never reaches this: the EHR names the visit, so context-ehr-encounter is claimed
			// while context-standalone-encounter is not.
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
	 * Ends the OpenMRS session the launch token created, now the hand-off is done.
	 * <p>
	 * A standalone launch has to sign the clinician in so they can search for a patient, and that
	 * session used to outlive the launch: the browser was left holding a fully privileged session
	 * nobody had asked for and no visible logout would obviously end. On a shared workstation that is
	 * the next person's session.
	 * <p>
	 * Only a session the bypass filter created is ended, identified by the marker it leaves. A
	 * clinician who was already signed in to OpenMRS keeps their session — that one is theirs, it
	 * predates the launch, and ending it would log them out of the application they are working in.
	 */
	private void endSessionIfItExistedOnlyForThisLaunch(HttpServletRequest req) {
		HttpSession session = req.getSession(false);

		if (session == null || session.getAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS) == null) {
			return;
		}

		// The authentication is ended, but the session container is left alone. Invalidating it leaves the
		// browser holding a cookie for a session that no longer exists, and OpenMRS answers 401 with an
		// HTML error page to the next request that presents it — including the session endpoint the
		// frontend polls, which expects 200 with authenticated false.
		Context.logout();
		session.removeAttribute(AuthenticationByPassFilter.SMART_AUTH_BYPASS);
	}

	/**
	 * Whether a launch target belongs to the configured authorization server.
	 * <p>
	 * Compared on scheme, host and port -- the issuer's origin -- rather than as a string prefix, so a
	 * host that merely starts with the issuer's cannot pass.
	 */
	private boolean isAuthorizationServerAddress(String target) {
		final SmartOAuth2Config config = SmartOAuth2ConfigHolder.getConfig();

		if (target == null || config == null || config.getIssuer() == null) {
			return false;
		}

		// UriComponentsBuilder rather than java.net.URI: the target carries the authorization server's
		// own {APP_TOKEN} placeholder, and braces are illegal in a URI -- constructing one threw, which
		// this method read as "not the authorization server" and refused every real launch.
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
	 * Which launch context the app asked for, read from the authorization server's action token.
	 * <p>
	 * The signature is not checked: the token is signed with the authorization server's key, which this
	 * module does not hold. Nothing security-relevant rests on the answer, which only decides whether
	 * the user is additionally asked to pick a visit.
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

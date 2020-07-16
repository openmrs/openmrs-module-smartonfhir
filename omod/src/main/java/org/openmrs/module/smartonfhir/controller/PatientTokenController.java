/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.controller;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PatientTokenController {
	
	@RequestMapping(value = "/openmrs/smartonfhir/patientSelected/{patientID}")
	private ResponseEntity<Void> tokenModifier(@RequestParam(value = "token") String token,
	        @PathVariable(value = "{patientID}") String patientId) throws UnsupportedEncodingException, URISyntaxException {
		Map<String, String> claims = new HashMap<String, String>();
		claims.put("patient", patientId);
		
		JsonWebToken tokenSentBack = new JsonWebToken();
		for (Map.Entry<String, String> entry : claims.entrySet()) {
			String decodedValue = URLDecoder.decode(entry.getValue(), "UTF-8");
			tokenSentBack.setOtherClaims(entry.getKey(), decodedValue);
		}
		String appToken = new JWSBuilder().jsonContent(tokenSentBack).none();
		String encodedToken = URLEncoder.encode(appToken, "UTF-8");
		
		String decodedUrl = URLDecoder.decode(token, "UTF-8");
		System.out.println(decodedUrl.replace("{APP_TOKEN}", encodedToken));
		return ResponseEntity.status(HttpStatus.FOUND).location(new URI(decodedUrl.replace("{APP_TOKEN}", encodedToken)))
		        .build();
	}
}

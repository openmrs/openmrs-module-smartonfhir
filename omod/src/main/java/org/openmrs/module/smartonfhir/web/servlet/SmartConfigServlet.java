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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.module.smartonfhir.web.SmartConformance;

public class SmartConfigServlet extends HttpServlet {
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	private final SmartConformance smartConformance;
	
	public SmartConfigServlet() {
		this.smartConformance = new SmartConformance();
		smartConformance.setAuthorizationEndpoint("http://localhost:8180/auth/realms/openmrs/protocol/openid-connect/auth");
		smartConformance.setTokenEndpoint("http://localhost:8180/auth/realms/openmrs/protocol/openid-connect/token");
		smartConformance.setTokenEndpointAuthMethodsSupported(new String[] { "client_secret_basic" });
		smartConformance.setRegistrationEndpoint("https://ehr.example.com/auth/register");
		smartConformance.setScopesSupported(
		    new String[] { "openid", "profile", "launch", "launch/patient", "patient/*.*", "user/*.*" });
		smartConformance.setResponseTypesSupported(new String[] { "code", "code id_token", "id_token", "refresh_token" });
		smartConformance.setManagementEndpoint("https://ehr.example.com/user/manage");
		smartConformance.setIntrospectionEndpoint("https://ehr.example.com/user/introspect");
		smartConformance.setRevocationEndpoint("https://ehr.example.com/user/revoke");
		smartConformance.setCapabilities(new String[] { "launch-standalone", "launch-ehr", "client-public",
		        "client-confidential-symmetric", "context-ehr-patient", "sso-openid-connect" });
	}
	
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		String fhirUri = "http://localhost:8080/openmrs/ws/fhir2/R3";
		String authUri = "http://localhost:8180/auth/realms/openmrs/protocol/openid-connect/auth";
		String tokenUri = "http://localhost:8180/auth/realms/openmrs/protocol/openid-connect/token";
		
		HashMap<String, Object> codingMap = new HashMap<>();
		codingMap.put("system", "http://hl7.org/fhir/restful-security-service");
		codingMap.put("code", "SMART-on-FHIR");
		
		List<HashMap<String, Object>> codingList = new ArrayList<>();
		codingList.add(codingMap);
		
		HashMap<String, Object> serviceMap = new HashMap<>();
		serviceMap.put("coding", codingList);
		serviceMap.put("text", "OAuth2 using SMART-on-FHIR profile (see http://docs.smarthealthit.org)");
		
		List<HashMap<String, Object>> serviceList = new ArrayList<>();
		serviceList.add(serviceMap);
		
		HashMap<String, Object> tokenMap = new HashMap<>();
		tokenMap.put("url", "token");
		tokenMap.put("valueUri", tokenUri);
		
		HashMap<String, Object> authorizeMap = new HashMap<>();
		authorizeMap.put("url", "authorize");
		authorizeMap.put("valueUri", authUri);
		
		HashMap<String, Object> fhirMap = new HashMap<>();
		fhirMap.put("url", "fhir");
		fhirMap.put("valueUri", fhirUri);
		
		List<HashMap<String, Object>> innerExtentionList = new ArrayList<>();
		innerExtentionList.add(tokenMap);
		innerExtentionList.add(authorizeMap);
		innerExtentionList.add(fhirMap);
		
		HashMap<String, Object> extentionMap = new HashMap<>();
		extentionMap.put("url", "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
		extentionMap.put("extension", innerExtentionList);
		
		List<HashMap<String, Object>> extentionList = new ArrayList<>();
		extentionList.add(extentionMap);
		
		HashMap<String, Object> securityMap = new HashMap<>();
		securityMap.put("service", serviceList);
		securityMap.put("extension", extentionList);
		
		HashMap<String, Object> restMap = new HashMap<>();
		restMap.put("security", securityMap);
		
		List<HashMap<String, Object>> restList = new ArrayList<>();
		restList.add(restMap);
		
		HashMap<String, Object> metadataMap = new HashMap<>();
		metadataMap.put("resourceType", "Conformance");
		metadataMap.put("rest", restList);
		
		res.setContentType("application/json");
		res.setCharacterEncoding("UTF-8");
		res.setStatus(200);
		objectMapper.writeValue(res.getWriter(), metadataMap);
	}
}

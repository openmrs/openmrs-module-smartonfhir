/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.page.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.ui.framework.page.Redirect;

@RunWith(MockitoJUnitRunner.class)
public class SmartAppsRedirectPageControllerTest {
	
	private static final String LAUNCH_URL = "http://127.0.0.1:9090/launch-standalone.html";
	
	private static final String LAUNCH_TYPE_EHR = "EHR";
	
	private static final String EHR_LAUNCH_SERVLET_URL = "ms/smartEhrLaunchServlet";
	
	private static final String STANDALONE_LAUNCH_SERVLET_URL = "ms/smartAppSelectorServlet";
	
	private static final String LAUNCH_CONTEXT_PATIENT = "patient";
	
	private static final String FHIR_VERSION_R4 = "R4";
	
	private static final String PATIENT_UUID = "12345";
	
	private static final String VISIT_UUID = "56789";
	
	private AppDescriptor appDescriptor;
	
	private SmartAppsRedirectPageController pageController;
	
	@Before
	public void setup() {
		appDescriptor = new AppDescriptor();
		appDescriptor.setConfig(new ObjectNode(JsonNodeFactory.instance));
		pageController = new SmartAppsRedirectPageController();
		
		appDescriptor.getConfig().put("launchUrl", LAUNCH_URL);
		appDescriptor.getConfig().put("launchContext", LAUNCH_CONTEXT_PATIENT);
		appDescriptor.getConfig().put("fhirVersion", FHIR_VERSION_R4);
	}
	
	@Test
	public void shouldReturnURLWhenLaunchTypeIsEHR() {
		appDescriptor.getConfig().put("launchType", LAUNCH_TYPE_EHR);
		
		Redirect result = pageController.get(appDescriptor, PATIENT_UUID, VISIT_UUID);
		
		assertThat(result, notNullValue());
		assertThat(result.getUrl(), containsString(LAUNCH_URL));
		assertThat(result.getUrl(), containsString(LAUNCH_CONTEXT_PATIENT));
		assertThat(result.getUrl(), containsString(FHIR_VERSION_R4));
		assertThat(result.getUrl(), containsString(EHR_LAUNCH_SERVLET_URL));
	}
	
	@Test
	public void shouldReturnURLWhenLaunchTypeIsStandalone() {
		appDescriptor.getConfig().put("launchType", "standalone");
		
		Redirect result = pageController.get(appDescriptor, PATIENT_UUID, VISIT_UUID);
		
		assertThat(result, notNullValue());
		assertThat(result.getUrl(), containsString(LAUNCH_URL));
		assertThat(result.getUrl(), containsString(FHIR_VERSION_R4));
		assertThat(result.getUrl(), containsString(STANDALONE_LAUNCH_SERVLET_URL));
	}
	
	@Test
	public void shouldReturnURLWhenLaunchTypeIsNotPresent() {
		appDescriptor.getConfig().put("launchType", "");
		
		Redirect result = pageController.get(appDescriptor, PATIENT_UUID, VISIT_UUID);
		
		assertThat(result, notNullValue());
		assertThat(result.getUrl(), containsString(STANDALONE_LAUNCH_SERVLET_URL));
	}
}

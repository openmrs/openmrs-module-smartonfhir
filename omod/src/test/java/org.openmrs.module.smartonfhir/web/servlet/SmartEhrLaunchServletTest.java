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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.module.smartonfhir.util.FhirBaseAddressStrategy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ SmartEhrLaunchServlet.class, FhirBaseAddressStrategy.class })
public class SmartEhrLaunchServletTest {
	
	private static final String BASE_LAUNCH_ADDRESS = "http://127.0.0.1:9090/launch-standalone.html?iss=http://demo.org/openmrs/ws/fhir2/R4&launch=";
	
	private static final String PATIENT_UUID = "12345";
	
	private static final String VISIT_UUID = "67890";
	
	private static final String PATIENT_LAUNCH_CONTEXT = "patient";
	
	private static final String VISIT_LAUNCH_CONTEXT = "encounter";
	
	@Mock
	private FhirBaseAddressStrategy fhirBaseAddressStrategy;
	
	private MockHttpServletResponse response;
	
	private MockHttpServletRequest request;
	
	private SmartEhrLaunchServlet servlet;
	
	@Before
	public void setup() throws Exception {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		servlet = new SmartEhrLaunchServlet();
		
		request.setParameter("patientId", PATIENT_UUID);
		request.setParameter("visitId", VISIT_UUID);
		
		whenNew(FhirBaseAddressStrategy.class).withNoArguments().thenReturn(fhirBaseAddressStrategy);
	}
	
	@Test
	public void shouldReturnCorrectURLForPatientContext() throws IOException {
		when(fhirBaseAddressStrategy.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS);
		request.setParameter("launchContext", PATIENT_LAUNCH_CONTEXT);
		
		servlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().equals(BASE_LAUNCH_ADDRESS + PATIENT_UUID), equalTo(true));
	}
	
	public void shouldReturnCorrectURLForEncounterContext() throws IOException {
		when(fhirBaseAddressStrategy.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS);
		request.setParameter("launchContext", VISIT_LAUNCH_CONTEXT);
		
		servlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().equals(BASE_LAUNCH_ADDRESS + VISIT_UUID), equalTo(true));
	}
	
	public void shouldReturnErrorWhenURLNotPresent() throws IOException {
		when(fhirBaseAddressStrategy.getBaseSmartLaunchAddress(request)).thenReturn("");
		
		servlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getErrorMessage(), equalTo("A url must be provided"));
	}
}

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
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.module.smartonfhir.util.FhirBaseAddressStrategy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class SmartEhrLaunchServletTest {
	
	private static final String BASE_LAUNCH_ADDRESS = "http://127.0.0.1:9090/launch-standalone.html?iss=http://demo.org/openmrs/ws/fhir2/R4&launch=";
	
	private static final String PATIENT_UUID = "12345";
	
	private static final String VISIT_UUID = "67890";
	
	private static final String PATIENT_LAUNCH_CONTEXT = "patient";
	
	private static final String VISIT_LAUNCH_CONTEXT = "encounter";
	
	private MockHttpServletResponse response;
	
	private MockHttpServletRequest request;
	
	private SmartEhrLaunchServlet servlet;
	
	private MockedConstruction<FhirBaseAddressStrategy> fhirBaseAddressStrategyMockedConstruction;
	
	@Before
	public void setup() throws Exception {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		servlet = new SmartEhrLaunchServlet();
		
		request.setParameter("patientId", PATIENT_UUID);
		request.setParameter("visitId", VISIT_UUID);
	}
	
	@After
	public void close() {
		fhirBaseAddressStrategyMockedConstruction.close();
	}
	
	@Test
	public void shouldReturnCorrectURLForPatientContext() throws IOException {
		fhirBaseAddressStrategyMockedConstruction = Mockito.mockConstruction(FhirBaseAddressStrategy.class,
		    (mock, context) -> {
			    when(mock.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS);
		    });
		request.setParameter("launchContext", PATIENT_LAUNCH_CONTEXT);
		
		servlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().equals(BASE_LAUNCH_ADDRESS + PATIENT_UUID), equalTo(true));
	}
	
	@Test
	public void shouldReturnCorrectURLForEncounterContext() throws IOException {
		fhirBaseAddressStrategyMockedConstruction = Mockito.mockConstruction(FhirBaseAddressStrategy.class,
		    (mock, context) -> {
			    when(mock.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS);
		    });
		request.setParameter("launchContext", VISIT_LAUNCH_CONTEXT);
		
		servlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().equals(BASE_LAUNCH_ADDRESS + VISIT_UUID), equalTo(true));
	}
	
	@Test
	public void shouldReturnErrorWhenLaunchContextNotPresent() throws IOException {
		fhirBaseAddressStrategyMockedConstruction = Mockito.mockConstruction(FhirBaseAddressStrategy.class,
		    (mock, context) -> {
			    when(mock.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS);
		    });
		
		servlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getErrorMessage(), equalTo("launchContext must be provided"));
	}
	
}

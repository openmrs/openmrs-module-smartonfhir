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
import static org.hamcrest.Matchers.containsString;
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
public class SmartAppSelectorServletTest {
	
	private static final String BASE_LAUNCH_ADDRESS_R4 = "http://127.0.0.1:9090/launch-standalone.html?iss=http://demo.org/openmrs/ws/fhir2/R4&launch=";
	
	private static final String BASE_LAUNCH_ADDRESS_R3 = "http://127.0.0.1:9090/launch-standalone.html?iss=http://demo.org/openmrs/ws/fhir2/R3&launch=";
	
	private static final String SMART_APP_BASE_URL = "http://127.0.0.1:9090/launch-standalone.html";
	
	private MockHttpServletResponse response;
	
	private MockHttpServletRequest request;
	
	private SmartAppSelectorServlet smartAppSelectorServlet;
	
	private MockedConstruction<FhirBaseAddressStrategy> fhirBaseAddressStrategyMockedConstruction;
	
	@Before
	public void setup() throws Exception {
		response = new MockHttpServletResponse();
		request = new MockHttpServletRequest();
		smartAppSelectorServlet = new SmartAppSelectorServlet();
	}
	
	@After
	public void close() {
		fhirBaseAddressStrategyMockedConstruction.close();
	}
	
	@Test
	public void shouldReturnCorrectSMARTAppBaseURLForR4() throws IOException {
		fhirBaseAddressStrategyMockedConstruction = Mockito.mockConstruction(FhirBaseAddressStrategy.class,
		    (mock, context) -> {
			    when(mock.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS_R4);
		    });
		
		smartAppSelectorServlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl(), containsString(SMART_APP_BASE_URL));
		assertThat(response.getRedirectedUrl(), equalTo(BASE_LAUNCH_ADDRESS_R4));
	}
	
	@Test
	public void shouldReturnCorrectSMARTAppBaseURLForR3() throws IOException {
		fhirBaseAddressStrategyMockedConstruction = Mockito.mockConstruction(FhirBaseAddressStrategy.class,
		    (mock, context) -> {
			    when(mock.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS_R3);
		    });
		
		smartAppSelectorServlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl(), containsString(SMART_APP_BASE_URL));
		assertThat(response.getRedirectedUrl(), equalTo(BASE_LAUNCH_ADDRESS_R3));
	}
	
	@Test
	public void shouldContainsEveryQuery() throws IOException {
		fhirBaseAddressStrategyMockedConstruction = Mockito.mockConstruction(FhirBaseAddressStrategy.class,
		    (mock, context) -> {
			    when(mock.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS_R4);
		    });
		
		smartAppSelectorServlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl(), containsString("iss="));
	}
}

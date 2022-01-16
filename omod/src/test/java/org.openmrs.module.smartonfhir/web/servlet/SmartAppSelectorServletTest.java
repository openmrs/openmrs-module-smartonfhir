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
@PrepareForTest({ SmartAppSelectorServlet.class, FhirBaseAddressStrategy.class })
public class SmartAppSelectorServletTest {
	
	private static final String BASE_LAUNCH_ADDRESS_R4 = "http://127.0.0.1:9090/launch-standalone.html?iss=http://demo.org/openmrs/ws/fhir2/R4&launch=";
	
	private static final String BASE_LAUNCH_ADDRESS_R3 = "http://127.0.0.1:9090/launch-standalone.html?iss=http://demo.org/openmrs/ws/fhir2/R3&launch=";
	
	private static final String SMART_APP_BASE_URL = "http://127.0.0.1:9090/launch-standalone.html";
	
	private MockHttpServletResponse response;
	
	private MockHttpServletRequest request;
	
	private SmartAppSelectorServlet smartAppSelectorServlet;
	
	@Mock
	private FhirBaseAddressStrategy fhirBaseAddressStrategy;
	
	@Before
	public void setup() throws Exception {
		response = new MockHttpServletResponse();
		request = new MockHttpServletRequest();
		smartAppSelectorServlet = new SmartAppSelectorServlet();
		
		whenNew(FhirBaseAddressStrategy.class).withNoArguments().thenReturn(fhirBaseAddressStrategy);
	}
	
	@Test
	public void shouldReturnCorrectSMARTAppBaseURLForR4() throws IOException {
		when(fhirBaseAddressStrategy.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS_R4);
		
		smartAppSelectorServlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl(), containsString(SMART_APP_BASE_URL));
		assertThat(response.getRedirectedUrl(), equalTo(BASE_LAUNCH_ADDRESS_R4));
	}
	
	@Test
	public void shouldReturnCorrectSMARTAppBaseURLForR3() throws IOException {
		when(fhirBaseAddressStrategy.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS_R3);
		
		smartAppSelectorServlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl(), containsString(SMART_APP_BASE_URL));
		assertThat(response.getRedirectedUrl(), equalTo(BASE_LAUNCH_ADDRESS_R3));
	}
	
	@Test
	public void shouldContainsEveryQuery() throws IOException {
		when(fhirBaseAddressStrategy.getBaseSmartLaunchAddress(request)).thenReturn(BASE_LAUNCH_ADDRESS_R4);
		
		smartAppSelectorServlet.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl(), containsString("iss="));
	}
}

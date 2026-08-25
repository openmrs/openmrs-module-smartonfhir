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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.api.SmartAppService;
import org.openmrs.module.smartonfhir.model.SmartApp;

/**
 * A launch handle is a redeemable credential, so one must not be issued for a context that does not
 * exist: the app would ask for the patient it was told about and be told there is none.
 */
@ExtendWith(MockitoExtension.class)
public class SmartEhrLaunchServletTest {

	private static final String PATIENT = "99f659f3-8182-4ffc-af5a-1466907d6d2d";

	private static final String VISIT = "6f9a2c9a-2ed9-4c92-9bbb-1a54bd0d9c0b";

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private PatientService patientService;

	@Mock
	private VisitService visitService;

	@Mock
	private IServerAddressStrategy addressStrategy;

	@Mock
	private SmartAppService smartAppService;

	private SmartApp patientApp;

	private SmartApp encounterApp;

	@BeforeEach
	public void registerTwoApps() {
		patientApp = new SmartApp();
		patientApp.setUuid("vitals");
		patientApp.setName("Vitals Review");
		patientApp.setLaunchUrl("https://vitals.example.org/launch.html");
		patientApp.setLaunchContext("patient");

		encounterApp = new SmartApp();
		encounterApp.setUuid("rounds");
		encounterApp.setName("Rounds");
		encounterApp.setLaunchUrl("https://rounds.example.org/launch.html");
		encounterApp.setLaunchContext("encounter");
	}

	@Test
	public void doGet_shouldRefuseALaunchForAPatientThatDoesNotExist() throws Exception {
		parameters("vitals", PATIENT, null);
		when(patientService.getPatientByUuid(PATIENT)).thenReturn(null);

		serve();

		verify(response).sendError(eq(HttpStatus.SC_BAD_REQUEST), anyString());
		// The point of the test: nothing was minted and nowhere was the browser sent.
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldLaunchForAPatientThatExists() throws Exception {
		parameters("vitals", PATIENT, null);
		when(patientService.getPatientByUuid(PATIENT)).thenReturn(new Patient());
		when(addressStrategy.determineServerBase(any(), any()))
		        .thenReturn("https://openmrs.example.org/openmrs/ws/fhir2/R4");
		when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

		serve();

		ArgumentCaptor<String> target = ArgumentCaptor.forClass(String.class);
		verify(response).sendRedirect(target.capture());

		assertThat(target.getValue(), containsString("https://vitals.example.org/launch.html?iss="));
		assertThat(target.getValue(), containsString("&launch="));
	}

	/** An encounter launch stands for a visit, so that is what has to exist. */
	@Test
	public void doGet_shouldRefuseAnEncounterLaunchForAVisitThatDoesNotExist() throws Exception {
		parameters("rounds", null, VISIT);
		when(visitService.getVisitByUuid(VISIT)).thenReturn(null);

		serve();

		verify(response).sendError(eq(HttpStatus.SC_BAD_REQUEST), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	@Test
	public void doGet_shouldLaunchForAVisitThatExists() throws Exception {
		parameters("rounds", null, VISIT);
		when(visitService.getVisitByUuid(VISIT)).thenReturn(new Visit());
		when(addressStrategy.determineServerBase(any(), any()))
		        .thenReturn("https://openmrs.example.org/openmrs/ws/fhir2/R4");
		when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

		serve();

		verify(response).sendRedirect(anyString());
	}

	/**
	 * A launch hands the app the clinician's own access, so someone who may not read the context is
	 * refused rather than given a handle nobody could have used.
	 */
	@Test
	public void doGet_shouldRefuseALaunchWhenTheContextCannotBeRead() throws Exception {
		parameters("vitals", PATIENT, null);
		when(patientService.getPatientByUuid(PATIENT)).thenThrow(new APIAuthenticationException("no privilege"));

		serve();

		verify(response).sendError(eq(HttpStatus.SC_FORBIDDEN), anyString());
		verify(response, never()).sendRedirect(anyString());
	}

	/** Stubs every parameter the servlet reads, so no test leaves one to strict-stub complaints. */
	private void parameters(String appId, String patientId, String visitId) {
		lenient().when(smartAppService.getSmartAppByUuid("vitals")).thenReturn(patientApp);
		lenient().when(smartAppService.getSmartAppByUuid("rounds")).thenReturn(encounterApp);
		when(request.getParameter("appId")).thenReturn(appId);
		when(request.getParameter("patientId")).thenReturn(patientId);
		when(request.getParameter("visitId")).thenReturn(visitId);
	}

	private void serve() throws Exception {
		User clinician = new User();
		clinician.setUsername("doctor");

		try (MockedStatic<Context> context = Mockito.mockStatic(Context.class)) {
			context.when(() -> Context.getService(SmartAppService.class)).thenReturn(smartAppService);
			context.when(Context::getAuthenticatedUser).thenReturn(clinician);
			context.when(Context::getPatientService).thenReturn(patientService);
			context.when(Context::getVisitService).thenReturn(visitService);
			context.when(() -> Context.getRegisteredComponent("openmrsFhirAddressStrategy", IServerAddressStrategy.class))
			        .thenReturn(addressStrategy);

			new SmartEhrLaunchServlet().doGet(request, response);
		}
	}
}

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
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.module.smartonfhir.util.SmartSecretKeyHolder;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.crypto.*")
@PrepareForTest({ SmartLaunchOptionSelected.class, SmartSecretKeyHolder.class })
public class SmartLaunchOptionSelectedTest {
	
	private static final String TOKEN_ENCOUNTER = "http://localhost:8180/auth/realms/openmrs/login-actions/action-token?key=eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICIzNGQzMTk4Ny0zYjI0LTQ4MzMtOWUwZi1hMWExYTIxYzc5NDUifQ.eyJleHAiOjE2NDEwMTQ0NzAsImlhdCI6MTY0MTAxNDE3MCwianRpIjoiMTVjZDgxYzItMGM5Ni00MGI2LTkyZTUtNGM2Y2MyMWEzZDQ4IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MTgwL2F1dGgvcmVhbG1zL29wZW5tcnMiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjgxODAvYXV0aC9yZWFsbXMvb3Blbm1ycyIsInN1YiI6ImY6ZDllNTk0ZDItMWE5NC00YjNjLTkwZTQtODBhYmI5ODAzOTY4OjEiLCJ0eXAiOiJzbWFydC1wYXRpZW50LXNlbGVjdGlvbiIsIm5vbmNlIjoiMTVjZDgxYzItMGM5Ni00MGI2LTkyZTUtNGM2Y2MyMWEzZDQ4IiwiYXNpZCI6ImRmZTUwMTczLWE1NjQtNDE1ZS1hOGQzLTBiYjNmZDY4MGFkNy5VZDhUS2x2amJoNC45MzBkM2JkYS1iNTY4LTQ1YTItODgyZS05MGQxMTNjNjMxNzciLCJhc2lkIjoiZGZlNTAxNzMtYTU2NC00MTVlLWE4ZDMtMGJiM2ZkNjgwYWQ3LlVkOFRLbHZqYmg0LjkzMGQzYmRhLWI1NjgtNDVhMi04ODJlLTkwZDExM2M2MzE3NyIsInVzZXIiOiJleUpoYkdjaU9pSklVekkxTmlJc0luUjVjQ0lnT2lBaVNsZFVJbjAuZXlKbGVIQWlPakUyTkRFd01UUTBOekFzSW1semN5STZJbWgwZEhBNkx5OXNiMk5oYkdodmMzUTZPREU0TUM5aGRYUm9MM0psWVd4dGN5OXZjR1Z1YlhKeklpd2lZWFZrSWpvaWFIUjBjRG92TDJ4dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYzNWaUlqb2lZV1J0YVc0aUxDSjBlWEFpT2lKemJXRnlkQzExYzJWeWJtRnRaUzEwYjJ0bGJpSXNJbUZ6YVdRaU9pSnpiV0Z5ZEVOc2FXVnVkQ0o5LmV0Wm5BN2JPZEpWWGR5dEhha1VCVEJPZ1BzLWg4WVBkV3hyUDRWZl9fbWMiLCJsYXVuY2hUeXBlIjoiL3BhdGllbnQgL2VuY291bnRlciJ9.SyloT1oqNdLGCPdFTb4CjKQMrvO0Pjhv1vOp5YLaSrI&client_id=smartClient&tab_id=Ud8TKlvjbh4&execution=9e89e1f3-41cd-4d92-946c-d8f56e9af62a&app-token=%7BAPP_TOKEN%7D";
	
	private static final String TOKEN_PATIENT = "http://localhost:8180/auth/realms/openmrs/login-actions/action-token?key=eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJkZjJhYTBjMC0wMzFjLTQ5ZjItYTczMC1hYmM0Mjg2OTM1Y2UifQ.eyJleHAiOjE2NDEyNzIyNzIsImlhdCI6MTY0MTI3MTk3MiwianRpIjoiMjIwZDlmYWMtYjgyNi00ZjVhLWJmM2UtNDg4NmY3MjI3OWFiIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MTgwL2F1dGgvcmVhbG1zL29wZW5tcnMiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjgxODAvYXV0aC9yZWFsbXMvb3Blbm1ycyIsInN1YiI6ImY6ZDllNTk0ZDItMWE5NC00YjNjLTkwZTQtODBhYmI5ODAzOTY4OjEiLCJ0eXAiOiJzbWFydC1wYXRpZW50LXNlbGVjdGlvbiIsIm5vbmNlIjoiMjIwZDlmYWMtYjgyNi00ZjVhLWJmM2UtNDg4NmY3MjI3OWFiIiwiYXNpZCI6IjcwZmU4ODNjLWVhNGUtNGRmNy1iYmI5LWU0MTdjNGVlOWI5OS5JOUlhSEFGWFBaMC45MzBkM2JkYS1iNTY4LTQ1YTItODgyZS05MGQxMTNjNjMxNzciLCJhc2lkIjoiNzBmZTg4M2MtZWE0ZS00ZGY3LWJiYjktZTQxN2M0ZWU5Yjk5Lkk5SWFIQUZYUFowLjkzMGQzYmRhLWI1NjgtNDVhMi04ODJlLTkwZDExM2M2MzE3NyIsInVzZXIiOiJleUpoYkdjaU9pSklVekkxTmlJc0luUjVjQ0lnT2lBaVNsZFVJbjAuZXlKbGVIQWlPakUyTkRFeU56SXlOeklzSW1semN5STZJbWgwZEhBNkx5OXNiMk5oYkdodmMzUTZPREU0TUM5aGRYUm9MM0psWVd4dGN5OXZjR1Z1YlhKeklpd2lZWFZrSWpvaWFIUjBjRG92TDJ4dlkyRnNhRzl6ZERvNE1EZ3dJaXdpYzNWaUlqb2lZV1J0YVc0aUxDSjBlWEFpT2lKemJXRnlkQzExYzJWeWJtRnRaUzEwYjJ0bGJpSXNJbUZ6YVdRaU9pSnpiV0Z5ZEVOc2FXVnVkQ0o5LnFoeHE1Z3Z1RERFcmNUbFYyY0lzOFpLSHpzd2VoZWkxRVlqZDNJR05mTk0iLCJsYXVuY2hUeXBlIjoiL3BhdGllbnQifQ.7rqOafcWeQhr0YGhG6cxrIrKUarDTxl3lb0dBi-Hjkk&client_id=smartClient&tab_id=Ud8TKlvjbh4&execution=9e89e1f3-41cd-4d92-946c-d8f56e9af62a&app-token=%7BAPP_TOKEN%7D";
	
	private static final byte[] SMART_SECRET_KEY_HOLDER = "SecretKey".getBytes(StandardCharsets.UTF_8);
	
	private static final String PATIENT_UUID = "12345";
	
	private static final String VISIT_UUID = "56789";
	
	private MockHttpServletRequest request;
	
	private MockHttpServletResponse response;
	
	private SmartLaunchOptionSelected smartLaunchOptionSelected;
	
	@Before
	public void setup() throws Exception {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		smartLaunchOptionSelected = new SmartLaunchOptionSelected();
		
		mockStatic(SmartSecretKeyHolder.class);
		
		doReturn(SMART_SECRET_KEY_HOLDER).when(SmartSecretKeyHolder.class, "getSecretKey");
	}
	
	@Test
	public void shouldReturnCorrectURLWhenLaunchTypeIsEncounter() throws IOException {
		request.setParameter("patientId", PATIENT_UUID);
		request.setParameter("token", TOKEN_ENCOUNTER);
		
		smartLaunchOptionSelected.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().contains("app=smartonfhir.search.visit"), equalTo(true));
		assertThat(response.getRedirectedUrl().contains("patientId=12345"), equalTo(true));
	}
	
	@Test
	public void shouldReturnCorrectURLWhenLaunchTypeIsPatient() throws IOException {
		request.setParameter("patientId", PATIENT_UUID);
		request.setParameter("token", TOKEN_PATIENT);
		
		smartLaunchOptionSelected.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().contains("key="), equalTo(true));
		assertThat(response.getRedirectedUrl().contains("app-token="), equalTo(true));
	}
	
	@Test
	public void shouldReturnCorrectRedirectURlWhenLaunchTypeIsEncounter() throws IOException {
		request.setParameter("token", TOKEN_ENCOUNTER);
		request.setParameter("visitId", VISIT_UUID);
		request.setParameter("patientId", PATIENT_UUID);
		
		smartLaunchOptionSelected.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getRedirectedUrl(), notNullValue());
		assertThat(response.getRedirectedUrl().contains("key="), equalTo(true));
		assertThat(response.getRedirectedUrl().contains("app-token="), equalTo(true));
	}
	
	@Test
	public void shouldThrowErrorWhenPatientAndVisitIdIsNull() throws IOException {
		request.setParameter("token", TOKEN_PATIENT);
		smartLaunchOptionSelected.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getErrorMessage(), notNullValue());
		assertThat(response.getErrorMessage(), equalTo("PatientId must be provided"));
	}
	
	@Test
	public void shouldThrowErrorWhenTokenIsNull() throws IOException {
		smartLaunchOptionSelected.doGet(request, response);
		
		assertThat(response, notNullValue());
		assertThat(response.getErrorMessage(), notNullValue());
		assertThat(response.getErrorMessage(), equalTo("Couldn't found token in url"));
	}
}

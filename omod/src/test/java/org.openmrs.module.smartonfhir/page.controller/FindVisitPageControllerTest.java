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
import static org.hamcrest.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.CoreAppsConstants;
import org.openmrs.module.coreapps.utils.VisitTypeHelper;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ PatientService.class, VisitService.class, Context.class })
public class FindVisitPageControllerTest {
	
	private static final String AFTER_SELECTED_URL = "/ms/smartLaunchOptionSelected?app=smart_client&patientId={{patient.uuid}}&token={{token}}";
	
	private static final String UNAFFECTED_AFTER_SELECTED_URL = "/ms/smartLaunchOptionSelected?app=smart_client";
	
	private static final String TOKEN_URL = "http://localhost:8180/auth/realms/openmrs/login-actions/action-token?key=123";
	
	private static final String PATIENT_UUID = "1234";
	
	private static final String PATIENT_ID = "12";
	
	private static final String VISIT_UUID = "56789";
	
	private static final String VISIT_TYPE_UUID = "9876";
	
	@Spy
	private VisitTypeHelper visitTypeHelper = new VisitTypeHelper();
	
	@Mock
	private PatientService patientService;
	
	@Mock
	private VisitService visitService;
	
	private UiSessionContext uiSessionContext;
	
	private PageModel pageModel;
	
	private FindVisitPageController findVisitPageController;
	
	private AppDescriptor appDescriptor;
	
	private List<Visit> list;
	
	private UiUtils ui;
	
	private Patient patient;
	
	private Visit visit;
	
	@Before
	public void setup() throws Exception {
		appDescriptor = new AppDescriptor();
		appDescriptor.setConfig(new ObjectNode(JsonNodeFactory.instance));
		pageModel = new PageModel();
		findVisitPageController = new FindVisitPageController();
		uiSessionContext = new UiSessionContext();
		patient = new Patient();
		list = new ArrayList<>();
		visit = new Visit();
		
		visit.setUuid(VISIT_UUID);
		list.add(visit);
		patient.setUuid(PATIENT_UUID);
		patient.setId(Integer.valueOf(PATIENT_ID));
		
		appDescriptor.getConfig().put("afterSelectedUrl", AFTER_SELECTED_URL);
		
		mockStatic(Context.class);
		mockStatic(PatientService.class);
		mockStatic(VisitService.class);
		
		doReturn(patientService).when(Context.class, "getPatientService");
		doReturn(visitService).when(Context.class, "getVisitService");
		when(patientService.getPatientByUuid(PATIENT_ID)).thenReturn(patient);
		when(visitService.getVisitsByPatient(patient)).thenReturn(list);
		doReturn(true).when(Context.class, "hasPrivilege", CoreAppsConstants.PRIVILEGE_PATIENT_VISITS);
	}
	
	@Test
	public void shouldReturnAllCorrectAttributes() throws Exception {
		findVisitPageController.get(uiSessionContext, pageModel, appDescriptor, null, visitService, PATIENT_ID, TOKEN_URL,
		    uiSessionContext, visitTypeHelper);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.get("visitSummaries"), notNullValue());
		assertThat(pageModel.get("canViewVisits"), notNullValue());
		assertThat(pageModel.get("visitTypesWithAttr"), notNullValue());
		assertThat(pageModel.get("afterSelectedUrl"), notNullValue());
	}
	
	@Test
	public void shouldReturnCorrectVisitSummaries() throws UnsupportedEncodingException {
		findVisitPageController.get(uiSessionContext, pageModel, appDescriptor, null, visitService, PATIENT_ID, TOKEN_URL,
		    uiSessionContext, visitTypeHelper);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.get("visitSummaries"), notNullValue());
		
		List<Visit> result = (List) pageModel.get("visitSummaries");
		
		assertThat(result, notNullValue());
		assertThat(result.size(), equalTo(1));
		assertThat(result.get(0).getUuid(), equalTo(VISIT_UUID));
	}
	
	@Test
	public void shouldReturnCorrectCanViewVisits() throws UnsupportedEncodingException {
		findVisitPageController.get(uiSessionContext, pageModel, appDescriptor, null, visitService, PATIENT_ID, TOKEN_URL,
		    uiSessionContext, visitTypeHelper);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.get("canViewVisits"), equalTo(true));
	}
	
	@Test
	public void shouldReturnCorrectVisitTypeWithAttr() throws UnsupportedEncodingException {
		VisitType visitType = new VisitType();
		visitType.setUuid(VISIT_TYPE_UUID);
		visitService.saveVisitType(visitType);
		
		Map<String, Object> typeAttr = new HashMap<>();
		doReturn(typeAttr).when(visitTypeHelper).getVisitTypeColorAndShortName(visitType);
		
		findVisitPageController.get(uiSessionContext, pageModel, appDescriptor, null, visitService, PATIENT_ID, TOKEN_URL,
		    uiSessionContext, visitTypeHelper);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.get("visitTypesWithAttr"), notNullValue());
		
		Map<Integer, Object> result = (Map<Integer, Object>) pageModel.get("visitTypesWithAttr");
		
		assertThat(result, notNullValue());
		assertThat(result.equals(typeAttr), equalTo(true));
	}
	
	@Test
	public void shouldReturnCorrectAfterSelectedURL() throws UnsupportedEncodingException {
		findVisitPageController.get(uiSessionContext, pageModel, appDescriptor, null, visitService, PATIENT_ID, TOKEN_URL,
		    uiSessionContext, visitTypeHelper);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.get("afterSelectedUrl"), notNullValue());
		
		String result = (String) pageModel.get("afterSelectedUrl");
		
		assertThat(result, notNullValue());
		assertThat(result.contains(UNAFFECTED_AFTER_SELECTED_URL), equalTo(true));
		assertThat(result.contains(URLEncoder.encode(TOKEN_URL, StandardCharsets.UTF_8.name())), equalTo(true));
	}
}

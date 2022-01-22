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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.helper.BreadcrumbHelper;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;

@RunWith(MockitoJUnitRunner.class)
public class FindPatientPageControllerTest {
	
	public static final String AFTER_SELECTED_URL = "/ms/smartLaunchOptionSelected?app=smart_client&patientId=123";
	
	public static final String TOKEN_URL = "http://localhost:8180/auth/realms/openmrs/login-actions/action-token?key=123";
	
	public static final String LABEL = "coreapps.findPatient.app.label";
	
	public static final String SHOW_LAST_VIEWED_PATIENT = "true";
	
	public static final String REGISTRATION_APP_LINK = "SMART-on-FHIR";
	
	public static final String HEADING = "";
	
	private UiSessionContext uiSessionContext;
	
	private PageModel pageModel;
	
	private FindPatientPageController findPatientPageController;
	
	private AppDescriptor appDescriptor;
	
	private List<Extension> list;
	
	private UiUtils ui;
	
	private MockedStatic<BreadcrumbHelper> breadcrumbHelperMockedStatic;
	
	@Before
	public void setup() throws Exception {
		appDescriptor = new AppDescriptor();
		appDescriptor.setConfig(new ObjectNode(JsonNodeFactory.instance));
		findPatientPageController = new FindPatientPageController();
		pageModel = new PageModel();
		uiSessionContext = new UiSessionContext();
		list = new ArrayList<>();
		
		appDescriptor.getConfig().put("afterSelectedUrl", AFTER_SELECTED_URL);
		appDescriptor.getConfig().put("heading", HEADING);
		appDescriptor.getConfig().put("label", LABEL);
		appDescriptor.getConfig().put("showLastViewedPatients", SHOW_LAST_VIEWED_PATIENT);
		
		breadcrumbHelperMockedStatic = Mockito.mockStatic(BreadcrumbHelper.class);
		
		breadcrumbHelperMockedStatic.when(() -> BreadcrumbHelper.addBreadcrumbsIfDefinedInApp(appDescriptor, pageModel, ui))
		        .then(invocationOnMock -> null);
	}
	
	@After
	public void close() {
		breadcrumbHelperMockedStatic.close();
	}
	
	@Test
	public void shouldReturnAllCorrectAttributes() throws Exception {
		appDescriptor.getConfig().put("registrationAppLink", REGISTRATION_APP_LINK);
		findPatientPageController.get(pageModel, appDescriptor, TOKEN_URL, uiSessionContext, ui);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.isEmpty(), equalTo(false));
		assertThat(pageModel.get("afterSelectedUrl"), notNullValue());
		assertThat(pageModel.get("afterSelectedUrl").toString(), containsString(URLEncoder.encode(TOKEN_URL)));
		assertThat(pageModel.get("heading"), equalTo(HEADING));
		assertThat(pageModel.get("label"), equalTo(LABEL));
		assertThat(pageModel.get("showLastViewedPatients"), equalTo(false));
		assertThat(pageModel.get("registrationAppLink"), equalTo(REGISTRATION_APP_LINK));
	}
	
	@Test
	public void shouldReturnCorrectResultWhenRegistrationLinkNotNull() throws UnsupportedEncodingException {
		findPatientPageController.get(pageModel, appDescriptor, TOKEN_URL, uiSessionContext, ui);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.isEmpty(), equalTo(false));
		assertThat(pageModel.get("afterSelectedUrl"), notNullValue());
		assertThat(pageModel.get("afterSelectedUrl").toString(), containsString(URLEncoder.encode(TOKEN_URL)));
		assertThat(pageModel.get("heading"), equalTo(HEADING));
		assertThat(pageModel.get("label"), equalTo(LABEL));
		assertThat(pageModel.get("showLastViewedPatients"), equalTo(false));
		assertThat(pageModel.get("registrationAppLink"), equalTo(""));
	}
}

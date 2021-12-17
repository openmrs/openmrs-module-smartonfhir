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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.ui.framework.page.PageModel;

@RunWith(MockitoJUnitRunner.class)
public class SmartAppPageControllerTest {
	
	public static final String SMART_APPS_EXTENSION_POINT = "smartAppManagement.apps";
	
	public static final String EXTENSION_ID = "123456789";
	
	public static final String EXTENSION_ATTRIBUTE_NAME = "extensions";
	
	@Spy
	private UiSessionContext uiSessionContext = new UiSessionContext();
	
	@Mock
	private AppFrameworkService appFrameworkService;
	
	private PageModel pageModel;
	
	private SmartAppsPageController smartAppsPageController;
	
	private List<Extension> list;
	
	@Before
	public void setup() {
		smartAppsPageController = new SmartAppsPageController();
		pageModel = new PageModel();
		list = new ArrayList<>();
		Extension extension = new Extension();
		
		extension.setId(EXTENSION_ID);
		list.add(extension);
	}
	
	@Test
	public void shouldReturnCorrectAttribute() {
		when(appFrameworkService.getExtensionsForCurrentUser(SMART_APPS_EXTENSION_POINT)).thenReturn(list);
		doNothing().when(uiSessionContext).requireAuthentication();
		
		smartAppsPageController.get(pageModel, uiSessionContext, appFrameworkService);
		
		assertThat(pageModel, notNullValue());
		assertThat(pageModel.isEmpty(), equalTo(false));
		assertThat(pageModel.getAttribute(EXTENSION_ATTRIBUTE_NAME), notNullValue());
		assertThat(pageModel.getAttribute(EXTENSION_ATTRIBUTE_NAME), equalTo(list));
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void shouldReturnExceptionWhenNotAuthenticated() {
		doThrow(new APIAuthenticationException()).when(uiSessionContext).requireAuthentication();
		smartAppsPageController.get(pageModel, uiSessionContext, appFrameworkService);
	}
}

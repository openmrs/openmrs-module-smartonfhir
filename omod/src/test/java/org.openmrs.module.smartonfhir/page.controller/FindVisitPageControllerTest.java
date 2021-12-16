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

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class FindVisitPageControllerTest {
	
	private static final String PATIENT_UUID = "1234";
	
	private static final Integer PATIENT_ID = 12;
	
	private static final String VISIT_UUID = "56789";
	
	private UiSessionContext uiSessionContext;
	
	private PageModel pageModel;
	
	private FindVisitPageController findVisitPageController;
	
	private AppDescriptor appDescriptor;
	
	private List<Visit> list;
	
	private UiUtils ui;
	
	private Visit visit;
	
	@Before
	public void setup() {
		appDescriptor = new AppDescriptor();
		appDescriptor.setConfig(new ObjectNode(JsonNodeFactory.instance));
		pageModel = new PageModel();
		findVisitPageController = new FindVisitPageController();
		uiSessionContext = new UiSessionContext();
		list = new ArrayList<>();
		visit = new Visit();
		
		visit.setUuid(VISIT_UUID);
		list.add(visit);
	}
	
	@Test
	public void shouldReturnAllCorrectAttribute() throws Exception {
		
	}
}

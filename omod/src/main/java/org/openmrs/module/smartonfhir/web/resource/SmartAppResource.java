/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.smartonfhir.api.SmartAppService;
import org.openmrs.module.smartonfhir.model.SmartApp;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.MetadataDelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * The SMART apps a launch may be sent to. A chart screen reads the list; registering one is gated
 * on a separate privilege, since it decides where a launch handle can be delivered.
 */
@Resource(name = RestConstants.VERSION_1 + "/smartapp", supportedClass = SmartApp.class, supportedOpenmrsVersions = {
        "2.8.* - 9.*" })
public class SmartAppResource extends MetadataDelegatingCrudResource<SmartApp> {

	@Override
	public SmartApp newDelegate() {
		return new SmartApp();
	}

	@Override
	public SmartApp getByUniqueId(String uuid) {
		return service().getSmartAppByUuid(uuid);
	}

	@Override
	public SmartApp save(SmartApp app) {
		return service().saveSmartApp(app);
	}

	@Override
	public void purge(SmartApp app, RequestContext context) throws ResponseException {
		if (app != null) {
			service().purgeSmartApp(app);
		}
	}

	@Override
	protected NeedsPaging<SmartApp> doGetAll(RequestContext context) {
		return new NeedsPaging<>(service().getSmartApps(context.getIncludeAll()), context);
	}

	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = super.getRepresentationDescription(rep);

		if (description == null) {
			return null;
		}

		// A chart screen launches by uuid, so it needs no launch URL: where a launch is sent is the
		// registry's business.
		description.addProperty("launchContext");

		if (Representation.FULL.equals(rep)) {
			description.addProperty("launchUrl");
			description.addProperty("clientId");
		}

		return description;
	}

	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = super.getCreatableProperties();
		description.addRequiredProperty("launchUrl");
		description.addProperty("clientId");
		description.addProperty("launchContext");

		return description;
	}

	private SmartAppService service() {
		return Context.getService(SmartAppService.class);
	}
}

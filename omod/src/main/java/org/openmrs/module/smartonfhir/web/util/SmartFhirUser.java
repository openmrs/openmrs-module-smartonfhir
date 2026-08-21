/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.util;

import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.util.PrivilegeConstants;

/**
 * Resolves the {@code fhirUser} claim, for whichever launch is establishing it.
 * <p>
 * This lived in the EHR-launch servlet, which is why a standalone launch shipped an id_token with
 * no {@code fhirUser} at all: the scope was requested and granted, and the claim was simply absent
 * because the standalone path never ran this code. Inferno's SMART App Launch STU2.2 suite is what
 * found it -- nothing in this project checked, because the application we wrote does not read the
 * claim.
 */
@Slf4j
public class SmartFhirUser {

	private SmartFhirUser() {
	}

	/**
	 * The {@code Practitioner} reference for a user, or null when there is no such resource.
	 * <p>
	 * A user is not a practitioner in OpenMRS; a person is, by having a provider record, and that
	 * record is what FHIR2 serves as a {@code Practitioner}. An account with no provider -- a clerk, a
	 * service account -- therefore has no resource to point at, and the claim is left out rather than
	 * pointed at something that would 404. Reading providers needs a privilege the launching clinician
	 * may not hold, so it runs under a proxy privilege removed immediately afterwards.
	 */
	public static String reference(User user) {
		if (user == null || user.getPerson() == null) {
			return null;
		}

		Context.addProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);

		try {
			Collection<Provider> providers = Context.getProviderService().getProvidersByPerson(user.getPerson());

			for (Provider provider : providers) {
				if (!provider.getRetired() && provider.getUuid() != null) {
					return "Practitioner/" + provider.getUuid();
				}
			}

			return null;
		}
		catch (Exception e) {
			// A launch that works is worth more than a claim that is nice to have.
			log.warn("Could not resolve a Practitioner for {}; the launch will carry no fhirUser", user.getUsername(), e);
			return null;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
		}
	}
}

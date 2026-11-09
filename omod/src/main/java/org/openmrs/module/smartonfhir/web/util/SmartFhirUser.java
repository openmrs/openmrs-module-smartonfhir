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

/** Resolves the {@code fhirUser} claim, for whichever launch is establishing it. */
@Slf4j
public class SmartFhirUser {

	private SmartFhirUser() {
	}

	/**
	 * The {@code Practitioner} reference for a user, or null when the account has no provider record.
	 * Reading providers runs under a proxy privilege removed immediately afterwards.
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

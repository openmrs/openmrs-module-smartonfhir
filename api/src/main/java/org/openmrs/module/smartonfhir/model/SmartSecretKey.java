/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SmartSecretKey {

	@JsonProperty(value = "smart-shared-secret-key", required = true)
	private String smartSharedSecretKey;

	/**
	 * Deliberately not the generated one.
	 * <p>
	 * Lombok's {@code @Data} produced a {@code toString} over the base64 shared secret, which is one
	 * {@code log.debug("loaded {}", key)} or one interpolated exception message away from writing the
	 * HMAC secret into a log file. Nothing in the type warned about it.
	 */
	@Override
	public String toString() {
		return "SmartSecretKey(***)";
	}
}

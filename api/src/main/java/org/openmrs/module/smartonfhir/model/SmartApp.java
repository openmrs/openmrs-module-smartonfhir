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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.BaseOpenmrsMetadata;

/** A SMART app this deployment permits to be launched, addressed by its uuid. */
@Entity
@Table(name = "smartonfhir_app")
@Getter
@Setter
public class SmartApp extends BaseOpenmrsMetadata {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "smartonfhir_app_id")
	private Integer smartAppId;

	/** The app's launch URL, to which {@code iss} and {@code launch} are appended. */
	@Column(name = "launch_url", length = 1024, nullable = false)
	private String launchUrl;

	/** The app's client id at the authorization server, recorded only to identify its registration. */
	@Column(name = "client_id", length = 255)
	private String clientId;

	/** {@code patient} or {@code encounter}; a launch asking for anything else is refused. */
	@Column(name = "launch_context", length = 50, nullable = false)
	private String launchContext = "patient";

	@Override
	public Integer getId() {
		return smartAppId;
	}

	@Override
	public void setId(Integer id) {
		this.smartAppId = id;
	}
}

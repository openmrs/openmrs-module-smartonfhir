/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.web.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.openmrs.module.smartonfhir.util.KeycloakConfigHolder;

@Slf4j
public class SmartSessionLogoutFilter implements Filter {
	
	private String logoutUrl = null;
	
	@Override
	public void init(FilterConfig filterConfig) {
		final KeycloakUriBuilder keycloakUriBuilder = KeycloakDeploymentBuilder
				.build(KeycloakConfigHolder.getKeycloakConfig()).getLogoutUrl();

		if (keycloakUriBuilder != null) {
			logoutUrl = keycloakUriBuilder.toTemplate();
		} else {
			log.error(
					"Could not find Keycloak configuration file. Please run keycloak server before openmrs to avoid this error");
		}
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
	        throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		if (request.getRequestURI().contains("/logout")) {
			keycloakSessionLogout();
		}
		
		filterChain.doFilter(servletRequest, servletResponse);
	}
	
	@Override
	public void destroy() {
		
	}
	
	private void keycloakSessionLogout() throws IOException {
		CloseableHttpClient closeableHttpClient = HttpClientBuilder.create().disableRedirectHandling().build();
		CloseableHttpResponse closeableHttpResponse = null;
		try {
			if (logoutUrl != null) {
				closeableHttpResponse = closeableHttpClient.execute(new HttpGet(logoutUrl));
			}
		}
		finally {
			if (closeableHttpResponse != null) {
				try {
					closeableHttpResponse.close();
				}
				finally {
					closeableHttpClient.close();
				}
			} else {
				closeableHttpClient.close();
			}
		}
	}
}

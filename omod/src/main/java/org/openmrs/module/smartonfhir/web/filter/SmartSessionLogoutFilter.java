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
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.openmrs.module.smartonfhir.util.KeycloakConfigHolder;

@Slf4j
public class SmartSessionLogoutFilter implements Filter {
	
	private volatile String logoutUrl = null;
	
	private volatile AdapterDeploymentContext adapterDeploymentContext = null;
	
	@Override
	public void init(FilterConfig filterConfig) {
		adapterDeploymentContext = new AdapterDeploymentContext(
		        KeycloakDeploymentBuilder.build(KeycloakConfigHolder.getKeycloakConfig()));
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
	        throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		if (request.getRequestURI().contains("/logout")) {
			keycloakSessionLogout((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
		}

		filterChain.doFilter(servletRequest, servletResponse);
	}
	
	@Override
	public void destroy() {
		
	}
	
	private void keycloakSessionLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
		CloseableHttpClient closeableHttpClient = HttpClientBuilder.create().disableRedirectHandling().build();
		CloseableHttpResponse closeableHttpResponse = null;
		try {
			closeableHttpResponse = closeableHttpClient.execute(new HttpGet(getLogoutUrl(request, response)));
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
	
	private String getLogoutUrl(HttpServletRequest request, HttpServletResponse response) {
		String logoutUrl = this.logoutUrl;
		if (logoutUrl == null) {
			synchronized (this) {
				logoutUrl = this.logoutUrl;
				if (logoutUrl == null) {
					HttpFacade httpFacade = new SimpleHttpFacade(request, response);
					this.logoutUrl = logoutUrl = adapterDeploymentContext.resolveDeployment(httpFacade).getLogoutUrl()
					        .toString();
				}
			}
		}
		
		return logoutUrl;
	}
}

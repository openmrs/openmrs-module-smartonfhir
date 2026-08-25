/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The module's XML resources are parsed at startup rather than by the compiler, so a malformed one
 * packages perfectly and then stops every module in the distribution from starting.
 */
class ModuleResourcesTest {

	/** XML forbids "--" inside a comment. It is easy to type and invisible until startup. */
	private static final Pattern COMMENT = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);

	static List<Path> xmlResources() throws IOException {
		List<Path> roots = new ArrayList<>();
		for (String candidate : new String[] { "src/main/resources", "omod/src/main/resources", "api/src/main/resources",
		        "../api/src/main/resources" }) {
			Path path = Paths.get(candidate);
			if (Files.isDirectory(path)) {
				roots.add(path);
			}
		}

		List<Path> found = new ArrayList<>();
		for (Path root : roots) {
			try (Stream<Path> walk = Files.walk(root)) {
				found.addAll(walk.filter(p -> p.toString().endsWith(".xml")).collect(Collectors.toList()));
			}
		}

		assertFalse(found.isEmpty(), "no XML resources found; this test would silently pass");

		return found;
	}

	@MethodSource("xmlResources")
	@ParameterizedTest(name = "{0}")
	@DisplayName("parses as XML")
	void parsesAsXml(Path resource) {
		assertDoesNotThrow(() -> {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// These reference DTDs by URL, and parsing is what is under test here, not validation.
			factory.setValidating(false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.newDocumentBuilder().parse(resource.toFile());
		}, resource + " does not parse, so OpenMRS will fail to start this module");
	}

	@MethodSource("xmlResources")
	@ParameterizedTest(name = "{0}")
	@DisplayName("contains no double hyphen inside a comment")
	void hasNoDoubleHyphenInComments(Path resource) throws IOException {
		String contents = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
		Matcher matcher = COMMENT.matcher(contents);

		while (matcher.find()) {
			String body = matcher.group(1);
			assertFalse(body.contains("--"),
			    resource + " has a comment containing \"--\", which XML forbids: " + body.replace('\n', ' ').trim());
		}
	}

	@Test
	@DisplayName("the module config declares only dependencies RefApp 3.7.1 actually ships")
	void requiredModulesAreAvailableInTheDistribution() throws IOException {
		String contents = configXml();

		// A require_module OpenMRS cannot satisfy prevents this module from starting at all.
		Matcher matcher = Pattern.compile("<require_module[^>]*>([^<]+)</require_module>").matcher(contents);
		List<String> required = new ArrayList<>();
		while (matcher.find()) {
			required.add(matcher.group(1).trim());
		}

		assertFalse(required.isEmpty(), "the module should declare its dependencies");

		for (String module : required) {
			assertTrue(module.equals("org.openmrs.module.fhir2") || module.equals("org.openmrs.module.authentication"),
			    "config.xml requires '" + module + "', which is not part of RefApp 3.7.1. The RefApp 2.x UI modules "
			            + "in particular (uiframework, appframework, coreapps, appui) are absent from 3.x.");
		}
	}

	/**
	 * The bypass filter's mappings and its {@code validUrls} init-param have to agree: a URL in only
	 * one of the two quietly arrives unauthenticated, which reads as a permissions problem.
	 */
	@Test
	@DisplayName("the bypass filter's valid URLs and its mappings agree")
	void bypassFilterUrlsAreConsistent() throws IOException {
		String contents = configXml();

		Matcher filter = Pattern
		        .compile("<filter>\\s*<filter-name>authenticationByPassFilter</filter-name>.*?</filter>", Pattern.DOTALL)
		        .matcher(contents);
		assertTrue(filter.find(), "the authentication bypass filter should be declared");

		Matcher param = Pattern.compile("<param-name>validUrls</param-name>\\s*<param-value>([^<]*)</param-value>")
		        .matcher(filter.group());
		assertTrue(param.find(), "the bypass filter should declare its validUrls");
		List<String> validUrls = Stream.of(param.group(1).split(",")).map(String::trim).filter(s -> !s.isEmpty())
		        .collect(Collectors.toList());

		Matcher mapping = Pattern
		        .compile("<filter-mapping>\\s*<filter-name>authenticationByPassFilter</filter-name>(.*?)</filter-mapping>",
		            Pattern.DOTALL)
		        .matcher(contents);
		assertTrue(mapping.find(), "the bypass filter should be mapped to something");

		List<String> patterns = new ArrayList<>();
		Matcher pattern = Pattern.compile("<url-pattern>([^<]+)</url-pattern>").matcher(mapping.group(1));
		while (pattern.find()) {
			patterns.add(pattern.group(1).trim());
		}

		assertFalse(validUrls.isEmpty(), "no validUrls found; this test would silently pass");
		assertFalse(patterns.isEmpty(), "no url-patterns found; this test would silently pass");

		for (String url : validUrls) {
			assertTrue(
			    patterns.stream().anyMatch(
			        p -> p.equals(url) || (p.endsWith("/*") && url.startsWith(p.substring(0, p.length() - 2)))),
			    "'" + url + "' may present a launch token but no mapping sends it through the filter, so the token "
			            + "is never read. Mappings are: " + patterns);
		}

		// Not the session endpoint: this filter logs out stale sessions, and O3 logs in through it.
		assertFalse(patterns.contains("/ws/rest/v1/session"),
		    "this filter must not sit in front of the session endpoint that O3 logs in through");
	}

	/** A launch needing a patient is redirected here, and 404s part-way through if it is unmapped. */
	@Test
	@DisplayName("the patient-selection servlet is registered")
	void patientSelectionServletIsRegistered() throws IOException {
		String contents = configXml();

		assertTrue(contents.contains("<servlet-name>smartPatientSelection</servlet-name>"),
		    "the patient-selection servlet should be registered under the name the realm redirects to");
		assertTrue(contents.contains("org.openmrs.module.smartonfhir.web.servlet.SmartPatientSelectionServlet"),
		    "the registered servlet class should exist");
	}

	private String configXml() throws IOException {
		Path config = xmlResources().stream().filter(p -> p.getFileName().toString().equals("config.xml")).findFirst()
		        .orElseThrow(() -> new AssertionError("config.xml not found"));

		return new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
	}
}

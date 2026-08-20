/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.smartonfhir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openmrs.module.smartonfhir.model.SmartOAuth2Config;
import org.openmrs.module.smartonfhir.util.SmartAccessTokenVerifier.SmartAccessToken;

/**
 * This is the gate on the FHIR API: whatever it accepts becomes an authenticated OpenMRS user. The
 * tests are weighted towards tokens that are plausible but must be refused, since those are what an
 * attacker actually presents.
 */
class SmartAccessTokenVerifierTest {

	private static final String ISSUER = "https://keycloak.example.org/realms/openmrs";

	private static final String AUDIENCE = "https://openmrs.example.org/openmrs/ws/fhir2/R4";

	private static final String USERNAME = "jdoe";

	/** The authorization server's key: it signs, we verify. */
	private static RSAKey serverKey;

	/** A key the authorization server does not hold, for forgery attempts. */
	private static RSAKey attackerKey;

	private static OctetSequenceKey octKey;

	private static SmartAccessTokenVerifier verifier;

	@BeforeAll
	static void generateKeys() throws Exception {
		serverKey = new RSAKeyGenerator(2048).keyID("server").generate();
		attackerKey = new RSAKeyGenerator(2048).keyID("attacker").generate();

		// Only the server's public key is published, exactly as a real JWKS would be.
		// An HMAC key is published alongside the RSA one deliberately. With only an RSA key, an HS256
		// token is refused for having no key of a matching type, so PERMITTED_ALGORITHMS is never what
		// refuses it -- and the algorithm-confusion test below passed even with HS256 added to that
		// whitelist. Publishing an oct key makes the whitelist load-bearing.
		octKey = new OctetSequenceKeyGenerator(256).keyID("hmac").generate();

		JWKSource<SecurityContext> published = new ImmutableJWKSet<>(
		        new JWKSet(Arrays.asList((JWK) serverKey.toPublicJWK(), (JWK) octKey)));
		verifier = new SmartAccessTokenVerifier(config(), published);
	}

	private static SmartOAuth2Config config() {
		SmartOAuth2Config config = new SmartOAuth2Config();
		config.setIssuer(ISSUER);
		config.setAudience(AUDIENCE);
		return config;
	}

	/** A claims set that should pass every check, as a starting point for each variation. */
	private static JWTClaimsSet.Builder validClaims() {
		return new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject("user-uuid").claim("preferred_username",USERNAME).expirationTime(new Date(System.currentTimeMillis()+300_000));
	}

	private static String signedBy(RSAKey key, JWTClaimsSet claims) throws Exception {
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	@Nested
	@DisplayName("a token the authorization server issued for this FHIR server")
	class Accepted {

		@Test
		@DisplayName("is accepted, and its user is extracted")
		void acceptsAValidToken() throws Exception {
			SmartAccessToken token = verifier.verify(signedBy(serverKey, validClaims().build()));

			assertNotNull(token, "a correctly issued token must be accepted");
			assertEquals(USERNAME, token.getUsername());
		}

		@Test
		@DisplayName("carries its launch context through")
		void extractsLaunchContext() throws Exception {
			SmartAccessToken token = verifier.verify(signedBy(serverKey,
			    validClaims().claim("patient", "patient-uuid").claim("encounter", "visit-uuid").build()));

			assertEquals("patient-uuid", token.getPatient());
			assertEquals("visit-uuid", token.getEncounter());
		}

		@Test
		@DisplayName("carries its granted scopes through, split on whitespace")
		void extractsScopes() throws Exception {
			SmartAccessToken token = verifier.verify(
			    signedBy(serverKey, validClaims().claim("scope", "openid launch/patient patient/Observation.rs").build()));

			assertEquals(3, token.getScopes().size());
			assertTrue(token.hasScope("patient/Observation.rs"));
			assertTrue(token.hasScope("launch/patient"));
		}

		@Test
		@DisplayName("without launch context or scopes is still a valid token, just an empty one")
		void absentContextIsNotAFailure() throws Exception {
			SmartAccessToken token = verifier.verify(signedBy(serverKey, validClaims().build()));

			assertNotNull(token);
			assertNull(token.getPatient());
			assertNull(token.getEncounter());
			assertTrue(token.getScopes().isEmpty());
		}

		@Test
		@DisplayName("is accepted when it names several audiences including this one")
		void acceptsOneOfSeveralAudiences() throws Exception {
			SmartAccessToken token = verifier.verify(signedBy(serverKey,
			    validClaims().audience(Arrays.asList("https://other.example.org/fhir", AUDIENCE)).build()));

			assertNotNull(token, "aud may be an array; this server appearing in it is enough");
		}
	}

	@Nested
	@DisplayName("a token that must be refused")
	class Refused {

		@Test
		@DisplayName("signed with a key the authorization server does not hold")
		void forgedSignature() throws Exception {
			assertNull(verifier.verify(signedBy(attackerKey, validClaims().build())),
			    "a token signed with an unpublished key must not be accepted");
		}

		/**
		 * The algorithm-confusion attack: the attacker re-signs with HMAC, hoping the verifier will treat
		 * the RSA public key as a shared secret.
		 */
		@Test
		@DisplayName("re-signed with HMAC, using the server's public key as the secret")
		void algorithmConfusion() throws Exception {
			byte[] publicKeyAsSecret = serverKey.toPublicJWK().toJSONString().getBytes();
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), validClaims().build());
			jwt.sign(new MACSigner(Arrays.copyOf(publicKeyAsSecret, Math.max(32, publicKeyAsSecret.length))));

			assertNull(verifier.verify(jwt.serialize()), "an HMAC-signed access token must never be accepted");
		}

		/**
		 * Distinct from the case above: this one names a key the server really does publish and really
		 * would verify with, so the only thing left to refuse it is the algorithm whitelist. Adding
		 * {@code HS256} to {@code PERMITTED_ALGORITHMS} makes this test fail, which is the property the
		 * case above cannot check.
		 */
		@Test
		@DisplayName("signed with HMAC using a key the server publishes")
		void hmacSignedWithAPublishedKey() throws Exception {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(octKey.getKeyID()).build(),
			        validClaims().build());
			jwt.sign(new MACSigner(octKey));

			assertNull(verifier.verify(jwt.serialize()), "only asymmetric algorithms may be accepted");
		}

		@Test
		@DisplayName("unsigned, with otherwise perfect claims")
		void unsigned() {
			assertNull(verifier.verify(new PlainJWT(validClaims().build()).serialize()));
		}

		@Test
		@DisplayName("issued by a different authorization server")
		void wrongIssuer() throws Exception {
			assertNull(
			    verifier.verify(signedBy(serverKey, validClaims().issuer("https://evil.example.org/realms/x").build())),
			    "a token from another issuer is not a token for this server");
		}

		/**
		 * The replay this check exists to prevent: a legitimate token, correctly signed by the same
		 * authorization server, but minted for a different FHIR server.
		 */
		@Test
		@DisplayName("issued for a different FHIR server")
		void wrongAudience() throws Exception {
			assertNull(
			    verifier.verify(signedBy(serverKey, validClaims().audience("https://other.example.org/fhir").build())),
			    "SMART requires aud to be checked precisely so this token cannot be replayed here");
		}

		@Test
		@DisplayName("naming no audience at all")
		void noAudience() throws Exception {
			assertNull(verifier.verify(signedBy(serverKey,new JWTClaimsSet.Builder().issuer(ISSUER).claim("preferred_username",USERNAME).expirationTime(new Date(System.currentTimeMillis()+300_000)).build())));
		}

		@Test
		@DisplayName("already expired")
		void expired() throws Exception {
			assertNull(verifier.verify(signedBy(serverKey,validClaims().expirationTime(new Date(System.currentTimeMillis()-600_000)).build())));
		}

		/**
		 * A token with no expiry would be valid forever. nimbus only enforces claims declared required, so
		 * this pins that exp is among them.
		 */
		@Test
		@DisplayName("carrying no expiry at all")
		void noExpiry() throws Exception {
			assertNull(
			    verifier.verify(signedBy(serverKey, new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE)
			            .claim("preferred_username", USERNAME).build())),
			    "a token without exp must be refused rather than treated as non-expiring");
		}

		@Test
		@DisplayName("verifying but naming no OpenMRS user")
		void noUsernameClaim() throws Exception {
			assertNull(verifier.verify(signedBy(serverKey,new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).expirationTime(new Date(System.currentTimeMillis()+300_000)).build())),"there is no user to act as, so the request cannot be authenticated");
		}

		@Test
		@DisplayName("tampered with after signing")
		void tamperedPayload() throws Exception {
			String[] parts = signedBy(serverKey, validClaims().build()).split("\\.");
			String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
			    ("{\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\",\"preferred_username\":\"admin\",\"exp\":"
			            + ((System.currentTimeMillis() / 1000) + 300) + "}").getBytes());

			assertNull(verifier.verify(parts[0] + "." + forged + "." + parts[2]),
			    "a payload naming a different user must not pass under the original signature");
		}

		@ValueSource(strings = { "", "   ", "not-a-token", "a.b", "a.b.c", "Bearer eyJ", "....", "eyJhbGciOiJSUzI1NiJ9" })
		@ParameterizedTest(name = "malformed: [{0}]")
		void malformed(String candidate) {
			assertNull(verifier.verify(candidate));
		}

		@Test
		@DisplayName("null")
		void nullToken() {
			assertNull(verifier.verify(null));
		}
	}

	@Nested
	@DisplayName("clock skew")
	class ClockSkew {

		@Test
		@DisplayName("a token expired within the tolerated skew is still accepted")
		void withinSkew() throws Exception {
			SmartOAuth2Config lenient=config();lenient.setAllowedClockSkewSeconds(120);SmartAccessTokenVerifier tolerant=new SmartAccessTokenVerifier(lenient,new ImmutableJWKSet<>(new JWKSet(serverKey.toPublicJWK())));

			assertNotNull(tolerant.verify(signedBy(serverKey,validClaims().expirationTime(new Date(System.currentTimeMillis()-30_000)).build())),"30 seconds past expiry is within a 120 second tolerance");
		}

		@Test
		@DisplayName("a token expired beyond the tolerated skew is refused")
		void beyondSkew() throws Exception {
			SmartOAuth2Config strict=config();strict.setAllowedClockSkewSeconds(10);SmartAccessTokenVerifier intolerant=new SmartAccessTokenVerifier(strict,new ImmutableJWKSet<>(new JWKSet(serverKey.toPublicJWK())));

			assertNull(intolerant.verify(signedBy(serverKey,validClaims().expirationTime(new Date(System.currentTimeMillis()-60_000)).build())));
		}
	}

	@Nested
	@DisplayName("configuration")
	class Configuration {

		@Test
		@DisplayName("the username claim is configurable, for servers that do not emit preferred_username")
		void usernameClaimIsConfigurable() throws Exception {
			SmartOAuth2Config custom = config();
			custom.setUsernameClaim("openmrs_user");
			SmartAccessTokenVerifier custom_verifier = new SmartAccessTokenVerifier(custom,
			        new ImmutableJWKSet<>(new JWKSet(serverKey.toPublicJWK())));

			SmartAccessToken token = custom_verifier
			        .verify(signedBy(serverKey, validClaims().claim("openmrs_user", "otheruser").build()));

			assertNotNull(token);
			assertEquals("otheruser", token.getUsername(), "the configured claim should be read, not preferred_username");
		}

		@Test
		@DisplayName("scopes are immutable, so a caller cannot widen its own grant")
		void scopesAreImmutable() throws Exception {
			SmartAccessToken token = verifier
			        .verify(signedBy(serverKey, validClaims().claim("scope", "patient/Observation.rs").build()));

			assertThrows(UnsupportedOperationException.class, () -> token.getScopes().add("patient/*.cruds"));
		}
	}
}

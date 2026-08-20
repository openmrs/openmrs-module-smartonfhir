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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tokens are what convinces the authorization server that a user picked a particular patient,
 * so a forged or replayed one grants access to the wrong record. The tests care most about what
 * must <em>not</em> verify.
 */
class SmartLaunchTokensTest {

	private static final String USERNAME = "jdoe";

	private static final String PATIENT_UUID = "6a1b2c3d-0000-4444-8888-abcdefabcdef";

	/**
	 * A fixed key, so that two calls agree. An earlier version generated one with
	 * {@code new SecureRandom(seed)}, whose seed supplements rather than replaces the default seeding:
	 * every call produced a different key, which made the wrong-secret test pass for the wrong reason.
	 */
	private static byte[] secret() {
		byte[] key = new byte[32];
		for (int i = 0; i < key.length; i++) {
			key[i] = (byte) (i + 1);
		}
		return key;
	}

	/** Differs from {@link #secret()} in a single bit, so a near-miss is still a miss. */
	private static byte[] differentSecret() {
		byte[] key = secret();
		key[key.length - 1] ^= 0x01;
		return key;
	}

	@Nested
	@DisplayName("a token this module signed")
	class RoundTrip {

		@Test
		@DisplayName("verifies against the same secret and keeps its claims")
		void roundTrips() {
			byte[] key = secret();
			String jws = SmartLaunchTokens
			        .sign(new JWTClaimsSet.Builder().subject(USERNAME).claim("patient", PATIENT_UUID).build(), key);

			assertNotNull(jws);
			JWTClaimsSet claims = SmartLaunchTokens.verify(jws, key);

			assertNotNull(claims, "a token signed with this secret must verify against it");
			assertEquals(USERNAME, claims.getSubject());
			assertEquals(PATIENT_UUID, claims.getClaim("patient"));
		}

		@Test
		@DisplayName("carries an expiry, so it cannot be replayed indefinitely")
		void isStampedWithAnExpiry() {
			JWTClaimsSet claims = SmartLaunchTokens.verify(
			    SmartLaunchTokens.sign(new JWTClaimsSet.Builder().subject(USERNAME).build(), secret()), secret());

			assertNotNull(claims.getExpirationTime(), "an unexpiring launch token stays replayable forever");
			assertTrue(claims.getExpirationTime().after(new Date()), "a freshly signed token should not be expired");
			assertNotNull(claims.getIssueTime());
		}
	}

	@Nested
	@DisplayName("a token that must not verify")
	class Rejected {

		@Test
		@DisplayName("signed with a different secret")
		void wrongSecret() {
			String jws = SmartLaunchTokens.sign(new JWTClaimsSet.Builder().subject(USERNAME).build(), secret());

			assertNull(SmartLaunchTokens.verify(jws, differentSecret()));
		}

		@Test
		@DisplayName("already expired")
		void expired() throws Exception {
			// Signed directly so the expiry can be set in the past; sign() always stamps a future one.
			JWTClaimsSet claims=new JWTClaimsSet.Builder().subject(USERNAME).expirationTime(new Date(System.currentTimeMillis()-60_000)).build();SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);jwt.sign(new MACSigner(secret()));

			assertNull(SmartLaunchTokens.verify(jwt.serialize(),secret()));
		}

		/**
		 * An unsigned token must never be accepted. This is the alg=none substitution: the claims are
		 * exactly right, and only the absent signature distinguishes it.
		 */
		@Test
		@DisplayName("unsigned, with otherwise valid claims")
		void unsignedIsNotAccepted() {
			PlainJWT plain=new PlainJWT(new JWTClaimsSet.Builder().subject(USERNAME).claim("patient",PATIENT_UUID).expirationTime(new Date(System.currentTimeMillis()+60_000)).build());

			assertNull(SmartLaunchTokens.verify(plain.serialize(),secret()),"an unsigned token must not verify, however plausible its claims");
		}

		@Test
		@DisplayName("tampered with after signing")
		void tamperedPayload() {
			String jws = SmartLaunchTokens
			        .sign(new JWTClaimsSet.Builder().subject(USERNAME).claim("patient", PATIENT_UUID).build(), secret());
			String[] parts = jws.split("\\.");

			// Swap in a payload naming a different patient, keeping the original signature.
			String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
			        .encodeToString(("{\"sub\":\"" + USERNAME + "\",\"patient\":\"someone-elses-uuid\"}").getBytes());

			assertNull(SmartLaunchTokens.verify(parts[0] + "." + forgedPayload + "." + parts[2], secret()),
			    "a payload swapped under the original signature must not verify");
		}

		@ValueSource(strings = { "", "   ", "not-a-jwt", "a.b", "a.b.c.d", "....", "eyJhbGciOiJIUzI1NiJ9" })
		@ParameterizedTest(name = "malformed: [{0}]")
		void malformedInput(String candidate) {
			assertNull(SmartLaunchTokens.verify(candidate, secret()));
		}

		@Test
		@DisplayName("null input")
		void nullInput() {
			assertNull(SmartLaunchTokens.verify(null, secret()));
		}
	}

	@Nested
	@DisplayName("when the secret is unusable, nothing is signed or verified")
	class UnusableSecret {

		@Test
		@DisplayName("an absent secret cannot sign")
		void absentSecretCannotSign() {
			assertNull(SmartLaunchTokens.sign(new JWTClaimsSet.Builder().subject(USERNAME).build(), null));
			assertNull(SmartLaunchTokens.sign(new JWTClaimsSet.Builder().subject(USERNAME).build(), new byte[0]));
		}

		/**
		 * A short key is a configuration error, not a reason to fall back to something weaker.
		 * <p>
		 * This pins the behaviour but cannot pin <em>our</em> check: nimbus rejects a sub-256-bit key as
		 * well, so removing the explicit length test yields the same null. The check is kept for the error
		 * message it produces, which names the problem instead of leaving a signing failure to be puzzled
		 * over.
		 */
		@Test
		@DisplayName("a secret shorter than HS256 requires never produces a token")
		void shortSecretIsRefused() {
			byte[] tooShort = new byte[16];

			assertNull(SmartLaunchTokens.sign(new JWTClaimsSet.Builder().subject(USERNAME).build(), tooShort));
			assertNull(SmartLaunchTokens.verify("a.b.c", tooShort));
		}

		@Test
		@DisplayName("an absent secret cannot verify a token that is otherwise valid")
		void absentSecretCannotVerify() {
			String jws = SmartLaunchTokens.sign(new JWTClaimsSet.Builder().subject(USERNAME).build(), secret());

			assertNull(SmartLaunchTokens.verify(jws, null));
		}
	}

	@Nested
	@DisplayName("reading the authorization server's action token")
	class UnverifiedRead {

		/**
		 * This path exists precisely because the token is signed with a key this module does not hold, so
		 * it must return claims without a secret. What matters is that it is only ever used for
		 * non-security decisions.
		 */
		@Test
		@DisplayName("returns claims without needing the signing key")
		void readsClaimsWithoutTheKey() {
			String jws = SmartLaunchTokens.sign(
			    new JWTClaimsSet.Builder().claim("launchType", "patient encounter").claim("user", "nested-token").build(),
			    differentSecret());

			JWTClaimsSet claims = SmartLaunchTokens.readUnverifiedClaims(jws);

			assertNotNull(claims);
			assertEquals("patient encounter", claims.getClaim("launchType"));
			assertEquals("nested-token", claims.getClaim("user"));
		}

		@ValueSource(strings = { "", "   ", "garbage", "a.b" })
		@ParameterizedTest(name = "malformed: [{0}]")
		void malformedReturnsNull(String candidate) {
			assertNull(SmartLaunchTokens.readUnverifiedClaims(candidate));
		}

		@Test
		@DisplayName("null input")
		void nullInput() {
			assertNull(SmartLaunchTokens.readUnverifiedClaims(null));
		}
	}
}

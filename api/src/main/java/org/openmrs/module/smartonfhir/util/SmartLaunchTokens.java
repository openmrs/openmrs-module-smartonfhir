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

import java.text.ParseException;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

/**
 * The short-lived HMAC-signed tokens exchanged during a SMART launch. The action token can only be
 * {@link #readUnverifiedClaims(String) read}; ours is {@link #verify(String, byte[]) verified}.
 */
@Slf4j
public final class SmartLaunchTokens {

	/** How long a token this module signs stays valid; a browser redirect follows immediately. */
	private static final long LIFETIME_MILLIS = 5 * 60 * 1000L;

	/** HS256 needs at least 256 bits; anything shorter is reported as a configuration error. */
	private static final int MINIMUM_SECRET_BYTES = 32;

	private SmartLaunchTokens() {
	}

	/**
	 * Reads a token's claims <strong>without verifying its signature</strong>, so nothing
	 * security-relevant may rest on the result.
	 *
	 * @return the claims, or null if the value is not a well-formed JWS
	 */
	public static JWTClaimsSet readUnverifiedClaims(String compactJws) {
		if (compactJws == null || compactJws.isBlank()) {
			return null;
		}

		try {
			return SignedJWT.parse(compactJws).getJWTClaimsSet();
		}
		catch (ParseException e) {
			log.error("Could not parse token", e);
			return null;
		}
	}

	/**
	 * Verifies an HS256 signature against the shared secret and checks that the token has not expired.
	 *
	 * @return the claims, or null if the token is malformed, wrongly signed, or expired. The reason is
	 *         logged; callers should answer with a generic failure rather than relaying it.
	 */
	public static JWTClaimsSet verify(String compactJws, byte[] secret) {
		if (compactJws == null || compactJws.isBlank()) {
			log.error("No token to verify");
			return null;
		}

		if (!isUsableSecret(secret)) {
			return null;
		}

		try {
			SignedJWT jwt = SignedJWT.parse(compactJws);
			JWSVerifier verifier = new MACVerifier(secret);

			if (!jwt.verify(verifier)) {
				log.error("Token signature does not match the configured SMART launch secret");
				return null;
			}

			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date expiration = claims.getExpirationTime();

			// sign() always stamps an expiry, so a token without one did not come from here.
			if (expiration == null) {
				log.error("Token carries no expiry; refusing it");
				return null;
			}

			if (expiration.before(new Date())) {
				log.error("Token expired at {}", expiration);
				return null;
			}

			return claims;
		}
		catch (ParseException e) {
			log.error("Could not parse token", e);
			return null;
		}
		catch (JOSEException e) {
			log.error("Could not verify token signature", e);
			return null;
		}
	}

	/**
	 * Signs claims with HS256 and the shared secret, stamping an expiry.
	 *
	 * @return the compact serialization, or null if signing was not possible
	 */
	public static String sign(JWTClaimsSet claims, byte[] secret) {
		if (!isUsableSecret(secret)) {
			return null;
		}

		try {
			Date now = new Date();
			JWTClaimsSet stamped = new JWTClaimsSet.Builder(claims).issueTime(now)
			        .expirationTime(new Date(now.getTime() + LIFETIME_MILLIS)).build();

			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), stamped);
			jwt.sign(new MACSigner(secret));

			return jwt.serialize();
		}
		catch (JOSEException e) {
			log.error("Could not sign the SMART launch token", e);
			return null;
		}
	}

	private static boolean isUsableSecret(byte[] secret) {
		if (secret == null || secret.length == 0) {
			log.error("No SMART launch secret is configured; see config/smart-secret-key.json");
			return false;
		}

		if (secret.length < MINIMUM_SECRET_BYTES) {
			log.error("The SMART launch secret is {} bytes; HS256 requires at least {}", secret.length,
			    MINIMUM_SECRET_BYTES);
			return false;
		}

		return true;
	}
}

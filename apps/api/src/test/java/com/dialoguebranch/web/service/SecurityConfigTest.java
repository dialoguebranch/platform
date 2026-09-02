package com.dialoguebranch.web.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link SecurityConfig#extractRealmName} and {@link SecurityConfig#isTrustedRealm}, the
 * trust decision behind {@link SecurityConfig#keycloakAuthenticationManagerResolver()}: which
 * Keycloak realm's issuer claim this service accepts a token from, given a hosting platform may
 * run one realm per client/tenant alongside its own base realm, rather than a single fixed realm.
 */
class SecurityConfigTest {

	private static final String REALMS_BASE = "http://keycloak:8080/realms/";

	@Test
	void extractsTheRealmNameFromAMatchingIssuer() {
		assertEquals("Acme", SecurityConfig.extractRealmName(REALMS_BASE + "Acme", REALMS_BASE));
		assertEquals("Acme-North", SecurityConfig.extractRealmName(REALMS_BASE + "Acme-North", REALMS_BASE));
	}

	@Test
	void returnsNullForAnIssuerFromAnUnrelatedKeycloakInstance() {
		assertNull(SecurityConfig.extractRealmName("http://evil.example.com/realms/Acme", REALMS_BASE));
	}

	@Test
	void returnsNullForAnIssuerWithNoRealmSegment() {
		assertNull(SecurityConfig.extractRealmName(REALMS_BASE, REALMS_BASE));
	}

	@Test
	void returnsNullForAnIssuerWithExtraPathSegmentsAfterTheRealmName() {
		// Guards against a crafted issuer smuggling extra path segments into what becomes part of
		// the JWKS URI this service itself calls out to.
		assertNull(SecurityConfig.extractRealmName(REALMS_BASE + "Acme/../admin", REALMS_BASE));
	}

	@Test
	void returnsNullForANullIssuer() {
		assertNull(SecurityConfig.extractRealmName(null, REALMS_BASE));
	}

	@Test
	void trustsTheAdminRealmItself() {
		assertTrue(SecurityConfig.isTrustedRealm("Acme", "Acme"));
	}

	@Test
	void trustsATenantRealmNamedAfterTheAdminRealm() {
		assertTrue(SecurityConfig.isTrustedRealm("Acme-North", "Acme"));
		assertTrue(SecurityConfig.isTrustedRealm("Acme-South", "Acme"));
	}

	@Test
	void doesNotTrustAnUnrelatedRealm() {
		assertFalse(SecurityConfig.isTrustedRealm("master", "Acme"));
		assertFalse(SecurityConfig.isTrustedRealm("dialoguebranch", "Acme"));
	}

	@Test
	void doesNotTrustARealmThatMerelyContainsTheAdminRealmNameAsAPrefixWithoutTheSeparator() {
		// "Acmecorp" must not be confused for an "Acme"-prefixed tenant realm.
		assertFalse(SecurityConfig.isTrustedRealm("Acmecorp", "Acme"));
	}
}

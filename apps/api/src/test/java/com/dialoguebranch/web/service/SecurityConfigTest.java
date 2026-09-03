package com.dialoguebranch.web.service;

import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

	// --- dlb.auth.keycloak.client-id → QueryRunner role extraction (issue #104) ---

	@AfterEach
	void restoreDefaultResourceAccessClientId() {
		QueryRunner.setResourceAccessClientId("dlb-web-service");
	}

	@Test
	void constructorSeedsQueryRunnerWithTheConfiguredClientId() {
		DlbProperties props = new DlbProperties();
		props.getAuth().getKeycloak().setClientId("acme-portal");

		new SecurityConfig(props);

		AuthenticationInfo info = QueryRunner.authenticationInfoFromKeycloakJwt(
				jwtWithResourceAccess("acme-portal", "admin"));
		assertArrayEquals(new String[] {"admin"}, info.getRoles(),
				"roles under the configured client id should be picked up");

		AuthenticationInfo wrongKey = QueryRunner.authenticationInfoFromKeycloakJwt(
				jwtWithResourceAccess("dlb-web-service", "admin"));
		assertArrayEquals(new String[0], wrongKey.getRoles(),
				"roles under the old hardcoded key should be ignored once a different id is configured");
	}

	@Test
	void constructorSeedsTheDefaultClientIdForAnUnconfiguredDeployment() {
		new SecurityConfig(new DlbProperties());

		AuthenticationInfo info = QueryRunner.authenticationInfoFromKeycloakJwt(
				jwtWithResourceAccess("dlb-web-service", "editor"));
		assertArrayEquals(new String[] {"editor"}, info.getRoles());
	}

	private static Jwt jwtWithResourceAccess(String clientId, String... roles) {
		return Jwt.withTokenValue("token")
				.header("alg", "RS256")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim("preferred_username", "alice")
				.claim("resource_access", Map.of(clientId, Map.of("roles", List.of(roles))))
				.build();
	}
}

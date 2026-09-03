package com.dialoguebranch.web.service;

import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.ForbiddenException;
import com.dialoguebranch.web.service.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link QueryRunner#runQuery}'s handling of the authenticated caller: the guard that turns a
 * request with no {@code JwtAuthenticationToken} in the security context into a {@code 401} rather
 * than letting {@link com.dialoguebranch.web.service.auth.AuthorizationService#require} raise a
 * {@code 403} for a {@code null} user.
 */
class QueryRunnerTest {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		// resourceAccessClientId is a static seeded by SecurityConfig at startup; reset it so a
		// test that changes it does not leak into the rest of the suite.
		QueryRunner.setResourceAccessClientId("dlb-web-service");
	}

	@Test
	void aRequestWithNoAuthenticatedCallerIsA401NotA403() {
		SecurityContextHolder.clearContext();

		UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
				QueryRunner.runQuery((version, user) -> "unreachable",
						"1", new MockHttpServletResponse(), "", Permission.DIALOGUE_RUN));

		assertEquals(ErrorCode.AUTH_TOKEN_NOT_FOUND, ex.getError().getCode());
	}

	@Test
	void theNoCallerGuardIsNotAForbiddenException() {
		SecurityContextHolder.clearContext();

		Exception ex = assertThrows(Exception.class, () ->
				QueryRunner.runQuery((version, user) -> "unreachable",
						"1", new MockHttpServletResponse(), "", Permission.DIALOGUE_RUN));

		assertInstanceOf(UnauthorizedException.class, ex);
	}

	@Test
	void anAuthenticatedCallerWithThePermissionClearsTheGuardAndRunsTheQuery() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(keycloakJwt("participant")));

		String result = QueryRunner.runQuery((version, user) -> "ran for " + user,
				"1", new MockHttpServletResponse(), "", Permission.DIALOGUE_RUN);

		assertEquals("ran for alice", result);
	}

	@Test
	void anAuthenticatedCallerWithoutThePermissionStillGetsA403() {
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(keycloakJwt("participant")));

		assertThrows(ForbiddenException.class, () ->
				QueryRunner.runQuery((version, user) -> "unreachable",
						"1", new MockHttpServletResponse(), "", Permission.PROJECT_CREATE));
	}

	@Test
	void rolesAreReadFromTheConfiguredResourceAccessClient() {
		QueryRunner.setResourceAccessClientId("acme-portal");

		AuthenticationInfo info = QueryRunner.authenticationInfoFromKeycloakJwt(
				keycloakJwtForClient("acme-portal", "editor", "participant"));

		assertArrayEquals(new String[] {"editor", "participant"}, info.getRoles());
	}

	@Test
	void rolesUnderADifferentClientKeyAreIgnored() {
		QueryRunner.setResourceAccessClientId("acme-portal");

		// Token carries roles under "dlb-web-service", but this deployment is configured for
		// "acme-portal" — the roles must not be picked up.
		AuthenticationInfo info = QueryRunner.authenticationInfoFromKeycloakJwt(
				keycloakJwtForClient("dlb-web-service", "admin"));

		assertArrayEquals(new String[0], info.getRoles());
	}

	@Test
	void theDefaultClientKeyIsDlbWebService() {
		AuthenticationInfo info = QueryRunner.authenticationInfoFromKeycloakJwt(
				keycloakJwt("admin"));

		assertArrayEquals(new String[] {"admin"}, info.getRoles());
	}

	private static Jwt keycloakJwt(String... roles) {
		return keycloakJwtForClient("dlb-web-service", roles);
	}

	private static Jwt keycloakJwtForClient(String resourceAccessClient, String... roles) {
		return Jwt.withTokenValue("token")
				.header("alg", "RS256")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.claim("preferred_username", "alice")
				.claim("resource_access",
						Map.of(resourceAccessClient, Map.of("roles", List.of(roles))))
				.build();
	}
}

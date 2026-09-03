package com.dialoguebranch.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that permission-based authorization is actually enforced on a migrated
 * end-point: {@code GET /v1/info/technical} now requires {@link com.dialoguebranch.web.service.auth.Permission#SERVICE_INFO_TECHNICAL}
 * (admin only). Proves the full path — Keycloak-shaped {@code resource_access} claim →
 * {@code AuthenticationInfo} → {@code AuthorizationService.require} → {@code ForbiddenException} →
 * {@code GlobalExceptionHandler} → HTTP 403 with the {@code INSUFFICIENT_PRIVILEGES} body — that
 * the rest of the #58 migration then just repeats.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	/**
	 * A request carrying a Keycloak-shaped JWT with the given {@code dlb-web-service} client roles.
	 * {@code jwt()} populates the security context directly; no real {@code Authorization} header is
	 * sent, so the OAuth2 resource-server filter does not attempt to decode a token.
	 */
	private static MockHttpServletRequestBuilder withRoles(MockHttpServletRequestBuilder builder,
			String... roles) {
		Consumer<Jwt.Builder> jwtClaims = jwt -> jwt
				.claim("preferred_username", "alice")
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(roles))));
		return builder.with(jwt().jwt(jwtClaims));
	}

	@Test
	void technicalInfoIsAllowedForAnAdmin() throws Exception {
		mockMvc.perform(withRoles(get("/v1/info/technical"), "admin"))
				.andExpect(status().isOk());
	}

	@Test
	void technicalInfoIsForbiddenForAnEditor() throws Exception {
		mockMvc.perform(withRoles(get("/v1/info/technical"), "editor"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"));
	}

	@Test
	void technicalInfoIsForbiddenForAParticipant() throws Exception {
		mockMvc.perform(withRoles(get("/v1/info/technical"), "participant"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"));
	}

	@Test
	void technicalInfoIsUnauthorizedWithoutAToken() throws Exception {
		mockMvc.perform(get("/v1/info/technical"))
				.andExpect(status().isUnauthorized());
	}

	// A second end-point at a different tier (editor), to confirm the permission gate is wired
	// across the migration and fires before the handler body runs.

	@Test
	void authoringListDialoguesIsForbiddenForAParticipant() throws Exception {
		mockMvc.perform(withRoles(get("/v1/authoring/list-dialogues").param("projectSlug", "nope"),
						"participant"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"));
	}

	@Test
	void authoringListDialoguesPassesTheGateForAnEditor() throws Exception {
		// Editor clears DIALOGUE_LIST, so the request reaches the handler; the unknown project
		// then yields 404 rather than an authorization failure.
		mockMvc.perform(withRoles(get("/v1/authoring/list-dialogues").param("projectSlug", "nope"),
						"editor"))
				.andExpect(status().isNotFound());
	}

	// Acting on behalf of another user (delegateUser) needs USER_DELEGATE, held only by admin —
	// even on an end-point the caller is otherwise allowed to use.

	@Test
	void delegatingToAnotherUserIsForbiddenForANonAdmin() throws Exception {
		mockMvc.perform(withRoles(get("/v1/variables/get")
						.param("timeZone", "UTC").param("projectSlug", "nope")
						.param("delegateUser", "someone-else"), "editor"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"));
	}

	// Listing known users (GET /users, #130) needs the dedicated USER_LIST permission — admin
	// only, and distinct from USER_DELEGATE.

	@Test
	void listUsersIsAllowedForAnAdmin() throws Exception {
		mockMvc.perform(withRoles(get("/v1/users"), "admin"))
				.andExpect(status().isOk());
	}

	@Test
	void listUsersIsForbiddenForAnEditor() throws Exception {
		mockMvc.perform(withRoles(get("/v1/users"), "editor"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"));
	}
}

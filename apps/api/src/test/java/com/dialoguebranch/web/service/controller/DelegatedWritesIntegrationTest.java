/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.execution.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Delegated writes ({@code delegateUser} naming another user on {@code /dialogue/start},
 * {@code /dialogue/continue}, {@code /dialogue/get-ongoing}, {@code /variables/set-single} and
 * {@code /variables/set}) must not overwrite that user's in-memory time zone with the caller's
 * (#331): the {@code timeZone} parameter is ignored for them, and what they change is stamped in
 * the user's own time zone. Requests for the caller's own identity still update it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DelegatedWritesIntegrationTest {

	private static final String PROJECT = "default-test";
	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";
	private static final ZoneId USER_ZONE = ZoneId.of("Europe/Amsterdam");
	private static final String CALLER_ZONE = "Asia/Tokyo";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	Application application;

	private static String newSubject() {
		return "delegated-" + UUID.randomUUID();
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
			String subject, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	/** A user who has made a request of their own, in {@link #USER_ZONE}. */
	private String activeUser() throws Exception {
		String user = newSubject();
		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "mood").param("value", "fine")
						.param("timeZone", USER_ZONE.getId()), user, "participant"))
				.andExpect(status().isOk());
		assertEquals(USER_ZONE, timeZoneOf(user));
		return user;
	}

	private MockHttpServletRequestBuilder asAdminFor(MockHttpServletRequestBuilder builder,
			String user) {
		return as(builder.param("timeZone", CALLER_ZONE).param("delegateUser", user),
				newSubject(), "admin");
	}

	private ZoneId timeZoneOf(String subject) {
		UserService service = application.getApplicationManager()
				.getActiveUserService(DialogueBranchUserId.withoutUsername(ISSUER, subject));
		return service.getDialogueBranchUser().getTimeZone();
	}

	@Test
	void aDelegatedSetSingleKeepsTheUsersTimeZoneAndStampsInIt() throws Exception {
		String user = activeUser();

		mockMvc.perform(asAdminFor(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "fixed").param("value", "yes"), user))
				.andExpect(status().isOk());

		assertEquals(USER_ZONE, timeZoneOf(user));
		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("variableNames", "fixed").param("timeZone", USER_ZONE.getId()),
						user, "participant"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'fixed')].updatedTimeZone")
						.value(contains(USER_ZONE.getId())));
	}

	@Test
	void aDelegatedSetKeepsTheUsersTimeZone() throws Exception {
		String user = activeUser();

		mockMvc.perform(asAdminFor(post("/v1/variables/set").param("projectSlug", PROJECT)
						.contentType(MediaType.APPLICATION_JSON).content("{\"fixed\": \"yes\"}"),
						user))
				.andExpect(status().isOk());

		assertEquals(USER_ZONE, timeZoneOf(user));
	}

	@Test
	void delegatedDialogueRequestsKeepTheUsersTimeZone() throws Exception {
		String user = activeUser();

		mockMvc.perform(asAdminFor(post("/v1/dialogue/start").param("projectSlug", PROJECT)
						.param("dialogueName", "basic").param("language", "en"), user))
				.andExpect(status().isOk());
		assertEquals(USER_ZONE, timeZoneOf(user));

		mockMvc.perform(asAdminFor(post("/v1/dialogue/continue").param("projectSlug", PROJECT)
						.param("dialogueName", "basic"), user))
				.andExpect(status().isOk());
		assertEquals(USER_ZONE, timeZoneOf(user));

		mockMvc.perform(asAdminFor(get("/v1/dialogue/get-ongoing").param("projectSlug", PROJECT),
						user))
				.andExpect(status().isOk());
		assertEquals(USER_ZONE, timeZoneOf(user));
	}

	@Test
	void aDelegatedWriteForAnInactiveUserStartsThemInTheCallersTimeZone() throws Exception {
		String user = newSubject();

		mockMvc.perform(asAdminFor(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "fixed").param("value", "yes"), user))
				.andExpect(status().isOk());

		assertEquals(ZoneId.of(CALLER_ZONE), timeZoneOf(user));
	}

	@Test
	void anOwnRequestStillUpdatesTheTimeZone() throws Exception {
		String user = activeUser();

		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "mood").param("value", "travelling")
						.param("timeZone", "America/New_York"), user, "participant"))
				.andExpect(status().isOk());

		assertEquals(ZoneId.of("America/New_York"), timeZoneOf(user));
	}
}

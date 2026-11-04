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
import com.dialoguebranch.web.service.repository.DBUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Delegated reads ({@code delegateUser} naming another user) must leave that user untouched: they
 * must not overwrite the user's in-memory time zone (#327), and must not create anything for a user
 * this service doesn't know, answering {@code 404 UNKNOWN_USER} instead (#328). Requests for the
 * caller's own identity keep their existing behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DelegatedReadsIntegrationTest {

	private static final String PROJECT = "default-test";
	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	Application application;

	@Autowired
	DBUserRepository userRepository;

	private static String newSubject() {
		return "delegated-" + UUID.randomUUID();
	}

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T as(T builder,
			String subject, String... roles) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(roles))));
		return builder.with(jwt().jwt(claims));
	}

	/** The user sets one of their own variables, which also creates their UserService. */
	private void setOwnVariable(String subject, String name, String value, String timeZone)
			throws Exception {
		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", name).param("value", value).param("timeZone", timeZone),
						subject, "participant"))
				.andExpect(status().isOk());
	}

	private UserService activeUserService(String subject) {
		return application.getApplicationManager()
				.getActiveUserService(new DialogueBranchUserId(ISSUER, subject, null));
	}

	private boolean userRowExists(String subject) {
		return userRepository.findByIssuerAndSubject(ISSUER, subject).isPresent();
	}

	// -------------------- #327: the delegated user's time zone -------------------- //

	@Test
	void aDelegatedVariablesReadLeavesTheUsersTimeZoneAlone() throws Exception {
		String user = newSubject();
		setOwnVariable(user, "mood", "fine", "Europe/Amsterdam");
		assertEquals(ZoneId.of("Europe/Amsterdam"),
				activeUserService(user).getDialogueBranchUser().getTimeZone());

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "Asia/Tokyo").param("delegateUser", user),
						newSubject(), "admin"))
				.andExpect(status().isOk());

		assertEquals(ZoneId.of("Europe/Amsterdam"),
				activeUserService(user).getDialogueBranchUser().getTimeZone());
	}

	@Test
	void aDelegatedVariablesReadReturnsTheStoredValuesSortedByName() throws Exception {
		String user = newSubject();
		setOwnVariable(user, "mood", "fine", "Europe/Amsterdam");
		setOwnVariable(user, "energy", "low", "Europe/Amsterdam");
		String admin = newSubject();

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", user), admin, "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", contains("energy", "mood")))
				.andExpect(jsonPath("$[1].value").value("fine"));

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", user)
						.param("variableNames", "mood"), admin, "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].value").value("fine"));
	}

	@Test
	void aDelegatedVariablesReadOfAnInactiveUserCreatesNoUserService() throws Exception {
		String user = newSubject();
		setOwnVariable(user, "mood", "fine", "Europe/Amsterdam");
		application.getApplicationManager().removeUserService(activeUserService(user));
		assertNull(activeUserService(user));

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", user), newSubject(), "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].value").value("fine"));

		assertNull(activeUserService(user));
	}

	// -------------------- #328: unknown delegated users -------------------- //

	@Test
	void aDelegatedVariablesReadOfAnUnknownUserIsNotFoundAndCreatesNothing() throws Exception {
		String unknown = newSubject();

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", unknown), newSubject(), "admin"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNKNOWN_USER"));

		assertTrue(!userRowExists(unknown), "no users row may be created for an unknown subject");
		assertNull(activeUserService(unknown));
	}

	@Test
	void aDelegatedHistoryReadOfAnUnknownUserIsNotFound() throws Exception {
		String unknown = newSubject();

		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", PROJECT)
						.param("delegateUser", unknown), newSubject(), "admin"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNKNOWN_USER"));

		assertTrue(!userRowExists(unknown));
	}

	@Test
	void delegatedLogReadsOfAnUnknownUserAreNotFoundAndCreateNothing() throws Exception {
		String unknown = newSubject();
		String admin = newSubject();

		mockMvc.perform(as(get("/v1/log/get-session").param("sessionId", "s-1")
						.param("delegateUser", unknown), admin, "admin"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNKNOWN_USER"));
		mockMvc.perform(as(get("/v1/log/verify-id").param("sessionId", "s-1")
						.param("delegateUser", unknown), admin, "admin"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNKNOWN_USER"));

		assertTrue(!userRowExists(unknown));
		assertNull(activeUserService(unknown));
	}

	@Test
	void delegatedLogReadsOfAKnownUserReturnTheirSession() throws Exception {
		String user = newSubject();
		String sessionId = "session-" + UUID.randomUUID();
		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", PROJECT)
						.param("dialogueName", "basic").param("language", "en")
						.param("timeZone", "Europe/Amsterdam").param("sessionId", sessionId),
						user, "participant"))
				.andExpect(status().isOk());
		String admin = newSubject();

		mockMvc.perform(as(get("/v1/log/get-session").param("sessionId", sessionId)
						.param("delegateUser", user), admin, "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].user").value(user))
				.andExpect(jsonPath("$[0].dialogueName").value("basic"));
		mockMvc.perform(as(get("/v1/log/verify-id").param("sessionId", sessionId)
						.param("delegateUser", user), admin, "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(true));
		mockMvc.perform(as(get("/v1/log/verify-id").param("sessionId", "no-such-session")
						.param("delegateUser", user), admin, "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(false));
	}

	// -------------------- the caller's own identity: unchanged -------------------- //

	@Test
	void aFirstOwnRequestStillCreatesTheCallersOwnUser() throws Exception {
		String user = newSubject();
		assertTrue(!userRowExists(user));

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "Europe/Lisbon"), user, "participant"))
				.andExpect(status().isOk());

		assertTrue(userRowExists(user));
		UserService service = activeUserService(user);
		assertNotNull(service);
		assertEquals(ZoneId.of("Europe/Lisbon"), service.getDialogueBranchUser().getTimeZone());
	}

	@Test
	void anOwnHistoryReadOfAUserWithoutHistoryIsStillAnEmptyList() throws Exception {
		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", PROJECT),
						newSubject(), "participant"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void namingYourselfAsDelegateUserIsNotADelegatedRead() throws Exception {
		String user = newSubject();

		// Not delegated, so the own-identity path applies: the user is created, no 404.
		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", user), user, "participant"))
				.andExpect(status().isOk());

		assertTrue(userRowExists(user));
	}
}

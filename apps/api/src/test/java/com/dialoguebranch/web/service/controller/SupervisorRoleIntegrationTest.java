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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code supervisor} role end to end (#326): it may read another user's variables, variable
 * history and dialogue logs through {@code delegateUser}, may never act on their behalf, and can
 * still do everything a participant can for themselves.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupervisorRoleIntegrationTest {

	private static final String PROJECT = "default-test";
	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	private static String newSubject() {
		return "supervised-" + UUID.randomUUID();
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

	/** A participant with one variable set and one logged dialogue session. */
	private String participantWithData(String sessionId) throws Exception {
		String participant = newSubject();
		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "mood").param("value", "fine")
						.param("timeZone", "Europe/Amsterdam"), participant, "participant"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", PROJECT)
						.param("dialogueName", "basic").param("language", "en")
						.param("timeZone", "Europe/Amsterdam").param("sessionId", sessionId),
						participant, "participant"))
				.andExpect(status().isOk());
		return participant;
	}

	@Test
	void aSupervisorCanReadAParticipantsVariablesHistoryAndLogs() throws Exception {
		String sessionId = "session-" + UUID.randomUUID();
		String participant = participantWithData(sessionId);
		String supervisor = newSubject();

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", participant),
						supervisor, "supervisor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("mood"))
				.andExpect(jsonPath("$[0].value").value("fine"));
		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", PROJECT)
						.param("variableName", "mood").param("delegateUser", participant),
						supervisor, "supervisor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].value").value("fine"));
		mockMvc.perform(as(get("/v1/log/get-session").param("sessionId", sessionId)
						.param("delegateUser", participant), supervisor, "supervisor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		mockMvc.perform(as(get("/v1/log/verify-id").param("sessionId", sessionId)
						.param("delegateUser", participant), supervisor, "supervisor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(true));
	}

	@Test
	void aSupervisorCannotChangeAParticipantsVariables() throws Exception {
		String participant = participantWithData("session-" + UUID.randomUUID());

		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "mood").param("value", "bad").param("timeZone", "UTC")
						.param("delegateUser", participant), newSubject(), "supervisor"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"))
				.andExpect(jsonPath("$.message", containsString("'USER_DELEGATE'")));
	}

	@Test
	void aSupervisorCannotRunOrInspectDialoguesForAParticipant() throws Exception {
		String participant = participantWithData("session-" + UUID.randomUUID());
		String supervisor = newSubject();

		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", PROJECT)
						.param("dialogueName", "basic").param("language", "en").param("timeZone", "UTC")
						.param("delegateUser", participant), supervisor, "supervisor"))
				.andExpect(status().isForbidden());
		// Read-only, but under DIALOGUE_RUN, so not open to read-only delegation.
		mockMvc.perform(as(get("/v1/dialogue/get-ongoing").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", participant),
						supervisor, "supervisor"))
				.andExpect(status().isForbidden());
	}

	@Test
	void aSupervisorGetsNotFoundForAnUnknownUser() throws Exception {
		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", newSubject()),
						newSubject(), "supervisor"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNKNOWN_USER"));
	}

	@Test
	void aSupervisorCanStillUseTheServiceForThemselves() throws Exception {
		String supervisor = newSubject();
		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", PROJECT)
						.param("name", "mood").param("value", "fine").param("timeZone", "UTC"),
						supervisor, "supervisor"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", PROJECT)
						.param("dialogueName", "basic").param("language", "en").param("timeZone", "UTC"),
						supervisor, "supervisor"))
				.andExpect(status().isOk());
	}

	@Test
	void aParticipantCannotReadAnotherParticipantsData() throws Exception {
		String participant = participantWithData("session-" + UUID.randomUUID());

		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", PROJECT)
						.param("timeZone", "UTC").param("delegateUser", participant),
						newSubject(), "participant"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message", containsString("'USER_DELEGATE_READ'")));
	}
}

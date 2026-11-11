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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request for a project that doesn't exist is a 404, not a 500 or an empty answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnknownProjectIntegrationTest {

	private static final String UNKNOWN_PROJECT = "no-such-project";
	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T asParticipant(T builder) {
		return as(builder, "participant");
	}

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T as(T builder,
			String role) {
		String subject = "unknown-project-" + UUID.randomUUID();
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	@Test
	void listDialoguesIsNotFound() throws Exception {
		mockMvc.perform(as(get("/v1/dialogue/list-dialogues")
						.param("projectSlug", UNKNOWN_PROJECT), "editor"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Project not found: " + UNKNOWN_PROJECT));
	}

	@Test
	void getOngoingDialogueIsNotFound() throws Exception {
		mockMvc.perform(asParticipant(get("/v1/dialogue/get-ongoing")
						.param("projectSlug", UNKNOWN_PROJECT).param("timeZone", "UTC")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Project not found: " + UNKNOWN_PROJECT));
	}

	@Test
	void getVariablesIsNotFound() throws Exception {
		mockMvc.perform(asParticipant(get("/v1/variables/get")
						.param("projectSlug", UNKNOWN_PROJECT).param("timeZone", "UTC")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Project not found: " + UNKNOWN_PROJECT));
	}

	@Test
	void setSingleVariableIsNotFound() throws Exception {
		mockMvc.perform(asParticipant(post("/v1/variables/set-single")
						.param("projectSlug", UNKNOWN_PROJECT).param("name", "mood")
						.param("value", "fine").param("timeZone", "UTC")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Project not found: " + UNKNOWN_PROJECT));
	}

	@Test
	void setVariablesIsNotFound() throws Exception {
		mockMvc.perform(asParticipant(post("/v1/variables/set")
						.param("projectSlug", UNKNOWN_PROJECT).param("timeZone", "UTC")
						.contentType(MediaType.APPLICATION_JSON).content("{\"mood\":\"fine\"}")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Project not found: " + UNKNOWN_PROJECT));
	}
}

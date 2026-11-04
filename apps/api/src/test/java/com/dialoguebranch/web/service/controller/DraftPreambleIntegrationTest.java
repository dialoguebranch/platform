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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests reading and changing a draft dialogue's preamble through the authoring end-points (#346):
 * {@code /list-dialogues} returns it, {@code /update-preamble} replaces it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DraftPreambleIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** The "main" dialogue in a list-dialogues response (a new project also has "default"). */
	private static final String MAIN = "$[?(@.name == 'main')]";

	@Autowired
	MockMvc mockMvc;

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T as(T builder,
			String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("preamble-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "preamble-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	private String newProjectWithDialogue() throws Exception {
		String slug = "preamble-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(post("/v1/authoring/create-dialogue").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"main\"}"), "admin"))
				.andExpect(status().isCreated());
		return slug;
	}

	private MockHttpServletRequestBuilder updatePreamble(String slug, String preambleJson,
			String role) {
		return as(post("/v1/authoring/update-preamble").param("projectSlug", slug)
				.param("dialogueName", "main").contentType(MediaType.APPLICATION_JSON)
				.content("{\"preamble\":" + preambleJson + "}"), role);
	}

	@Test
	void aNewDialogueHasAnEmptyPreamble() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(MAIN + ".preamble", contains("")));
	}

	@Test
	void updatePreambleStoresCommentLinesWithoutBlankLines() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(updatePreamble(slug,
						"\"// What this is for.\\n\\n  //   Kept exactly.\\r\\n\"", "editor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preamble").value("// What this is for.\n  //   Kept exactly."));
		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(MAIN + ".preamble", contains("// What this is for.\n  //   Kept exactly.")))
				.andExpect(jsonPath(MAIN + ".isChanged", contains(true)));
	}

	@Test
	void updatePreambleRefusesLinesThatArentComments() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(updatePreamble(slug, "\"// Fine.\\ntitle: Start\"", "admin"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Line 2")));
	}

	@Test
	void anEmptyPreambleRemovesIt() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(updatePreamble(slug, "\"// Something.\"", "admin")).andExpect(status().isOk());
		mockMvc.perform(updatePreamble(slug, "\"  \\n \"", "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preamble").value(""));
		mockMvc.perform(updatePreamble(slug, "null", "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preamble").value(""));
	}

	@Test
	void participantsCannotChangeAPreamble() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(updatePreamble(slug, "\"// x\"", "participant"))
				.andExpect(status().isForbidden());
	}
}

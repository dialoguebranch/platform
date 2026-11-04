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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the draft node end-points' header tags (#350): nodes are returned with their header as an
 * ordered list of tags, created and updated from tags, and a node's title only changes by renaming
 * it (#343).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DraftNodeTagsIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T asAdmin(T builder) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("node-tags-admin")
				.claim("iss", ISSUER)
				.claim("preferred_username", "node-tags-admin")
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of("admin"))));
		return builder.with(jwt().jwt(claims));
	}

	private String newProjectWithDialogue() throws Exception {
		String slug = "node-tags-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(asAdmin(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}")))
				.andExpect(status().isCreated());
		mockMvc.perform(asAdmin(post("/v1/authoring/create-dialogue").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"main\"}")))
				.andExpect(status().isCreated());
		return slug;
	}

	/** Tags as JSON, from alternating keys and values. */
	private static String tags(String... keysAndValues) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < keysAndValues.length; i += 2) {
			if (i > 0) json.append(',');
			json.append("{\"key\":\"").append(keysAndValues[i]).append("\",\"value\":\"")
					.append(keysAndValues[i + 1]).append("\"}");
		}
		return json.append(']').toString();
	}

	private MockHttpServletRequestBuilder createNode(String slug, String tags) {
		return asAdmin(post("/v1/authoring/create-node").param("projectSlug", slug)
				.param("dialogueName", "main").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tags\":" + tags + ",\"body\":\"Hi.\"}"));
	}

	private MockHttpServletRequestBuilder updateNode(String slug, String title, String tags) {
		return asAdmin(post("/v1/authoring/update-node").param("projectSlug", slug)
				.param("dialogueName", "main").param("nodeTitle", title)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tags\":" + tags + ",\"body\":\"Hi.\"}"));
	}

	@Test
	void nodesAreCreatedFromTagsAndReturnedWithTagsInOrder() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(createNode(slug, tags("title", "Start", "speaker", "Robin", "2", "digit key",
						"comment", "see https://example.com // kept")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Start"))
				.andExpect(jsonPath("$.header").doesNotExist())
				.andExpect(jsonPath("$.tags[2].key").value("2"))
				.andExpect(jsonPath("$.tags[3].value").value("see https://example.com // kept"));

		mockMvc.perform(asAdmin(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "main")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Start"))
				.andExpect(jsonPath("$[0].tags[0].key").value("title"))
				.andExpect(jsonPath("$[0].tags[1].key").value("speaker"))
				.andExpect(jsonPath("$[0].tags[2].key").value("2"))
				.andExpect(jsonPath("$[0].tags[3].key").value("comment"))
				.andExpect(jsonPath("$[0].body").value("Hi."));
	}

	@Test
	void createNodeRefusesTagsThatDontFormAValidHeader() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(createNode(slug, tags("speaker", "Robin")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(createNode(slug, tags("title", "not valid")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(createNode(slug, tags("title", "Start", "my key", "x")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Invalid header key")));
		mockMvc.perform(createNode(slug, tags("title", "Start", "speaker", "A", "speaker", "B")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Duplicate header key")));
		mockMvc.perform(createNode(slug, tags("title", "Start", "comment", "two\\nlines")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(asAdmin(post("/v1/authoring/create-node").param("projectSlug", slug)
						.param("dialogueName", "main").contentType(MediaType.APPLICATION_JSON)
						.content("{\"body\":\"Hi.\"}")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateNodeReplacesAllTagsButRefusesAChangedOrMissingTitle() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(createNode(slug, tags("title", "Start", "speaker", "Robin", "position", "0,0")))
				.andExpect(status().isCreated());

		mockMvc.perform(updateNode(slug, "Start", tags("title", "Start", "position", "10,20")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tags.length()").value(2))
				.andExpect(jsonPath("$.tags[1].value").value("10,20"));
		mockMvc.perform(updateNode(slug, "Start", tags("title", "Renamed")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("/rename-node")));
		mockMvc.perform(updateNode(slug, "Start", tags("position", "10,20")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void renameNodeReturnsTheNodeWithTags() throws Exception {
		String slug = newProjectWithDialogue();
		mockMvc.perform(createNode(slug, tags("title", "Start", "speaker", "Robin")))
				.andExpect(status().isCreated());
		mockMvc.perform(asAdmin(post("/v1/authoring/rename-node").param("projectSlug", slug)
						.param("dialogueName", "main").param("oldTitle", "Start")
						.param("newTitle", "Begin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.referencesUpdated").value(0))
				.andExpect(jsonPath("$.node.title").value("Begin"))
				.andExpect(jsonPath("$.node.tags[0].value").value("Begin"))
				.andExpect(jsonPath("$.node.tags[1].key").value("speaker"));
	}
}

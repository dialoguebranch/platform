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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests discarding all of a project's draft changes through {@code /project/discard-draft-changes}
 * (#364): the whole draft becomes identical to the latest published version again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscardProjectChangesIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	private static final String MAIN = "title: Start\nspeaker: Robin\nposition: 10,20\n---\n" +
			"Hello.\n===\n";

	private static final String SECOND = "title: Start\nspeaker: Anna\n---\nHi.\n===\n";

	private static final String DUTCH = "{\"Robin\":{\"Hello.\":\"Hallo.\"}}";

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T as(T builder, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("discard-all-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "discard-all-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	private static String dialogue(String name) {
		return "$[?(@.name == '" + name + "')]";
	}

	private String newProject() throws Exception {
		String slug = "discard-all-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"Published name\"," +
								"\"description\":\"Published description\"," +
								"\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		return slug;
	}

	/** A project with the language "nl" and the dialogues "main" (translated) and "second". */
	private String publishedProject() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(post("/v1/project/add-translation-language").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"translationLanguageName\":\"Dutch\",\"translationLanguageCode\":\"nl\"}"),
						"admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								MAIN.getBytes(StandardCharsets.UTF_8)))
						.file(new MockMultipartFile("files", "second.dlb", "text/plain",
								SECOND.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug), "admin"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/authoring/update-translation").param("projectSlug", slug)
						.param("dialogueName", "main").param("language", "nl")
						.contentType(MediaType.APPLICATION_JSON)
						.content(JSON.writeValueAsString(Map.of("content", DUTCH))), "admin"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug), "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		return slug;
	}

	private void rename(String slug, String from, String to) throws Exception {
		mockMvc.perform(as(post("/v1/authoring/rename-dialogue").param("projectSlug", slug)
						.param("dialogueName", from).param("newName", to), "editor"))
				.andExpect(status().isOk());
	}

	private JsonNode readJson(MockHttpServletRequestBuilder request) throws Exception {
		return JSON.readTree(mockMvc.perform(request).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private MockHttpServletRequestBuilder discardAll(String slug, String role) {
		return as(post("/v1/project/discard-draft-changes").param("projectSlug", slug), role);
	}

	@Test
	void bringsTheWholeDraftBackToThePublishedVersion() throws Exception {
		String slug = publishedProject();
		// Content: an edited node, swapped dialogue names, a new dialogue, a pending deletion.
		mockMvc.perform(as(post("/v1/authoring/update-node").param("projectSlug", slug)
						.param("dialogueName", "main").param("nodeTitle", "Start")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tags\":[{\"key\":\"title\",\"value\":\"Start\"}," +
								"{\"key\":\"speaker\",\"value\":\"Robin\"}," +
								"{\"key\":\"position\",\"value\":\"300,400\"}]," +
								"\"body\":\"Changed.\"}"), "editor"))
				.andExpect(status().isOk());
		rename(slug, "main", "swap");
		rename(slug, "second", "main");
		rename(slug, "swap", "second");
		mockMvc.perform(as(post("/v1/authoring/create-dialogue").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"fresh\"}"), "editor"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(post("/v1/authoring/delete-dialogue").param("projectSlug", slug)
						.param("dialogueName", "main"), "editor"))
				.andExpect(status().isNoContent());
		// Project: changed metadata, a renamed language code and an added language.
		String nlId = readJson(as(get("/v1/project/get-project").param("projectSlug", slug), "admin"))
				.get("draftTranslationLanguages").get(0).get("id").asText();
		mockMvc.perform(as(post("/v1/project/update-draft").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"Draft name\",\"description\":\"Draft description\"," +
								"\"updateLanguages\":[{\"id\":\"" + nlId + "\",\"translationLanguageName\":\"Nederlands\"," +
								"\"translationLanguageCode\":\"nl-NL\"}]," +
								"\"addLanguages\":[{\"translationLanguageName\":\"Portuguese\",\"translationLanguageCode\":\"pt\"}]}"),
						"admin"))
				.andExpect(status().isOk());

		mockMvc.perform(discardAll(slug, "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialoguesRemoved").value(1));

		JsonNode project = readJson(as(get("/v1/project/get-project").param("projectSlug", slug), "admin"));
		org.junit.jupiter.api.Assertions.assertEquals("Published name", project.get("draftDisplayName").asText());
		org.junit.jupiter.api.Assertions.assertEquals("Published description", project.get("draftDescription").asText());
		JsonNode languages = project.get("draftTranslationLanguages");
		org.junit.jupiter.api.Assertions.assertEquals(1, languages.size());
		org.junit.jupiter.api.Assertions.assertEquals("nl", languages.get(0).get("translationLanguageCode").asText());
		org.junit.jupiter.api.Assertions.assertEquals("Dutch", languages.get(0).get("translationLanguageName").asText());
		org.junit.jupiter.api.Assertions.assertFalse(languages.get(0).get("isNew").asBoolean());
		org.junit.jupiter.api.Assertions.assertFalse(languages.get(0).get("isDeleted").asBoolean());

		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("fresh"), empty()))
				.andExpect(jsonPath(dialogue("swap"), empty()))
				.andExpect(jsonPath("$[?(@.isNew == true || @.isChanged == true || @.isDeleted == true)]", empty()))
				.andExpect(jsonPath(dialogue("main") + ".translatedInto[*]", contains("nl")));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "main"), "admin"))
				.andExpect(jsonPath("$[0].body").value("Hello."))
				.andExpect(jsonPath("$[0].tags[?(@.key == 'position')].value", contains("10,20")));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "second"), "admin"))
				.andExpect(jsonPath("$[0].body").value("Hi."));
		JsonNode dutch = readJson(as(get("/v1/authoring/get-translation").param("projectSlug", slug)
				.param("dialogueName", "main").param("language", "nl"), "admin"));
		org.junit.jupiter.api.Assertions.assertEquals(JSON.readTree(DUTCH),
				JSON.readTree(dutch.get("content").asText()));
	}

	@Test
	void aProjectThatWasNeverPublishedIsRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(discardAll(slug, "admin"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("never published")));
	}

	@Test
	void editorsCannotDiscardAllChanges() throws Exception {
		String slug = publishedProject();
		mockMvc.perform(discardAll(slug, "editor"))
				.andExpect(status().isForbidden());
	}
}

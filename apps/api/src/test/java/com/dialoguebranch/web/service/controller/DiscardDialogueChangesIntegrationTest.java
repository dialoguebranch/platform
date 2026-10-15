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
 * Tests discarding a draft dialogue's changes through {@code /authoring/discard-dialogue-changes}
 * (#363): the draft becomes identical to its latest published version again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscardDialogueChangesIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	private static final String SCRIPT = "// About main.\n" +
			"title: Start\nspeaker: Robin\nposition: 10,20\n---\nHello.\n[[Next|Second]]\n===\n" +
			"title: Second\nspeaker: Robin\n---\nBye.\n===\n";

	private static final String DUTCH = "{\"Robin\":{\"Hello.\":\"Hallo.\"}}";

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T as(T builder, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("discard-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "discard-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	private static String dialogue(String name) {
		return "$[?(@.name == '" + name + "')]";
	}

	private static String node(String title) {
		return "$[?(@.title == '" + title + "')]";
	}

	/** A project with the language "nl" and the dialogue "main" (translated), published. */
	private String publishedProject() throws Exception {
		String slug = "discard-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(post("/v1/project/add-translation-language").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"translationLanguageName\":\"Dutch\",\"translationLanguageCode\":\"nl\"}"),
						"admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								SCRIPT.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug), "admin"))
				.andExpect(status().isOk());
		updateTranslation(slug, "main", "nl", DUTCH);
		publish(slug);
		return slug;
	}

	private void publish(String slug) throws Exception {
		mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug), "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	private void updateTranslation(String slug, String dialogueName, String language,
			String content) throws Exception {
		mockMvc.perform(as(post("/v1/authoring/update-translation").param("projectSlug", slug)
						.param("dialogueName", dialogueName).param("language", language)
						.contentType(MediaType.APPLICATION_JSON)
						.content(JSON.writeValueAsString(Map.of("content", content))), "admin"))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder discard(String slug, String dialogueName, String role) {
		return as(post("/v1/authoring/discard-dialogue-changes").param("projectSlug", slug)
				.param("dialogueName", dialogueName), role);
	}

	private void editStart(String slug, String dialogueName) throws Exception {
		mockMvc.perform(as(post("/v1/authoring/update-node").param("projectSlug", slug)
						.param("dialogueName", dialogueName).param("nodeTitle", "Start")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tags\":[{\"key\":\"title\",\"value\":\"Start\"}," +
								"{\"key\":\"speaker\",\"value\":\"Robin\"}," +
								"{\"key\":\"position\",\"value\":\"300,400\"}]," +
								"\"body\":\"Hello there.\\n[[Next|Second]]\"}"), "editor"))
				.andExpect(status().isOk());
	}

	private JsonNode readJson(MockHttpServletRequestBuilder request) throws Exception {
		return JSON.readTree(mockMvc.perform(request).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	@Test
	void restoresNodesPreambleTranslationsAndName() throws Exception {
		String slug = publishedProject();
		mockMvc.perform(as(post("/v1/project/add-translation-language").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"translationLanguageName\":\"Portuguese\",\"translationLanguageCode\":\"pt\"}"),
						"admin"))
				.andExpect(status().isCreated());
		editStart(slug, "main");
		mockMvc.perform(as(post("/v1/authoring/update-preamble").param("projectSlug", slug)
						.param("dialogueName", "main").contentType(MediaType.APPLICATION_JSON)
						.content("{\"preamble\":\"// Changed.\"}"), "editor"))
				.andExpect(status().isOk());
		updateTranslation(slug, "main", "nl", "{\"Robin\":{\"Hello there.\":\"Hallo daar.\"}}");
		updateTranslation(slug, "main", "pt", "{\"Robin\":{\"Hello there.\":\"Olá.\"}}");
		mockMvc.perform(as(post("/v1/authoring/rename-dialogue").param("projectSlug", slug)
						.param("dialogueName", "main").param("newName", "renamed"), "editor"))
				.andExpect(status().isOk());
		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("renamed") + ".isChanged", contains(true)))
				.andExpect(jsonPath(dialogue("renamed") + ".previousPublishedName", contains("main")));

		mockMvc.perform(discard(slug, "renamed", "editor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueName").value("main"))
				.andExpect(jsonPath("$.discardedName").value("renamed"));

		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("renamed"), empty()))
				.andExpect(jsonPath(dialogue("main") + ".isChanged", contains(false)))
				.andExpect(jsonPath(dialogue("main") + ".previousPublishedName", contains((Object) null)))
				.andExpect(jsonPath(dialogue("main") + ".preamble", contains("// About main.")))
				.andExpect(jsonPath(dialogue("main") + ".translatedInto[*]", contains("nl")));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "main"), "admin"))
				.andExpect(jsonPath(node("Start") + ".body", contains("Hello.\n[[Next|Second]]")))
				.andExpect(jsonPath(node("Start") + ".tags[?(@.key == 'position')].value", contains("10,20")))
				.andExpect(jsonPath(node("Second") + ".body", contains("Bye.")));
		JsonNode dutch = readJson(as(get("/v1/authoring/get-translation").param("projectSlug", slug)
				.param("dialogueName", "main").param("language", "nl"), "admin"));
		org.junit.jupiter.api.Assertions.assertEquals(JSON.readTree(DUTCH),
				JSON.readTree(dutch.get("content").asText()));
		mockMvc.perform(as(get("/v1/authoring/get-translation").param("projectSlug", slug)
						.param("dialogueName", "main").param("language", "pt"), "admin"))
				.andExpect(content().string(""));
	}

	@Test
	void aDialogueThatWasNeverPublishedIsRefused() throws Exception {
		String slug = publishedProject();
		mockMvc.perform(as(post("/v1/authoring/create-dialogue").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"fresh\"}"), "editor"))
				.andExpect(status().isCreated());
		mockMvc.perform(discard(slug, "fresh", "editor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("never published")));
	}

	@Test
	void aDialoguePendingDeletionIsRefused() throws Exception {
		String slug = publishedProject();
		mockMvc.perform(as(post("/v1/authoring/delete-dialogue").param("projectSlug", slug)
						.param("dialogueName", "main"), "editor"))
				.andExpect(status().isNoContent());
		mockMvc.perform(discard(slug, "main", "editor"))
				.andExpect(status().isConflict());
	}

	@Test
	void aTakenPublishedNameIsRefusedAndNothingChanges() throws Exception {
		String slug = publishedProject();
		editStart(slug, "main");
		mockMvc.perform(as(post("/v1/authoring/rename-dialogue").param("projectSlug", slug)
						.param("dialogueName", "main").param("newName", "other"), "editor"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/authoring/create-dialogue").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"main\"}"), "editor"))
				.andExpect(status().isCreated());
		mockMvc.perform(discard(slug, "other", "editor"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("another dialogue now has that name")));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "other"), "admin"))
				.andExpect(jsonPath(node("Start") + ".body", contains("Hello there.\n[[Next|Second]]")));
	}

	@Test
	void aRenamedTranslationLanguageIsRefusedAndNothingChanges() throws Exception {
		String slug = publishedProject();
		editStart(slug, "main");
		String languageId = readJson(as(get("/v1/project/get-project").param("projectSlug", slug), "admin"))
				.get("draftTranslationLanguages").get(0).get("id").asText();
		mockMvc.perform(as(post("/v1/project/update-draft").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"updateLanguages\":[{\"id\":\"" + languageId + "\"," +
								"\"translationLanguageName\":\"Dutch\",\"translationLanguageCode\":\"nl-NL\"}]}"),
						"admin"))
				.andExpect(status().isOk());
		mockMvc.perform(discard(slug, "main", "editor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Translation language 'nl' was renamed")));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "main"), "admin"))
				.andExpect(jsonPath(node("Start") + ".body", contains("Hello there.\n[[Next|Second]]")));
	}

	@Test
	void participantsCannotDiscardChanges() throws Exception {
		String slug = publishedProject();
		mockMvc.perform(discard(slug, "main", "participant"))
				.andExpect(status().isForbidden());
	}
}

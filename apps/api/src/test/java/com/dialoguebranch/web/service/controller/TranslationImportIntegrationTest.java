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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests importing translation files into a translation language of a project through {@code
 * /authoring/import-translations} (#340).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TranslationImportIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** Its translatable terms: Robin "Hello." and the user's "Fine.". */
	private static final String SCRIPT =
			"title: Start\nspeaker: Robin\n---\nHello.\n[[Fine.|Next]]\n===\n" +
			"title: Next\nspeaker: Robin\n---\nHello.\n===\n";

	private static final String WELCOME_NL =
			"{\"Robin\":{\"Hello.\":\"Hallo.\"},\"_user\":{\"Fine.\":\"Goed.\"}}";

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T as(T builder, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("translations-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "translations-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	/**
	 * Creates a project with the translation language "nl" and the dialogues "intro/welcome" and
	 * "intro/goodbye".
	 */
	private String newProject() throws Exception {
		String slug = "translations-" + UUID.randomUUID().toString().substring(0, 8);
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
						.file(file("welcome.dlb", SCRIPT)).file(file("goodbye.dlb", SCRIPT))
						.param("projectSlug", slug).param("folder", "intro"), "admin"))
				.andExpect(status().isOk());
		return slug;
	}

	private static MockMultipartFile file(String name, String content) {
		return file(name, content.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("files", name, "application/json", content);
	}

	private static MockMultipartHttpServletRequestBuilder importInto(String slug, String folder,
			MockMultipartFile... files) {
		MockMultipartHttpServletRequestBuilder request =
				multipart("/v1/authoring/import-translations");
		for (MockMultipartFile f : files) request.file(f);
		request.param("projectSlug", slug).param("language", "nl").param("folder", folder);
		return as(request, "editor");
	}

	private MockHttpServletRequestBuilder getTranslation(String slug, String dialogueName) {
		return as(get("/v1/authoring/get-translation").param("projectSlug", slug)
				.param("dialogueName", dialogueName).param("language", "nl"), "admin");
	}

	private static String dialogue(String name) {
		return "$[?(@.name == '" + name + "')]";
	}

	@Test
	void importsTranslationsAndReportsHowTheyCoverTheDialogue() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "/intro/",
						file("welcome.json", "﻿" + WELCOME_NL),
						file("goodbye.json", "{\"Robin\":{\"Hello.\":\"Hoi.\",\"Gone.\":\"Weg.\"," +
								"\"Fine.\":\"Goed.\"}}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].dialogueName").value("intro/welcome"))
				.andExpect(jsonPath("$[0].replaced").value(false))
				.andExpect(jsonPath("$[0].unmatchedEntries").value(0))
				.andExpect(jsonPath("$[0].untranslatedTerms").value(0))
				// "Gone." matches no term, and "Fine." is filed under Robin instead of the user.
				.andExpect(jsonPath("$[1].unmatchedEntries").value(2))
				.andExpect(jsonPath("$[1].untranslatedTerms").value(1));
		mockMvc.perform(getTranslation(slug, "intro/welcome"))
				.andExpect(jsonPath("$.content").value("{\n  \"Robin\" : {\n    \"Hello.\" : " +
						"\"Hallo.\"\n  },\n  \"_user\" : {\n    \"Fine.\" : \"Goed.\"\n  }\n}"));
		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("intro/welcome") + ".translatedInto[0]", contains("nl")))
				.andExpect(jsonPath(dialogue("intro/welcome") + ".isChanged", contains(true)));
	}

	@Test
	void entriesInContextGroupsCountUnderTheirSpeaker() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "intro", file("welcome.json",
						"{\"Robin\":{\"female_addressee\":{\"Hello.\":\"Hallo.\"}}," +
						"\"_user\":{\"Fine.\":\"Goed.\"}}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].unmatchedEntries").value(0))
				.andExpect(jsonPath("$[0].untranslatedTerms").value(0));
	}

	@Test
	void anExistingTranslationIsOnlyReplacedWhenAsked() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "intro", file("welcome.json", WELCOME_NL)))
				.andExpect(status().isOk());
		mockMvc.perform(importInto(slug, "intro",
						file("welcome.json", "{\"Robin\":{\"Hello.\":\"Hoi.\"}}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['welcome.json'][0]",
						containsString("already has a translation in Dutch")));
		mockMvc.perform(importInto(slug, "intro",
						file("welcome.json", "{\"Robin\":{\"Hello.\":\"Hoi.\"}}"))
						.param("replace", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].replaced").value(true))
				.andExpect(jsonPath("$[0].untranslatedTerms").value(1));
		mockMvc.perform(getTranslation(slug, "intro/welcome"))
				.andExpect(jsonPath("$.content").value("{\n  \"Robin\" : {\n    \"Hello.\" : " +
						"\"Hoi.\"\n  }\n}"));
	}

	@Test
	void oneRefusedFileRefusesTheWholeImport() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "intro",
						file("welcome.json", WELCOME_NL),
						file("missing.json", WELCOME_NL)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Nothing was imported: 1 of 2")))
				.andExpect(jsonPath("$.errors['missing.json'][0]",
						containsString("there is no dialogue 'intro/missing'")));
		mockMvc.perform(getTranslation(slug, "intro/welcome"))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
	}

	@Test
	void aDialoguePendingDeletionIsRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(post("/v1/authoring/delete-dialogue").param("projectSlug", slug)
						.param("dialogueName", "intro/welcome"), "admin"))
				.andExpect(status().isNoContent());
		mockMvc.perform(importInto(slug, "intro", file("welcome.json", WELCOME_NL)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['welcome.json'][0]", containsString("pending deletion")));
	}

	@Test
	void filesThatArentUsableTranslationsAreRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "intro",
						file("welcome.txt", WELCOME_NL),
						file("goodbye.json", "{\"Robin\":{\"Hello.\":\"\"}}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['welcome.txt'][0]").value("not a .json file"))
				.andExpect(jsonPath("$.errors['goodbye.json'][0]").value("contains no translations"));
		mockMvc.perform(importInto(slug, "intro",
						file("welcome.json", "{\"Hello.\":\"Hallo.\"}"),
						file("goodbye.json", "{\"Robin\": {")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['welcome.json'][0]", containsString("has no speaker")))
				.andExpect(jsonPath("$.errors['goodbye.json'][0]", containsString("not valid JSON")));
		mockMvc.perform(importInto(slug, "intro",
						file("welcome.json", new byte[] { (byte) 0xC3, (byte) 0x28 })))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['welcome.json'][0]").value("not a UTF-8 text file"));
	}

	@Test
	void aDialogueWhoseDraftDoesntParseIsImportedWithoutCoverage() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(file("broken.dlb", "title: Start\nspeaker: Robin\n---\nHello <<if>>.\n===\n"))
						.param("projectSlug", slug), "admin"))
				.andExpect(status().isOk());
		mockMvc.perform(importInto(slug, "", file("broken.json", WELCOME_NL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].dialogueName").value("broken"))
				.andExpect(jsonPath("$[0].unmatchedEntries").value(nullValue()))
				.andExpect(jsonPath("$[0].untranslatedTerms").value(nullValue()));
	}

	@Test
	void anUnknownLanguageIsNotFound() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "intro", file("welcome.json", WELCOME_NL))
						.param("language", "fr"))
				.andExpect(status().isNotFound());
	}

	@Test
	void aDialogueWithoutTranslationsListsNoLanguages() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("intro/goodbye") + ".translatedInto[*]", empty()));
	}

	@Test
	void participantsCannotImportTranslations() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(multipart("/v1/authoring/import-translations")
						.file(file("welcome.json", WELCOME_NL))
						.param("projectSlug", slug).param("language", "nl"), "participant"))
				.andExpect(status().isForbidden());
	}
}

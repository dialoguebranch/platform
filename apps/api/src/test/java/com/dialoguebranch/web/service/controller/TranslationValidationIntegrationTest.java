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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that {@code /update-translation} checks a translation's content before storing it, and
 * stores it re-serialised (#376).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TranslationValidationIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
			String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("translation-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "translation-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	/** Creates a project with a dialogue "main" and the translation language "nl". */
	private String newProject() throws Exception {
		String slug = "translation-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(post("/v1/authoring/create-dialogue").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"main\"}"), "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(post("/v1/project/add-translation-language").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"translationLanguageName\":\"Dutch\",\"translationLanguageCode\":\"nl\"}"),
						"admin"))
				.andExpect(status().isCreated());
		return slug;
	}

	private MockHttpServletRequestBuilder updateTranslation(String slug, String payloadJson) {
		return as(post("/v1/authoring/update-translation").param("projectSlug", slug)
				.param("dialogueName", "main").param("language", "nl")
				.contentType(MediaType.APPLICATION_JSON).content(payloadJson), "admin");
	}

	private static String payload(String content) {
		return "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
	}

	private MockHttpServletRequestBuilder getTranslation(String slug) {
		return as(get("/v1/authoring/get-translation").param("projectSlug", slug)
				.param("dialogueName", "main").param("language", "nl"), "admin");
	}

	@Test
	void validContentIsStoredReSerialisedWithoutEmptyTranslations() throws Exception {
		String slug = newProject();
		mockMvc.perform(updateTranslation(slug,
						payload("{\"Martin\":{\"Hello.\":\"Hallo.\",\"Bye.\":\"\"}}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content")
						.value("{\n  \"Martin\" : {\n    \"Hello.\" : \"Hallo.\"\n  }\n}"));
		mockMvc.perform(getTranslation(slug))
				.andExpect(jsonPath("$.content")
						.value("{\n  \"Martin\" : {\n    \"Hello.\" : \"Hallo.\"\n  }\n}"));
	}

	@Test
	void aTranslationWithoutEntriesIsStored() throws Exception {
		String slug = newProject();
		mockMvc.perform(updateTranslation(slug, payload("{\"Martin\":{\"Hello.\":\"\"}}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("{ }"));
	}

	@Test
	void invalidJsonIsRefusedAndNothingIsStored() throws Exception {
		String slug = newProject();
		mockMvc.perform(updateTranslation(slug, payload("{\"Martin\": {")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("'main' contains errors")))
				.andExpect(jsonPath("$.errors['main'][0]", containsString("not valid JSON")));
		mockMvc.perform(getTranslation(slug))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
	}

	@Test
	void anEntryWithoutASpeakerIsRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(updateTranslation(slug, payload("{\"Hello.\":\"Hallo.\"}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main'][0]", containsString("has no speaker")));
	}

	@Test
	void contentInTheWrongShapeIsRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(updateTranslation(slug, payload("[\"Hallo.\"]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main'][0]", containsString("not a JSON object")));
		mockMvc.perform(updateTranslation(slug, payload("{\"Martin\":{\"Hello [[a|b]].\":\"Hallo.\"}}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main'][0]", containsString("Failed to parse translation key")));
	}

	@Test
	void missingContentIsRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(updateTranslation(slug, "{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main'][0]", containsString("the translation is empty")));
	}

	@Test
	void anUnknownDialogueIsStillReportedAsNotFound() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(post("/v1/authoring/update-translation").param("projectSlug", slug)
						.param("dialogueName", "missing").param("language", "nl")
						.contentType(MediaType.APPLICATION_JSON).content(payload("[")), "admin"))
				.andExpect(status().isNotFound());
	}
}

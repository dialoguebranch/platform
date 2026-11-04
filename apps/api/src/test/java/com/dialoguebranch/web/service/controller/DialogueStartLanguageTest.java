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
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.DraftProjectService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.PublishService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /dialogue/start} without a {@code language} starts the dialogue in the project's source
 * language (#333): Studio's participant view relies on this, since a participant can't read a
 * project's languages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DialogueStartLanguageTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	Application application;

	@Autowired
	ProjectService projectService;

	@Autowired
	DraftProjectService draftProjectService;

	@Autowired
	DraftDialogueService draftDialogueService;

	@Autowired
	PublishService publishService;

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T asParticipant(T builder,
			String subject) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of("participant"))));
		return builder.with(jwt().jwt(claims));
	}

	/**
	 * Starts {@code dialogueName} with the given language parameter (or none, if {@code null}),
	 * and returns the language the session was logged in.
	 */
	private String startAndReadLoggedLanguage(String projectSlug, String dialogueName,
			String language) throws Exception {
		String subject = "language-" + UUID.randomUUID();
		String sessionId = "session-" + UUID.randomUUID();
		MockHttpServletRequestBuilder start = post("/v1/dialogue/start")
				.param("projectSlug", projectSlug).param("dialogueName", dialogueName)
				.param("timeZone", "UTC").param("sessionId", sessionId);
		if (language != null) start.param("language", language);
		mockMvc.perform(asParticipant(start, subject)).andExpect(status().isOk());

		String body = mockMvc.perform(asParticipant(get("/v1/log/get-session")
						.param("sessionId", sessionId), subject))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].dialogueName").value(dialogueName))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$[0].language");
	}

	@Test
	void withoutALanguageTheDialogueStartsInTheSourceLanguage() throws Exception {
		assertEquals("en", startAndReadLoggedLanguage("default-test", "menu", null));
	}

	@Test
	void anEmptyLanguageAlsoMeansTheSourceLanguage() throws Exception {
		assertEquals("en", startAndReadLoggedLanguage("default-test", "menu", ""));
		assertEquals("en", startAndReadLoggedLanguage("default-test", "menu", "  "));
	}

	@Test
	void anExplicitLanguageIsStillUsed() throws Exception {
		assertEquals("nl-NL", startAndReadLoggedLanguage("default-test", "menu", "nl-NL"));
	}

	@Test
	void theFallbackIsTheSourceLanguageEvenWhenItIsNotEnglish() throws Exception {
		String slug = "language-fallback-" + UUID.randomUUID();
		DBProject project = projectService.createProject(slug, "Language Fallback", "",
				"pt-PT", "Portuguese (Portugal)");
		DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "welcome");
		draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Agent", "Olá.");
		DBDraftTranslationLanguage english = draftProjectService.addDraftLanguage(project,
				"English", "en");
		draftDialogueService.createOrUpdateTranslation(dialogue, english, "{}");
		PublishService.PublishResult publish = publishService.publish(project, null);
		assertTrue(publish.isSuccess(), "publish failed: " + publish.getErrors());

		assertEquals("pt-PT", application.getApplicationManager().getSourceLanguage(slug));
		assertEquals("pt-PT", startAndReadLoggedLanguage(slug, "welcome", null));
		assertEquals("en", startAndReadLoggedLanguage(slug, "welcome", "en"));
	}

	@Test
	void theSourceLanguageOfAnUnknownProjectIsNull() {
		assertNull(application.getApplicationManager().getSourceLanguage("no-such-project"));
	}
}

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
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A dialogue started in a language the project doesn't have still runs, in the project's source
 * language: a deliberate robustness fallback, so a client whose language setting doesn't exactly
 * match the project's still gets a dialogue rather than an error. The mismatch is logged as a
 * warning, so it doesn't go unnoticed (#335).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class DialogueLanguageFallbackTest {

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

	/** Starts a dialogue (expecting 200) and returns the language its session was logged in. */
	private String startAndReadLoggedLanguage(String projectSlug, String dialogueName,
			String language) throws Exception {
		String subject = "fallback-" + UUID.randomUUID();
		String sessionId = "session-" + UUID.randomUUID();
		mockMvc.perform(asParticipant(post("/v1/dialogue/start").param("projectSlug", projectSlug)
						.param("dialogueName", dialogueName).param("language", language)
						.param("timeZone", "UTC").param("sessionId", sessionId), subject))
				.andExpect(status().isOk());
		String body = mockMvc.perform(asParticipant(get("/v1/log/get-session")
						.param("sessionId", sessionId), subject))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$[0].language");
	}

	@Test
	void anUnknownLanguageRunsTheSourceLanguageAndIsLogged(CapturedOutput output) throws Exception {
		assertEquals("en", startAndReadLoggedLanguage("default-test", "menu", "xx"));
		assertTrue(output.getOut().contains("in language 'xx', which project 'default-test' doesn't have"),
				"expected a warning about the unknown language");
		assertTrue(output.getOut().contains("using its source language 'en'"));
	}

	@Test
	void aLanguageIsMatchedExactlySoAPrefixAlsoFallsBack(CapturedOutput output) throws Exception {
		// The project declares nl-NL, not nl: the source language runs, and it's logged.
		assertEquals("en", startAndReadLoggedLanguage("default-test", "menu", "nl"));
		assertTrue(output.getOut().contains("in language 'nl', which project 'default-test' doesn't have"));
	}

	@Test
	void aSupportedLanguageRunsAsRequestedWithoutAWarning(CapturedOutput output) throws Exception {
		assertEquals("nl-NL", startAndReadLoggedLanguage("default-test", "menu", "nl-NL"));
		assertFalse(output.getOut().contains("which project 'default-test' doesn't have"));
	}

	@Test
	void aDeclaredLanguageWithoutTranslationsYetIsNotWarnedAbout(CapturedOutput output)
			throws Exception {
		String slug = "fallback-untranslated-" + UUID.randomUUID();
		DBProject project = projectService.createProject(slug, "Untranslated", "", "en",
				"English");
		DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "welcome");
		draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Agent", "Hello.");
		// Registered, but no dialogue has a German translation yet.
		draftProjectService.addDraftLanguage(project, "German", "de");
		PublishService.PublishResult publish = publishService.publish(project, null);
		assertTrue(publish.isSuccess(), "publish failed: " + publish.getErrors());

		// Runs the source content (there's no German yet), but "de" is a project language.
		assertEquals("en", startAndReadLoggedLanguage(slug, "welcome", "de"));
		assertFalse(output.getOut().contains("which project '" + slug + "' doesn't have"));
	}

	@Test
	void theApplicationManagerKnowsEachLoadedProjectsLanguages() {
		var manager = application.getApplicationManager();
		assertEquals(List.of("en", "nl-NL", "pt-PT"), List.copyOf(manager.getLanguages("default-test")));
		assertTrue(manager.isSupportedLanguage("default-test", "pt-PT"));
		assertFalse(manager.isSupportedLanguage("default-test", "de"));
		// An unknown project isn't judged here: dialogue lookup reports that more specifically.
		assertTrue(manager.isSupportedLanguage("no-such-project", "xx"));
		assertTrue(manager.getLanguages("no-such-project").isEmpty());
	}
}

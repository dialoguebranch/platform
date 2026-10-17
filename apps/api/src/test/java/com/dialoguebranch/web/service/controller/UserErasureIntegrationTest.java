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

import com.dialoguebranch.web.service.repository.DBLoggedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
import com.dialoguebranch.web.service.repository.DBVariableRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBUser;
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

import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests erasing a user's data through {@code /users/erase-user} (#320): their variables, variable
 * history and dialogue logs in every project, and the user itself, while other users' data and
 * the versions the user published stay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserErasureIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** Sets a variable (with history) and leaves the dialogue ongoing at Start. */
	private static final String SCRIPT = "title: Start\nspeaker: Robin\n---\n<<set $seen = 1>>\n"
			+ "Hello.\n[[Next|Second]]\n===\ntitle: Second\nspeaker: Robin\n---\nBye.\n===\n";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DBProjectRepository projectRepository;

	@Autowired
	DBProjectVersionRepository projectVersionRepository;

	@Autowired
	DBUserRepository userRepository;

	@Autowired
	DBVariableRepository variableRepository;

	@Autowired
	DBVariableHistoryRepository variableHistoryRepository;

	@Autowired
	DBLoggedDialogueRepository loggedDialogueRepository;

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
			String role, String subject) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
		return as(builder, "admin", "erasure-admin");
	}

	private static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().substring(0, 8);
	}

	/** Creates and publishes, as the given admin, a project with the dialogue "main". */
	private void publishProject(String slug, String admin) throws Exception {
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"),
						"admin", admin))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								SCRIPT.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true"), "admin", admin))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug),
						"admin", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	/** Gives the participant a variable, variable history and an ongoing log in the project. */
	private void startDialogue(String slug, String participant) throws Exception {
		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", slug)
						.param("dialogueName", "main").param("timeZone", "Europe/Amsterdam"),
						"participant", participant))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder erase(String subject) {
		return asAdmin(post("/v1/users/erase-user").param("subject", subject));
	}

	private UUID idOf(String subject) {
		return userRepository.findByIssuerAndSubject(ISSUER, subject).orElseThrow().getId();
	}

	private long variablesOf(UUID userId) {
		return variableRepository.findAll().stream()
				.filter(v -> v.getUser().getId().equals(userId)).count();
	}

	private long historyOf(UUID userId) {
		return variableHistoryRepository.findAll().stream()
				.filter(h -> h.getUser().getId().equals(userId)).count();
	}

	private long logsOf(UUID userId) {
		return loggedDialogueRepository.findAll().stream()
				.filter(l -> l.getUser().getId().equals(userId)).count();
	}

	@Test
	void erasingRemovesTheUsersDataInEveryProjectAndTheUserItself() throws Exception {
		String first = unique("erasure-");
		String second = unique("erasure-");
		String participant = unique("erasure-p-");
		String other = unique("erasure-o-");
		publishProject(first, "erasure-admin");
		publishProject(second, "erasure-admin");
		startDialogue(first, participant);
		startDialogue(second, participant);
		startDialogue(first, other);
		UUID participantId = idOf(participant);
		UUID otherId = idOf(other);
		long variables = variablesOf(participantId);
		long history = historyOf(participantId);
		assertTrue(variables >= 2);
		assertTrue(history >= 2);
		assertEquals(2, logsOf(participantId));

		mockMvc.perform(erase(participant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.variables").value((int) variables))
				.andExpect(jsonPath("$.variableHistory").value((int) history))
				.andExpect(jsonPath("$.dialogueLogs").value(2));

		assertEquals(0, variablesOf(participantId));
		assertEquals(0, historyOf(participantId));
		assertEquals(0, logsOf(participantId));
		assertTrue(userRepository.findByIssuerAndSubject(ISSUER, participant).isEmpty());
		// Other users keep theirs.
		assertEquals(1, logsOf(otherId));
		assertTrue(variablesOf(otherId) >= 1);

		// The participant's in-memory session is gone too: they come back as a new user, with no
		// variables and no ongoing dialogue.
		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", first).param("timeZone", "UTC"),
						"participant", participant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'seen')]", empty()));
		mockMvc.perform(as(get("/v1/dialogue/get-ongoing").param("projectSlug", first).param("timeZone", "UTC"),
						"participant", participant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.value").doesNotExist());
	}

	@Test
	void erasingAPublisherKeepsTheVersionsTheyPublished() throws Exception {
		String slug = unique("erasure-");
		String publisher = unique("erasure-pub-");
		publishProject(slug, "erasure-admin");
		DBUser user = userRepository.save(new DBUser(ISSUER, publisher, publisher));
		DBProject project = projectRepository.findBySlug(slug).orElseThrow();
		DBProjectVersion version =
				projectVersionRepository.findByProjectOrderByVersionNumberDesc(project).get(0);
		version.setPublishedBy(user);
		projectVersionRepository.save(version);

		mockMvc.perform(erase(publisher)).andExpect(status().isOk());

		DBProjectVersion kept = projectVersionRepository.findById(version.getId()).orElseThrow();
		assertNull(kept.getPublishedBy());
		assertTrue(userRepository.findByIssuerAndSubject(ISSUER, publisher).isEmpty());
	}

	@Test
	void erasingIsScopedToTheCallersRealm() throws Exception {
		String subject = unique("erasure-r-");
		userRepository.save(new DBUser("https://keycloak.test/realms/other", subject, subject));

		mockMvc.perform(erase(subject)).andExpect(status().isNotFound());

		assertTrue(userRepository.findByIssuerAndSubject("https://keycloak.test/realms/other", subject)
				.isPresent());
	}

	@Test
	void erasingAnUnknownUserIsNotFound() throws Exception {
		mockMvc.perform(erase(unique("erasure-unknown-"))).andExpect(status().isNotFound());
	}

	@Test
	void onlyAdminsCanEraseUsers() throws Exception {
		String participant = unique("erasure-p-");
		String slug = unique("erasure-");
		publishProject(slug, "erasure-admin");
		startDialogue(slug, participant);
		for (String role : List.of("participant", "editor", "supervisor")) {
			mockMvc.perform(as(post("/v1/users/erase-user").param("subject", participant),
							role, "erasure-" + role))
					.andExpect(status().isForbidden());
		}
		assertEquals(1, logsOf(idOf(participant)));
	}
}

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
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
import com.dialoguebranch.web.service.repository.DBVariableRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests deleting a project through {@code /project/delete-project} (#315, #316, #317): it removes
 * the dialogues but keeps the users' data unless asked otherwise, under a renamed slug, so a new
 * project with the same slug never inherits any of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectDeletionIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** Sets a variable (with history) and leaves the dialogue ongoing at Start. */
	private static final String SCRIPT = "title: Start\nspeaker: Robin\n---\n<<set $seen = 1>>\n"
			+ "Hello.\n[[Next|Second]]\n===\ntitle: Second\nspeaker: Robin\n---\nBye.\n===\n";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DBProjectRepository projectRepository;

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
		return as(builder, "admin", "deletion-admin");
	}

	/** Creates and publishes a project with the dialogue "main". */
	private void publishProject(String slug) throws Exception {
		mockMvc.perform(asAdmin(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}")))
				.andExpect(status().isCreated());
		mockMvc.perform(asAdmin(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								SCRIPT.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true")))
				.andExpect(status().isOk());
		mockMvc.perform(asAdmin(post("/v1/publish/create-version").param("projectSlug", slug)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	/** A project in which a participant has a variable, variable history and an ongoing log. */
	private DBProject projectWithUserData(String slug, String participant) throws Exception {
		publishProject(slug);
		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", slug)
						.param("dialogueName", "main").param("timeZone", "UTC"), "participant", participant))
				.andExpect(status().isOk());
		DBProject project = projectRepository.findBySlug(slug).orElseThrow();
		assertFalse(variableRepository.findAll().stream()
				.noneMatch(v -> v.getProject().getId().equals(project.getId())));
		assertFalse(loggedDialogueRepository.findAll().stream()
				.noneMatch(l -> l.getProjectSlug().equals(slug)));
		return project;
	}

	private MockHttpServletRequestBuilder delete(String slug, boolean variables, boolean logs) {
		return asAdmin(post("/v1/project/delete-project").param("projectSlug", slug)
				.param("deleteVariables", String.valueOf(variables))
				.param("deleteLogs", String.valueOf(logs)));
	}

	private long variablesOf(UUID projectId) {
		return variableRepository.findAll().stream()
				.filter(v -> v.getProject().getId().equals(projectId)).count();
	}

	private long historyOf(UUID projectId) {
		return variableHistoryRepository.findAll().stream()
				.filter(h -> h.getProject().getId().equals(projectId)).count();
	}

	private long logsOf(String slug) {
		return loggedDialogueRepository.findAll().stream()
				.filter(l -> l.getProjectSlug().equals(slug)).count();
	}

	@Test
	void deletingKeepsUsersDataUnderARenamedSlugAndFreesTheSlug() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		String participant = "deletion-p-" + UUID.randomUUID().toString().substring(0, 8);
		DBProject project = projectWithUserData(slug, participant);
		long variables = variablesOf(project.getId());
		long history = historyOf(project.getId());

		mockMvc.perform(delete(slug, false, false)).andExpect(status().isNoContent());

		DBProject deleted = projectRepository.findById(project.getId()).orElseThrow();
		assertTrue(deleted.getIsDeleted());
		assertEquals(slug + "__deleted", deleted.getSlug());
		assertEquals(variables, variablesOf(project.getId()));
		assertEquals(history, historyOf(project.getId()));
		assertEquals(0, logsOf(slug));
		assertEquals(1, logsOf(slug + "__deleted"));

		// Not found, not listed, not playable anymore.
		mockMvc.perform(asAdmin(get("/v1/project/get-project").param("projectSlug", slug)))
				.andExpect(status().isNotFound());
		mockMvc.perform(asAdmin(get("/v1/project/get-project").param("projectSlug", slug + "__deleted")))
				.andExpect(status().isNotFound());
		mockMvc.perform(asAdmin(get("/v1/project/list-projects")))
				.andExpect(jsonPath("$[?(@.slug == '" + slug + "__deleted')]", empty()));
		mockMvc.perform(as(get("/v1/dialogue/list-dialogues").param("projectSlug", slug),
						"participant", participant))
				.andExpect(status().is4xxClientError());

		// A new project with the same slug inherits nothing: no variables (the participant's
		// session, with its cached store, is still active), no ongoing dialogue.
		publishProject(slug);
		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", slug).param("timeZone", "UTC"),
						"participant", participant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'seen')]", empty()));
		mockMvc.perform(as(get("/v1/dialogue/get-ongoing").param("projectSlug", slug).param("timeZone", "UTC"),
						"participant", participant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.value").value(nullValue()));
	}

	@Test
	void deletingTwiceKeepsBothUnderNumberedSlugs() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		publishProject(slug);
		mockMvc.perform(delete(slug, false, false)).andExpect(status().isNoContent());
		publishProject(slug);
		mockMvc.perform(delete(slug, false, false)).andExpect(status().isNoContent());
		assertTrue(projectRepository.findBySlug(slug + "__deleted").isPresent());
		assertTrue(projectRepository.findBySlug(slug + "__deleted-2").isPresent());
		assertTrue(projectRepository.findBySlug(slug).isEmpty());
	}

	@Test
	void deletingWithAllUsersDataRemovesTheProjectForGood() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		DBProject project = projectWithUserData(slug, "deletion-p-" + UUID.randomUUID().toString().substring(0, 8));

		mockMvc.perform(delete(slug, true, true)).andExpect(status().isNoContent());

		assertTrue(projectRepository.findById(project.getId()).isEmpty());
		assertEquals(0, variablesOf(project.getId()));
		assertEquals(0, historyOf(project.getId()));
		assertEquals(0, logsOf(slug));
		assertTrue(projectRepository.findBySlug(slug + "__deleted").isEmpty());
	}

	@Test
	void deletingWithOnlyTheVariablesKeepsTheLogs() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		DBProject project = projectWithUserData(slug, "deletion-p-" + UUID.randomUUID().toString().substring(0, 8));

		mockMvc.perform(delete(slug, true, false)).andExpect(status().isNoContent());

		assertTrue(projectRepository.findById(project.getId()).orElseThrow().getIsDeleted());
		assertEquals(0, variablesOf(project.getId()));
		assertEquals(0, historyOf(project.getId()));
		assertEquals(1, logsOf(slug + "__deleted"));
	}

	@Test
	void deletingWithOnlyTheLogsKeepsTheVariables() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		DBProject project = projectWithUserData(slug, "deletion-p-" + UUID.randomUUID().toString().substring(0, 8));
		long variables = variablesOf(project.getId());

		mockMvc.perform(delete(slug, false, true)).andExpect(status().isNoContent());

		DBProject deleted = projectRepository.findById(project.getId()).orElseThrow();
		assertTrue(deleted.getIsDeleted());
		assertEquals(variables, variablesOf(project.getId()));
		assertEquals(0, logsOf(slug));
		assertEquals(0, logsOf(deleted.getSlug()));
	}

	@Test
	void editorsCannotDeleteProjects() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		publishProject(slug);
		mockMvc.perform(as(post("/v1/project/delete-project").param("projectSlug", slug), "editor", "deletion-editor"))
				.andExpect(status().isForbidden());
	}

	// ---- Deleted projects (#403) ----

	@Test
	void deletedProjectsAreListedWithHowMuchUserDataTheyKeep() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		projectWithUserData(slug, "deletion-p-" + UUID.randomUUID().toString().substring(0, 8));
		mockMvc.perform(delete(slug, false, false)).andExpect(status().isNoContent());

		String entry = "$[?(@.slug == '" + slug + "__deleted')]";
		mockMvc.perform(asAdmin(get("/v1/project/list-deleted-projects")))
				.andExpect(status().isOk())
				.andExpect(jsonPath(entry, hasSize(1)))
				.andExpect(jsonPath(entry + ".displayName", contains("T")))
				.andExpect(jsonPath(entry + ".usersWithVariables", contains(1)))
				.andExpect(jsonPath(entry + ".dialogueLogs", contains(1)))
				.andExpect(jsonPath(entry + ".deletedAt", contains(org.hamcrest.Matchers.startsWith("20"))));
		mockMvc.perform(asAdmin(get("/v1/project/list-deleted-projects")))
				.andExpect(jsonPath("$[?(@.slug == '" + slug + "')]", empty()));
	}

	@Test
	void purgingADeletedProjectRemovesItAndAllItsUsersData() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		DBProject project = projectWithUserData(slug, "deletion-p-" + UUID.randomUUID().toString().substring(0, 8));
		mockMvc.perform(delete(slug, false, false)).andExpect(status().isNoContent());

		mockMvc.perform(asAdmin(post("/v1/project/purge-deleted-project").param("projectSlug", slug + "__deleted")))
				.andExpect(status().isNoContent());

		assertTrue(projectRepository.findById(project.getId()).isEmpty());
		assertEquals(0, variablesOf(project.getId()));
		assertEquals(0, historyOf(project.getId()));
		assertEquals(0, logsOf(slug + "__deleted"));
		mockMvc.perform(asAdmin(get("/v1/project/list-deleted-projects")))
				.andExpect(jsonPath("$[?(@.slug == '" + slug + "__deleted')]", empty()));
	}

	@Test
	void anActiveProjectCannotBePurged() throws Exception {
		String slug = "deletion-" + UUID.randomUUID().toString().substring(0, 8);
		publishProject(slug);
		mockMvc.perform(asAdmin(post("/v1/project/purge-deleted-project").param("projectSlug", slug)))
				.andExpect(status().isNotFound());
		assertTrue(projectRepository.findBySlug(slug).isPresent());
	}

	@Test
	void editorsCannotListOrPurgeDeletedProjects() throws Exception {
		mockMvc.perform(as(get("/v1/project/list-deleted-projects"), "editor", "deletion-editor"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/v1/project/purge-deleted-project").param("projectSlug", "x__deleted"),
						"editor", "deletion-editor"))
				.andExpect(status().isForbidden());
	}
}

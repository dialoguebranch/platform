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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A project's speakers in the web service (#394): kept as a draft through {@code update-draft},
 * snapshotted by publishing, carried by export and import, restored by discarding all draft
 * changes, and gone with a deleted project.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectSpeakersIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	private static final String SPEAKERS = """
			[{"name": "Martin McOwl", "gender": "male", "default": true},
			 {"name": "Anna", "gender": "female"},
			 {"name": "Narrator"}]""";

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T asAdmin(T builder) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("speakers-admin")
				.claim("iss", ISSUER)
				.claim("preferred_username", "speakers-admin")
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of("admin"))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	private String newProject() throws Exception {
		String slug = "speakers-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(asAdmin(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"Speakers\"," +
								"\"description\":\"\",\"sourceLanguageCode\":\"en\"," +
								"\"sourceLanguageName\":\"English\"}")))
				.andExpect(status().isCreated());
		mockMvc.perform(asAdmin(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								"title: Start\nspeaker: Anna\n---\nHello.\n===\n"
										.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true")))
				.andExpect(status().isOk());
		return slug;
	}

	private ResultActions updateDraft(String slug, String speakersJson) throws Exception {
		String speakers = speakersJson == null ? "" : ",\"speakers\":" + speakersJson;
		return mockMvc.perform(asAdmin(post("/v1/project/update-draft").param("projectSlug", slug)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"Speakers\",\"description\":\"\"" + speakers + "}")));
	}

	private ResultActions getProject(String slug) throws Exception {
		return mockMvc.perform(asAdmin(get("/v1/project/get-project").param("projectSlug", slug)));
	}

	private void publish(String slug) throws Exception {
		mockMvc.perform(asAdmin(post("/v1/publish/create-version").param("projectSlug", slug)))
				.andExpect(status().isOk());
	}

	@Test
	void theDraftSpeakersAreSetInTheirOrderWithGenderAndDefault() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());

		getProject(slug)
				.andExpect(jsonPath("$.draftSpeakers[*].name",
						contains("Martin McOwl", "Anna", "Narrator")))
				.andExpect(jsonPath("$.draftSpeakers[*].gender",
						contains("male", "female", "undefined")))
				.andExpect(jsonPath("$.draftSpeakers[*].default", contains(true, false, false)));
	}

	@Test
	void anUpdateChangesKeepsAddsAndRemovesSpeakers() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());

		updateDraft(slug, """
				[{"name": "Narrator", "default": true},
				 {"name": "Anna", "gender": "undefined"},
				 {"name": "Bob", "gender": "male"}]""").andExpect(status().isOk());

		getProject(slug)
				.andExpect(jsonPath("$.draftSpeakers[*].name", contains("Narrator", "Anna", "Bob")))
				.andExpect(jsonPath("$.draftSpeakers[*].gender",
						contains("undefined", "undefined", "male")))
				.andExpect(jsonPath("$.draftSpeakers[*].default", contains(true, false, false)));
	}

	@Test
	void anUpdateWithoutSpeakersLeavesThem() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());
		updateDraft(slug, null).andExpect(status().isOk());
		getProject(slug).andExpect(jsonPath("$.draftSpeakers", hasSize(3)));
	}

	@Test
	void invalidSpeakersAreRefusedAndNothingChanges() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());

		for (String invalid : List.of(
				"[{\"name\": \"Anna\"}, {\"name\": \"Anna\"}]",
				"[{\"name\": \"_user\"}]",
				"[{\"name\": \"\"}]",
				"[{\"name\": \"Anna\", \"gender\": \"other\"}]",
				"[{\"name\": \"A\", \"default\": true}, {\"name\": \"B\", \"default\": true}]")) {
			updateDraft(slug, invalid)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors[0].field").value("speakers"));
		}
		getProject(slug).andExpect(jsonPath("$.draftSpeakers[*].name",
				contains("Martin McOwl", "Anna", "Narrator")));
	}

	@Test
	void publishingSnapshotsTheDraftSpeakers() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());
		publish(slug);

		// A later draft change doesn't touch the published version
		updateDraft(slug, "[{\"name\": \"Bob\"}]").andExpect(status().isOk());

		getProject(slug)
				.andExpect(jsonPath("$.draftSpeakers[*].name", contains("Bob")))
				.andExpect(jsonPath("$.latestVersion.speakers[*].name",
						contains("Martin McOwl", "Anna", "Narrator")))
				.andExpect(jsonPath("$.latestVersion.speakers[0].default").value(true));
	}

	@Test
	void discardingAllDraftChangesRestoresThePublishedSpeakers() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());
		publish(slug);
		updateDraft(slug, "[{\"name\": \"Bob\"}]").andExpect(status().isOk());

		mockMvc.perform(asAdmin(post("/v1/project/discard-draft-changes")
						.param("projectSlug", slug)))
				.andExpect(status().isOk());

		getProject(slug).andExpect(jsonPath("$.draftSpeakers[*].name",
				contains("Martin McOwl", "Anna", "Narrator")));
	}

	@Test
	void exportAndImportCarryTheSpeakers() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());
		publish(slug);

		byte[] archive = mockMvc.perform(asAdmin(get("/v1/project/export-project")
						.param("projectSlug", slug)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		// The slug is in the archive, so the original has to make way for the import
		mockMvc.perform(asAdmin(post("/v1/project/delete-project").param("projectSlug", slug)
						.param("deleteVariables", "true").param("deleteLogs", "true")))
				.andExpect(status().isNoContent());

		mockMvc.perform(asAdmin(multipart("/v1/project/import-project")
						.file(new MockMultipartFile("file", "speakers.zip", "application/zip",
								archive))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.draftSpeakers[*].name",
						contains("Martin McOwl", "Anna", "Narrator")))
				.andExpect(jsonPath("$.latestVersion.speakers[*].gender",
						contains("male", "female", "undefined")))
				.andExpect(jsonPath("$.latestVersion.speakers[*].default",
						contains(true, false, false)));
	}

	@Test
	void aProjectWithSpeakersCanBeDeletedAndPurged() throws Exception {
		String slug = newProject();
		updateDraft(slug, SPEAKERS).andExpect(status().isOk());
		publish(slug);

		// Kept as a deleted shell first, then removed for good: neither is held back by the
		// speakers of the draft or of the published version
		mockMvc.perform(asAdmin(post("/v1/project/delete-project").param("projectSlug", slug)))
				.andExpect(status().isNoContent());
		mockMvc.perform(asAdmin(post("/v1/project/purge-deleted-project")
						.param("projectSlug", slug + "__deleted")))
				.andExpect(status().isNoContent());
		getProject(slug).andExpect(status().isNotFound());
	}

	private ResultActions verify(String slug) throws Exception {
		return mockMvc.perform(asAdmin(post("/v1/publish/verify").param("projectSlug", slug)));
	}

	@Test
	void verifyWarnsAboutANodeWhoseSpeakerIsUndefinedWithoutFailing() throws Exception {
		String slug = newProject();
		updateDraft(slug, "[{\"name\":\"Robin\"}]").andExpect(status().isOk());

		verify(slug)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.warnings.*[*]", hasItem(
						containsString("\"Anna\", which isn't one of the project's speakers"))));
		publish(slug);
	}

	@Test
	void verifyHasNoSpeakerWarningsWithoutSpeakers() throws Exception {
		String slug = newProject();

		verify(slug)
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.warnings").isEmpty());
	}

	// -------------------- Renaming a speaker (#398) -------------------- //

	/** A project whose speaker Anna speaks in two dialogues and has a Dutch translation. */
	private String projectWithAnna() throws Exception {
		String slug = newProject();
		mockMvc.perform(asAdmin(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "second.dlb", "text/plain",
								("title: Start\nspeaker: Anna\n---\nHi.\n[[Next|Next]]\n===\n"
										+ "title: Next\nspeaker: Narrator\n---\nOn we go.\n===\n")
										.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true")))
				.andExpect(status().isOk());
		mockMvc.perform(asAdmin(post("/v1/project/update-draft").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"addLanguages\":[{\"translationLanguageName\":\"Dutch\","
								+ "\"translationLanguageCode\":\"nl\"}],\"speakers\":" + SPEAKERS + "}")))
				.andExpect(status().isOk());
		mockMvc.perform(asAdmin(post("/v1/authoring/update-translation").param("projectSlug", slug)
						.param("dialogueName", "main").param("language", "nl")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"{\\\"Anna\\\":{\\\"Hello.\\\":\\\"Hallo.\\\"}}\"}")))
				.andExpect(status().isOk());
		return slug;
	}

	private String nodeSpeakers(String slug, String dialogue) throws Exception {
		return mockMvc.perform(asAdmin(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", dialogue)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String translation(String slug) throws Exception {
		return mockMvc.perform(asAdmin(get("/v1/authoring/get-translation").param("projectSlug", slug)
						.param("dialogueName", "main").param("language", "nl")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void findSpeakerReferencesCountsNodesDialoguesAndTranslations() throws Exception {
		String slug = projectWithAnna();
		mockMvc.perform(asAdmin(get("/v1/project/find-speaker-references")
						.param("projectSlug", slug).param("speaker", "Anna")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nodes").value(2))
				.andExpect(jsonPath("$.dialogues").value(2))
				.andExpect(jsonPath("$.translations").value(1));
	}

	@Test
	void renamingASpeakerRenamesItInTheNodesAndTranslations() throws Exception {
		String slug = projectWithAnna();
		updateDraft(slug, """
				[{"name": "Martin McOwl", "gender": "male", "default": true},
				 {"name": "Annie", "previousName": "Anna", "gender": "female"},
				 {"name": "Narrator"}]""").andExpect(status().isOk());

		getProject(slug)
				.andExpect(jsonPath("$.draftSpeakers[*].name",
						contains("Martin McOwl", "Annie", "Narrator")))
				.andExpect(jsonPath("$.draftSpeakers[1].gender").value("female"));
		assertTrue(nodeSpeakers(slug, "main").contains("\"value\":\"Annie\""));
		assertTrue(nodeSpeakers(slug, "second").contains("\"value\":\"Annie\""));
		assertFalse(nodeSpeakers(slug, "second").contains("\"value\":\"Anna\""));
		assertTrue(translation(slug).contains("Annie"));
		assertFalse(translation(slug).contains("Anna\\\""));
	}

	@Test
	void swappingTwoSpeakersNamesWorks() throws Exception {
		String slug = projectWithAnna();
		updateDraft(slug, """
				[{"name": "Martin McOwl", "gender": "male", "default": true},
				 {"name": "Narrator", "previousName": "Anna", "gender": "female"},
				 {"name": "Anna", "previousName": "Narrator"}]""").andExpect(status().isOk());

		getProject(slug).andExpect(jsonPath("$.draftSpeakers[*].name",
				contains("Martin McOwl", "Narrator", "Anna")));
		String second = nodeSpeakers(slug, "second");
		assertTrue(second.indexOf("\"value\":\"Narrator\"") < second.indexOf("\"value\":\"Anna\""),
				"The first node's speaker should now be Narrator, the second one's Anna: " + second);
	}

	@Test
	void renamingASpeakerTheProjectDoesntHaveIsRefused() throws Exception {
		String slug = projectWithAnna();
		updateDraft(slug, "[{\"name\": \"Annie\", \"previousName\": \"Ann\"}]")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("speakers"));
		assertTrue(nodeSpeakers(slug, "main").contains("\"value\":\"Anna\""));
	}
}

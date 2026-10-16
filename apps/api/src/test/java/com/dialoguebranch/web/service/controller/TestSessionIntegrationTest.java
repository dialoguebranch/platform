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
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that test sessions ({@code /test-session/*}) run against the tester's own copy of the
 * variables (#319): a test never changes the user's real variables, variable history or dialogue
 * logs; a tester's sessions in one project share one copy, which can be read, changed and reset;
 * and testing as another user only reads their variables, to seed the copy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestSessionIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** "main" sets $mark and links on; "check" shows $mark. */
	private static final String MAIN = "title: Start\nspeaker: Robin\n---\n<<set $mark = 7>>\n"
			+ "Hello.\n[[Next|Second]]\n===\ntitle: Second\nspeaker: Robin\n---\n"
			+ "<<set $second = 1>>\nBye.\n===\n";
	private static final String CHECK = "title: Start\nspeaker: Robin\n---\nMark is $mark.\n===\n";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DBUserRepository userRepository;

	@Autowired
	DBVariableHistoryRepository variableHistoryRepository;

	@Autowired
	DBLoggedDialogueRepository loggedDialogueRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
			String subject, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	private static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().substring(0, 8);
	}

	/** A project with the draft dialogues "main" and "check" (never published). */
	private String project() throws Exception {
		String slug = unique("test-session-");
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"),
						"test-session-admin", "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								MAIN.getBytes(StandardCharsets.UTF_8)))
						.file(new MockMultipartFile("files", "check.dlb", "text/plain",
								CHECK.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true"),
						"test-session-admin", "admin"))
				.andExpect(status().isOk());
		return slug;
	}

	/** The user stores one real variable of their own, $mood = fine. */
	private void storeRealMood(String slug, String user) throws Exception {
		mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", slug)
						.param("name", "mood").param("value", "fine").param("timeZone", "UTC"),
						user, "participant"))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder start(String slug, String dialogue) {
		return post("/v1/test-session/start").param("projectSlug", slug)
				.param("dialogueName", dialogue).param("language", "en").param("timeZone", "UTC");
	}

	private String sessionId(String json) throws Exception {
		return objectMapper.readTree(json).get("testSessionId").asText();
	}

	/** Starts "main" and follows its reply to "Second", so it sets both $mark and $second. */
	private void runMain(String slug, String tester, String role, String delegateUser)
			throws Exception {
		MockHttpServletRequestBuilder request = start(slug, "main");
		if (delegateUser != null) request.param("delegateUser", delegateUser);
		String json = mockMvc.perform(as(request, tester, role))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		int replyId = objectMapper.readTree(json).get("dialogueMessage").get("replies")
				.get(0).get("replyId").asInt();
		mockMvc.perform(as(post("/v1/test-session/progress").param("testSessionId", sessionId(json))
						.param("replyId", String.valueOf(replyId)).param("timeZone", "UTC"),
						tester, role))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder testVariables(String slug) {
		return get("/v1/test-session/variables").param("projectSlug", slug);
	}

	private MockHttpServletRequestBuilder realVariables(String slug) {
		return get("/v1/variables/get").param("projectSlug", slug).param("timeZone", "UTC");
	}

	private long historyOf(String subject) {
		UUID id = userRepository.findByIssuerAndSubject(ISSUER, subject).orElseThrow().getId();
		return variableHistoryRepository.findAll().stream()
				.filter(h -> h.getUser().getId().equals(id)).count();
	}

	private long logsOf(String subject) {
		return userRepository.findByIssuerAndSubject(ISSUER, subject)
				.map(user -> loggedDialogueRepository.findAll().stream()
						.filter(l -> l.getUser().getId().equals(user.getId())).count())
				.orElse(0L);
	}

	@Test
	void aTestRunChangesOnlyTheCopy() throws Exception {
		String slug = project();
		String editor = unique("test-session-editor-");
		storeRealMood(slug, editor);
		long history = historyOf(editor);

		runMain(slug, editor, "editor", null);

		mockMvc.perform(as(testVariables(slug), editor, "editor"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mark", "mood", "second")));
		mockMvc.perform(as(realVariables(slug), editor, "editor"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mood")));
		assertEquals(history, historyOf(editor));
		assertEquals(0, logsOf(editor));
	}

	@Test
	void aTestersSessionsShareTheirCopyAndOtherTestersDoNot() throws Exception {
		String slug = project();
		String editor = unique("test-session-editor-");
		String other = unique("test-session-other-");

		mockMvc.perform(as(start(slug, "main"), editor, "editor")).andExpect(status().isOk());

		mockMvc.perform(as(start(slug, "check"), editor, "editor"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Mark is 7")));
		mockMvc.perform(as(start(slug, "check"), other, "editor"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Mark is 7"))));
	}

	@Test
	void setVariableAndResetChangeOnlyTheCopy() throws Exception {
		String slug = project();
		String editor = unique("test-session-editor-");
		storeRealMood(slug, editor);

		mockMvc.perform(as(post("/v1/test-session/set-variable").param("projectSlug", slug)
						.param("name", "mark").param("value", "42").param("timeZone", "UTC"),
						editor, "editor"))
				.andExpect(status().isNoContent());
		mockMvc.perform(as(start(slug, "check"), editor, "editor"))
				.andExpect(content().string(containsString("Mark is 42")));
		mockMvc.perform(as(post("/v1/test-session/set-variable").param("projectSlug", slug)
						.param("name", "mood").param("timeZone", "UTC"), editor, "editor"))
				.andExpect(status().isNoContent());
		mockMvc.perform(as(testVariables(slug), editor, "editor"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mark")));

		mockMvc.perform(as(post("/v1/test-session/reset").param("projectSlug", slug),
						editor, "editor"))
				.andExpect(status().isNoContent());

		mockMvc.perform(as(testVariables(slug), editor, "editor"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mood")))
				.andExpect(jsonPath("$[0].value").value("fine"));
		mockMvc.perform(as(realVariables(slug), editor, "editor"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mood")));
	}

	@Test
	void testingAsAnotherUserOnlyReadsTheirVariables() throws Exception {
		String slug = project();
		String participant = unique("test-session-participant-");
		String admin = unique("test-session-admin-");
		storeRealMood(slug, participant);
		long history = historyOf(participant);

		runMain(slug, admin, "admin", participant);

		mockMvc.perform(as(testVariables(slug).param("delegateUser", participant), admin, "admin"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mark", "mood", "second")));
		mockMvc.perform(as(realVariables(slug), participant, "participant"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mood")));
		assertEquals(history, historyOf(participant));
		assertEquals(0, logsOf(participant));
		// The admin's own copy is a different one.
		mockMvc.perform(as(testVariables(slug), admin, "admin"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder()));
	}

	@Test
	void testingAsAnUnknownUserIsNotFoundAndCreatesNothing() throws Exception {
		String slug = project();
		String unknown = unique("test-session-unknown-");

		mockMvc.perform(as(start(slug, "main").param("delegateUser", unknown),
						unique("test-session-admin-"), "admin"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("UNKNOWN_USER"));
		assertEquals(false, userRepository.findByIssuerAndSubject(ISSUER, unknown).isPresent());
	}

	@Test
	void onlyTheTesterCanUseTheirSession() throws Exception {
		String slug = project();
		String editor = unique("test-session-editor-");
		String json = mockMvc.perform(as(start(slug, "main"), editor, "editor"))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(as(post("/v1/test-session/cancel").param("testSessionId", sessionId(json)),
						unique("test-session-other-"), "editor"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/v1/test-session/cancel").param("testSessionId", sessionId(json)),
						editor, "editor"))
				.andExpect(status().isNoContent());
		mockMvc.perform(as(post("/v1/test-session/cancel").param("testSessionId", sessionId(json)),
						editor, "editor"))
				.andExpect(status().isNotFound());
	}

	@Test
	void editorsCannotTestAsAnotherUserAndParticipantsCannotTest() throws Exception {
		String slug = project();
		String participant = unique("test-session-participant-");
		storeRealMood(slug, participant);

		mockMvc.perform(as(start(slug, "main").param("delegateUser", participant),
						unique("test-session-editor-"), "editor"))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(start(slug, "main"), participant, "participant"))
				.andExpect(status().isForbidden());
	}
}

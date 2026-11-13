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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Going back a step in a test session ({@code /test-session/back}, #490), as {@code /dialogue/back}
 * does in live playback: the session keeps its conversation log in memory, so its steps say where
 * they are in it, and going back shows the previous step again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestSessionBackIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** Start, Two and Three, each with one reply on; Two counts how often it is shown. */
	private static final String CHAIN = "title: Start\nspeaker: Robin\n---\nHello.\n"
			+ "[[On|Two]]\n===\n"
			+ "title: Two\nspeaker: Robin\n---\n<<set $count = $count + 1>>Two, $count.\n"
			+ "[[On|Three]]\n===\n"
			+ "title: Three\nspeaker: Robin\n---\nThree.\n[[Fin|End]]\n===\n"
			+ "title: End\nspeaker: Robin\n---\n===\n";

	@Autowired
	MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private static <T extends AbstractMockHttpServletRequestBuilder<T>> T as(T builder,
			String subject, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		return builder.with(jwt().jwt(claims));
	}

	/** A project with the draft dialogue "chain" (never published). */
	private String project() throws Exception {
		String slug = "test-back-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"),
						"test-back-admin", "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "chain.dlb", "text/plain",
								CHAIN.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true"),
						"test-back-admin", "admin"))
				.andExpect(status().isOk());
		return slug;
	}

	private JsonNode call(MockHttpServletRequestBuilder request, String editor) throws Exception {
		return objectMapper.readTree(mockMvc.perform(as(request, editor, "editor"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode start(String slug, String editor) throws Exception {
		return call(post("/v1/test-session/start").param("projectSlug", slug)
				.param("dialogueName", "chain").param("language", "en").param("timeZone", "UTC"),
				editor);
	}

	/** Selects the only reply of the given step. */
	private JsonNode reply(String sessionId, JsonNode step, String editor) throws Exception {
		return call(post("/v1/test-session/progress").param("testSessionId", sessionId)
				.param("replyId", step.get("replies").get(0).get("replyId").asText())
				.param("timeZone", "UTC"), editor).get("value");
	}

	private JsonNode back(String sessionId, String editor) throws Exception {
		return call(post("/v1/test-session/back").param("testSessionId", sessionId)
				.param("timeZone", "UTC"), editor);
	}

	private static String text(JsonNode step) {
		StringBuilder text = new StringBuilder();
		step.get("statement").get("segments").forEach(s -> text.append(s.path("text").asText()));
		return text.toString().trim();
	}

	@Test
	void theStepsSayWhereTheyAreInTheSessionsLog() throws Exception {
		String editor = "test-back-" + UUID.randomUUID();
		JsonNode started = start(project(), editor);
		JsonNode first = started.get("dialogueMessage");
		JsonNode second = reply(started.get("testSessionId").asText(), first, editor);

		assertEquals(0, first.get("loggedInteractionIndex").asInt());
		assertEquals(2, second.get("loggedInteractionIndex").asInt());
		assertEquals(first.get("loggedDialogueId").asText(), second.get("loggedDialogueId").asText());
	}

	@Test
	void goingBackShowsThePreviousStepAgainAndAReplyGoesOnFromThere() throws Exception {
		String editor = "test-back-" + UUID.randomUUID();
		JsonNode started = start(project(), editor);
		String session = started.get("testSessionId").asText();
		JsonNode two = reply(session, started.get("dialogueMessage"), editor);
		JsonNode three = reply(session, two, editor);
		assertEquals("Three", three.get("node").asText());

		JsonNode again = back(session, editor);
		assertEquals("Two", again.get("node").asText());
		// the node runs again, so its set command too
		assertEquals("Two, 2.", text(again));
		assertEquals(2, again.get("loggedInteractionIndex").asInt());

		assertEquals("Three", reply(session, again, editor).get("node").asText());
	}

	@Test
	void goingBackAtTheFirstStepShowsItAgain() throws Exception {
		String editor = "test-back-" + UUID.randomUUID();
		JsonNode started = start(project(), editor);
		JsonNode again = back(started.get("testSessionId").asText(), editor);
		assertEquals("Start", again.get("node").asText());
		assertEquals(0, again.get("loggedInteractionIndex").asInt());
	}

	@Test
	void anotherTestersSessionIsForbidden() throws Exception {
		JsonNode started = start(project(), "test-back-" + UUID.randomUUID());
		mockMvc.perform(as(post("/v1/test-session/back")
						.param("testSessionId", started.get("testSessionId").asText())
						.param("timeZone", "UTC"), "test-back-" + UUID.randomUUID(), "editor"))
				.andExpect(status().isForbidden());
	}

	@Test
	void anUnknownSessionIsNotFound() throws Exception {
		mockMvc.perform(as(post("/v1/test-session/back").param("testSessionId", "no-such-session")
						.param("timeZone", "UTC"), "test-back-" + UUID.randomUUID(), "editor"))
				.andExpect(status().isNotFound());
	}
}

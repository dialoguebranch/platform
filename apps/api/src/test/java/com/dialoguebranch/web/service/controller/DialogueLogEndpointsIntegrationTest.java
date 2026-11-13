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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests going back in a dialogue and the guards around resuming one, through the dialogue
 * end-points: the log is kept and read by the dialogue itself (#483), so these show that what it
 * records, and what the Web Service does with it, is what the end-points always did.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DialogueLogEndpointsIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** A chain of steps; the last asks for a name and sets it to something else afterwards. */
	private static final String CHAIN = "title: Start\nspeaker: Robin\n---\nHello.\n"
			+ "[[Go on.|Two]]\n===\n"
			+ "title: Two\nspeaker: Robin\n---\nTwo.\n[[Go on.|Three]]\n[[Stop.|End]]\n===\n"
			+ "title: Three\nspeaker: Robin\n---\nThree.\n"
			+ "[[Name <<input type=\"text\" value=\"$name\">>|Four|<<set $name = \"changed\">>]]\n===\n"
			+ "title: Four\nspeaker: Robin\n---\nHi $name.\n===\n"
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

	private static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().substring(0, 8);
	}

	private String publishedProject() throws Exception {
		String slug = unique("dialogue-log-");
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"),
						"dialogue-log-admin", "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "chain.dlb", "text/plain",
								CHAIN.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug), "dialogue-log-admin", "admin"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug),
						"dialogue-log-admin", "admin"))
				.andExpect(status().isOk());
		return slug;
	}

	private JsonNode json(RequestBuilder request) throws Exception {
		return objectMapper.readTree(mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode start(String slug, String sessionId, String subject) throws Exception {
		return json(as(post("/v1/dialogue/start").param("projectSlug", slug)
				.param("dialogueName", "chain").param("language", "en")
				.param("timeZone", "UTC").param("sessionId", sessionId), subject, "participant"));
	}

	/** The ID of the reply with the given text, or of the first reply when the text is null. */
	private int replyId(JsonNode step, String text) {
		if (text == null)
			return step.get("replies").get(0).get("replyId").asInt();
		for (JsonNode reply : step.get("replies")) {
			if (reply.toString().contains("\"" + text + "\""))
				return reply.get("replyId").asInt();
		}
		throw new AssertionError("no reply \"" + text + "\" in " + step);
	}

	/** Selects the reply with the given text; the next step, or {@code null} when it ends. */
	private JsonNode reply(JsonNode step, String text, String subject, String inputs)
			throws Exception {
		var request = post("/v1/dialogue/progress")
				.param("loggedDialogueId", step.get("loggedDialogueId").asText())
				.param("loggedInteractionIndex", step.get("loggedInteractionIndex").asText())
				.param("replyId", String.valueOf(replyId(step, text)));
		if (inputs != null)
			request = request.contentType(MediaType.APPLICATION_JSON).content(inputs);
		JsonNode next = json(as(request, subject, "participant")).get("value");
		return next == null || next.isNull() ? null : next;
	}

	private JsonNode reply(JsonNode step, String text, String subject) throws Exception {
		JsonNode next = reply(step, text, subject, null);
		assertFalse(next == null, "the dialogue ended after \"" + text + "\"");
		return next;
	}

	private RequestBuilder back(String loggedDialogueId, int index, String subject) {
		return as(post("/v1/dialogue/back").param("loggedDialogueId", loggedDialogueId)
				.param("loggedInteractionIndex", String.valueOf(index)), subject, "participant");
	}

	private JsonNode log(String sessionId, String subject) throws Exception {
		return json(as(get("/v1/log/get-session").param("sessionId", sessionId), subject,
				"participant")).get(0);
	}

	private static String text(JsonNode step) {
		return step.get("statement").toString();
	}

	@Test
	void goingBackShowsThePreviousStepAndAddsNothingToTheLog() throws Exception {
		String slug = publishedProject();
		String subject = unique("dialogue-log-");
		String sessionId = unique("session-");
		JsonNode one = start(slug, sessionId, subject);
		JsonNode two = reply(one, "Go on.", subject);
		JsonNode three = reply(two, "Go on.", subject);
		assertEquals(4, three.get("loggedInteractionIndex").asInt());

		JsonNode back = json(back(three.get("loggedDialogueId").asText(), 4, subject));

		assertEquals("Two", back.get("node").asText());
		assertTrue(text(back).contains("Two."), back.toString());
		assertEquals(2, back.get("loggedInteractionIndex").asInt());
		assertEquals(5, log(sessionId, subject).get("interactionList").size());

		// A reply to the step shown again continues from it, as a branch of the log.
		JsonNode again = reply(back, "Go on.", subject);
		assertEquals("Three", again.get("node").asText());
		JsonNode interactions = log(sessionId, subject).get("interactionList");
		assertEquals(7, interactions.size());
		assertEquals(2, interactions.get(5).get("previousIndex").asInt());
		assertEquals(5, interactions.get(6).get("previousIndex").asInt());
	}

	@Test
	void goingBackAtTheFirstStepShowsItAgain() throws Exception {
		String slug = publishedProject();
		String subject = unique("dialogue-log-");
		JsonNode one = start(slug, unique("session-"), subject);

		JsonNode back = json(back(one.get("loggedDialogueId").asText(), 0, subject));

		assertEquals("Start", back.get("node").asText());
		assertEquals(0, back.get("loggedInteractionIndex").asInt());
	}

	@Test
	void aStepThatIsNotInTheLogIsNotFound() throws Exception {
		String slug = publishedProject();
		String subject = unique("dialogue-log-");
		JsonNode one = start(slug, unique("session-"), subject);
		String id = one.get("loggedDialogueId").asText();

		mockMvc.perform(back(id, 99, subject)).andExpect(status().isNotFound());
		mockMvc.perform(as(post("/v1/dialogue/progress").param("loggedDialogueId", id)
				.param("loggedInteractionIndex", "99").param("replyId", "1"), subject,
				"participant")).andExpect(status().isNotFound());
	}

	@Test
	void aFinishedOrCancelledDialogueCantBeProgressedOrGoneBackIn() throws Exception {
		String slug = publishedProject();
		String subject = unique("dialogue-log-");

		JsonNode two = reply(start(slug, unique("session-"), subject), "Go on.", subject);
		String finishedId = two.get("loggedDialogueId").asText();
		assertEquals(null, reply(two, "Stop.", subject, null));
		mockMvc.perform(back(finishedId, 2, subject)).andExpect(status().isBadRequest());
		mockMvc.perform(as(post("/v1/dialogue/progress").param("loggedDialogueId", finishedId)
				.param("loggedInteractionIndex", "2").param("replyId", "1"), subject,
				"participant")).andExpect(status().isBadRequest());

		JsonNode open = start(slug, unique("session-"), subject);
		String cancelledId = open.get("loggedDialogueId").asText();
		mockMvc.perform(as(post("/v1/dialogue/cancel").param("loggedDialogueId", cancelledId),
				subject, "participant")).andExpect(status().isOk());
		mockMvc.perform(back(cancelledId, 0, subject)).andExpect(status().isBadRequest());
	}

	@Test
	void whatTheUserTypedIsLoggedBeforeTheRepliesCommandsRun() throws Exception {
		String slug = publishedProject();
		String subject = unique("dialogue-log-");
		String sessionId = unique("session-");
		JsonNode two = reply(start(slug, sessionId, subject), "Go on.", subject);
		JsonNode three = reply(two, "Go on.", subject);

		JsonNode four = reply(three, null, subject, "{\"name\":\"Ann\"}");

		assertTrue(text(four).contains("Hi changed."), four.toString());
		JsonNode interactions = log(sessionId, subject).get("interactionList");
		String logged = interactions.get(5).get("statement").asText();
		assertTrue(logged.contains("Ann"), logged);
		assertTrue(logged.contains("Name"), logged);
		assertFalse(logged.contains("changed"), logged);
	}
}

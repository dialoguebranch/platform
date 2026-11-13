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

import com.dialoguebranch.web.service.DlbProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests real playback ({@code /dialogue/*}) through a reply that links into another dialogue
 * (#473). Pins down what the Web Service does at such a switch, so that moving the switch into
 * the engine keeps it: the old dialogue's log is completed and a new log starts in the same
 * session, the variables carry over, the External Variable Service is read for the new
 * dialogue, the target node and the language are chosen as before, and going back stops at
 * the start of the dialogue that was switched to. Runs against a small stub External Variable
 * Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossDialogueLinkIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** "main" sets $mood, shows $mark, and links into "other" at its Start and at Second. */
	private static final String MAIN = "title: Start\nspeaker: Robin\n---\n"
			+ "<<set $mood = \"happy\">>\nMark is $mark.\n"
			+ "[[On|other.Start]]\n[[Skip ahead|other.Second]]\n[[Again|main.Start]]\n===\n";

	/** "other" shows $weather and $mood, then goes through Middle and ends at Second. */
	private static final String OTHER = "title: Start\nspeaker: Robin\n---\n"
			+ "Weather is $weather. Mood is $mood.\n[[Next|Middle]]\n===\n"
			+ "title: Middle\nspeaker: Robin\n---\nAlmost.\n[[Next|Second]]\n===\n"
			+ "title: Second\nspeaker: Robin\n---\nBye.\n===\n";

	private static final String MAIN_NL = "{\"_user\":{\"On\":\"Verder\",\"Skip ahead\":"
			+ "\"Overslaan\",\"Again\":\"Opnieuw\"},"
			+ "\"Robin\":{\"Mark is $mark.\":\"Cijfer is $mark.\"}}";

	private static final String OTHER_NL = "{\"_user\":{\"Next\":\"Volgende\"},\"Robin\":{"
			+ "\"Weather is $weather. Mood is $mood.\":\"Weer is $weather. Stemming is $mood.\","
			+ "\"Almost.\":\"Bijna.\",\"Bye.\":\"Doei.\"}}";

	/** A request the stub received. */
	private record Received(String path, String query, String body) {
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DlbProperties dlbProperties;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final List<Received> received = new CopyOnWriteArrayList<>();
	private HttpServer server;

	@BeforeEach
	void startStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(),
					StandardCharsets.UTF_8);
			String path = exchange.getRequestURI().getPath();
			byte[] answer = "[]".getBytes(StandardCharsets.UTF_8);
			if (path.equals("/token")) {
				answer = "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":300}"
						.getBytes(StandardCharsets.UTF_8);
			} else {
				received.add(new Received(path, exchange.getRequestURI().getQuery(), body));
			}
			if (path.endsWith("/retrieve-updates")) {
				long now = System.currentTimeMillis();
				answer = ("[{\"name\":\"mark\",\"value\":\"from-evs\",\"updatedTime\":" + now
						+ ",\"updatedTimeZone\":\"UTC\",\"updatedSource\":\"EXTERNAL\"},"
						+ "{\"name\":\"weather\",\"value\":\"sunny\",\"updatedTime\":" + now
						+ ",\"updatedTimeZone\":\"UTC\",\"updatedSource\":\"EXTERNAL\"}]")
						.getBytes(StandardCharsets.UTF_8);
			}
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, answer.length);
			exchange.getResponseBody().write(answer);
			exchange.close();
		});
		server.start();
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		evs.setEnabled(true);
		evs.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
		evs.setApiVersion(1);
		evs.getOauth2().setTokenUri("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
		evs.getOauth2().setClientId("dlb-web-service-evs");
		evs.getOauth2().setClientSecret("test-secret");
	}

	@AfterEach
	void stopStub() {
		dlbProperties.getExternalVariableService().setEnabled(false);
		server.stop(0);
	}

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

	private static MockMultipartFile file(String name, String content) {
		return new MockMultipartFile("files", name, "text/plain",
				content.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Publishes a project with "main" and "other", and the translation language "nl", into
	 * which "main" is always translated and "other" only when asked.
	 */
	private String publishedProject(boolean translateOther) throws Exception {
		String slug = unique("cross-link-");
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"),
						"cross-link-admin", "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(post("/v1/project/add-translation-language").param("projectSlug", slug)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"translationLanguageName\":\"Dutch\",\"translationLanguageCode\":\"nl\"}"),
						"cross-link-admin", "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(file("main.dlb", MAIN)).file(file("other.dlb", OTHER))
						.param("projectSlug", slug), "cross-link-admin", "admin"))
				.andExpect(status().isOk());
		var translations = multipart("/v1/authoring/import-translations")
				.file(file("main.json", MAIN_NL));
		if (translateOther)
			translations.file(file("other.json", OTHER_NL));
		mockMvc.perform(as(translations.param("projectSlug", slug).param("language", "nl")
						.param("folder", ""), "cross-link-admin", "admin"))
				.andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug),
						"cross-link-admin", "admin"))
				.andExpect(status().isOk());
		return slug;
	}

	private JsonNode json(org.springframework.test.web.servlet.RequestBuilder request)
			throws Exception {
		return objectMapper.readTree(mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode start(String slug, String language, String sessionId, String subject)
			throws Exception {
		return json(as(post("/v1/dialogue/start").param("projectSlug", slug)
				.param("dialogueName", "main").param("language", language)
				.param("timeZone", "UTC").param("sessionId", sessionId), subject, "participant"));
	}

	/** Selects the reply with the given text in the given dialogue step. */
	private JsonNode reply(JsonNode step, String text, String subject) throws Exception {
		int replyId = -1;
		for (JsonNode reply : step.get("replies")) {
			if (reply.toString().contains("\"" + text + "\""))
				replyId = reply.get("replyId").asInt();
		}
		assertNotEquals(-1, replyId, "no reply \"" + text + "\" in " + step);
		JsonNode next = json(as(post("/v1/dialogue/progress")
				.param("loggedDialogueId", step.get("loggedDialogueId").asText())
				.param("loggedInteractionIndex", step.get("loggedInteractionIndex").asText())
				.param("replyId", String.valueOf(replyId)), subject, "participant")).get("value");
		assertFalse(next == null || next.isNull(), "the dialogue ended after \"" + text + "\"");
		return next;
	}

	private JsonNode back(JsonNode step, String subject) throws Exception {
		return json(as(post("/v1/dialogue/back")
				.param("loggedDialogueId", step.get("loggedDialogueId").asText())
				.param("loggedInteractionIndex", step.get("loggedInteractionIndex").asText()),
				subject, "participant"));
	}

	private JsonNode session(String sessionId, String subject) throws Exception {
		return json(as(get("/v1/log/get-session").param("sessionId", sessionId),
				subject, "participant"));
	}

	private List<Received> reads() {
		return received.stream().filter(r -> r.path().endsWith("/retrieve-updates")).toList();
	}

	@Test
	void aLinkIntoAnotherDialogueStartsANewLogInTheSameSession() throws Exception {
		String slug = publishedProject(false);
		String subject = unique("cross-link-");
		String sessionId = unique("session-");

		JsonNode main = start(slug, "en", sessionId, subject);
		assertEquals("main", main.get("dialogue").asText());
		assertEquals("Start", main.get("node").asText());
		assertTrue(main.toString().contains("Mark is from-evs"), main.toString());

		JsonNode other = reply(main, "On", subject);
		assertEquals("other", other.get("dialogue").asText());
		assertEquals("Start", other.get("node").asText());
		assertNotEquals(main.get("loggedDialogueId").asText(),
				other.get("loggedDialogueId").asText());
		// $mood was set in "main" and carries over; $weather comes from the read below
		assertTrue(other.toString().contains("Weather is sunny. Mood is happy."),
				other.toString());

		// The External Variable Service is read again for the dialogue that was switched to
		List<Received> reads = reads();
		assertEquals(2, reads.size());
		assertTrue(reads.get(0).body().contains("\"mark\""));
		assertTrue(reads.get(1).body().contains("\"weather\""));
		assertTrue(reads.get(1).query().contains("testSession=false"));

		// One log per dialogue, both in the session; the one that was left is completed
		JsonNode logs = session(sessionId, subject);
		assertEquals(2, logs.size(), logs.toString());
		assertEquals("main", logs.get(0).get("dialogueName").asText());
		assertTrue(logs.get(0).get("completed").asBoolean());
		assertEquals(main.get("loggedDialogueId").asText(), logs.get(0).get("id").asText());
		assertEquals("other", logs.get(1).get("dialogueName").asText());
		assertFalse(logs.get(1).get("completed").asBoolean());
		assertEquals(other.get("loggedDialogueId").asText(), logs.get(1).get("id").asText());
		assertEquals(sessionId, logs.get(1).get("sessionId").asText());

		JsonNode middle = reply(other, "Next", subject);
		assertEquals("other", middle.get("dialogue").asText());
		assertEquals("Middle", middle.get("node").asText());
		assertEquals(other.get("loggedDialogueId").asText(),
				middle.get("loggedDialogueId").asText());

		// Back goes to the start of "other"...
		JsonNode backOnce = back(middle, subject);
		assertEquals("other", backOnce.get("dialogue").asText());
		assertEquals("Start", backOnce.get("node").asText());
		assertEquals(other.get("loggedDialogueId").asText(),
				backOnce.get("loggedDialogueId").asText());

		// ...and no further: it doesn't go back into "main"
		JsonNode backTwice = back(backOnce, subject);
		assertEquals("other", backTwice.get("dialogue").asText());
		assertEquals("Start", backTwice.get("node").asText());
		assertEquals(other.get("loggedDialogueId").asText(),
				backTwice.get("loggedDialogueId").asText());

		// Reaching a node without replies completes the log of "other"
		JsonNode second = reply(reply(backTwice, "Next", subject), "Next", subject);
		assertEquals("Second", second.get("node").asText());
		assertEquals(0, second.get("replies").size());
		logs = session(sessionId, subject);
		assertEquals(2, logs.size(), logs.toString());
		assertTrue(logs.get(1).get("completed").asBoolean());
	}

	@Test
	void aLinkIntoTheStartOfTheSameDialogueStartsANewLogToo() throws Exception {
		String slug = publishedProject(false);
		String subject = unique("cross-link-");
		String sessionId = unique("session-");

		JsonNode main = start(slug, "en", sessionId, subject);
		JsonNode again = reply(main, "Again", subject);

		assertEquals("main", again.get("dialogue").asText());
		assertEquals("Start", again.get("node").asText());
		assertNotEquals(main.get("loggedDialogueId").asText(),
				again.get("loggedDialogueId").asText());
		JsonNode logs = session(sessionId, subject);
		assertEquals(2, logs.size(), logs.toString());
		assertTrue(logs.get(0).get("completed").asBoolean());
		assertFalse(logs.get(1).get("completed").asBoolean());
	}

	@Test
	void aLinkToANodeOtherThanStartArrivesAtThatNode() throws Exception {
		String slug = publishedProject(false);
		String subject = unique("cross-link-");

		JsonNode main = start(slug, "en", unique("session-"), subject);
		JsonNode second = reply(main, "Skip ahead", subject);
		assertEquals("other", second.get("dialogue").asText());
		assertEquals("Second", second.get("node").asText());
		assertTrue(second.toString().contains("Bye."), second.toString());
		assertNotEquals(main.get("loggedDialogueId").asText(),
				second.get("loggedDialogueId").asText());
	}

	@Test
	void theDialogueSwitchedToIsPlayedInTheSameLanguage() throws Exception {
		String slug = publishedProject(true);
		String subject = unique("cross-link-");
		String sessionId = unique("session-");

		JsonNode main = start(slug, "nl", sessionId, subject);
		assertTrue(main.toString().contains("Cijfer is from-evs"), main.toString());

		JsonNode other = reply(main, "Verder", subject);
		assertEquals("other", other.get("dialogue").asText());
		assertTrue(other.toString().contains("Weer is sunny. Stemming is happy."),
				other.toString());

		JsonNode logs = session(sessionId, subject);
		assertEquals("nl", logs.get(1).get("language").asText(), logs.toString());
	}

	@Test
	void aDialogueWithoutTheTranslationIsPlayedInTheSourceLanguage() throws Exception {
		String slug = publishedProject(false);
		String subject = unique("cross-link-");
		String sessionId = unique("session-");

		JsonNode main = start(slug, "nl", sessionId, subject);
		assertTrue(main.toString().contains("Cijfer is from-evs"), main.toString());

		JsonNode other = reply(main, "Verder", subject);
		assertEquals("other", other.get("dialogue").asText());
		assertTrue(other.toString().contains("Weather is sunny. Mood is happy."),
				other.toString());

		JsonNode logs = session(sessionId, subject);
		assertEquals("en", logs.get(1).get("language").asText(), logs.toString());
	}
}

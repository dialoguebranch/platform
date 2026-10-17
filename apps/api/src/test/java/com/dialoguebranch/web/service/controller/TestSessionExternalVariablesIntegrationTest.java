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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that test sessions read from the External Variable Service like real playback does
 * (#411): at every dialogue start, also through a reply into another dialogue, with
 * {@code testSession=true} and the values of the session's copy. The answers go into the copy
 * only, and a test session never notifies the service. Real playback sends
 * {@code testSession=false}. Runs against a small stub External Variable Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestSessionExternalVariablesIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/** "main" shows $mark and links to "other", which shows $weather. */
	private static final String MAIN = "title: Start\nspeaker: Robin\n---\nMark is $mark.\n"
			+ "[[On|other.Start]]\n===\n";
	private static final String OTHER = "title: Start\nspeaker: Robin\n---\nWeather is $weather.\n"
			+ "[[Next|Second]]\n===\ntitle: Second\nspeaker: Robin\n---\nBye.\n===\n";

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
			received.add(new Received(path, exchange.getRequestURI().getQuery(), body));
			byte[] answer = "[]".getBytes(StandardCharsets.UTF_8);
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
		evs.setApiKey("test-key");
	}

	@AfterEach
	void stopStub() {
		dlbProperties.getExternalVariableService().setEnabled(false);
		server.stop(0);
	}

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

	private String project(boolean publish) throws Exception {
		String slug = unique("evs-test-");
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"),
						"evs-admin", "admin"))
				.andExpect(status().isCreated());
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.file(new MockMultipartFile("files", "main.dlb", "text/plain",
								MAIN.getBytes(StandardCharsets.UTF_8)))
						.file(new MockMultipartFile("files", "other.dlb", "text/plain",
								OTHER.getBytes(StandardCharsets.UTF_8)))
						.param("projectSlug", slug).param("replace", "true"), "evs-admin", "admin"))
				.andExpect(status().isOk());
		if (publish) {
			mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug),
							"evs-admin", "admin"))
					.andExpect(status().isOk());
		}
		return slug;
	}

	private List<Received> requests(String endpoint) {
		return received.stream().filter(r -> r.path().endsWith(endpoint)).toList();
	}

	@Test
	void aTestSessionReadsAtEveryDialogueStartIntoItsCopyOnly() throws Exception {
		String slug = project(false);
		String editor = unique("evs-editor-");

		String json = mockMvc.perform(as(post("/v1/test-session/start").param("projectSlug", slug)
						.param("dialogueName", "main").param("language", "en")
						.param("timeZone", "UTC"), editor, "editor"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Mark is from-evs")))
				.andReturn().getResponse().getContentAsString();

		List<Received> reads = requests("/retrieve-updates");
		assertEquals(1, reads.size());
		assertTrue(reads.get(0).query().contains("testSession=true"));
		assertTrue(reads.get(0).query().contains("subject=" + editor));
		assertTrue(reads.get(0).body().contains("\"mark\""));

		String sessionId = objectMapper.readTree(json).get("testSessionId").asText();
		int replyId = objectMapper.readTree(json).get("dialogueMessage").get("replies").get(0)
				.get("replyId").asInt();
		mockMvc.perform(as(post("/v1/test-session/progress").param("testSessionId", sessionId)
						.param("replyId", String.valueOf(replyId)).param("timeZone", "UTC"),
						editor, "editor"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Weather is sunny")));

		reads = requests("/retrieve-updates");
		assertEquals(2, reads.size());
		assertTrue(reads.get(1).query().contains("testSession=true"));
		assertTrue(reads.get(1).body().contains("\"weather\""));

		mockMvc.perform(as(get("/v1/test-session/variables").param("projectSlug", slug),
						editor, "editor"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder("mark", "weather")));
		mockMvc.perform(as(get("/v1/variables/get").param("projectSlug", slug)
						.param("timeZone", "UTC"), editor, "editor"))
				.andExpect(jsonPath("$[*].name").value(containsInAnyOrder()));
		assertTrue(requests("/notify-updated").isEmpty());
		assertTrue(requests("/notify-cleared").isEmpty());
	}

	@Test
	void realPlaybackSaysItIsNotATestSession() throws Exception {
		String slug = project(true);
		String participant = unique("evs-participant-");

		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", slug)
						.param("dialogueName", "main").param("language", "en")
						.param("timeZone", "UTC"), participant, "participant"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Mark is from-evs")));

		List<Received> reads = requests("/retrieve-updates");
		assertEquals(1, reads.size());
		assertTrue(reads.get(0).query().contains("testSession=false"));
	}
}

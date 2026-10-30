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
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.execution.ApplicationManager;
import com.dialoguebranch.web.service.execution.UserService;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.project.PublishService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requests of the same user that arrive at the same time (#214, #216, #284): a double tap, a
 * retry racing the original, two tabs. Each test fires its requests together from several
 * threads, so without the safeguards they would interleave.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentRequestsIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";
	private static final int CONCURRENT = 20;

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired Application application;
	@Autowired ProjectService projectService;
	@Autowired DraftDialogueService draftDialogueService;
	@Autowired PublishService publishService;

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
			String subject) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of("participant"))));
		return builder.with(jwt().jwt(claims));
	}

	/** Runs {@code task} {@link #CONCURRENT} times at once, and returns each run's result. */
	private static <T> List<T> atOnce(Callable<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < CONCURRENT; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) results.add(future.get());
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

	/** A published project with one dialogue whose node counts each time it runs, in a loop. */
	private String publishCountingProject() throws Exception {
		String slug = "concurrent-" + UUID.randomUUID();
		DBProject project = projectService.createProject(slug, "Concurrent", "", "en", "English");
		DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, "main");
		draftDialogueService.createNode(dialogue, "Start", "title: Start\nspeaker: Agent",
				"<<set $count = $count + 1>>\nCount: $count.\n\n[[Again.|Start]]");
		PublishService.PublishResult publish = publishService.publish(project, null);
		assertTrue(publish.isSuccess(), "publish failed: " + publish.getErrors());
		return slug;
	}

	@Test
	void simultaneousStepsOfOneDialogueAreAllKept() throws Exception {
		String slug = publishCountingProject();
		String subject = "concurrent-" + UUID.randomUUID();
		// A number, so that $count + 1 adds (set-single would store the text "0")
		mockMvc.perform(as(post("/v1/variables/set").param("projectSlug", slug)
						.param("timeZone", "Europe/Lisbon")
						.contentType(MediaType.APPLICATION_JSON).content("{\"count\": 0}"), subject))
				.andExpect(status().isOk());
		JsonNode start = objectMapper.readTree(mockMvc.perform(as(post("/v1/dialogue/start")
						.param("projectSlug", slug).param("dialogueName", "main")
						.param("language", "en").param("timeZone", "Europe/Lisbon"), subject))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		String loggedDialogueId = start.get("loggedDialogueId").asText();
		int index = start.get("loggedInteractionIndex").asInt();
		int replyId = start.get("replies").get(0).get("replyId").asInt();

		// The same step, sent twenty times at once, as a client retrying the same tap would
		atOnce(() -> mockMvc.perform(as(post("/v1/dialogue/progress")
						.param("loggedDialogueId", loggedDialogueId)
						.param("loggedInteractionIndex", String.valueOf(index))
						.param("replyId", String.valueOf(replyId)), subject))
				.andExpect(status().isOk())
				.andReturn().getResponse().getStatus());

		// Every run of the node counted once (#216)...
		JsonNode variables = objectMapper.readTree(mockMvc.perform(as(get("/v1/variables/get")
						.param("projectSlug", slug).param("variableNames", "count")
						.param("timeZone", "Europe/Lisbon"), subject))
				.andReturn().getResponse().getContentAsString());
		assertEquals(1 + CONCURRENT, variables.get(0).get("value").asInt());

		// ...and every step is in the log: none overwrote another (#284)
		UserService userService = application.getApplicationManager().getActiveUserService(
				new DialogueBranchUserId(ISSUER, subject, null));
		int interactions = userService.getLoggedDialogueStore()
				.findLoggedDialogue(loggedDialogueId).getInteractionList().size();
		assertEquals(index + 1 + 2 * CONCURRENT, interactions);
	}

	@Test
	void simultaneousVariableChangesAreAllKept() throws Exception {
		String slug = publishCountingProject();
		String subject = "concurrent-" + UUID.randomUUID();

		List<Integer> statuses = atOnce(() -> {
			String name = "v" + UUID.randomUUID().toString().substring(0, 8);
			return mockMvc.perform(as(post("/v1/variables/set-single").param("projectSlug", slug)
							.param("name", name).param("value", "x")
							.param("timeZone", "Europe/Lisbon"), subject))
					.andReturn().getResponse().getStatus();
		});
		assertTrue(statuses.stream().allMatch(s -> s == 200), statuses.toString());

		JsonNode variables = objectMapper.readTree(mockMvc.perform(as(get("/v1/variables/get")
						.param("projectSlug", slug).param("timeZone", "Europe/Lisbon"), subject))
				.andReturn().getResponse().getContentAsString());
		assertEquals(CONCURRENT, variables.size());
	}

	@Test
	void aNewUsersSimultaneousRequestsShareOneUserService() throws Exception {
		ApplicationManager manager = application.getApplicationManager();
		DialogueBranchUserId userId = new DialogueBranchUserId(ISSUER,
				"concurrent-" + UUID.randomUUID(), null);

		List<UserService> services = atOnce(() -> manager.getOrCreateActiveUserService(userId));

		for (UserService service : services) assertSame(services.get(0), service);
		assertSame(services.get(0), manager.getActiveUserService(userId));
	}
}

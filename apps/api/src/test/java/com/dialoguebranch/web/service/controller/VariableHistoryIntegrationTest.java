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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for {@code GET /variables/history} (#259): changes made through the regular
 * variable end-points are recorded by the history listener every {@code UserService} registers,
 * and read back newest first, per user and per project.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VariableHistoryIntegrationTest {

	private static final String PROJECT = "default-test";
	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	/** A fresh user per test, so tests sharing the Spring context never see each other's rows. */
	private static String newSubject() {
		return "history-" + UUID.randomUUID();
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
			String subject, String... roles) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject(subject)
				.claim("iss", ISSUER)
				.claim("preferred_username", subject)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(roles))));
		return builder.with(jwt().jwt(claims));
	}

	private void setSingle(String subject, String name, String value) throws Exception {
		MockHttpServletRequestBuilder request = post("/v1/variables/set-single")
				.param("projectSlug", PROJECT).param("name", name)
				.param("timeZone", "Europe/Lisbon");
		if (value != null) request.param("value", value);
		mockMvc.perform(as(request, subject, "participant")).andExpect(status().isOk());
	}

	/** Starts a published default-test dialogue and returns the response's JSON. */
	private JsonNode startDialogue(String subject, String dialogueName) throws Exception {
		String body = mockMvc.perform(as(post("/v1/dialogue/start")
						.param("projectSlug", PROJECT).param("dialogueName", dialogueName)
						.param("language", "en").param("timeZone", "Europe/Lisbon"),
						subject, "participant"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	/** Picks a reply in an ongoing dialogue, optionally with input-field values as the body. */
	private void progress(String subject, JsonNode step, int replyId, String inputValuesJson)
			throws Exception {
		MockHttpServletRequestBuilder request = post("/v1/dialogue/progress")
				.param("loggedDialogueId", step.get("loggedDialogueId").asText())
				.param("loggedInteractionIndex", step.get("loggedInteractionIndex").asText())
				.param("replyId", String.valueOf(replyId));
		if (inputValuesJson != null)
			request.contentType(MediaType.APPLICATION_JSON).content(inputValuesJson);
		mockMvc.perform(as(request, subject, "participant")).andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder history(String subject) {
		return as(get("/v1/variables/history").param("projectSlug", PROJECT), subject,
				"participant");
	}

	@Test
	void recordsEveryChangeNewestFirstWithoutDeduplicating() throws Exception {
		String subject = newSubject();
		setSingle(subject, "mood", "fine");
		setSingle(subject, "mood", "fine");
		setSingle(subject, "mood", "bad");

		mockMvc.perform(history(subject))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].name").value("mood"))
				.andExpect(jsonPath("$[0].value").value("bad"))
				.andExpect(jsonPath("$[1].value").value("fine"))
				.andExpect(jsonPath("$[2].value").value("fine"))
				.andExpect(jsonPath("$[0].source").value("WEB_SERVICE"))
				.andExpect(jsonPath("$[0].recordedTimeZone").value("Europe/Lisbon"))
				.andExpect(jsonPath("$[0].recordedTime").isNumber())
				.andExpect(jsonPath("$[0].id").isNumber());
	}

	@Test
	void recordsARemovalAsANullValue() throws Exception {
		String subject = newSubject();
		setSingle(subject, "mood", "fine");
		setSingle(subject, "mood", null);

		mockMvc.perform(history(subject))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name").value("mood"))
				.andExpect(jsonPath("$[0].value").value(nullValue()))
				.andExpect(jsonPath("$[1].value").value("fine"));
	}

	@Test
	void filtersByVariableNameAndPaginates() throws Exception {
		String subject = newSubject();
		setSingle(subject, "mood", "fine");
		setSingle(subject, "energy", "low");
		setSingle(subject, "mood", "bad");

		mockMvc.perform(history(subject).param("variableName", "mood"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].value").value("bad"))
				.andExpect(jsonPath("$[1].value").value("fine"));

		mockMvc.perform(history(subject).param("page", "1").param("pageSize", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("energy"));
	}

	@Test
	void onlyReturnsTheCallersOwnHistory() throws Exception {
		String alice = newSubject();
		String bob = newSubject();
		setSingle(alice, "mood", "fine");

		mockMvc.perform(history(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void setCommandsInAPlayedDialogueAreRecordedAsScript() throws Exception {
		// The seeded "variables" dialogue: Start sets $subjectToday; its reply leads to
		// Variables1, which sets $variableOne, $variable2 and $variable_three.
		String subject = newSubject();
		JsonNode start = startDialogue(subject, "variables");
		progress(subject, start, start.get("replies").get(0).get("replyId").asInt(), null);

		mockMvc.perform(history(subject))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[*].source", everyItem(is("DLB_SCRIPT"))))
				.andExpect(jsonPath("$[*].name", contains(
						"variable_three", "variable2", "variableOne", "subjectToday")))
				.andExpect(jsonPath("$[0].value").value("Three"))
				.andExpect(jsonPath("$[3].value").value("variables"));
	}

	@Test
	void valuesEnteredInAReplysInputFieldAreRecordedAsInputReply() throws Exception {
		// The seeded "inputs" dialogue: Start's only reply holds a text input for $inName.
		String subject = newSubject();
		JsonNode start = startDialogue(subject, "inputs");
		progress(subject, start, start.get("replies").get(0).get("replyId").asInt(),
				"{\"inName\": \"Robin\"}");

		mockMvc.perform(history(subject).param("variableName", "inName"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].value").value("Robin"))
				.andExpect(jsonPath("$[0].source").value("INPUT_REPLY"));
	}

	@Test
	void anAdminCanReadAnotherUsersHistoryThroughDelegation() throws Exception {
		String participant = newSubject();
		setSingle(participant, "mood", "fine");

		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", PROJECT)
						.param("delegateUser", participant), newSubject(), "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("mood"))
				.andExpect(jsonPath("$[0].value").value("fine"));
	}

	/**
	 * Sets a variable and returns the time the Web Service recorded the change at. Pauses first so
	 * that every change in a test is recorded at a different millisecond.
	 */
	private long recordChange(String subject, String name, String value) throws Exception {
		Thread.sleep(5);
		setSingle(subject, name, value);
		String body = mockMvc.perform(history(subject).param("pageSize", "1"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get(0).get("recordedTime").asLong();
	}

	@Test
	void fromAloneIncludesTheBoundAndEverythingAfterIt() throws Exception {
		String subject = newSubject();
		recordChange(subject, "mood", "a");
		long second = recordChange(subject, "mood", "b");
		recordChange(subject, "mood", "c");

		mockMvc.perform(history(subject).param("from", String.valueOf(second)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("c", "b")));
	}

	@Test
	void toAloneExcludesTheBoundAndEverythingAfterIt() throws Exception {
		String subject = newSubject();
		recordChange(subject, "mood", "a");
		long second = recordChange(subject, "mood", "b");
		recordChange(subject, "mood", "c");

		mockMvc.perform(history(subject).param("to", String.valueOf(second)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("a")));
	}

	@Test
	void fromAndToTogetherSelectAHalfOpenRange() throws Exception {
		String subject = newSubject();
		long first = recordChange(subject, "mood", "a");
		recordChange(subject, "mood", "b");
		long third = recordChange(subject, "mood", "c");

		mockMvc.perform(history(subject).param("from", String.valueOf(first))
						.param("to", String.valueOf(third)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("b", "a")));
	}

	@Test
	void consecutiveRangesNeitherOverlapNorLeaveGaps() throws Exception {
		String subject = newSubject();
		recordChange(subject, "mood", "a");
		long second = recordChange(subject, "mood", "b");
		recordChange(subject, "mood", "c");

		mockMvc.perform(history(subject).param("to", String.valueOf(second)))
				.andExpect(jsonPath("$", hasSize(1)));
		mockMvc.perform(history(subject).param("from", String.valueOf(second)))
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void aRangeCombinesWithTheVariableNameAndPaging() throws Exception {
		String subject = newSubject();
		recordChange(subject, "mood", "a");
		long energy = recordChange(subject, "energy", "low");
		recordChange(subject, "mood", "b");
		recordChange(subject, "mood", "c");

		mockMvc.perform(history(subject).param("variableName", "mood")
						.param("from", String.valueOf(energy)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("c", "b")));

		mockMvc.perform(history(subject).param("variableName", "mood")
						.param("from", String.valueOf(energy))
						.param("page", "1").param("pageSize", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("b")));

		mockMvc.perform(history(subject).param("from", String.valueOf(energy))
						.param("pageSize", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("c", "b")));
	}

	@Test
	void anEmptyRangeReturnsAnEmptyList() throws Exception {
		String subject = newSubject();
		long only = recordChange(subject, "mood", "a");

		mockMvc.perform(history(subject).param("from", String.valueOf(only + 1_000_000)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(history(subject).param("from", String.valueOf(only))
						.param("to", String.valueOf(only)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void aRangeWorksThroughDelegation() throws Exception {
		String participant = newSubject();
		recordChange(participant, "mood", "a");
		long second = recordChange(participant, "mood", "b");

		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", PROJECT)
						.param("delegateUser", participant)
						.param("from", String.valueOf(second)), newSubject(), "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].value", contains("b")));
	}

	@Test
	void aFromAfterToIsABadRequest() throws Exception {
		mockMvc.perform(history(newSubject()).param("from", "2000").param("to", "1000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void anUnparseableTimeIsABadRequest() throws Exception {
		mockMvc.perform(history(newSubject()).param("from", "yesterday"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(history(newSubject()).param("to", "2026-10-14T12:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void unknownProjectIsNotFound() throws Exception {
		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", "no-such-project"),
						newSubject(), "participant"))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidVariableNameIsABadRequest() throws Exception {
		mockMvc.perform(history(newSubject()).param("variableName", "1nvalid"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void readingAnotherUsersHistoryNeedsDelegation() throws Exception {
		mockMvc.perform(as(get("/v1/variables/history").param("projectSlug", PROJECT)
						.param("delegateUser", "someone-else"), newSubject(), "editor"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_PRIVILEGES"));
	}

	@Test
	void withoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/v1/variables/history").param("projectSlug", PROJECT))
				.andExpect(status().isUnauthorized());
	}

}

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

import com.dialoguebranch.web.service.repository.DBUserRepository;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the {@code /users} listing (#407): it matches the username or the subject, includes users
 * with no known username after the named ones, and says how many variables and logged dialogues
 * each user has.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsersListingIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	private static final String SCRIPT = "title: Start\nspeaker: Robin\n---\n<<set $seen = 1>>\n"
			+ "Hello.\n[[Next|Second]]\n===\ntitle: Second\nspeaker: Robin\n---\nBye.\n===\n";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DBUserRepository userRepository;

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
		return as(builder, "admin", "listing-admin");
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

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
				.andExpect(status().isOk());
	}

	@Test
	void listsNamedUsersFirstThenUnnamedOnesBySubject() throws Exception {
		String tag = unique();
		userRepository.save(new DBUser(ISSUER, "listing-" + tag + "-z", null));
		userRepository.save(new DBUser(ISSUER, "listing-" + tag + "-y", "listing-" + tag + "-b"));
		userRepository.save(new DBUser(ISSUER, "listing-" + tag + "-x", "listing-" + tag + "-a"));

		mockMvc.perform(asAdmin(get("/v1/users").param("search", "listing-" + tag)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].subject").value(contains(
						"listing-" + tag + "-x", "listing-" + tag + "-y", "listing-" + tag + "-z")))
				.andExpect(jsonPath("$[2].username").doesNotExist());
	}

	@Test
	void matchesTheSubjectCaseInsensitively() throws Exception {
		String subject = "listing-subject-" + unique();
		userRepository.save(new DBUser(ISSUER, subject, "someone-else-" + unique()));

		mockMvc.perform(asAdmin(get("/v1/users").param("search", subject.toUpperCase())))
				.andExpect(jsonPath("$[*].subject").value(contains(subject)));
	}

	@Test
	void countsEachUsersVariablesAndDialogueLogs() throws Exception {
		String slug = "listing-" + unique();
		String participant = "listing-p-" + unique();
		String idle = "listing-idle-" + unique();
		publishProject(slug);
		mockMvc.perform(as(post("/v1/dialogue/start").param("projectSlug", slug)
						.param("dialogueName", "main").param("timeZone", "UTC"), "participant", participant))
				.andExpect(status().isOk());
		userRepository.save(new DBUser(ISSUER, idle, idle));

		mockMvc.perform(asAdmin(get("/v1/users").param("search", participant)))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].variables").value(1))
				.andExpect(jsonPath("$[0].dialogueLogs").value(1));
		mockMvc.perform(asAdmin(get("/v1/users").param("search", idle)))
				.andExpect(jsonPath("$[0].variables").value(0))
				.andExpect(jsonPath("$[0].dialogueLogs").value(0));
	}

	@Test
	void listsOnlyTheCallersRealm() throws Exception {
		String subject = "listing-realm-" + unique();
		userRepository.save(new DBUser("https://keycloak.test/realms/other", subject, subject));

		mockMvc.perform(asAdmin(get("/v1/users").param("search", subject)))
				.andExpect(jsonPath("$", empty()));
	}
}

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that a request the caller got wrong is answered with a 4xx and the reason, not with a
 * generic 500: a malformed upload (such as one whose body a proxy lost on the way), a wrong
 * content type or method, a missing or invalid parameter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientErrorResponsesIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T as(T builder, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("client-error-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "client-error-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	private String newProject() throws Exception {
		String slug = "client-error-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		return slug;
	}

	@Test
	void anUploadWithoutItsFilesPartIsABadRequestNotAServerError() throws Exception {
		String slug = newProject();
		// What the service receives when a proxy forwards the multipart headers but not the body.
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.param("projectSlug", slug).param("folder", ""), "editor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message", containsString("'files'")))
				.andExpect(jsonPath("$.message", containsString("proxy")));
	}

	@Test
	void aFilePartThatIsNotAFileIsABadRequest() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts")
						.param("projectSlug", slug).param("files", "not a file"), "editor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("'files'")));
	}

	@Test
	void aRequestThatIsNotMultipartIsAnUnsupportedMediaType() throws Exception {
		mockMvc.perform(as(post("/v1/authoring/import-scripts")
						.contentType(MediaType.APPLICATION_JSON).content("{}"), "editor"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.message", containsString("application/json")));
	}

	@Test
	void aWrongMethodIsMethodNotAllowedAndNamesTheAllowedOne() throws Exception {
		mockMvc.perform(as(get("/v1/authoring/import-scripts"), "editor"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", containsString("POST")));
	}

	@Test
	void aMissingRequiredParameterIsABadRequestNamingIt() throws Exception {
		mockMvc.perform(as(get("/v1/authoring/list-nodes"), "editor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.message", containsString("projectSlug")));
	}

	@Test
	void aParameterOfTheWrongTypeIsABadRequestNamingIt() throws Exception {
		String slug = newProject();
		MockMultipartFile file = new MockMultipartFile("files", "a.dlb", "text/plain",
				"title: Start\nspeaker: Robin\n---\nHello.\n===\n".getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts").file(file)
						.param("projectSlug", slug).param("replace", "maybe"), "editor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("replace")))
				.andExpect(jsonPath("$.message", containsString("maybe")));
	}
}

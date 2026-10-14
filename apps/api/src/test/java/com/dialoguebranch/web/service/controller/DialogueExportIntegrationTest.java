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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests exporting a single draft dialogue as a {@code .dlb} script through {@code
 * /authoring/export-dialogue} (#341), including that what it exports imports again unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DialogueExportIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	/**
	 * A script with a preamble, a header comment tag, two nodes, a reply, and non-ASCII text, in
	 * the canonical form a script is exported in: blank lines in the preamble are not kept (see
	 * {@code DialogueBranchScriptSplitter}), so the preamble runs straight into the first node.
	 */
	private static final String SCRIPT =
			"// Welcome flow, café édition.\n"
			+ "// Second preamble line.\n"
			+ "title: Start\n"
			+ "speaker: Robin\n"
			+ "comment: see https://example.com\n"
			+ "---\n"
			+ "Hello — 日本語 ¿qué tal?\n"
			+ "\n"
			+ "[[Continue|End]]\n"
			+ "===\n"
			+ "title: End\n"
			+ "speaker: Robin\n"
			+ "---\n"
			+ "Bye.\n"
			+ "===\n";

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T as(T builder, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("export-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "export-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	private String newProject() throws Exception {
		String slug = "export-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		return slug;
	}

	private void importScript(String slug, String folder, String fileName, String content)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/v1/authoring/import-scripts");
		request.file(new MockMultipartFile("files", fileName, "text/plain",
				content.getBytes(StandardCharsets.UTF_8)));
		request.param("projectSlug", slug).param("folder", folder);
		mockMvc.perform(as(request, "editor")).andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder export(String slug, String dialogueName, String role) {
		return as(get("/v1/authoring/export-dialogue").param("projectSlug", slug)
				.param("dialogueName", dialogueName), role);
	}

	private String exported(String slug, String dialogueName) throws Exception {
		return mockMvc.perform(export(slug, dialogueName, "editor"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void exportsTheDialogueAsADlbDownloadNamedAfterIt() throws Exception {
		String slug = newProject();
		importScript(slug, "", "main.dlb", SCRIPT);

		mockMvc.perform(export(slug, "main", "editor"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(header().string("Content-Disposition", containsString("filename=\"main.dlb\"")))
				.andExpect(content().string(SCRIPT));
	}

	@Test
	void aDialogueInAFolderIsNamedAfterItsLastSegmentOnly() throws Exception {
		String slug = newProject();
		importScript(slug, "onboarding/week1", "welcome.dlb", SCRIPT);

		mockMvc.perform(export(slug, "onboarding/week1/welcome", "editor"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("filename=\"welcome.dlb\"")))
				.andExpect(header().string("Content-Disposition", not(containsString("onboarding"))))
				.andExpect(content().string(SCRIPT));
	}

	@Test
	void aBlankLineAfterThePreambleIsNotKept() throws Exception {
		String slug = newProject();
		importScript(slug, "", "main.dlb", SCRIPT.replace("preamble line.\n", "preamble line.\n\n"));

		assertEquals(SCRIPT, exported(slug, "main"));
	}

	@Test
	void anExportedFileImportsAgainUnchanged() throws Exception {
		String slug = newProject();
		importScript(slug, "", "main.dlb", SCRIPT);

		// The exported file, imported into another folder under the same file name, is again the
		// same script.
		String firstExport = exported(slug, "main");
		importScript(slug, "copy", "main.dlb", firstExport);
		assertEquals(SCRIPT, firstExport);
		assertEquals(firstExport, exported(slug, "copy/main"));
	}

	@Test
	void exportsTheCurrentDraftNotThePublishedVersion() throws Exception {
		String slug = newProject();
		importScript(slug, "", "main.dlb", SCRIPT);
		mockMvc.perform(as(post("/v1/publish/create-version").param("projectSlug", slug), "admin"))
				.andExpect(status().isOk());

		// An edit after publishing lives in the draft only.
		mockMvc.perform(as(post("/v1/authoring/update-node").param("projectSlug", slug)
						.param("dialogueName", "main").param("nodeTitle", "End")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tags\":[{\"key\":\"title\",\"value\":\"End\"},"
								+ "{\"key\":\"speaker\",\"value\":\"Robin\"}],\"body\":\"Edited after publishing.\"}"),
						"admin"))
				.andExpect(status().isOk());

		mockMvc.perform(export(slug, "main", "editor"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Edited after publishing.")))
				.andExpect(content().string(not(containsString("Bye."))));
	}

	@Test
	void anUnknownDialogueOrProjectIsNotFound() throws Exception {
		String slug = newProject();
		mockMvc.perform(export(slug, "no-such-dialogue", "editor"))
				.andExpect(status().isNotFound());
		mockMvc.perform(export("no-such-project", "main", "editor"))
				.andExpect(status().isNotFound());
	}

	@Test
	void needsTheAuthorPermission() throws Exception {
		String slug = newProject();
		importScript(slug, "", "main.dlb", SCRIPT);

		mockMvc.perform(export(slug, "main", "participant")).andExpect(status().isForbidden());
		mockMvc.perform(get("/v1/authoring/export-dialogue").param("projectSlug", slug)
						.param("dialogueName", "main"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(export(slug, "main", "admin")).andExpect(status().isOk());
	}

}

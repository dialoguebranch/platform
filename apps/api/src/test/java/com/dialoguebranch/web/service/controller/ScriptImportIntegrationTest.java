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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests importing {@code .dlb} scripts into a folder of a project through {@code
 * /authoring/import-scripts} (#339).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScriptImportIntegrationTest {

	private static final String ISSUER = "https://keycloak.test/realms/dialoguebranch";

	private static final String VALID = "title: Start\nspeaker: Robin\n---\nHello.\n===\n";

	@Autowired
	MockMvc mockMvc;

	private static <T extends MockHttpServletRequestBuilder> T as(T builder, String role) {
		Consumer<Jwt.Builder> claims = jwt -> jwt
				.subject("import-" + role)
				.claim("iss", ISSUER)
				.claim("preferred_username", "import-" + role)
				.claim("resource_access",
						Map.of("dlb-web-service", Map.of("roles", List.of(role))));
		builder.with(jwt().jwt(claims));
		return builder;
	}

	private String newProject() throws Exception {
		String slug = "import-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(as(post("/v1/project/create-project")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slug\":\"" + slug + "\",\"displayName\":\"T\",\"description\":\"\","
								+ "\"sourceLanguageCode\":\"en\",\"sourceLanguageName\":\"English\"}"), "admin"))
				.andExpect(status().isCreated());
		return slug;
	}

	private static MockMultipartFile file(String name, String content) {
		return file(name, content.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("files", name, "text/plain", content);
	}

	private static MockMultipartHttpServletRequestBuilder importInto(String slug, String folder,
			MockMultipartFile... files) {
		MockMultipartHttpServletRequestBuilder request = multipart("/v1/authoring/import-scripts");
		for (MockMultipartFile f : files) request.file(f);
		request.param("projectSlug", slug).param("folder", folder);
		return as(request, "editor");
	}

	private static String dialogue(String name) {
		return "$[?(@.name == '" + name + "')]";
	}

	@Test
	void importsScriptsIntoAFolder() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "/onboarding/",
						file("welcome.dlb", "// About the welcome.\r\n" + VALID.replace("\n", "\r\n")),
						file("goodbye.dlb", "title: Start\nspeaker: Robin\ncomment: see https://example.com\n---\nBye.\n===\n")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].dialogueName").value("onboarding/welcome"))
				.andExpect(jsonPath("$[0].replaced").value(false))
				.andExpect(jsonPath("$[0].parseErrors", empty()))
				.andExpect(jsonPath("$[1].dialogueName").value("onboarding/goodbye"));

		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("onboarding/welcome") + ".preamble", contains("// About the welcome.")))
				.andExpect(jsonPath(dialogue("onboarding/welcome") + ".isNew", contains(true)));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "onboarding/goodbye"), "admin"))
				.andExpect(jsonPath("$[0].tags[2].value").value("see https://example.com"));
	}

	@Test
	void importsIntoTheProjectRootWithoutAFolder() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "", file("intro.dlb", VALID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].dialogueName").value("intro"));
	}

	@Test
	void anExistingDialogueIsOnlyReplacedWhenAsked() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "", file("main.dlb", VALID))).andExpect(status().isOk());

		String replacement = "title: Start\nspeaker: Lizz\n---\nReplaced.\n===\n";
		mockMvc.perform(importInto(slug, "", file("main.dlb", replacement)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main.dlb'][0]", containsString("already exists")));

		mockMvc.perform(importInto(slug, "", file("main.dlb", replacement)).param("replace", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].replaced").value(true));
		mockMvc.perform(as(get("/v1/authoring/list-nodes").param("projectSlug", slug)
						.param("dialogueName", "main"), "admin"))
				.andExpect(jsonPath("$[0].body").value("Replaced."));
	}

	@Test
	void replacingADialoguePendingDeletionRestoresIt() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "", file("main.dlb", VALID))).andExpect(status().isOk());
		mockMvc.perform(as(post("/v1/authoring/delete-dialogue").param("projectSlug", slug)
						.param("dialogueName", "main"), "admin"))
				.andExpect(status().isNoContent());

		mockMvc.perform(importInto(slug, "", file("main.dlb", VALID)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main.dlb'][0]", containsString("pending deletion")));
		mockMvc.perform(importInto(slug, "", file("main.dlb", VALID)).param("replace", "true"))
				.andExpect(status().isOk());
		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath(dialogue("main") + ".isDeleted", contains(false)));
	}

	@Test
	void nothingIsImportedWhenOneFileIsRefused() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "batch",
						file("good.dlb", VALID),
						file("bad.dlb", "title: Start\nnot a tag\n---\nHi.\n===\n")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Nothing was imported")))
				.andExpect(jsonPath("$.errors['bad.dlb'][0]", containsString("not a 'key: value' line")))
				.andExpect(jsonPath("$.errors['good.dlb']").doesNotExist());

		mockMvc.perform(as(get("/v1/authoring/list-dialogues").param("projectSlug", slug), "admin"))
				.andExpect(jsonPath("$[*].name", not(hasItem("batch/good"))));
	}

	@Test
	void syntaxErrorsDontRefuseAFileButAreReported() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "", file("rough.dlb",
						"title: Start\nspeaker: Robin\n---\nHello <<set $x = >>\n[[Go|Nowhere]]\n===\n")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].parseErrors", not(empty())));
	}

	@Test
	void refusesFilesThatCantBeImported() throws Exception {
		String slug = newProject();
		byte[] notUtf8 = {(byte) 0x4D, (byte) 0x5A, (byte) 0x90, (byte) 0xFF, (byte) 0xFE};
		byte[] tooLarge = ("title: Start\n---\n" + "x".repeat(1024 * 1024) + "\n===\n")
				.getBytes(StandardCharsets.UTF_8);
		mockMvc.perform(importInto(slug, "",
						file("notes.txt", VALID),
						file("My Dialogue.dlb", VALID),
						file("program.dlb", notUtf8),
						file("huge.dlb", tooLarge),
						file("untitled.dlb", "speaker: Robin\n---\nHi.\n===\n"),
						file("twice.dlb", VALID + VALID)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['notes.txt'][0]", containsString("not a .dlb file")))
				.andExpect(jsonPath("$.errors['My Dialogue.dlb'][0]", containsString("isn't a valid dialogue name")))
				.andExpect(jsonPath("$.errors['program.dlb'][0]", containsString("not a UTF-8 text file")))
				.andExpect(jsonPath("$.errors['huge.dlb'][0]", containsString("larger than")))
				.andExpect(jsonPath("$.errors['untitled.dlb'][0]", containsString("no 'title'")))
				.andExpect(jsonPath("$.errors['twice.dlb'][0]", containsString("duplicate node title: Start")));
	}

	@Test
	void refusesTheSameNameTwiceAndAnInvalidFolder() throws Exception {
		String slug = newProject();
		mockMvc.perform(importInto(slug, "", file("main.dlb", VALID), file("dir/main.dlb", VALID)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['main.dlb'][0]", containsString("more than one file")));
		mockMvc.perform(importInto(slug, "../escape", file("main.dlb", VALID)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Invalid folder")));
	}

	@Test
	void participantsCannotImport() throws Exception {
		String slug = newProject();
		mockMvc.perform(as(multipart("/v1/authoring/import-scripts").file(file("main.dlb", VALID))
						.param("projectSlug", slug), "participant"))
				.andExpect(status().isForbidden());
	}
}

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
 * Portions of this file are vendored from the rrd-utils library
 * (https://github.com/RoessinghResearch/rrd-utils), used under the MIT License.
 *
 * Copyright (c) 2022 Roessingh Research and Development
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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.exception.ProjectParseException;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ProjectScriptLoader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests {@link ExecutableProject#load(Path)}: loading a project in one call, failing with one
 * exception that lists every parse error (#473).
 */
public class ExecutableProjectLoadTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static Path example(String project) throws URISyntaxException {
		return Path.of(ExecutableProjectLoadTest.class.getClassLoader()
				.getResource(project + "/dlb-project.xml").toURI());
	}

	private static ProjectParserResult parse(Path metadataFile) throws Exception {
		return new ProjectParser(new ProjectScriptLoader(metadataFile.toFile())).parse();
	}

	/** Writes a project with the source language "en" and the translation language "nl". */
	private Path writeProject(Map<String, String> files) throws IOException {
		File root = folder.newFolder();
		Files.writeString(root.toPath().resolve("dlb-project.xml"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<dlb-project name=\"Load Test\" slug=\"load-test\" version=\"1\">\n"
				+ "\t<description>A project for load tests.</description>\n"
				+ "\t<language-map>\n"
				+ "\t\t<source-language name=\"English\" code=\"en\" />\n"
				+ "\t\t<translation-language name=\"Dutch\" code=\"nl\" />\n"
				+ "\t</language-map>\n"
				+ "</dlb-project>\n", StandardCharsets.UTF_8);
		for (Map.Entry<String, String> file : files.entrySet()) {
			Path path = root.toPath().resolve(file.getKey());
			Files.createDirectories(path.getParent());
			Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
		}
		return root.toPath().resolve("dlb-project.xml");
	}

	private static final String HELLO =
			"title: Start\nspeaker: Robin\n---\nHello.\n[[Bye.|Farewell]]\n===\n"
			+ "title: Farewell\nspeaker: Robin\n---\nGoodbye.\n===\n";

	@Test
	public void loadsTheSameProjectAsTheParser() throws Exception {
		Path metadataFile = example("project-test");
		ExecutableProject project = ExecutableProject.load(metadataFile);
		ExecutableProject parsed = parse(metadataFile).getProject();

		assertNotNull(parsed);
		assertEquals(parsed.getDialogues().keySet(), project.getDialogues().keySet());
		assertEquals(parsed.getSourceDialogues().keySet(), project.getSourceDialogues().keySet());
		assertEquals(parsed.getTranslations().keySet(), project.getTranslations().keySet());
		assertEquals(List.of("en", "nl-NL", "pt-PT"), project.getLanguages());
		assertNotNull(project.getMetaData());
		assertEquals("default-test", project.getMetaData().getSlug());
		assertTrue(project.getWarnings().isEmpty());
	}

	@Test
	public void aProjectWithParseErrorsFailsWithAllOfThem() throws Exception {
		Path metadataFile = example("error-test");
		Map<String, List<ParseException>> expected = parse(metadataFile).getParseErrors();
		assertFalse(expected.isEmpty());

		ProjectParseException e = assertThrows(ProjectParseException.class,
				() -> ExecutableProject.load(metadataFile));

		assertEquals(expected.keySet(), e.getErrors().keySet());
		int count = 0;
		for (Map.Entry<String, List<ParseException>> file : expected.entrySet()) {
			List<ParseException> errors = e.getErrors().get(file.getKey());
			assertEquals(file.getValue().size(), errors.size());
			for (ParseException error : errors) {
				assertTrue(e.getMessage(),
						e.getMessage().contains(file.getKey() + ": " + error.getMessage()));
				count++;
			}
		}
		assertTrue(e.getMessage(), e.getMessage().startsWith("Project at " + metadataFile
				+ " has " + count + " parse errors:"));
	}

	@Test
	public void aSingleParseErrorIsReportedInTheSingular() throws Exception {
		Path metadataFile = writeProject(Map.of("en/broken.dlb",
				"title: Start\nspeaker: Robin\n---\nHello.\n[[Bye.|Nowhere]]\n===\n"));

		ProjectParseException e = assertThrows(ProjectParseException.class,
				() -> ExecutableProject.load(metadataFile));

		assertEquals(1, e.getErrors().size());
		assertEquals(1, e.getErrors().values().iterator().next().size());
		assertTrue(e.getMessage(), e.getMessage().contains(" has 1 parse error:"));
		assertTrue(e.getMessage(), e.getMessage().contains("Nowhere"));
	}

	@Test
	public void warningsDontStopTheProjectFromLoading() throws Exception {
		Path metadataFile = writeProject(Map.of("en/hello.dlb", HELLO, "nl/hello.json", ""));

		ExecutableProject project = ExecutableProject.load(metadataFile);

		assertEquals(parse(metadataFile).getWarnings(), project.getWarnings());
		assertEquals(1, project.getWarnings().size());
		assertEquals(List.of("Empty translation file"),
				project.getWarnings().values().iterator().next());
		assertThrows(UnsupportedOperationException.class,
				() -> project.getWarnings().clear());
	}

	@Test
	public void aMissingProjectFileFails() {
		Path missing = folder.getRoot().toPath().resolve("missing/dlb-project.xml");
		assertThrows(IOException.class, () -> ExecutableProject.load(missing));
	}

	@Test
	public void theErrorsCantBeChanged() throws Exception {
		ProjectParseException e = assertThrows(ProjectParseException.class,
				() -> ExecutableProject.load(example("error-test")));
		assertThrows(UnsupportedOperationException.class, () -> e.getErrors().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> e.getErrors().values().iterator().next().clear());
	}
}

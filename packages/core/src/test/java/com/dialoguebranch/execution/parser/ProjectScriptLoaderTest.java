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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.exception.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests the on-disk layout validation {@link ProjectScriptLoader} runs at construction: the
 * project metadata must declare a source language, the source-language folder may hold only
 * {@code .dlb} files, and translation-language folders may hold only {@code .json} files.
 * Anything else at the project root is ignored.
 */
public class ProjectScriptLoaderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final String METADATA = """
		<dlb-project name="Layout Test" version="1">
			<language-map>
				<source-language code="en" name="English"/>
				<translation-language code="nl" name="Dutch"/>
			</language-map>
		</dlb-project>
		""";

	private File writeProject(String metadata) throws IOException {
		File metadataFile = new File(tmp.getRoot(), "dlb-project.xml");
		Files.writeString(metadataFile.toPath(), metadata);
		return metadataFile;
	}

	private void writeFile(String relativePath, String content) throws IOException {
		File file = new File(tmp.getRoot(), relativePath);
		assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
		Files.writeString(file.toPath(), content);
	}

	@Test
	public void acceptsAWellFormedProject() throws Exception {
		File metadataFile = writeProject(METADATA);
		writeFile("en/intro.dlb", "title: Start\nspeaker: X\n---\n\n===\n");
		writeFile("en/sub/more.dlb", "title: Start\nspeaker: X\n---\n\n===\n");
		writeFile("nl/intro.json", "{}");
		writeFile("notes.txt", "ignored — not inside a language folder");

		ProjectScriptLoader loader = new ProjectScriptLoader(metadataFile);

		assertEquals(3, loader.listDialogueBranchFiles().size());
	}

	@Test
	public void rejectsMetadataWithoutASourceLanguage() throws Exception {
		File metadataFile = writeProject("""
			<dlb-project name="No Source" version="1">
				<language-map>
				</language-map>
			</dlb-project>
			""");
		writeFile("en/intro.dlb", "title: Start\nspeaker: X\n---\n\n===\n");

		ParseException ex = assertThrows(ParseException.class,
				() -> new ProjectScriptLoader(metadataFile));
		assertTrue(ex.getMessage(), ex.getMessage().contains("source language"));
	}

	@Test
	public void rejectsAScriptFileInATranslationFolder() throws Exception {
		File metadataFile = writeProject(METADATA);
		writeFile("en/intro.dlb", "title: Start\nspeaker: X\n---\n\n===\n");
		writeFile("nl/intro.dlb", "title: Start\nspeaker: X\n---\n\n===\n");

		ParseException ex = assertThrows(ParseException.class,
				() -> new ProjectScriptLoader(metadataFile));
		assertTrue(ex.getMessage(), ex.getMessage().contains("intro.dlb"));
		assertTrue(ex.getMessage(), ex.getMessage().contains("\"nl\""));
	}

	@Test
	public void rejectsATranslationFileInTheSourceFolder() throws Exception {
		File metadataFile = writeProject(METADATA);
		writeFile("en/intro.json", "{}");

		ParseException ex = assertThrows(ParseException.class,
				() -> new ProjectScriptLoader(metadataFile));
		assertTrue(ex.getMessage(), ex.getMessage().contains("intro.json"));
	}

	@Test
	public void rejectsAStrayFileInALanguageFolder() throws Exception {
		File metadataFile = writeProject(METADATA);
		writeFile("en/intro.dlb", "title: Start\nspeaker: X\n---\n\n===\n");
		writeFile("nl/readme.md", "not a translation file");

		ParseException ex = assertThrows(ParseException.class,
				() -> new ProjectScriptLoader(metadataFile));
		assertTrue(ex.getMessage(), ex.getMessage().contains("readme.md"));
	}

	@Test
	public void ignoresDotFilesInLanguageFolders() throws Exception {
		File metadataFile = writeProject(METADATA);
		writeFile("en/intro.dlb", "title: Start\nspeaker: X\n---\n\n===\n");
		writeFile("en/.DS_Store", "junk");
		writeFile("nl/intro.json", "{}");

		ProjectScriptLoader loader = new ProjectScriptLoader(metadataFile);

		assertEquals(2, loader.listDialogueBranchFiles().size());
	}
}

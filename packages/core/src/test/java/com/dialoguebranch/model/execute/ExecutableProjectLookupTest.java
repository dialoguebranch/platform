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

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.common.ResourceType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests looking up a dialogue of a project by name and language with {@link
 * ExecutableProject#getDialogue(String, String)} and {@link
 * ExecutableProject#getDialoguePointer(String, String)}, and {@link
 * ExecutableProject#getSourceLanguage()} (#473). The language rules are the Web Service's: an
 * exact match, otherwise the source language.
 */
public class ExecutableProjectLookupTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static ExecutableProject project;

	@BeforeClass
	public static void loadProject() throws Exception {
		project = ExecutableProject.load(Path.of(ExecutableProjectLookupTest.class.getClassLoader()
				.getResource("project-test/dlb-project.xml").toURI()));
	}

	/** Finds the dialogue in the project's map of dialogues, without the methods under test. */
	private static Dialogue inMap(ExecutableProject project, String language, String name) {
		for (Map.Entry<ResourcePointer, Dialogue> entry : project.getDialogues().entrySet()) {
			if (language.equals(entry.getKey().getLanguage())
					&& name.equals(entry.getKey().getDialogueName()))
				return entry.getValue();
		}
		throw new AssertionError("No " + name + " in " + language);
	}

	@Test
	public void theSourceLanguageComesFromTheMetadata() {
		assertEquals("en", project.getSourceLanguage());
	}

	@Test
	public void theMetadataDecidesTheSourceLanguageEvenWithoutDialogues() throws Exception {
		ProjectMetaData metaData = new ProjectMetaData("T", "", "1");
		metaData.setLanguageMap(new LanguageMap(new Language("Frisian", "fy")));
		assertEquals("fy", new ExecutableProject(metaData).getSourceLanguage());
	}

	@Test
	public void withoutMetadataTheSourceLanguageIsThatOfTheSourceDialogues() {
		ExecutableProject bare = new ExecutableProject();
		assertNull(bare.getSourceLanguage());

		Map<ResourcePointer, Dialogue> source = new LinkedHashMap<>();
		source.put(new ResourcePointer("de", "hello", ResourceType.SCRIPT), new Dialogue("hello"));
		bare.setSourceDialogues(source);
		bare.setDialogues(new LinkedHashMap<>(source));
		assertNull(bare.getMetaData());
		assertEquals("de", bare.getSourceLanguage());
	}

	@Test
	public void withoutALanguageTheDialogueIsInTheSourceLanguage() throws Exception {
		assertSame(inMap(project, "en", "basic"), project.getDialogue("basic"));
		assertSame(inMap(project, "en", "basic"), project.getDialogue("basic", null));
		assertEquals("en", project.getDialoguePointer("basic", null).getLanguage());
	}

	@Test
	public void aTranslationIsChosenByItsExactLanguage() throws Exception {
		Dialogue dutch = project.getDialogue("basic", "nl-NL");
		assertSame(inMap(project, "nl-NL", "basic"), dutch);
		assertNotSame(project.getDialogue("basic"), dutch);
		assertSame(inMap(project, "pt-PT", "basic"), project.getDialogue("basic", "pt-PT"));
		assertSame(inMap(project, "en", "basic"), project.getDialogue("basic", "en"));

		ResourcePointer pointer = project.getDialoguePointer("basic", "nl-NL");
		assertEquals("nl-NL", pointer.getLanguage());
		assertEquals("basic", pointer.getDialogueName());
		assertSame(dutch, project.getDialogues().get(pointer));
	}

	@Test
	public void anUnknownLanguageFallsBackToTheSourceLanguageWithoutGuessing() throws Exception {
		assertSame(inMap(project, "en", "basic"), project.getDialogue("basic", "nl"));
		assertSame(inMap(project, "en", "basic"), project.getDialogue("basic", "fr"));
		assertEquals("en", project.getDialoguePointer("basic", "nl").getLanguage());
	}

	@Test
	public void aDialogueInAFolderIsFoundWithOrWithoutALeadingSlash() throws Exception {
		Dialogue imoen = inMap(project, "en", "bg1/bg1-candlekeep-imoen");
		assertSame(imoen, project.getDialogue("bg1/bg1-candlekeep-imoen"));
		assertSame(imoen, project.getDialogue("/bg1/bg1-candlekeep-imoen"));
		assertSame(inMap(project, "nl-NL", "bg1/test/scripts/menu"),
				project.getDialogue("/bg1/test/scripts/menu", "nl-NL"));
		// "menu" at the root and "bg1/test/scripts/menu" are different dialogues
		assertNotSame(project.getDialogue("menu"), project.getDialogue("bg1/test/scripts/menu"));
	}

	@Test
	public void anUnknownDialogueFailsWithDialogueNotFound() {
		for (String name : new String[] { "nonexistent", "Basic", "bg1-candlekeep-imoen",
				"/", "" }) {
			ExecutionException e = assertThrows(ExecutionException.class,
					() -> project.getDialogue(name, "nl-NL"));
			assertEquals(ExecutionException.Type.DIALOGUE_NOT_FOUND, e.getType());
			assertEquals("Dialogue not found: " + name, e.getMessage());
		}
	}

	@Test
	public void aDialogueWithoutTheTranslationIsInTheSourceLanguage() throws Exception {
		File root = folder.newFolder();
		Files.writeString(root.toPath().resolve("dlb-project.xml"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<dlb-project name=\"T\" slug=\"t\" version=\"1\">\n"
				+ "\t<description>T</description>\n\t<language-map>\n"
				+ "\t\t<source-language name=\"English\" code=\"en\" />\n"
				+ "\t\t<translation-language name=\"Dutch\" code=\"nl\" />\n"
				+ "\t</language-map>\n</dlb-project>\n", StandardCharsets.UTF_8);
		String script = "title: Start\nspeaker: Robin\n---\nHello.\n===\n";
		Files.createDirectories(root.toPath().resolve("en"));
		Files.createDirectories(root.toPath().resolve("nl"));
		Files.writeString(root.toPath().resolve("en/translated.dlb"), script);
		Files.writeString(root.toPath().resolve("en/untranslated.dlb"), script);
		Files.writeString(root.toPath().resolve("nl/translated.json"),
				"{\"Robin\":{\"Hello.\":\"Hallo.\"}}");
		ExecutableProject small = ExecutableProject.load(root.toPath().resolve("dlb-project.xml"));

		assertEquals("nl", small.getDialoguePointer("translated", "nl").getLanguage());
		assertEquals("en", small.getDialoguePointer("untranslated", "nl").getLanguage());
		assertSame(inMap(small, "en", "untranslated"), small.getDialogue("untranslated", "nl"));
		assertTrue(small.getDialogue("translated", "nl").getStartNode().getBody().toString()
				.contains("Hallo."));
	}
}

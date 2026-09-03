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

package com.dialoguebranch.editing.writer;

import com.dialoguebranch.execution.parser.ProjectMetaDataParser;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * A {@link ProjectMetaData} written by {@link ProjectMetaDataWriter} parses back to an equal
 * object via {@link ProjectMetaDataParser} — both sides live in {@code packages/core}, so the
 * round-trip belongs here. Part of the #90 area-E test hardening (#158).
 */
public class ProjectMetaDataWriterTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void writeThenParseRoundTripsEveryField() throws Exception {
		LanguageMap languages = new LanguageMap(new Language("English", "en"), List.of(
				new Language("Dutch", "nl-NL"),
				new Language("Portuguese", "pt-PT")));
		ProjectMetaData original =
				new ProjectMetaData("Demo Project", "A round-trip fixture.", "3");
		original.setSlug("demo-project");
		original.setLanguageMap(languages);

		ByteArrayOutputStream xml = new ByteArrayOutputStream();
		ProjectMetaDataWriter.writeToXMLFile(xml, original);

		File file = tmp.newFile("dlb-project.xml");
		Files.write(file.toPath(), xml.toByteArray());
		ProjectMetaData parsed = ProjectMetaDataParser.parse(file);

		assertEquals("Demo Project", parsed.getName());
		assertEquals("A round-trip fixture.", parsed.getDescription());
		assertEquals("3", parsed.getVersion());
		assertEquals("demo-project", parsed.getSlug());

		LanguageMap parsedLanguages = parsed.getLanguageMap();
		assertEquals("en", parsedLanguages.getSourceLanguage().getCode());
		assertEquals("English", parsedLanguages.getSourceLanguage().getName());
		assertEquals(List.of("nl-NL", "pt-PT"),
				parsedLanguages.getTranslationLanguages().stream()
						.map(Language::getCode).toList());
	}

	@Test
	public void writeOmitsTheSlugWhenThereIsNone() throws Exception {
		ProjectMetaData original = new ProjectMetaData("No Slug", "", "1");
		original.setLanguageMap(new LanguageMap(new Language("English", "en")));

		ByteArrayOutputStream xml = new ByteArrayOutputStream();
		ProjectMetaDataWriter.writeToXMLFile(xml, original);

		String written = xml.toString();
		assertEquals("no slug attribute expected: " + written, false, written.contains("slug="));
	}

	@Test
	public void writeRequiresALanguageMap() {
		ProjectMetaData original = new ProjectMetaData("No Languages", "", "1");
		assertThrows(NullPointerException.class,
				() -> ProjectMetaDataWriter.writeToXMLFile(new ByteArrayOutputStream(), original));
	}
}

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
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.LanguageMap;
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
 * Tests for {@link ProjectMetaDataParser}: a valid {@code dlb-project.xml} parses into the
 * expected {@link ProjectMetaData}, and malformed metadata — a bad root element, a missing
 * project name, a language with no code, or two languages sharing a code — is rejected with a
 * {@link ParseException}.
 */
public class ProjectMetaDataParserTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File metadataFile(String xml) throws IOException {
		File file = tmp.newFile("dlb-project.xml");
		Files.writeString(file.toPath(), xml);
		return file;
	}

	@Test
	public void parsesValidMetadata() throws Exception {
		ProjectMetaData meta = ProjectMetaDataParser.parse(metadataFile("""
			<dlb-project name="Demo" version="3">
				<description>A demo project.</description>
				<language-map>
					<source-language code="en" name="English"/>
					<translation-language code="nl-NL" name="Nederlands"/>
					<translation-language code="pt-PT" name="Portugues"/>
				</language-map>
			</dlb-project>
			"""));

		assertEquals("Demo", meta.getName());
		assertEquals("3", meta.getVersion());
		LanguageMap languages = meta.getLanguageMap();
		assertEquals("en", languages.getSourceLanguage().getCode());
		assertEquals(2, languages.getTranslationLanguages().size());
	}

	@Test
	public void rejectsWrongRootElement() throws Exception {
		File file = metadataFile("<not-a-project name=\"Demo\"></not-a-project>");
		assertThrows(ParseException.class, () -> ProjectMetaDataParser.parse(file));
	}

	@Test
	public void rejectsMissingProjectName() throws Exception {
		File file = metadataFile("<dlb-project version=\"1\"></dlb-project>");
		assertThrows(ParseException.class, () -> ProjectMetaDataParser.parse(file));
	}

	@Test
	public void rejectsDuplicateTranslationLanguageCode() throws Exception {
		File file = metadataFile("""
			<dlb-project name="Demo">
				<language-map>
					<source-language code="en" name="English"/>
					<translation-language code="nl-NL" name="Nederlands"/>
					<translation-language code="nl-NL" name="Nederlands (again)"/>
				</language-map>
			</dlb-project>
			""");
		ParseException ex = assertThrows(ParseException.class,
				() -> ProjectMetaDataParser.parse(file));
		assertTrue(ex.getMessage().toLowerCase().contains("duplicate"));
	}

	@Test
	public void rejectsTranslationLanguageSharingSourceLanguageCodeCaseInsensitively()
			throws Exception {
		File file = metadataFile("""
			<dlb-project name="Demo">
				<language-map>
					<source-language code="en" name="English"/>
					<translation-language code="EN" name="English (again)"/>
				</language-map>
			</dlb-project>
			""");
		assertThrows(ParseException.class, () -> ProjectMetaDataParser.parse(file));
	}

	@Test
	public void rejectsLanguageWithoutCode() throws Exception {
		File file = metadataFile("""
			<dlb-project name="Demo">
				<language-map>
					<source-language code="en" name="English"/>
					<translation-language name="Nameless"/>
				</language-map>
			</dlb-project>
			""");
		assertThrows(ParseException.class, () -> ProjectMetaDataParser.parse(file));
	}
}

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
package com.dialoguebranch.model.common;

import com.dialoguebranch.editing.writer.ProjectMetaDataWriter;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.parser.ProjectMetaDataParser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * A project's speakers in its metadata (#393): reading them, writing them, and the rules they
 * follow.
 */
public class SpeakersTest {

	private static String project(String speakers) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<dlb-project name="Speakers" version="1">
				  <description>With speakers.</description>
				  <language-map>
				    <source-language name="English" code="en"/>
				  </language-map>
				  %s
				</dlb-project>
				""".formatted(speakers);
	}

	private static ProjectMetaData parse(String xml) throws Exception {
		return ProjectMetaDataParser.parse(
				new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	private static String parseError(String speakers) {
		return assertThrows(ParseException.class, () -> parse(project(speakers))).getMessage();
	}

	@Test
	public void speakersAreReadWithTheirGenderAndDefault() throws Exception {
		ProjectMetaData metaData = parse(project("""
				<speakers>
				  <speaker name="Martin McOwl" gender="male" default="true"/>
				  <speaker name="Anna" gender="female"/>
				  <speaker name="Narrator"/>
				</speakers>
				"""));

		assertEquals(List.of(
				new Speaker("Martin McOwl", Speaker.Gender.MALE, true),
				new Speaker("Anna", Speaker.Gender.FEMALE, false),
				new Speaker("Narrator", Speaker.Gender.UNDEFINED, false)),
				metaData.getSpeakers());
		assertEquals(Speaker.Gender.FEMALE, metaData.getSpeaker("Anna").gender());
		assertNull(metaData.getSpeaker("Bob"));
	}

	@Test
	public void aProjectWithoutSpeakersHasNone() throws Exception {
		assertTrue(parse(project("")).getSpeakers().isEmpty());
		assertTrue(parse(project("<speakers/>")).getSpeakers().isEmpty());
	}

	@Test
	public void speakersSurviveWritingAndReadingBack() throws Exception {
		ProjectMetaData original = parse(project("""
				<speakers>
				  <speaker name="Martin McOwl" gender="male" default="true"/>
				  <speaker name="Anna" gender="female"/>
				  <speaker name="Narrator" gender="undefined"/>
				</speakers>
				"""));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ProjectMetaDataWriter.writeToXMLFile(out, original);
		String written = out.toString(StandardCharsets.UTF_8);

		assertEquals(original.getSpeakers(), parse(written).getSpeakers());
		// Only what says something is written: no gender="undefined", no default="false"
		assertTrue(written, written.contains("<speaker name=\"Narrator\"/>"));
	}

	@Test
	public void aProjectWithoutSpeakersIsWrittenWithoutASpeakersElement() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ProjectMetaDataWriter.writeToXMLFile(out, parse(project("")));
		assertFalse(out.toString(StandardCharsets.UTF_8).contains("<speakers"));
	}

	@Test
	public void invalidSpeakersAreRefused() {
		assertTrue(parseError("<speakers><speaker name=\"\"/></speakers>").contains("empty"));
		assertTrue(parseError("<speakers><speaker name=\" Anna\"/></speakers>").contains("spaces"));
		assertTrue(parseError("<speakers><speaker name=\"_user\"/></speakers>").contains("reserved"));
		assertTrue(parseError("<speakers><speaker name=\"Anna\"/><speaker name=\"Anna\"/></speakers>")
				.contains("more than once"));
		assertTrue(parseError("<speakers><speaker name=\"A\" default=\"true\"/>"
				+ "<speaker name=\"B\" default=\"true\"/></speakers>").contains("Only one"));
		assertTrue(parseError("<speakers><speaker name=\"Anna\" gender=\"other\"/></speakers>")
				.contains("Unknown gender"));
		assertTrue(parseError("<speakers><speaker name=\"Anna\" default=\"yes\"/></speakers>")
				.contains("'default'"));
		assertTrue(parseError("<speakers><speaker gender=\"male\"/></speakers>")
				.contains("Missing attribute 'name'"));
		assertTrue(parseError("<speakers><person name=\"Anna\"/></speakers>")
				.contains("Unexpected element"));
	}

	@Test
	public void genderIsReadInAnyCase() throws Exception {
		assertEquals(Speaker.Gender.FEMALE, parse(project(
				"<speakers><speaker name=\"Anna\" gender=\"Female\"/></speakers>"))
				.getSpeakers().get(0).gender());
	}
}

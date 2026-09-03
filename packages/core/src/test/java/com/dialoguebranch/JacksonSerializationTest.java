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

package com.dialoguebranch;

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.i18n.TranslationFile;
import com.dialoguebranch.model.execute.protocol.DialogueAction;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.DialogueStatement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies that Jackson serialization and deserialization works correctly across the three main
 * areas of the codebase that use Jackson: {@link TranslationFile}, {@link DialogueStatement},
 * and {@link Variable}.
 */
public class JacksonSerializationTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final ObjectMapper mapper = new ObjectMapper();

	// --------------------------------------------------------- //
	// -------------------- TranslationFile -------------------- //
	// --------------------------------------------------------- //

	/**
	 * Verifies that a {@link TranslationFile} can be written to disk and read back, with all
	 * speaker/term/translation entries preserved.
	 */
	@Test
	public void testTranslationFileRoundTrip() throws Exception {
		TranslationFile original = new TranslationFile("basic");
		original.addTerm("Martin McOwl", "Hello!", "Hallo!");
		original.addTerm("Martin McOwl", "Goodbye.", "Tot ziens.");
		original.addTerm("_user", "Yes.", "Ja.");

		File dir = tmp.newFolder("translations");
		original.writeToDirectory(dir);

		File written = new File(dir, "basic.json");
		assertTrue("Translation JSON file was not created", written.exists());

		TranslationFile loaded = new TranslationFile("basic");
		loaded.readFromFile(written);

		assertEquals("Martin McOwl entry count",
				2, loaded.getContentMap().get("Martin McOwl").size());
		assertEquals("Hallo!", loaded.getContentMap().get("Martin McOwl").get("Hello!"));
		assertEquals("Tot ziens.", loaded.getContentMap().get("Martin McOwl").get("Goodbye."));
		assertEquals("Ja.", loaded.getContentMap().get("_user").get("Yes."));
	}

	/**
	 * Verifies that a {@link TranslationFile} written via {@code writeToFile} can be read back
	 * with all content intact.
	 */
	@Test
	public void testTranslationFileWriteToFileRoundTrip() throws Exception {
		TranslationFile original = new TranslationFile("test");
		original.addTerm("Speaker", "term1", "vertaling1");

		File file = tmp.newFile("test.json");
		original.writeToFile(file);

		TranslationFile loaded = new TranslationFile("test");
		loaded.readFromFile(file);

		assertEquals("vertaling1", loaded.getContentMap().get("Speaker").get("term1"));
	}

	// ----------------------------------------------------------- //
	// -------------------- DialogueStatement -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Verifies that a {@link DialogueStatement} containing a {@link DialogueStatement.TextSegment}
	 * survives a full JSON serialization/deserialization round-trip.
	 */
	@Test
	public void testDialogueStatementTextSegmentRoundTrip() throws Exception {
		DialogueStatement.TextSegment text = new DialogueStatement.TextSegment("Hello, world!");

		String json = mapper.writeValueAsString(text);
		DialogueStatement.TextSegment restored =
				mapper.readValue(json, DialogueStatement.TextSegment.class);

		assertEquals("Hello, world!", restored.getText());
		assertEquals(DialogueStatement.SegmentType.TEXT, restored.getSegmentType());
	}

	/**
	 * Verifies that a {@link DialogueStatement.InputSegment} serializes and deserializes correctly,
	 * including the custom {@link DialogueStatement.InputSegmentSerializer} and
	 * {@link DialogueStatement.InputSegmentDeserializer}.
	 */
	@Test
	public void testDialogueStatementInputSegmentRoundTrip() throws Exception {
		DialogueStatement.InputSegment input = new DialogueStatement.InputSegment(
				"text", "Enter your name", java.util.Map.of());

		String json = mapper.writeValueAsString(input);

		assertTrue("Serialized JSON should contain inputType", json.contains("inputType"));
		assertTrue("Serialized JSON should contain segmentType", json.contains("segmentType"));

		DialogueStatement.InputSegment restored =
				mapper.readValue(json, DialogueStatement.InputSegment.class);

		assertEquals("text", restored.getInputType());
		assertEquals("Enter your name", restored.getDescription());
		assertEquals(DialogueStatement.SegmentType.INPUT, restored.getSegmentType());
	}

	/**
	 * Verifies that a {@link DialogueStatement} with a list of mixed segments (text + input)
	 * round-trips correctly through JSON, exercising the polymorphic
	 * {@link DialogueStatement.SegmentDeserializer}.
	 */
	@Test
	public void testDialogueStatementWithMixedSegmentsRoundTrip() throws Exception {
		DialogueStatement statement = new DialogueStatement();

		statement.getSegments().add(new DialogueStatement.TextSegment("What is your name?"));
		statement.getSegments().add(new DialogueStatement.InputSegment(
				"text", "Your name", java.util.Map.of()));

		String json = mapper.writeValueAsString(statement);
		DialogueStatement restored = mapper.readValue(json, DialogueStatement.class);

		List<DialogueStatement.Segment> segments = restored.getSegments();
		assertEquals("Expected 2 segments", 2, segments.size());
		assertSame(DialogueStatement.SegmentType.TEXT, segments.get(0).getSegmentType());
		assertSame(DialogueStatement.SegmentType.INPUT, segments.get(1).getSegmentType());
		assertEquals("What is your name?",
				((DialogueStatement.TextSegment) segments.get(0)).getText());
		assertEquals("text",
				((DialogueStatement.InputSegment) segments.get(1)).getInputType());
	}

	// -------------------------------------------------- //
	// -------------------- Variable -------------------- //
	// -------------------------------------------------- //

	/**
	 * Verifies that a {@link Variable} serializes to JSON and deserializes back correctly,
	 * exercising the {@code @JsonProperty}-annotated constructor.
	 */
	@Test
	public void testVariableRoundTrip() throws Exception {
		Variable original = new Variable("userName", "Alice", ZonedDateTime.now(), VariableUpdatedSource.UNKNOWN);

		String json = mapper.writeValueAsString(original);

		assertTrue("Serialized JSON should contain 'name'", json.contains("\"name\""));
		assertTrue("Serialized JSON should contain 'value'", json.contains("\"value\""));
		assertTrue("Serialized JSON should contain 'updatedTime'",
				json.contains("\"updatedTime\""));

		Variable restored = mapper.readValue(json, Variable.class);

		assertEquals("userName", restored.getName());
		assertEquals("Alice", restored.getValue());
		assertNotNull(restored.getUpdatedTime());
	}

	/**
	 * Verifies that a {@link Variable} holding a numeric value deserializes correctly — Jackson
	 * may deserialize numbers as {@code Integer} or {@code Long} depending on the version.
	 */
	@Test
	public void testVariableWithNumericValue() throws Exception {
		Variable original = new Variable("score", 42, ZonedDateTime.now(), VariableUpdatedSource.UNKNOWN);
		String json = mapper.writeValueAsString(original);
		Variable restored = mapper.readValue(json, Variable.class);

		assertEquals("score", restored.getName());
		assertEquals(42, ((Number) restored.getValue()).intValue());
	}

	// -------------------------------------------------------- //
	// -------------------- DialogueAction -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Verifies that a {@link DialogueAction} serializes to the expected JSON shape
	 * ({@code type} / {@code value} / {@code parameters}) and round-trips back through the
	 * {@code @JsonCreator} constructor.
	 */
	@Test
	public void testDialogueActionRoundTrip() throws Exception {
		DialogueAction action = new DialogueAction("link", "https://example.org",
				new java.util.LinkedHashMap<>(java.util.Map.of("target", "_blank")));

		String json = mapper.writeValueAsString(action);
		assertTrue(json.contains("\"type\":\"link\""));
		assertTrue(json.contains("\"value\":\"https://example.org\""));
		assertTrue(json.contains("\"parameters\":{\"target\":\"_blank\"}"));

		DialogueAction restored = mapper.readValue(json, DialogueAction.class);
		assertEquals("link", restored.getType());
		assertEquals("https://example.org", restored.getValue());
		assertEquals("_blank", restored.getParameters().get("target"));
	}

	/**
	 * Verifies that a {@link DialogueAction} with no {@code parameters} field in the JSON
	 * deserializes to an empty (non-null) parameters map.
	 */
	@Test
	public void testDialogueActionDeserializesWithoutParameters() throws Exception {
		DialogueAction restored = mapper.readValue(
				"{\"type\":\"generic\",\"value\":\"x\"}", DialogueAction.class);
		assertEquals("generic", restored.getType());
		assertTrue(restored.getParameters().isEmpty());
	}

	// --------------------------------------------------------- //
	// -------------------- DialogueMessage -------------------- //
	// --------------------------------------------------------- //

	/**
	 * Verifies that a fully-populated {@link DialogueMessage} serializes to the expected JSON
	 * shape and round-trips back through the {@code @JsonCreator} constructor.
	 */
	@Test
	public void testDialogueMessageRoundTrip() throws Exception {
		DialogueStatement statement = new DialogueStatement();
		statement.getSegments().add(new DialogueStatement.TextSegment("Hello"));

		DialogueMessage message = new DialogueMessage("greeting", "Start", "log-1", 3,
				"Robin", statement, new java.util.ArrayList<>());

		String json = mapper.writeValueAsString(message);
		assertTrue(json.contains("\"dialogue\":\"greeting\""));
		assertTrue(json.contains("\"node\":\"Start\""));
		assertTrue(json.contains("\"loggedDialogueId\":\"log-1\""));
		assertTrue(json.contains("\"loggedInteractionIndex\":3"));
		assertTrue(json.contains("\"speaker\":\"Robin\""));

		DialogueMessage restored = mapper.readValue(json, DialogueMessage.class);
		assertEquals("greeting", restored.getDialogue());
		assertEquals("Start", restored.getNode());
		assertEquals("log-1", restored.getLoggedDialogueId());
		assertEquals(3, restored.getLoggedInteractionIndex());
		assertEquals("Robin", restored.getSpeaker());
		assertEquals("Hello", ((DialogueStatement.TextSegment)
				restored.getStatement().getSegments().get(0)).getText());
		assertTrue(restored.getReplies().isEmpty());
	}

	/**
	 * Verifies that a {@link DialogueMessage} with null optional fields
	 * ({@code loggedDialogueId}, {@code speaker}) and no {@code replies} round-trips, with the
	 * reply list coming back empty rather than null.
	 */
	@Test
	public void testDialogueMessageRoundTripWithNullOptionals() throws Exception {
		DialogueMessage message = new DialogueMessage("d", "n", null, 0, null,
				new DialogueStatement(), null);

		DialogueMessage restored = mapper.readValue(
				mapper.writeValueAsString(message), DialogueMessage.class);
		assertNull(restored.getLoggedDialogueId());
		assertNull(restored.getSpeaker());
		assertNotNull(restored.getReplies());
		assertTrue(restored.getReplies().isEmpty());
	}
}

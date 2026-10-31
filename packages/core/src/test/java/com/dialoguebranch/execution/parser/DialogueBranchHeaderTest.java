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
import com.dialoguebranch.execution.parser.DialogueBranchHeader.LineKind;
import com.dialoguebranch.execution.parser.DialogueBranchHeader.Tag;
import com.dialoguebranch.i18n.SourceTranslatable;
import com.dialoguebranch.i18n.TranslatableExtractor;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.execute.Node;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link DialogueBranchHeader}, and for how {@link DialogueBranchParser} applies it: a
 * node header is a set of {@code key: value} tags and nothing else.
 */
public class DialogueBranchHeaderTest {

	@Test
	public void readsTagLines() {
		DialogueBranchHeader.Line line = DialogueBranchHeader.readLine("  speaker :  Robin  ");
		assertEquals(LineKind.TAG, line.kind());
		assertEquals("speaker", line.key());
		assertEquals("Robin", line.value());
		assertEquals(3, line.keyColumn());
		assertEquals(14, line.valueColumn());
	}

	@Test
	public void slashesAndColonsArePartOfTheValue() {
		DialogueBranchHeader.Line line = DialogueBranchHeader.readLine(
				"image: https://example.com/a.png // not a comment");
		assertEquals("https://example.com/a.png // not a comment", line.value());
		assertEquals("10:30", DialogueBranchHeader.readLine("time: 10:30").value());
	}

	@Test
	public void classifiesLinesThatAreNotTags() {
		assertEquals(LineKind.BLANK, DialogueBranchHeader.readLine("   ").kind());
		assertEquals(LineKind.NO_SEPARATOR, DialogueBranchHeader.readLine("just text").kind());
		assertEquals(LineKind.EMPTY_KEY, DialogueBranchHeader.readLine("  : value").kind());
		assertEquals(LineKind.INVALID_KEY, DialogueBranchHeader.readLine("// Note: x").kind());
		assertEquals(LineKind.INVALID_KEY, DialogueBranchHeader.readLine("my key: x").kind());
		assertEquals(LineKind.TAG, DialogueBranchHeader.readLine("color_ID-2:").kind());
		assertEquals("", DialogueBranchHeader.readLine("tags:").value());
	}

	@Test
	public void readsTagsLeniently() {
		Map<String, String> tags = DialogueBranchHeader.read(
				"title: Start\r\n\nnot a tag\nspeaker: Robin\ntitle: Ignored\n// Note: x");
		assertEquals(List.of("title", "speaker"), List.copyOf(tags.keySet()));
		assertEquals("Start", tags.get("title"));
		assertEquals("Robin", DialogueBranchHeader.get("speaker: Robin", "speaker"));
		assertNull(DialogueBranchHeader.get("speaker: Robin", "title"));
		assertTrue(DialogueBranchHeader.read(null).isEmpty());
	}

	@Test
	public void setReplacesOnlyThatTagsLine() {
		String header = "title: Start\nnot a tag\nposition: 0,0\nspeaker: Robin";
		assertEquals("title: Start\nnot a tag\nposition: 10,20\nspeaker: Robin",
				DialogueBranchHeader.set(header, "position", "10,20"));
		assertEquals("title: Start\nnot a tag\nposition: 0,0\nspeaker: Robin\nnodeColor: #0369a1",
				DialogueBranchHeader.set(header, "nodeColor", "#0369a1"));
		assertEquals("title: New", DialogueBranchHeader.set("", "title", " New "));
	}

	@Test
	public void writeAndReadRoundTrip() {
		List<Tag> tags = List.of(new Tag("title", "Start"), new Tag("speaker", "Robin"),
				new Tag("comment", "see https://example.com // really"), new Tag("2", "a digit key"),
				new Tag("tags", ""));
		String header = DialogueBranchHeader.write(tags);
		assertEquals("title: Start\nspeaker: Robin\ncomment: see https://example.com // really\n"
				+ "2: a digit key\ntags: ", header);
		assertEquals(tags, DialogueBranchHeader.readTags(header));
	}

	@Test
	public void refusesTagsThatCannotBeWritten() {
		assertThrows(IllegalArgumentException.class,
				() -> DialogueBranchHeader.set("", "my key", "x"));
		assertThrows(IllegalArgumentException.class,
				() -> DialogueBranchHeader.set("", "comment", "two\nlines"));
		assertThrows(IllegalArgumentException.class,
				() -> DialogueBranchHeader.write(List.of(new Tag("", "x"))));
		assertThrows(IllegalArgumentException.class,
				() -> DialogueBranchHeader.write(List.of(new Tag("title", "A"), new Tag("title", "B"))));
		assertThrows(IllegalArgumentException.class,
				() -> DialogueBranchHeader.write(List.of(new Tag("title", null))));
	}

	@Test
	public void checksAHeaderStrictly() {
		assertTrue(DialogueBranchHeader.check("title: Start\n\nspeaker: Robin").isEmpty());
		assertEquals(List.of("line 2: not a 'key: value' line", "line 3: invalid header key: // Note",
				"line 4: empty header key", "line 5: duplicate header key: title"),
				DialogueBranchHeader.check("title: Start\njust text\n// Note: x\n: v\ntitle: Again"));
	}

	// -------------------- How the parser applies it -------------------- //

	private static List<ParseException> parse(String script) throws IOException {
		try (DialogueBranchParser parser = new DialogueBranchParser("test",
				new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)))) {
			return parser.readDialogue().getParseErrors();
		}
	}

	private static String errors(String script) throws IOException {
		return parse(script).toString();
	}

	@Test
	public void parserKeepsSlashesInHeaderValues() throws IOException {
		String script = "title: Start\nspeaker: Robin\nimage: https://example.com/a.png\n---\nHi.\n===\n";
		assertTrue(parse(script).isEmpty());
	}

	@Test
	public void parserRejectsATrailingCommentOnATitle() throws IOException {
		String errors = errors("title: Start // entry point\nspeaker: Robin\n---\nHi.\n===\n");
		assertTrue(errors, errors.contains("Invalid node title: Start // entry point"));
	}

	@Test
	public void parserRejectsCommentLinesInAHeader() throws IOException {
		String inside = errors("title: Start\n// a note\nspeaker: Robin\n---\nHi.\n===\n");
		assertTrue(inside, inside.contains("Comments are not allowed in a node header"));
		assertTrue(inside, inside.contains("line 2"));

		String above = errors("title: Start\nspeaker: Robin\n---\nHi.\n===\n"
				+ "// about the next node\ntitle: Next\nspeaker: Robin\n---\nBye.\n===\n");
		assertTrue(above, above.contains("Comments are not allowed in a node header"));
		assertTrue(above, above.contains("line 6"));
	}

	@Test
	public void parserRejectsInvalidHeaderKeys() throws IOException {
		String errors = errors("title: Start\nspeaker: Robin\nmy key: x\n---\nHi.\n===\n");
		assertTrue(errors, errors.contains("Invalid header name: my key"));
	}

	@Test
	public void commentIsAReservedTagForAuthorsOnly() throws IOException {
		assertTrue(Arrays.asList(DialogueBranchConstants.DLB_RESERVED_HEADER_TAGS).contains("comment"));
		String script = "title: Start\nspeaker: Robin\ncomment: Greets the user; see https://example.com // ok\n"
				+ "---\nHello.\n===\n";
		ParserResult result;
		try (DialogueBranchParser parser = new DialogueBranchParser("test",
				new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)))) {
			result = parser.readDialogue();
		}
		assertTrue(result.getParseErrors().isEmpty());
		Node node = result.getDialogue().getNodes().get(0);
		assertEquals("Greets the user; see https://example.com // ok",
				node.getHeader().getOptionalTags().get("comment"));
		List<SourceTranslatable> translatables = new TranslatableExtractor().extractFromNode(node);
		assertEquals(List.of("Hello."), translatables.stream()
				.map(t -> t.translatable().toExportFriendlyString()).toList());
	}

	@Test
	public void parserStillAcceptsAPreambleAndATrailingCommentBlock() throws IOException {
		String script = "// About this script.\n\ntitle: Start\nspeaker: Robin\n---\nHi. // a body comment\n"
				+ "===\n\n// the end\n";
		assertTrue(parse(script).isEmpty());
	}
}

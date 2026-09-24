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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Characterizes {@link NodeBody#addSegment}'s normalization invariant (#293, a prerequisite for
 * #210's immutable-model refactor): adjacent {@link NodeBody.TextSegment}s are always merged into
 * one, while a {@link NodeBody.CommandSegment} in between prevents that — the specific behavior
 * a {@code Builder} replacing today's incremental {@code add*()} calls must preserve exactly.
 *
 * @author Harm op den Akker
 */
public class NodeBodyTest {

	private static Dialogue parse(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("nodebody", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue();
		}
	}

	@Test
	public void adjacentTextSegmentsMergeIntoOne() {
		NodeBody body = new NodeBody();
		body.addSegment(new NodeBody.TextSegment(new VariableString("Hello ")));
		body.addSegment(new NodeBody.TextSegment(new VariableString("world.")));

		assertEquals(1, body.getSegments().size());
		assertEquals("Hello world.", body.getSegments().get(0).toString());
	}

	@Test
	public void threeConsecutiveTextSegmentsMergeIntoOne() {
		NodeBody body = new NodeBody();
		body.addSegment(new NodeBody.TextSegment(new VariableString("One ")));
		body.addSegment(new NodeBody.TextSegment(new VariableString("two ")));
		body.addSegment(new NodeBody.TextSegment(new VariableString("three.")));

		assertEquals(1, body.getSegments().size());
		assertEquals("One two three.", body.getSegments().get(0).toString());
	}

	/**
	 * At parse time, {@code BodyParser} collects a contiguous run of text/variable tokens into a
	 * single {@code addSegment} call, so a {@code <<set>>} command between two statement lines is
	 * what actually forces two separate {@code TextSegment}s to exist around it — this is the real
	 * parse-time shape of "text separated by a command segment does not merge".
	 */
	@Test
	public void textSegmentsSeparatedByACommandSegmentDoNotMerge() throws Exception {
		Dialogue dialogue = parse("""
			title: Start
			tags:
			speaker: Robot
			---
			Hello <<set $x = 1>> world.
			===
			""");

		List<NodeBody.Segment> segments = dialogue.getNodeById("Start").getBody().getSegments();
		assertEquals(3, segments.size());
		assertTrue(segments.get(0) instanceof NodeBody.TextSegment);
		assertTrue(segments.get(1) instanceof NodeBody.CommandSegment);
		assertTrue(segments.get(2) instanceof NodeBody.TextSegment);
		assertEquals("Hello", segments.get(0).toString().trim());
		assertEquals("world.", segments.get(2).toString().trim());
	}
}

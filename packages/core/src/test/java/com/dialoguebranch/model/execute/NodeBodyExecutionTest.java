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
import com.dialoguebranch.model.execute.command.ActionCommand;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Characterizes {@link NodeBody#execute}'s behavior (#293, a prerequisite for #210's immutable-
 * model refactor): segment/reply ordering surviving both parse and execute, {@code <<if>>}
 * resolving to all three output cardinalities (zero/one/many segments), and — the specific risk
 * #293 calls out — that a command resolving to *zero* segments (like {@code <<set>>}, or an
 * unmatched {@code <<if>>} with no {@code <<else>>}) lets the text on either side of it merge at
 * execution time, even though {@link NodeBodyTest} shows the same text stays in separate segments
 * at parse time (a real {@code CommandSegment} sits between them there; nothing does once it
 * resolves to nothing).
 *
 * @author Harm op den Akker
 */
public class NodeBodyExecutionTest {

	private static Node parseStartNode(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("nodebodyexec", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue().getNodeById("Start");
		}
	}

	private static ResolvedNodeBody execute(Node node, Map<String,Object> variables)
			throws Exception {
		NodeBody body = (NodeBody) node.getBody();
		return body.execute(variables, true);
	}

	@Test
	public void adjacentTextAroundAZeroOutputSetCommandMergesAtExecutionTime() throws Exception {
		// Contrast with NodeBodyTest.textSegmentsSeparatedByACommandSegmentDoNotMerge(), which
		// shows this exact fixture stays as 3 distinct segments at parse time.
		Node node = parseStartNode("""
			title: Start
			tags:
			speaker: Robot
			---
			Hello <<set $x = 1>> world.
			===
			""");

		ResolvedNodeBody processed = execute(node, new LinkedHashMap<>());

		assertEquals(1, processed.getSegments().size());
		assertEquals("Hello  world.", processed.getSegments().get(0).toString());
	}

	@Test
	public void unmatchedIfWithNoElseContributesZeroSegmentsAndSurroundingTextMerges()
			throws Exception {
		Node node = parseStartNode("""
			title: Start
			tags:
			speaker: Robot
			---
			Before.
			<<if $x == true>>
			Should not appear.
			<<endif>>
			After.
			===
			""");

		ResolvedNodeBody processed = execute(node, Map.of("x", false));

		String text = processed.getSegments().get(0).toString();
		assertEquals(1, processed.getSegments().size());
		assertFalse("if-clause content leaked through despite not matching: " + text,
				text.contains("Should not appear"));
		assertTrue("Expected text before the if, got: " + text, text.contains("Before."));
		assertTrue("Expected text after the if, got: " + text, text.contains("After."));
	}

	@Test
	public void matchedIfWithElseContributesOnlyItsOwnClause() throws Exception {
		Node node = parseStartNode("""
			title: Start
			tags:
			speaker: Robot
			---
			<<if $x == true>>
			True branch.
			<<else>>
			False branch.
			<<endif>>
			===
			""");

		ResolvedNodeBody processed = execute(node, Map.of("x", false));

		assertEquals(1, processed.getSegments().size());
		String text = processed.getSegments().get(0).toString();
		assertTrue(text, text.contains("False branch."));
		assertFalse(text, text.contains("True branch."));
	}

	/**
	 * The "many" cardinality: a matched clause whose own body has multiple segments, including a
	 * real {@link ActionCommand} — which (unlike {@code <<set>>}) always contributes its own
	 * {@code CommandSegment} and so cannot merge with the text around it, proving the resolved
	 * segments keep their individual boundaries where a genuine boundary exists.
	 */
	@Test
	public void matchedIfWithMultiSegmentClauseContributesAllOfThem() throws Exception {
		Node node = parseStartNode("""
			title: Start
			tags:
			speaker: Robot
			---
			<<if $x == true>>
			Before action.
			<<action type="generic" value="thing">>
			After action.
			<<endif>>
			===
			""");

		ResolvedNodeBody processed = execute(node, Map.of("x", true));

		List<NodeBody.Segment> segments = processed.getSegments();
		assertEquals(3, segments.size());
		assertTrue(segments.get(0) instanceof NodeBody.TextSegment);
		assertTrue(segments.get(1) instanceof NodeBody.CommandSegment);
		assertTrue(segments.get(2) instanceof NodeBody.TextSegment);
		assertTrue(segments.get(0).toString().contains("Before action."));
		assertTrue(segments.get(2).toString().contains("After action."));
	}

	@Test
	public void repliesKeepTheirOriginalOrderThroughExecute() throws Exception {
		Node node = parseStartNode("""
			title: Start
			tags:
			speaker: Robot
			---
			Choose:

			[[First.|Start]]
			[[Second.|Start]]
			[[Third.|Start]]
			===
			""");

		ResolvedNodeBody processed = execute(node, new LinkedHashMap<>());

		List<Reply> replies = processed.getReplies();
		assertEquals(3, replies.size());
		assertTrue(replies.get(0).getStatement().toString().contains("First."));
		assertTrue(replies.get(1).getStatement().toString().contains("Second."));
		assertTrue(replies.get(2).getStatement().toString().contains("Third."));
	}
}

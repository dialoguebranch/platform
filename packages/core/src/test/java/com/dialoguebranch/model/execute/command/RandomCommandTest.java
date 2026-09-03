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

package com.dialoguebranch.model.execute.command;

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.NodeBody;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Parses {@code <<random>> … <<or>> … <<endrandom>>} out of a node body and checks clause
 * parsing, weights, deterministic clause selection (via the package-private {@code setRandom}
 * seam), {@code toString()}, {@code clone()}, the variable-name delegates, and the unterminated
 * error path. Part of the #90 area-B test hardening (#155).
 */
public class RandomCommandTest {

	private static final String TWO_CLAUSES = """
		title: Start
		tags:
		speaker: Robot
		---
		<<random>>
		Option one.
		<<or>>
		Option two.
		<<endrandom>>
		[[Go.|End]]
		===
		title: End
		tags:
		speaker:
		---

		===
		""";

	private static Dialogue parse(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("random", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue();
		}
	}

	private static List<ParseException> parseErrors(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("bad", new StringReader(dlb))) {
			return parser.readDialogue().getParseErrors();
		}
	}

	private static RandomCommand randomCommandOf(Dialogue dialogue) {
		for (NodeBody.Segment segment : dialogue.getNodeById("Start").getBody().getSegments()) {
			if (segment instanceof NodeBody.CommandSegment cmd
					&& cmd.getCommand() instanceof RandomCommand random) {
				return random;
			}
		}
		throw new AssertionError("no <<random>> command in the Start node");
	}

	/** A {@link Random} whose {@code nextFloat()} always returns {@code value}. */
	private static Random fixedNextFloat(float value) {
		return new Random() {
			@Override
			public float nextFloat() {
				return value;
			}
		};
	}

	@Test
	public void parsesOneClausePerBranch() throws Exception {
		RandomCommand command = randomCommandOf(parse(TWO_CLAUSES));

		assertEquals(2, command.getClauses().size());
		assertEquals(1f, command.getClauses().get(0).getWeight(), 0f);
		assertEquals(1f, command.getClauses().get(1).getWeight(), 0f);
		assertTrue(command.getClauses().get(0).getStatement().toString().contains("Option one."));
	}

	@Test
	public void weightAttributesApplyToTheirClause() throws Exception {
		RandomCommand command = randomCommandOf(parse("""
			title: Start
			tags:
			speaker: Robot
			---
			<<random weight="2">>
			Heavy.
			<<or weight="3">>
			Heavier.
			<<endrandom>>
			[[Go.|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			"""));

		assertEquals(2f, command.getClauses().get(0).getWeight(), 0f);
		assertEquals(3f, command.getClauses().get(1).getWeight(), 0f);
	}

	@Test
	public void aLowRandomDrawSelectsTheFirstClause() throws Exception {
		RandomCommand command = randomCommandOf(parse(TWO_CLAUSES));
		command.setRandom(fixedNextFloat(0.1f));

		NodeBody processed = new NodeBody();
		command.executeBodyCommand(new LinkedHashMap<>(), processed);

		assertTrue(processed.toString(), processed.toString().contains("Option one."));
		assertFalse(processed.toString(), processed.toString().contains("Option two."));
	}

	@Test
	public void aHighRandomDrawSelectsTheLastClause() throws Exception {
		RandomCommand command = randomCommandOf(parse(TWO_CLAUSES));
		command.setRandom(fixedNextFloat(0.99f));

		NodeBody processed = new NodeBody();
		command.executeBodyCommand(new LinkedHashMap<>(), processed);

		assertTrue(processed.toString(), processed.toString().contains("Option two."));
		assertFalse(processed.toString(), processed.toString().contains("Option one."));
	}

	@Test
	public void toStringRoundTripsTheRandomBlock() throws Exception {
		String s = randomCommandOf(parse(TWO_CLAUSES)).toString();
		assertTrue(s, s.contains("<<random"));
		assertTrue(s, s.contains("<<or"));
		assertTrue(s, s.contains("<<endrandom>>"));
		assertTrue(s, s.contains("Option one."));
		assertTrue(s, s.contains("Option two."));
	}

	@Test
	public void cloneDeepCopiesTheClauses() throws Exception {
		RandomCommand original = randomCommandOf(parse(TWO_CLAUSES));
		RandomCommand copy = original.clone();

		assertEquals(2, copy.getClauses().size());
		copy.getClauses().get(0).setWeight(9f);
		assertEquals(1f, original.getClauses().get(0).getWeight(), 0f);
	}

	@Test
	public void variableNameDelegatesWalkTheClauseStatements() throws Exception {
		RandomCommand command = randomCommandOf(parse("""
			title: Start
			tags:
			speaker: Robot
			---
			<<random>>
			<<set $picked = "one">>
			You get $picked.
			<<or>>
			<<set $picked = "two">>
			<<endrandom>>
			[[Go.|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			"""));

		Set<String> writes = new HashSet<>();
		command.getWriteVariableNames(writes);
		assertTrue(writes.contains("picked"));

		Set<String> reads = new HashSet<>();
		command.getReadVariableNames(reads);
		assertTrue(reads.contains("picked"));
	}

	@Test
	public void anUnterminatedRandomBlockIsAParseError() throws Exception {
		List<ParseException> errors = parseErrors("""
			title: Start
			tags:
			speaker: Robot
			---
			<<random>>
			Option one.
			<<or>>
			Option two.
			[[Go.|End]]
			===
			title: End
			tags:
			speaker:
			---

			===
			""");
		assertFalse(errors.isEmpty());
		assertTrue(errors.toString(), errors.toString().toLowerCase().contains("random"));
	}
}

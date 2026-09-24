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
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.ResourcePointer;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Characterizes today's actual parser error-handling behavior — a mix of accumulate-and-continue
 * (at the per-node and per-dialogue granularity) and fail-fast (within a single node, where
 * {@code DialogueBranchParser.readNode()} wraps header and body parsing in one {@code try/catch},
 * so only the first error found anywhere in a node ever surfaces) — before that fail-fast part is
 * changed to accumulate-and-continue (#211).
 *
 * <p>These tests are a prerequisite for #211, not independent test coverage: they pin down the
 * current baseline (including its real limitation, one error per node) so a future change to
 * accumulate-and-continue can be judged against it — both for what must keep working (errors
 * across different nodes are already accumulated today) and for what should improve (multiple
 * errors within one node) without regressing into a flood of misleading cascading errors.</p>
 *
 * @author Harm op den Akker
 */
public class ParserErrorHandlingBaselineTest {

	private ProjectParserResult parse(Map<String, String> scriptsByName) throws IOException {
		return new ProjectParser(new MapScriptLoader(scriptsByName)).parse();
	}

	private int totalErrorCount(ProjectParserResult result) {
		int count = 0;
		for (List<ParseException> errors : result.getParseErrors().values())
			count += errors.size();
		return count;
	}

	@Test
	public void testSingleIsolatedErrorIsReported() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n<<sett>>\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected exactly one parse error", 1, totalErrorCount(result));
		assertTrue("Expected the error to mention the unrecognized command name, got: " +
				result.getParseErrors(), result.getParseErrors().toString().contains("sett"));
	}

	/**
	 * Characterizes today's real limitation: {@code readNode()} wraps header and body parsing in a
	 * single {@code try/catch}, so only the first error anywhere in a node is ever reported — a
	 * second, independent error later in the same node's body is silently never reached. After
	 * #211, both should be reported.
	 */
	@Test
	public void testOnlyFirstOfMultipleErrorsInSameNodeIsReportedToday() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n<<sett>>\n<<ift>>\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected only the first error to be reported today", 1,
				totalErrorCount(result));
		String allErrors = result.getParseErrors().toString();
		assertTrue("Expected the first (reached) error to be reported, got: " + allErrors,
				allErrors.contains("sett"));
		assertFalse("Did not expect the second (never-reached) error to be reported, got: " +
				allErrors, allErrors.contains("ift"));
	}

	/**
	 * Regression guard: errors in different nodes of the same dialogue are already accumulated
	 * today, at per-node granularity ({@code readDialogue()}'s loop continues past a failed node).
	 * This must keep working through any #211 change.
	 */
	@Test
	public void testErrorsInDifferentNodesAreBothReportedToday() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n<<sett>>\n===\n" +
				"title: Second\nspeaker: Narrator\n---\n<<ift>>\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected one error per broken node", 2, totalErrorCount(result));
		String allErrors = result.getParseErrors().toString();
		assertTrue("Expected the error from node Start, got: " + allErrors,
				allErrors.contains("Start") && allErrors.contains("sett"));
		assertTrue("Expected the error from node Second, got: " + allErrors,
				allErrors.contains("Second") && allErrors.contains("ift"));
	}

	@Test
	public void testMalformedNodeHeaderIsReported() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker Narrator\n---\nHello.\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected exactly one parse error", 1, totalErrorCount(result));
		assertTrue("Expected the error to flag the missing header separator, got: " +
				result.getParseErrors(),
				result.getParseErrors().toString().contains("Character : not found"));
	}

	@Test
	public void testMalformedNodeBodyCommandIsReported() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n<<sett>>\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected exactly one parse error", 1, totalErrorCount(result));
		assertTrue("Expected the error to flag the unrecognized command, got: " +
				result.getParseErrors(),
				result.getParseErrors().toString().contains("Unexpected command: sett"));
	}

	/**
	 * A reply's own statement (what the user "says" when choosing it) only allows {@code input} —
	 * see {@link ReplyParser#parseStatement}'s whitelist comment (#208).
	 */
	@Test
	public void testMalformedReplyStatementIsReported() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n[[<<set $x = 1>>|Start]]\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected exactly one parse error", 1, totalErrorCount(result));
		assertTrue("Expected the error to flag \"set\" as invalid in a reply statement, got: " +
				result.getParseErrors(),
				result.getParseErrors().toString().contains("Unexpected command: set"));
	}

	/**
	 * A reply's post-pipe commands only allow {@code action}/{@code set}, deliberately excluding
	 * {@code if}/{@code random} — see {@link ReplyParser#parseCommands}'s whitelist comment (#208).
	 */
	@Test
	public void testMalformedReplyCommandIsReported() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n[[Continue.|Start|<<if>>]]\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected exactly one parse error", 1, totalErrorCount(result));
		assertTrue("Expected the error to flag \"if\" as invalid in a reply's command section, " +
				"got: " + result.getParseErrors(),
				result.getParseErrors().toString().contains("Unexpected command: if"));
	}

	/**
	 * Documents today's baseline for the scenario most likely to make accumulate-and-continue
	 * actively worse than today if implemented carelessly: a single root-cause error (here, an
	 * unclosed {@code <<}) positioned early in a node body, followed by otherwise-valid content.
	 * Today's fail-fast parsing reports exactly one error for the whole node — once #211 lands,
	 * re-running this exact fixture must still produce one clear error, not a flood of spurious
	 * follow-on errors from a poorly-chosen resynchronization point.
	 */
	@Test
	public void testUnclosedCommandEarlyInBodyDoesNotCascadeToday() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n<<set $x = 1\nHello there.\n===\n");

		ProjectParserResult result = parse(scripts);

		assertEquals("Expected a single error, not a cascade of follow-on errors", 1,
				totalErrorCount(result));
	}

	/**
	 * A minimal {@link ScriptLoader} serving dialogue scripts (no translations) from an in-memory
	 * map of dialogue name to {@code .dlb} script content, all in a single unnamed source
	 * language — enough to exercise {@link ProjectParser} without touching the filesystem.
	 */
	private static class MapScriptLoader implements ScriptLoader {

		private final Map<String, String> scripts;

		MapScriptLoader(Map<String, String> scripts) {
			this.scripts = scripts;
		}

		@Override
		public List<ResourcePointer> listDialogueBranchFiles() {
			List<ResourcePointer> pointers = new ArrayList<>();
			for (String dialogueName : scripts.keySet())
				pointers.add(new ResourcePointer("en", dialogueName, ResourceType.SCRIPT));
			return pointers;
		}

		@Override
		public Reader openFile(ResourcePointer fileDescription) throws IOException {
			String content = scripts.get(fileDescription.getDialogueName());
			if (content == null)
				throw new IOException("Script not found: " + fileDescription.getDialogueName());
			return new StringReader(content);
		}
	}
}

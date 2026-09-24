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
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Real-file counterpart to the fine-grained, inline-fixture tests in
 * {@link ParserErrorHandlingBaselineTest}, {@link ExternalNodePointerValidationTest}, and
 * {@link OrphanedNodeDetectionTest}: parses the bundled {@code error-test} example project (a real
 * file tree, loaded the same way {@link com.dialoguebranch.ProjectTest} loads {@code project-test})
 * and checks that every scenario those inline tests characterize individually is also caught when
 * the exact same content lives in real {@code .dlb} files on disk.
 *
 * <p>This does not replace those inline tests — a shared multi-file project can't isolate one
 * construct per test the way an inline fixture can — it's a coarser, real-project sanity check on
 * top.</p>
 *
 * @author Harm op den Akker
 */
public class ErrorTestProjectParsingTest {

	private static ProjectParserResult result;

	@BeforeClass
	public static void loadProject() throws Exception {
		URL xmlUrl = ErrorTestProjectParsingTest.class.getClassLoader()
				.getResource("error-test/dlb-project.xml");
		assertNotNull("error-test/dlb-project.xml not found on test classpath", xmlUrl);
		File projectFile = new File(xmlUrl.toURI());

		ProjectScriptLoader loader = new ProjectScriptLoader(projectFile);
		result = new ProjectParser(loader).parse();
	}

	private List<ParseException> errorsFor(String dialogueName) {
		return result.getParseErrors().getOrDefault("en/" + dialogueName, List.of());
	}

	@Test
	public void testUnknownCommandIsReported() {
		String errors = errorsFor("unknown-command").toString();
		assertTrue("Expected an error for the unrecognized command, got: " + errors,
				errors.contains("Unexpected command: sett"));
	}

	@Test
	public void testMultipleErrorsInOneNodeAreBothReported() {
		List<ParseException> errors = errorsFor("multiple-errors-in-one-node");
		assertEquals("Expected both errors to be reported", 2, errors.size());
		String message = errors.toString();
		assertTrue("Expected the first error, got: " + message,
				message.contains("sett"));
		assertTrue("Expected the second error too, got: " + message,
				message.contains("foo"));
	}

	@Test
	public void testErrorsInDifferentNodesAreBothReported() {
		List<ParseException> errors = errorsFor("errors-in-different-nodes");
		assertEquals("Expected one error per broken node", 2, errors.size());
		String message = errors.toString();
		assertTrue("Expected the error from node Start, got: " + message,
				message.contains("Start") && message.contains("sett"));
		assertTrue("Expected the error from node Second, got: " + message,
				message.contains("Second") && message.contains("foo"));
	}

	@Test
	public void testMalformedHeaderIsReported() {
		String errors = errorsFor("malformed-header").toString();
		assertTrue("Expected the missing header separator to be flagged, got: " + errors,
				errors.contains("Character : not found"));
	}

	@Test
	public void testMalformedReplyStatementIsReported() {
		String errors = errorsFor("malformed-reply-statement").toString();
		assertTrue("Expected \"set\" to be flagged as invalid in a reply statement, got: " + errors,
				errors.contains("Unexpected command: set"));
	}

	@Test
	public void testMalformedReplyCommandIsReported() {
		String errors = errorsFor("malformed-reply-command").toString();
		assertTrue("Expected \"if\" to be flagged as invalid in a reply's command section, got: " +
				errors, errors.contains("Unexpected command: if"));
	}

	@Test
	public void testUnclosedCommandDoesNotCascade() {
		List<ParseException> errors = errorsFor("unclosed-command");
		assertEquals("Expected a single error, not a cascade of follow-on errors", 1,
				errors.size());
	}

	/**
	 * The riskiest resync path in #211: a recognized command's own internal validation (not
	 * {@link CommandParser}'s name check) fails after {@code ExpressionCommand.readCommandContent}
	 * already consumed through this command's own {@code >>} — no explicit skip is added (or
	 * needed) at the call site, unlike the unrecognized-command-name case. Two independent
	 * instances prove neither the first command's failure swallows the second, nor does anything
	 * double-skip past it.
	 */
	@Test
	public void testMalformedCommandExpressionsAreBothReported() {
		List<ParseException> errors = errorsFor("malformed-command-expression");
		assertEquals("Expected both malformed-expression errors to be reported", 2, errors.size());
		String message = errors.toString();
		assertEquals("Expected both errors to be the same kind (not an assignment)", 2,
				countOccurrences(message, "is not an assignment"));
	}

	@Test
	public void testErrorInsideNestedIfIsReported() {
		List<ParseException> errors = errorsFor("error-inside-nested-if");
		assertEquals("Expected exactly one error, from inside the nested if body", 1,
				errors.size());
		assertTrue("Expected the unrecognized command from inside <<if>> to be flagged, got: " +
				errors, errors.toString().contains("sett"));
	}

	@Test
	public void testTooManyReplySectionsIsReportedAndDoesNotOverConsume() {
		List<ParseException> errors = errorsFor("too-many-reply-sections");
		assertEquals("Expected both the section-count error and the unrelated error after it", 2,
				errors.size());
		String message = errors.toString();
		assertTrue("Expected the exceeded-sections error, got: " + message,
				message.contains("Exceeded maximum"));
		assertTrue("Expected the unrelated error after it too, got: " + message,
				message.contains("sett"));
	}

	@Test
	public void testContentAfterReplyIsReportedAndDoesNotOverConsume() {
		List<ParseException> errors = errorsFor("content-after-reply");
		assertEquals("Expected both the content-after-reply error and the one after it", 2,
				errors.size());
		String message = errors.toString();
		assertTrue("Expected \"Found content after reply\", got: " + message,
				message.contains("Found content after reply"));
		// The trailing <<sett>> is itself a command in "after reply" position, so it hits that
		// same check before its own (invalid) name is ever considered — still proof resync here
		// neither swallows nor duplicates the second error.
		assertTrue("Expected the second, unrelated-position error too, got: " + message,
				message.contains("Found << after reply"));
	}

	@Test
	public void testCommandAfterReplyIsReportedAndDoesNotOverConsume() {
		List<ParseException> errors = errorsFor("command-after-reply");
		assertEquals("Expected both command-after-reply errors, not one merged or dropped", 2,
				errors.size());
		// Both commands sit after the reply, so both independently hit the same check — this
		// proves resync reports each occurrence separately rather than merging or dropping one.
		assertEquals("Expected two separate \"Found << after reply\" errors", 2,
				countOccurrences(errors.toString(), "Found << after reply"));
	}

	@Test
	public void testDuplicateAutoForwardReplyIsReportedAndDoesNotOverConsume() {
		List<ParseException> errors = errorsFor("duplicate-autoforward-reply");
		assertEquals("Expected both the duplicate-autoforward error and the one after it", 2,
				errors.size());
		String message = errors.toString();
		assertTrue("Expected \"Found more than one autoforward reply\", got: " + message,
				message.contains("Found more than one autoforward reply"));
		// The trailing <<sett>> is itself a command in "after reply" position (two replies were
		// already added by the time it's reached), so it hits that check first.
		assertTrue("Expected the second, unrelated-position error too, got: " + message,
				message.contains("Found << after reply"));
	}

	@Test
	public void testMalformedCommandNameTokenIsReportedAndDoesNotOverConsume() {
		List<ParseException> errors = errorsFor("malformed-command-name-token");
		assertEquals("Expected both the empty-command error and the one after it", 2,
				errors.size());
		String message = errors.toString();
		assertTrue("Expected \"Expected command name\", got: " + message,
				message.contains("Expected command name"));
		assertTrue("Expected the unrelated error after it too, got: " + message,
				message.contains("sett"));
	}

	private int countOccurrences(String haystack, String needle) {
		int count = 0;
		int index = 0;
		while ((index = haystack.indexOf(needle, index)) != -1) {
			count++;
			index += needle.length();
		}
		return count;
	}

	@Test
	public void testBrokenInternalPointerIsReported() {
		String errors = errorsFor("broken-pointers").toString();
		assertTrue("Expected the broken internal pointer to be flagged, got: " + errors,
				errors.contains("NoSuchNode"));
	}

	@Test
	public void testBrokenExternalPointerToUnknownDialogueIsReported() {
		String errors = errorsFor("broken-pointers").toString();
		assertTrue("Expected the unknown target dialogue to be flagged, got: " + errors,
				errors.contains("no-such-dialogue"));
	}

	@Test
	public void testBrokenExternalPointerToUnknownNodeIsReported() {
		String errors = errorsFor("broken-pointers").toString();
		assertTrue("Expected the unknown target node to be flagged, got: " + errors,
				errors.contains("pointer-target") && errors.contains("NoSuchNode"));
	}

	@Test
	public void testPointerTargetItselfHasNoErrors() {
		assertTrue("pointer-target.dlb is meant to be valid, got: " + errorsFor("pointer-target"),
				errorsFor("pointer-target").isEmpty());
	}

	@Test
	public void testOrphanedNodeIsReportedAsWarning() {
		assertTrue("Did not expect any parse errors for orphaned-node, got: " +
				errorsFor("orphaned-node"), errorsFor("orphaned-node").isEmpty());
		List<String> warnings = result.getWarnings().getOrDefault("en/orphaned-node", List.of());
		assertFalse("Expected a warning for the orphaned node", warnings.isEmpty());
		assertTrue("Expected the warning to mention the orphaned node, got: " + warnings,
				warnings.toString().contains("Disconnected"));
	}
}

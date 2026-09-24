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

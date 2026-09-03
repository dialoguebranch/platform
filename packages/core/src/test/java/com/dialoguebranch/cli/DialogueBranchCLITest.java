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

package com.dialoguebranch.cli;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link DialogueBranchCLI}'s non-interactive entry points (added in #105):
 * {@code --validate}, {@code --execute}, {@code --help} and the usage-error paths, asserting the
 * process exit status. The interactive menu loop is out of scope. Part of the #90 area-E test
 * hardening (#158).
 */
public class DialogueBranchCLITest {

	private static final int EXIT_OK = 0;
	private static final int EXIT_PARSE_ERROR = 1;
	private static final int EXIT_USAGE_ERROR = 2;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();
	private PrintStream originalOut;
	private PrintStream originalErr;

	@Before
	public void captureStdio() {
		originalOut = System.out;
		originalErr = System.err;
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	@After
	public void restoreStdio() {
		System.setOut(originalOut);
		System.setErr(originalErr);
	}

	private String stdout() {
		return out.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return err.toString(StandardCharsets.UTF_8);
	}

	/** Writes a minimal single-language project and returns its {@code dlb-project.xml}. */
	private File project(String introBody) throws IOException {
		Files.writeString(new File(tmp.getRoot(), "dlb-project.xml").toPath(), """
			<dlb-project name="CLI Test" version="1">
				<language-map>
					<source-language code="en" name="English"/>
				</language-map>
			</dlb-project>
			""");
		File en = tmp.newFolder("en");
		Files.writeString(new File(en, "intro.dlb").toPath(),
				"title: Start\ntags:\nspeaker: Robot\n---\n" + introBody + "\n===\n");
		return new File(tmp.getRoot(), "dlb-project.xml");
	}

	// ---------------------------------------------------------------- //
	// -------------------- validate ---------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void validateACleanProjectExitsZero() throws Exception {
		int code = DialogueBranchCLI.runNonInteractive(
				new String[] {project("This is the whole dialogue.").getAbsolutePath(), "--validate"});
		assertEquals(EXIT_OK, code);
		assertTrue(stdout(), stdout().contains("Summary"));
	}

	@Test
	public void aBarePathDefaultsToValidate() throws Exception {
		int code = DialogueBranchCLI.runNonInteractive(
				new String[] {project("Just a statement.").getAbsolutePath()});
		assertEquals(EXIT_OK, code);
	}

	@Test
	public void validateAProjectWithAParseErrorExitsOne() throws Exception {
		File xml = project("[[Go nowhere.|DoesNotExist]]");
		int code = DialogueBranchCLI.runNonInteractive(
				new String[] {xml.getAbsolutePath(), "--validate"});
		assertEquals(EXIT_PARSE_ERROR, code);
		assertTrue(stderr(), stderr().contains("parse errors"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- execute ---------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void executeARepliesFreeDialogueRunsToCompletion() throws Exception {
		int code = DialogueBranchCLI.runNonInteractive(new String[] {
				project("The end, immediately.").getAbsolutePath(), "--execute", "en", "intro"});
		assertEquals(EXIT_OK, code);
		assertTrue(stdout(), stdout().contains("Dialogue finished."));
	}

	@Test
	public void executeAnUnknownDialogueExitsWithAUsageError() throws Exception {
		int code = DialogueBranchCLI.runNonInteractive(new String[] {
				project("Hi.").getAbsolutePath(), "--execute", "en", "nope"});
		assertEquals(EXIT_USAGE_ERROR, code);
	}

	@Test
	public void executeWithoutLanguageAndDialogueArgsIsAUsageError() throws Exception {
		int code = DialogueBranchCLI.runNonInteractive(new String[] {
				project("Hi.").getAbsolutePath(), "--execute"});
		assertEquals(EXIT_USAGE_ERROR, code);
	}

	@Test
	public void validateAndExecuteCannotBeCombined() throws Exception {
		int code = DialogueBranchCLI.runNonInteractive(new String[] {
				project("Hi.").getAbsolutePath(), "--validate", "--execute", "en", "intro"});
		assertEquals(EXIT_USAGE_ERROR, code);
	}

	// ---------------------------------------------------------------- //
	// -------------------- usage / help ---------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void helpExitsZeroAndPrintsUsage() {
		int code = DialogueBranchCLI.runNonInteractive(new String[] {"--help"});
		assertEquals(EXIT_OK, code);
		assertTrue(stdout(), stdout().contains("Usage:"));
	}

	@Test
	public void aMissingLeadingPathIsAUsageError() {
		int code = DialogueBranchCLI.runNonInteractive(new String[] {"--validate"});
		assertEquals(EXIT_USAGE_ERROR, code);
	}

	@Test
	public void aNonExistentProjectFileIsAUsageError() {
		int code = DialogueBranchCLI.runNonInteractive(
				new String[] {"/no/such/dlb-project.xml", "--validate"});
		assertEquals(EXIT_USAGE_ERROR, code);
	}
}

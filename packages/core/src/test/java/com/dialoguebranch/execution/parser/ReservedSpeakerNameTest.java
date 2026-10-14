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
import com.dialoguebranch.model.common.DialogueBranchConstants;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests that {@code _user}, the speaker name of the user in translation files, is reserved and
 * can't be a node's speaker (#383).
 */
public class ReservedSpeakerNameTest {

	private static List<ParseException> parseErrors(String script) throws IOException {
		try (DialogueBranchParser parser =
					 new DialogueBranchParser("main", new StringReader(script))) {
			return parser.readDialogue().getParseErrors();
		}
	}

	@Test
	public void theUserSpeakerNameIsReserved() {
		assertEquals("_user", DialogueBranchConstants.DLB_USER_SPEAKER);
	}

	@Test
	public void aNodeSpokenByUnderscoreUserIsAParseError() throws IOException {
		List<ParseException> errors = parseErrors(
				"title: Start\nspeaker: _user\n---\nHello.\n===\n");
		assertEquals(1, errors.size());
		assertTrue(errors.get(0).getMessage(),
				errors.get(0).getMessage().contains("Speaker name \"_user\" is reserved"));
		assertTrue(errors.get(0).getMessage(),
				errors.get(0).getMessage().contains("(line 2, column 10)"));
	}

	@Test
	public void theSpeakerOfAnEndNodeIsIgnored() throws IOException {
		assertEquals(List.of(), parseErrors(
				"title: Start\nspeaker: Robin\n---\nBye.\n[[Bye.|End]]\n===\n" +
				"title: End\nspeaker: _user\n---\n===\n"));
	}

	@Test
	public void similarSpeakerNamesAreAllowed() throws IOException {
		for (String speaker : List.of("_User", "user", "_users", "the_user")) {
			assertEquals(speaker, List.of(), parseErrors(
					"title: Start\nspeaker: " + speaker + "\n---\nHello.\n===\n"));
		}
	}
}

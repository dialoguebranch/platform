/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.web.service.controller.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * {@link DialogueListPayload} sorts the dialogue names it is given "folders first" — a {@code /}
 * separates folder levels, sub-folders precede loose dialogues at every level, and entries are
 * then alphabetical (case-insensitive).
 */
class DialogueListPayloadTest {

	@Test
	void foldersSortBeforeLooseDialoguesAtEveryLevel() {
		String[] input = {
				"zebra", "intro/welcome", "apple", "intro/setup",
				"intro/deep/first", "banana", "intro/deep/second",
		};
		String[] expected = {
				"intro/deep/first", "intro/deep/second", "intro/setup", "intro/welcome",
				"apple", "banana", "zebra",
		};
		assertArrayEquals(expected, new DialogueListPayload(input).getDialogueNames());
	}

	@Test
	void plainAlphabeticalWhenThereAreNoFolders() {
		String[] input = { "gamma", "alpha", "Beta" };
		String[] expected = { "alpha", "Beta", "gamma" };
		assertArrayEquals(expected, new DialogueListPayload(input).getDialogueNames());
	}

	@Test
	void orderingWithinAFolderIsCaseInsensitive() {
		String[] input = { "menu/zeta", "menu/Alpha" };
		String[] expected = { "menu/Alpha", "menu/zeta" };
		assertArrayEquals(expected, new DialogueListPayload(input).getDialogueNames());
	}

	@Test
	void doesNotMutateTheCallerArray() {
		String[] input = { "b", "a" };
		new DialogueListPayload(input);
		assertArrayEquals(new String[] { "b", "a" }, input);
	}
}

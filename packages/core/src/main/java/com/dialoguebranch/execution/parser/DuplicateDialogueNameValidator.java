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
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ResourcePointer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Builds the dialogue-name → {@link Dialogue} index {@link ProjectParser} needs to resolve
 * external node pointers and detect orphaned nodes, reporting a parse error for every name
 * defined by more than one script file along the way — used by {@link ProjectParser}, split out
 * as one of its independent validation passes.
 *
 * @author Harm op den Akker
 */
class DuplicateDialogueNameValidator {

	/** Utility class — no instances. */
	private DuplicateDialogueNameValidator() {
	}

	/**
	 * Builds a dialogue-name → {@link Dialogue} index from the given successfully-parsed
	 * dialogues. A project has exactly one source language, so a name resolving to more than one
	 * script file means the same dialogue was placed in two language folders — reported via
	 * {@code onError} as a parse error (against the second file found) rather than silently
	 * picking one; that file's dialogue is excluded from the returned index.
	 *
	 * @param dialogues every successfully-parsed dialogue, keyed by its source file.
	 * @param onError called with the offending file and a {@link ParseException} for each
	 *                duplicate name found.
	 * @return the dialogue-name → {@link Dialogue} index, excluding any duplicate-named entries.
	 */
	static Map<String, Dialogue> buildDialoguesByName(
			Map<ResourcePointer, Dialogue> dialogues,
			BiConsumer<ResourcePointer, ParseException> onError) {
		Map<String, Dialogue> dialoguesByName = new HashMap<>();
		Map<String, ResourcePointer> fileByName = new HashMap<>();
		for (Map.Entry<ResourcePointer, Dialogue> entry : dialogues.entrySet()) {
			String name = entry.getValue().getDialogueName();
			ResourcePointer previous = fileByName.putIfAbsent(name, entry.getKey());
			if (previous != null) {
				onError.accept(entry.getKey(), new ParseException(String.format(
						"Dialogue \"%s\" is defined by more than one script file (found in " +
						"language folders \"%s\" and \"%s\")", name, previous.getLanguage(),
						entry.getKey().getLanguage())));
				continue;
			}
			dialoguesByName.put(name, entry.getValue());
		}
		return dialoguesByName;
	}
}

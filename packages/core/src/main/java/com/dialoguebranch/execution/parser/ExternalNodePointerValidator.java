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
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Validates every {@link ExternalNodePointer} across a project's dialogues — that it references a
 * known dialogue, and a node that actually exists within it — used by {@link ProjectParser}, split
 * out as one of its independent validation passes.
 *
 * @author Harm op den Akker
 */
class ExternalNodePointerValidator {

	/** Utility class — no instances. */
	private ExternalNodePointerValidator() {
	}

	/**
	 * Scans every dialogue that parsed at all (not just error-free ones, so a broken external
	 * pointer sitting next to some other, unrelated error in the same dialogue is still reported)
	 * and reports a parse error via {@code onError} for each {@link ExternalNodePointer} that
	 * references an unknown dialogue or a non-existent node within an otherwise-known one.
	 *
	 * @param allParsedDialogues every dialogue that parsed at all, keyed by its source file.
	 * @param dialoguesByName every successfully-parsed dialogue, keyed by dialogue name (see
	 *                        {@link DuplicateDialogueNameValidator}).
	 * @param onError called with the originating file and a {@link ParseException} for each
	 *                invalid external node pointer found.
	 */
	static void validate(Map<ResourcePointer, Dialogue> allParsedDialogues,
						 Map<String, Dialogue> dialoguesByName,
						 BiConsumer<ResourcePointer, ParseException> onError) {
		for (Map.Entry<ResourcePointer, Dialogue> entry : allParsedDialogues.entrySet()) {
			ResourcePointer fileDescription = entry.getKey();
			Dialogue dlg = entry.getValue();
			for (ExternalNodePointer pointer : dlg.getExternalNodePointers()) {
				Dialogue target = dialoguesByName.get(pointer.getAbsoluteTargetDialogue());
				if (target == null) {
					onError.accept(fileDescription, new ParseException(String.format(
							"Found external node pointer in node %s to unknown dialogue %s",
							pointer.getOriginNodeId(), pointer.getAbsoluteTargetDialogue())));
					continue;
				}
				if (!target.nodeExists(pointer.getTargetNodeId())) {
					onError.accept(fileDescription, new ParseException(String.format(
							"Found external node pointer in node %s to non-existing node %s in " +
							"dialogue %s", pointer.getOriginNodeId(), pointer.getTargetNodeId(),
							pointer.getAbsoluteTargetDialogue())));
				}
			}
		}
	}
}

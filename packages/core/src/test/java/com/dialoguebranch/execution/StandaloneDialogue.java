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
 * Portions of this file are vendored from the rrd-utils library
 * (https://github.com/RoessinghResearch/rrd-utils), used under the MIT License.
 *
 * Copyright (c) 2022 Roessingh Research and Development
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

package com.dialoguebranch.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ResourcePointer;

/**
 * Creates an {@link ActiveDialogue} for a dialogue that isn't part of a project, for tests: its
 * {@link DialogueResolver} finds no other dialogue.
 */
public final class StandaloneDialogue {

	private StandaloneDialogue() { }

	/**
	 * Creates an {@link ActiveDialogue} for the given dialogue, which can't follow a reply into
	 * another dialogue.
	 *
	 * @param pointer the dialogue's name and language.
	 * @param dialogue the dialogue.
	 * @param variableStore the variables it reads and writes.
	 * @return the active dialogue, not started yet.
	 */
	public static ActiveDialogue of(ResourcePointer pointer, Dialogue dialogue,
			VariableStore variableStore) {
		DialogueResolver none = (name, language) -> {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
					"Dialogue not found: " + name);
		};
		return new ActiveDialogue(none, new ResolvedDialogue(pointer, dialogue), variableStore);
	}
}

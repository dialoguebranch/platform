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
import com.dialoguebranch.model.execute.ResourcePointer;

/**
 * Hears when an {@link ActiveDialogue} follows a reply into another dialogue (#473), just before
 * it executes the first node there. An application uses it for what it does at the start of a
 * dialogue, such as reading the variables the new dialogue uses from an outside service, or
 * closing the log of the dialogue that was left.
 *
 * @author Harm op den Akker
 */
@FunctionalInterface
public interface DialogueSwitchListener {

	/**
	 * Called when the {@link ActiveDialogue} has found the dialogue a reply links into, before
	 * it executes the first node there.
	 *
	 * @param from the dialogue that is left.
	 * @param to the dialogue that is entered.
	 * @throws ExecutionException to stop the switch; the {@link ActiveDialogue} then stays in
	 *                            the dialogue it was in, and the exception reaches the caller.
	 */
	void onDialogueSwitch(ResourcePointer from, ResolvedDialogue to) throws ExecutionException;
}

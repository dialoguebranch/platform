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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.model.common.ResourceType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * {@link DialogueState} bundles the five pieces of session state; this checks the constructor
 * stores each and the getters return them unchanged. Part of the #90 area-D test hardening
 * (#157).
 */
public class DialogueStateTest {

	@Test
	public void constructorAndGetters() {
		ResourcePointer pointer = new ResourcePointer("en", "flow", ResourceType.SCRIPT);
		Dialogue definition = new Dialogue("flow");
		LoggedDialogue log = new TestLoggedDialogue();
		ActiveDialogue active = new ActiveDialogue(pointer, definition,
				new VariableStore(new User("u")));

		DialogueState state = new DialogueState(pointer, definition, log, 5, active);

		assertSame(pointer, state.getDialogueDescription());
		assertSame(definition, state.getDialogueDefinition());
		assertSame(log, state.getLoggedDialogue());
		assertEquals(5, state.getLoggedInteractionIndex());
		assertSame(active, state.getActiveDialogue());
	}
}

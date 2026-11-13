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

/**
 * Captures the full runtime state of an in-progress dialogue session. It bundles together the
 * persistent execution log ({@link LoggedDialogue} and the current interaction index) and the
 * live {@link ActiveDialogue} that drives execution, which knows the dialogue it is in ({@link
 * #getDialogueDescription()} and {@link #getDialogueDefinition()}).
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class DialogueState {

	private final LoggedDialogue loggedDialogue;
	private final int loggedInteractionIndex;
	private final ActiveDialogue activeDialogue;

	/**
	 * Creates a fully populated {@link DialogueState}.
	 *
	 * @param loggedDialogue        the {@link LoggedDialogue} tracking the session history.
	 * @param loggedInteractionIndex the index into the logged interaction list pointing to the
	 *                               current step.
	 * @param activeDialogue        the {@link ActiveDialogue} managing live execution state.
	 */
	public DialogueState(LoggedDialogue loggedDialogue, int loggedInteractionIndex,
						 ActiveDialogue activeDialogue) {
		this.loggedDialogue = loggedDialogue;
		this.loggedInteractionIndex = loggedInteractionIndex;
		this.activeDialogue = activeDialogue;
	}

	/**
	 * Returns the {@link ResourcePointer} that identifies the dialogue the {@link ActiveDialogue}
	 * is in.
	 *
	 * @return the dialogue file descriptor.
	 */
	public ResourcePointer getDialogueDescription() {
		return activeDialogue.getDialogueFileDescription();
	}

	/**
	 * Returns the parsed {@link Dialogue} definition the {@link ActiveDialogue} is in.
	 *
	 * @return the dialogue definition.
	 */
	public Dialogue getDialogueDefinition() {
		return activeDialogue.getDialogueDefinition();
	}

	/**
	 * Returns the {@link LoggedDialogue} that records the interaction history for this session.
	 *
	 * @return the logged dialogue.
	 */
	public LoggedDialogue getLoggedDialogue() {
		return loggedDialogue;
	}

	/**
	 * Returns the index into the {@link LoggedDialogue}'s interaction list pointing to the current
	 * step in the conversation.
	 *
	 * @return the current logged interaction index.
	 */
	public int getLoggedInteractionIndex() {
		return loggedInteractionIndex;
	}

	/**
	 * Returns the {@link ActiveDialogue} that manages the live execution state of the current
	 * dialogue session.
	 *
	 * @return the active dialogue.
	 */
	public ActiveDialogue getActiveDialogue() {
		return activeDialogue;
	}

}

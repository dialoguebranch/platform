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

package com.dialoguebranch.model;

import com.dialoguebranch.execution.ActiveDialogue;

/**
 * Captures the full runtime state of an in-progress dialogue session. It bundles together the
 * static dialogue definition ({@link FileDescriptor} and {@link Dialogue}), the persistent
 * execution log ({@link LoggedDialogue} and the current interaction index), and the live
 * {@link ActiveDialogue} wrapper that drives execution.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class DialogueState {

	private final FileDescriptor dialogueDescription;
	private final Dialogue dialogueDefinition;
	private final LoggedDialogue loggedDialogue;
	private final int loggedInteractionIndex;
	private final ActiveDialogue activeDialogue;

	/**
	 * Creates a fully populated {@link DialogueState}.
	 *
	 * @param dialogueDescription   the {@link FileDescriptor} identifying the dialogue file.
	 * @param dialogueDefinition    the parsed {@link Dialogue} definition.
	 * @param loggedDialogue        the {@link LoggedDialogue} tracking the session history.
	 * @param loggedInteractionIndex the index into the logged interaction list pointing to the
	 *                               current step.
	 * @param activeDialogue        the {@link ActiveDialogue} managing live execution state.
	 */
	public DialogueState(FileDescriptor dialogueDescription,
						 Dialogue dialogueDefinition, LoggedDialogue loggedDialogue,
						 int loggedInteractionIndex, ActiveDialogue activeDialogue) {
		this.dialogueDescription = dialogueDescription;
		this.dialogueDefinition = dialogueDefinition;
		this.loggedDialogue = loggedDialogue;
		this.loggedInteractionIndex = loggedInteractionIndex;
		this.activeDialogue = activeDialogue;
	}

	/**
	 * Returns the {@link FileDescriptor} that identifies the dialogue file associated with this
	 * state.
	 *
	 * @return the dialogue file descriptor.
	 */
	public FileDescriptor getDialogueDescription() {
		return dialogueDescription;
	}

	/**
	 * Returns the parsed {@link Dialogue} definition associated with this state.
	 *
	 * @return the dialogue definition.
	 */
	public Dialogue getDialogueDefinition() {
		return dialogueDefinition;
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

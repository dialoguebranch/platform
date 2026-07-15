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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.model.execute.Dialogue;

import java.util.UUID;

/**
 * An ephemeral, in-memory session for test-running a single draft dialogue's current content, as
 * managed by {@link DraftExecutionService}. Unlike a real dialogue session, this is never
 * persisted to {@link com.dialoguebranch.web.service.storage.LoggedDialogueStore} — it exists
 * only in memory for the lifetime of the test.
 *
 * @author Harm op den Akker
 */
public class DraftTestSession {

	private final String id;
	private final String userId;
	private final ActiveDialogue activeDialogue;
	private final Dialogue dialogueDefinition;

	/**
	 * The full contents of the user's {@link com.dialoguebranch.execution.VariableStore} at the
	 * moment this session was started, used by
	 * {@link DraftExecutionService#revertVariables(DraftTestSession)} to restore the pre-test
	 * state.
	 */
	private final Variable[] variableSnapshot;

	/**
	 * Creates a new {@link DraftTestSession} with a freshly generated ID.
	 *
	 * @param userId             the ID of the user testing the draft.
	 * @param activeDialogue     the {@link ActiveDialogue} driving execution for this session.
	 * @param dialogueDefinition the parsed {@link Dialogue} being tested.
	 * @param variableSnapshot   the variable store snapshot taken when this session started.
	 */
	public DraftTestSession(String userId, ActiveDialogue activeDialogue,
							Dialogue dialogueDefinition, Variable[] variableSnapshot) {
		this.id = UUID.randomUUID().toString().toLowerCase().replaceAll("-", "");
		this.userId = userId;
		this.activeDialogue = activeDialogue;
		this.dialogueDefinition = dialogueDefinition;
		this.variableSnapshot = variableSnapshot;
	}

	/**
	 * Returns the unique identifier of this draft test session.
	 *
	 * @return the session ID.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the ID of the user testing the draft.
	 *
	 * @return the user ID.
	 */
	public String getUserId() {
		return userId;
	}

	/**
	 * Returns the {@link ActiveDialogue} driving execution for this session.
	 *
	 * @return the active dialogue.
	 */
	public ActiveDialogue getActiveDialogue() {
		return activeDialogue;
	}

	/**
	 * Returns the parsed {@link Dialogue} being tested.
	 *
	 * @return the dialogue definition.
	 */
	public Dialogue getDialogueDefinition() {
		return dialogueDefinition;
	}

	/**
	 * Returns the variable store snapshot taken when this session started.
	 *
	 * @return the variable snapshot.
	 */
	public Variable[] getVariableSnapshot() {
		return variableSnapshot;
	}

}

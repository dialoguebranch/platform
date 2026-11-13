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
package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;

import java.util.UUID;

/**
 * An ephemeral, in-memory session for test-running a dialogue, as managed by {@link
 * TestSessionService}. Unlike a real dialogue session, it is never persisted to {@link
 * com.dialoguebranch.web.service.storage.LoggedDialogueStore}, and it runs against a copy of the
 * user's variables rather than their real ones (#319): it exists only in memory for the lifetime
 * of the test.
 *
 * @author Harm op den Akker
 */
public class TestSession {

	private final String id;
	private final DialogueBranchUserId tester;
	private final TestSessionService.CopyKey copyKey;
	private final ActiveDialogue activeDialogue;
	private volatile long lastActivityTime = System.currentTimeMillis();

	/**
	 * Creates a new {@link TestSession} with a freshly generated ID.
	 *
	 * @param tester         the authenticated user running the test.
	 * @param copyKey        the copy of the variables this session runs against (the tester's,
	 *                       for the tested user and project).
	 * @param activeDialogue the {@link ActiveDialogue} driving execution for this session. It
	 *                       follows replies into the project's other dialogues itself (#473).
	 */
	public TestSession(DialogueBranchUserId tester, TestSessionService.CopyKey copyKey,
			ActiveDialogue activeDialogue) {
		this.id = UUID.randomUUID().toString().toLowerCase().replaceAll("-", "");
		this.tester = tester;
		this.copyKey = copyKey;
		this.activeDialogue = activeDialogue;
	}

	/**
	 * Returns the unique identifier of this test session.
	 *
	 * @return the session ID.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the authenticated user running the test, the only one who may use this session.
	 *
	 * @return the tester.
	 */
	public DialogueBranchUserId getTester() {
		return tester;
	}

	/**
	 * Returns the key of the copy of the variables this session runs against.
	 *
	 * @return the copy's key.
	 */
	public TestSessionService.CopyKey getCopyKey() {
		return copyKey;
	}

	/**
	 * Returns when this session was last used, for its idle eviction (#218).
	 *
	 * @return the time of the last request on it, in epoch milliseconds.
	 */
	public long getLastActivityTime() {
		return lastActivityTime;
	}

	/** Records that this session was just used. */
	void recordActivity() {
		lastActivityTime = System.currentTimeMillis();
	}

	/**
	 * Returns the {@link ActiveDialogue} driving execution for this session. It isn't
	 * thread-safe: {@link TestSessionService#progressSession} serializes the requests on a
	 * session (#215).
	 *
	 * @return the active dialogue.
	 */
	public ActiveDialogue getActiveDialogue() {
		return activeDialogue;
	}

	/**
	 * Returns the parsed {@link Dialogue} the session is in: the one being tested, or the one a
	 * reply has led into since.
	 *
	 * @return the dialogue definition.
	 */
	public Dialogue getDialogueDefinition() {
		return activeDialogue.getDialogueDefinition();
	}

}

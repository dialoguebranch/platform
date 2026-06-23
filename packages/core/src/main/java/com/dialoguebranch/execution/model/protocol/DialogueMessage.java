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

package com.dialoguebranch.execution.model.protocol;

import com.dialoguebranch.execution.model.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used for dialogue messages that are sent to the client in the
 * web service protocol. It can be generated from an executed {@link Node} using the {@link DialogueMessageFactory}.
 * The {@link Node} having been executed means that variables have
 * been resolved and "if" and "set" commands have been executed.
 *
 * @author Dennis Hofs
 */
public class DialogueMessage {

	/** Creates an empty {@link DialogueMessage}. Required for JSON deserialization. */
	public DialogueMessage() {
	}

	private String dialogue;
	private String node;
	private String loggedDialogueId;
	private int loggedInteractionIndex;
	private String speaker;
	private DialogueStatement statement;
	private List<ReplyMessage> replies = new ArrayList<>();

	/**
	 * Returns the name of the dialogue that produced this message.
	 * @return the dialogue name.
	 */
	public String getDialogue() {
		return dialogue;
	}

	/**
	 * Sets the name of the dialogue that produced this message.
	 * @param dialogue the dialogue name.
	 */
	public void setDialogue(String dialogue) {
		this.dialogue = dialogue;
	}

	/**
	 * Returns the name of the node that produced this message.
	 * @return the node name.
	 */
	public String getNode() {
		return node;
	}

	/**
	 * Sets the name of the node that produced this message.
	 * @param node the node name.
	 */
	public void setNode(String node) {
		this.node = node;
	}

	/**
	 * Returns the identifier of the logged dialogue session, or {@code null} if not logged.
	 * @return the logged dialogue session identifier.
	 */
	public String getLoggedDialogueId() {
		return loggedDialogueId;
	}

	/**
	 * Sets the identifier of the logged dialogue session.
	 * @param loggedDialogueId the logged dialogue session identifier.
	 */
	public void setLoggedDialogueId(String loggedDialogueId) {
		this.loggedDialogueId = loggedDialogueId;
	}

	/**
	 * Returns the index of the logged interaction within the dialogue session.
	 * @return the logged interaction index.
	 */
	public int getLoggedInteractionIndex() {
		return loggedInteractionIndex;
	}

	/**
	 * Sets the index of the logged interaction within the dialogue session.
	 * @param loggedInteractionIndex the logged interaction index.
	 */
	public void setLoggedInteractionIndex(int loggedInteractionIndex) {
		this.loggedInteractionIndex = loggedInteractionIndex;
	}

	/**
	 * Returns the name of the agent (speaker) delivering this message.
	 * @return the speaker name.
	 */
	public String getSpeaker() {
		return speaker;
	}

	/**
	 * Sets the name of the agent (speaker) delivering this message.
	 * @param speaker the speaker name.
	 */
	public void setSpeaker(String speaker) {
		this.speaker = speaker;
	}

	/**
	 * Returns the {@link DialogueStatement} that makes up the body of this message.
	 * @return the dialogue statement.
	 */
	public DialogueStatement getStatement() {
		return statement;
	}

	/**
	 * Sets the {@link DialogueStatement} that makes up the body of this message.
	 * @param statement the dialogue statement.
	 */
	public void setStatement(DialogueStatement statement) {
		this.statement = statement;
	}

	/**
	 * Returns the list of reply options presented to the user for this message.
	 * @return the list of reply messages.
	 */
	public List<ReplyMessage> getReplies() {
		return replies;
	}

	/**
	 * Sets the list of reply options for this message.
	 * @param replies the list of reply messages.
	 */
	public void setReplies(List<ReplyMessage> replies) {
		this.replies = replies;
	}

	/**
	 * Adds a reply option to this message.
	 * @param reply the reply to add.
	 */
	public void addReply(ReplyMessage reply) {
		replies.add(reply);
	}
}

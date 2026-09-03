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

package com.dialoguebranch.model.execute.protocol;

import com.dialoguebranch.model.execute.Node;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

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

	private final String dialogue;
	private final String node;
	private final @Nullable String loggedDialogueId;
	private final int loggedInteractionIndex;
	private final @Nullable String speaker;
	private final DialogueStatement statement;
	private final List<ReplyMessage> replies;

	/**
	 * Creates a {@link DialogueMessage}. This is also the JSON deserialization entry point.
	 *
	 * @param dialogue the name of the dialogue that produced this message.
	 * @param node the name of the node that produced this message.
	 * @param loggedDialogueId the logged dialogue session identifier, or {@code null}.
	 * @param loggedInteractionIndex the index of the logged interaction within the session.
	 * @param speaker the agent (speaker) delivering this message, or {@code null}.
	 * @param statement the {@link DialogueStatement} that makes up the message body.
	 * @param replies the reply options, or {@code null} for none.
	 */
	@JsonCreator
	public DialogueMessage(
			@JsonProperty("dialogue") String dialogue,
			@JsonProperty("node") String node,
			@JsonProperty("loggedDialogueId") @Nullable String loggedDialogueId,
			@JsonProperty("loggedInteractionIndex") int loggedInteractionIndex,
			@JsonProperty("speaker") @Nullable String speaker,
			@JsonProperty("statement") DialogueStatement statement,
			@JsonProperty("replies") @Nullable List<ReplyMessage> replies) {
		this.dialogue = dialogue;
		this.node = node;
		this.loggedDialogueId = loggedDialogueId;
		this.loggedInteractionIndex = loggedInteractionIndex;
		this.speaker = speaker;
		this.statement = statement;
		this.replies = replies != null ? replies : new ArrayList<>();
	}

	/**
	 * Returns the name of the dialogue that produced this message.
	 * @return the dialogue name.
	 */
	public String getDialogue() {
		return dialogue;
	}

	/**
	 * Returns the name of the node that produced this message.
	 * @return the node name.
	 */
	public String getNode() {
		return node;
	}

	/**
	 * Returns the identifier of the logged dialogue session, or {@code null} if not logged.
	 * @return the logged dialogue session identifier.
	 */
	public @Nullable String getLoggedDialogueId() {
		return loggedDialogueId;
	}

	/**
	 * Returns the index of the logged interaction within the dialogue session.
	 * @return the logged interaction index.
	 */
	public int getLoggedInteractionIndex() {
		return loggedInteractionIndex;
	}

	/**
	 * Returns the name of the agent (speaker) delivering this message.
	 * @return the speaker name.
	 */
	public @Nullable String getSpeaker() {
		return speaker;
	}

	/**
	 * Returns the {@link DialogueStatement} that makes up the body of this message.
	 * @return the dialogue statement.
	 */
	public DialogueStatement getStatement() {
		return statement;
	}

	/**
	 * Returns the list of reply options presented to the user for this message.
	 * @return the list of reply messages.
	 */
	public List<ReplyMessage> getReplies() {
		return replies;
	}

}

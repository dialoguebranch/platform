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

package com.dialoguebranch.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Records a single step in a Dialogue Branch conversation session. Each interaction captures
 * when the step occurred, who produced the message ({@link MessageSource}), the name of that
 * source, which dialogue and node were active, the text of the statement shown, and —
 * optionally — the reply the user chose.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class LoggedInteraction {

	private long timestamp;
	private MessageSource messageSource;
	private String sourceName;
	private String dialogueId;
	private String nodeId;
	private String statement;
	private int previousIndex = -1;

	@JsonInclude(Include.NON_NULL)
	private int replyId;


	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an empty {@link LoggedInteraction}. Required for JSON deserialization.
	 */
	public LoggedInteraction() {
	}

	/**
	 * Creates a {@link LoggedInteraction} representing an agent statement (no reply chosen yet).
	 *
	 * @param timestamp       the UTC epoch time (ms) when this interaction was recorded.
	 * @param messageSource   whether the message came from the {@link MessageSource#USER} or
	 *                        {@link MessageSource#AGENT}.
	 * @param sourceName      the name of the agent or user producing the message.
	 * @param dialogueId      the identifier of the dialogue being executed.
	 * @param nodeId          the identifier of the node that produced this interaction.
	 * @param previousIndex   the index of the preceding interaction in the session log, or
	 *                        {@code -1} if this is the first interaction.
	 * @param statement       the text of the statement shown to the user.
	 */
	public LoggedInteraction(long timestamp,
							 MessageSource messageSource, String sourceName,
							 String dialogueId, String nodeId, int previousIndex,
							 String statement) {
		this.timestamp = timestamp;
		this.messageSource = messageSource;
		this.sourceName = sourceName;
		this.dialogueId = dialogueId;
		this.nodeId = nodeId;
		this.previousIndex = previousIndex;
		this.statement = statement;
	}

	/**
	 * Creates a {@link LoggedInteraction} representing a user reply to an agent statement.
	 *
	 * @param timestamp       the UTC epoch time (ms) when this interaction was recorded.
	 * @param messageSource   whether the message came from the {@link MessageSource#USER} or
	 *                        {@link MessageSource#AGENT}.
	 * @param sourceName      the name of the agent or user producing the message.
	 * @param dialogueId      the identifier of the dialogue being executed.
	 * @param nodeId          the identifier of the node that produced this interaction.
	 * @param previousIndex   the index of the preceding interaction in the session log, or
	 *                        {@code -1} if this is the first interaction.
	 * @param statement       the text of the statement shown to the user.
	 * @param replyId         the identifier of the reply option chosen by the user.
	 */
	public LoggedInteraction(long timestamp,
							 MessageSource messageSource, String sourceName,
							 String dialogueId, String nodeId, int previousIndex,
							 String statement, int replyId) {
		this.timestamp = timestamp;
		this.messageSource = messageSource;
		this.sourceName = sourceName;
		this.dialogueId = dialogueId;
		this.nodeId = nodeId;
		this.previousIndex = previousIndex;
		this.statement = statement;
		this.replyId = replyId;
	}
	
	// ------------------------------------------------- //
	// -------------------- Getters -------------------- //
	// ------------------------------------------------- //
	
	/**
	 * Returns the UTC epoch time (ms) when this interaction was recorded.
	 * @return the timestamp in milliseconds.
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Returns whether this interaction was produced by the {@link MessageSource#USER} or the
	 * {@link MessageSource#AGENT}.
	 * @return the message source.
	 */
	public MessageSource getMessageSource() {
		return messageSource;
	}

	/**
	 * Returns the name of the agent or user that produced this interaction's message.
	 * @return the source name.
	 */
	public String getSourceName() {
		return sourceName;
	}

	/**
	 * Returns the identifier of the dialogue that was active when this interaction occurred.
	 * @return the dialogue identifier.
	 */
	public String getDialogueId() {
		return this.dialogueId;
	}

	/**
	 * Returns the identifier of the node that produced this interaction.
	 * @return the node identifier.
	 */
	public String getNodeId() {
		return this.nodeId;
	}

	/**
	 * Returns the index of the preceding interaction in the session log, or {@code -1} if this
	 * is the first interaction.
	 * @return the previous interaction index.
	 */
	public int getPreviousIndex() {
		return previousIndex;
	}

	/**
	 * Returns the text of the statement shown to the user during this interaction.
	 * @return the statement text.
	 */
	public String getStatement() {
		return statement;
	}

	/**
	 * Returns the identifier of the reply option chosen by the user, or {@code 0} if no reply
	 * has been recorded (e.g. for agent statements).
	 * @return the reply identifier.
	 */
	public int getReplyId() {
		return this.replyId;
	}
	
	// ------------------------------------------------- //
	// -------------------- Setters -------------------- //
	// ------------------------------------------------- //
	
	/**
	 * Sets the UTC epoch time (ms) when this interaction was recorded.
	 * @param timestamp the timestamp in milliseconds.
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Sets whether this interaction was produced by the {@link MessageSource#USER} or the
	 * {@link MessageSource#AGENT}.
	 * @param messageSource the message source.
	 */
	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	/**
	 * Sets the name of the agent or user that produced this interaction's message.
	 * @param sourceName the source name.
	 */
	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}

	/**
	 * Sets the identifier of the dialogue that was active when this interaction occurred.
	 * @param dialogueId the dialogue identifier.
	 */
	public void setDialogueId(String dialogueId) {
		this.dialogueId = dialogueId;
	}

	/**
	 * Sets the identifier of the node that produced this interaction.
	 * @param nodeId the node identifier.
	 */
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	/**
	 * Sets the index of the preceding interaction in the session log.
	 * @param previousIndex the previous interaction index, or {@code -1} for the first step.
	 */
	public void setPreviousIndex(int previousIndex) {
		this.previousIndex = previousIndex;
	}

	/**
	 * Sets the text of the statement shown to the user during this interaction.
	 * @param statement the statement text.
	 */
	public void setStatement(String statement) {
		this.statement = statement;
	}

	/**
	 * Sets the identifier of the reply option chosen by the user.
	 * @param replyId the reply identifier.
	 */
	public void setReplyId(int replyId) {
		this.replyId = replyId;
	}
	
	
}

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

package com.dialoguebranch.parser;

import java.util.ArrayList;
import java.util.List;

import com.dialoguebranch.model.nodepointer.NodePointer;

/**
 * Holds mutable state accumulated while parsing a single Dialogue Branch node. A new
 * {@link NodeState} is created for each node encountered by {@link DialogueBranchParser} and is
 * threaded through the various sub-parsers ({@link BodyParser}, {@link CommandParser},
 * {@link ReplyParser}) so they can share information such as the current node title, speaker, and
 * the running reply-ID counter.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class NodeState {
	private final String dialogueName;
	private String title = null;
	private String speaker = null;
	private int speakerLine = 0;
	private int speakerColumn = 0;
	private int nextReplyId = 1;
	private final List<NodePointerToken> nodePointerTokens = new ArrayList<>();

	/**
	 * Creates a {@link NodeState} for the dialogue with the given name.
	 * @param dialogueName the name of the dialogue being parsed.
	 */
	public NodeState(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	/**
	 * Returns the name of the dialogue being parsed.
	 * @return the dialogue name.
	 */
	public String getDialogueName() {
		return dialogueName;
	}

	/**
	 * Returns the title of the node currently being parsed, or {@code null} if not yet set.
	 * @return the node title, or {@code null}.
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Sets the title of the node currently being parsed.
	 * @param title the node title.
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Returns the speaker name declared in the node header, or {@code null} if not yet set.
	 * @return the speaker name, or {@code null}.
	 */
	public String getSpeaker() {
		return speaker;
	}

	/**
	 * Sets the speaker name declared in the node header.
	 * @param speaker the speaker name.
	 */
	public void setSpeaker(String speaker) {
		this.speaker = speaker;
	}

	/**
	 * Returns the line number at which the speaker header was found (used for error reporting).
	 * @return the speaker header line number.
	 */
	public int getSpeakerLine() {
		return speakerLine;
	}

	/**
	 * Sets the line number at which the speaker header was found.
	 * @param speakerLine the speaker header line number.
	 */
	public void setSpeakerLine(int speakerLine) {
		this.speakerLine = speakerLine;
	}

	/**
	 * Returns the column number at which the speaker header value starts (used for error reporting).
	 * @return the speaker header column number.
	 */
	public int getSpeakerColumn() {
		return speakerColumn;
	}

	/**
	 * Sets the column number at which the speaker header value starts.
	 * @param speakerColumn the speaker header column number.
	 */
	public void setSpeakerColumn(int speakerColumn) {
		this.speakerColumn = speakerColumn;
	}

	/**
	 * Returns the next available reply ID and increments the internal counter. Reply IDs are
	 * assigned sequentially starting at 1.
	 * @return the next reply ID.
	 */
	public int createNextReplyId() {
		return nextReplyId++;
	}

	/**
	 * Returns the list of node-pointer tokens collected while parsing this node. Each entry
	 * associates a parsed {@link NodePointer} with the {@link BodyToken} that contained it, so the
	 * caller can validate that the referenced nodes exist after all nodes have been parsed.
	 * @return the list of {@link NodePointerToken}s.
	 */
	public List<NodePointerToken> getNodePointerTokens() {
		return nodePointerTokens;
	}

	/**
	 * Records a node-pointer token encountered while parsing this node.
	 * @param pointer the parsed {@link NodePointer}.
	 * @param token the {@link BodyToken} from which the pointer was parsed (used for error
	 *              reporting).
	 */
	public void addNodePointerToken(NodePointer pointer, BodyToken token) {
		nodePointerTokens.add(new NodePointerToken(title, pointer, token));
	}

	/**
	 * Associates a parsed {@link NodePointer} with the {@link BodyToken} that contained it and the
	 * title of the node in which it was found.
	 *
	 * @param nodeTitle the title of the node in which this pointer was found.
	 * @param pointer the parsed node pointer.
	 * @param token the body token from which the pointer was read.
	 */
	public record NodePointerToken(String nodeTitle, NodePointer pointer, BodyToken token) { }
}

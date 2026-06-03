/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

package com.dialoguebranch.model.protocol;

import com.dialoguebranch.model.command.Command;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.Node;
import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.command.ActionCommand;
import com.dialoguebranch.model.command.InputCommand;
import com.dialoguebranch.model.nodepointer.InternalNodePointer;

public class DialogueMessageFactory {
	
	/**
	 * Generates a DialogueMessage based on the given executed node. Since the
	 * node has already been executed, it should not contain variables or "if"
	 * and "set" commands.
	 * 
	 * @param executedNode the executed node
	 * @return the DialogueMessage
	 */
	public static DialogueMessage generateDialogueMessage(ExecuteNodeResult executedNode) {
		DialogueMessage dialogueMessage = new DialogueMessage();
		Node node = executedNode.node();
		NodeBody body = node.getBody();
		dialogueMessage.setDialogue(executedNode.dialogue()
				.getDialogueName());
		dialogueMessage.setNode(node.getTitle());
		if (executedNode.loggedDialogue() != null) {
			dialogueMessage.setLoggedDialogueId(executedNode.loggedDialogue()
					.getId());
			dialogueMessage.setLoggedInteractionIndex(
					executedNode.interactionIndex());
		}
		dialogueMessage.setSpeaker(node.getHeader().getSpeaker());
		dialogueMessage.setStatement(generateDialogueStatement(body));
		for (Reply reply : body.getReplies()) {
			dialogueMessage.addReply(generateDialogueReply(reply));
		}
		return dialogueMessage;
	}
	
	private static DialogueStatement generateDialogueStatement(
			NodeBody body) {
		DialogueStatement statement = new DialogueStatement();
		for (NodeBody.Segment segment : body.getSegments()) {
			if (segment instanceof NodeBody.TextSegment) {
				NodeBody.TextSegment textSegment =
						(NodeBody.TextSegment)segment;
				String text = textSegment.getText().evaluate(null);
				statement.addTextSegment(text);
			} else {
				NodeBody.CommandSegment cmdSegment =
						(NodeBody.CommandSegment)segment;
				Command cmd = cmdSegment.getCommand();
				if (cmd instanceof ActionCommand) {
					statement.addActionSegment((ActionCommand)cmd);
				} else if (cmd instanceof InputCommand) {
					statement.addInputSegment((InputCommand)cmd);
				}
			}
		}
		return statement;
	}
	
	private static ReplyMessage generateDialogueReply(Reply reply) {
		ReplyMessage replyMsg = new ReplyMessage();
		replyMsg.setReplyId(reply.getReplyId());
		if (reply.getStatement() != null) {
			replyMsg.setStatement(generateDialogueStatement(
					reply.getStatement()));
		}
		if (reply.getNodePointer() instanceof InternalNodePointer) {
			InternalNodePointer pointer =
					(InternalNodePointer)reply.getNodePointer();
			if (pointer.getTargetNodeId().equalsIgnoreCase("end"))
				replyMsg.setEndsDialogue(true);
		}
		for (Command cmd : reply.getCommands()) {
			if (!(cmd instanceof ActionCommand))
				continue;
			ActionCommand actionCmd = (ActionCommand)cmd;
			replyMsg.addAction(new DialogueAction(actionCmd));
		}
		return replyMsg;
	}
}

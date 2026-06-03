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

package com.dialoguebranch.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.model.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.nodepointer.InternalNodePointer;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.exception.ParseException;

public class ReplyParser {
	private NodeState nodeState;
	
	private ReplySection statementSection;
	private ReplySection nodePointerSection;
	private ReplySection commandSection;
	
	public ReplyParser(NodeState nodeState) {
		this.nodeState = nodeState;
	}
	
	public Reply parse(CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		readSections(tokens);
		NodeBody statement = parseStatement();
		NodePointer nodePointer = parseNodePointer();
		Reply reply = new Reply(nodeState.createNextReplyId(),
				statement, nodePointer);
		if (commandSection != null)
			parseCommands(reply);
		return reply;
	}
	
	private void readSections(CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		int maxSections = 3;
		BodyToken startToken = tokens.getCurrent();
		tokens.moveNext();
		List<ReplySection> sections = new ArrayList<>();
		ReplySection currSection = new ReplySection();
		sections.add(currSection);
		boolean foundEnd = false;
		while (!foundEnd && tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			switch (token.getType()) {
			case REPLY_SEPARATOR:
				if (sections.size() == maxSections) {
					throw new LineNumberParseException(String.format(
							"Exceeded maximum number of %s sections",
							maxSections), token.getLineNumber(),
							token.getColNumber());
				}
				currSection.endLineNum = token.getLineNumber();
				currSection.endColNum = token.getColNumber();
				currSection = new ReplySection();
				sections.add(currSection);
				break;
			case REPLY_END:
				currSection.endLineNum = token.getLineNumber();
				currSection.endColNum = token.getColNumber();
				foundEnd = true;
				break;
			default:
				currSection.tokens.add(token);
			}
			tokens.moveNext();
		}
		if (!foundEnd) {
			throw new LineNumberParseException("Reply not terminated",
					startToken.getLineNumber(), startToken.getColNumber());
		}
		statementSection = null;
		nodePointerSection = null;
		commandSection = null;
		if (sections.size() == 1) {
			nodePointerSection = sections.get(0);
		} else if (sections.size() == 2) {
			statementSection = sections.get(0);
			nodePointerSection = sections.get(1);
		} else {
			statementSection = sections.get(0);
			nodePointerSection = sections.get(1);
			commandSection = sections.get(2);
		}
	}
	
	private NodeBody parseStatement() throws LineNumberParseException {
		if (statementSection == null)
			return null;
		BodyParser bodyParser = new BodyParser(nodeState);
		NodeBody result = bodyParser.parse(statementSection.tokens,
				Arrays.asList("input"));
		if (result.getSegments().isEmpty())
			return null;
		else
			return result;
	}
	
	private NodePointer parseNodePointer() throws LineNumberParseException {
		BodyToken.trimWhitespace(nodePointerSection.tokens);
		if (nodePointerSection.tokens.size() == 0) {
			throw new LineNumberParseException("Empty node pointer in reply",
					nodePointerSection.endLineNum,
					nodePointerSection.endColNum);
		}
		BodyToken nodePointerToken = nodePointerSection.tokens.get(0);
		if (nodePointerSection.tokens.size() != 1 ||
				nodePointerToken.getType() != BodyToken.Type.TEXT) {
			throw new LineNumberParseException(
					"Invalid node pointer in reply",
					nodePointerToken.getLineNumber(),
					nodePointerToken.getColNumber());
		}
		String nodePointerStr = (String)nodePointerToken.getValue();
		NodePointer result;
		if (nodePointerStr.matches(DialogueBranchParser.NODE_NAME_REGEX)) {
			result = new InternalNodePointer(nodeState.getTitle(),nodePointerStr);
		} else if (nodePointerStr.matches(
				DialogueBranchParser.EXTERNAL_NODE_POINTER_REGEX)) {
			int sep = nodePointerStr.lastIndexOf('.');
			try {
				result = new ExternalNodePointer(
						nodeState.getDialogueName(),
						nodeState.getTitle(),
						nodePointerStr.substring(0, sep),
						nodePointerStr.substring(sep + 1));
			} catch (ParseException ex) {
				throw new LineNumberParseException(
						"Invalid node pointer in reply: " + ex.getMessage(),
						nodePointerToken.getLineNumber(),
						nodePointerToken.getColNumber(), ex);
			}
		} else {
			throw new LineNumberParseException(
					"Invalid node pointer in reply: " + nodePointerStr,
					nodePointerToken.getLineNumber(),
					nodePointerToken.getColNumber());
		}
		nodeState.addNodePointerToken(result, nodePointerToken);
		return result;
	}
	
	private void parseCommands(Reply reply) throws LineNumberParseException {
		CurrentIterator<BodyToken> it = new CurrentIterator<>(
				commandSection.tokens.iterator());
		it.moveNext();
		BodyToken.skipWhitespace(it);
		while (it.getCurrent() != null) {
			BodyToken token = it.getCurrent();
			if (token.getType() != BodyToken.Type.COMMAND_START) {
				throw new LineNumberParseException(
						"Expected <<, found token: " + token.getType(),
						token.getLineNumber(), token.getColNumber());
			}
			CommandParser cmdParser = new CommandParser(
					Arrays.asList("action", "set"), nodeState);
			reply.addCommand(cmdParser.parseFromStart(it));
			BodyToken.skipWhitespace(it);
		}
	}

	private class ReplySection {
		private List<BodyToken> tokens = new ArrayList<>();
		private int endLineNum;
		private int endColNum;
	}
}

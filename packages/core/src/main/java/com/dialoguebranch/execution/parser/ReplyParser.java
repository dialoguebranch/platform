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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.exception.LineNumberParseException;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Parses a single reply construct ({@code [[ ... ]]}) from a Dialogue Branch node body into a
 * {@link Reply} model object. A reply consists of up to three pipe-separated sections: an optional
 * statement, a mandatory node-pointer, and an optional command section. The parser delegates
 * command parsing to {@link CommandParser}.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class ReplyParser {
	private final NodeState nodeState;

	/**
	 * The pipe-separated sections of one reply: an optional statement, the mandatory node
	 * pointer, and an optional command section.
	 */
	private record Sections(@Nullable ReplySection statement, ReplySection nodePointer,
			@Nullable ReplySection command) {}

	/**
	 * Creates a {@link ReplyParser} that uses the given node state for reply-ID generation and
	 * node-pointer tracking.
	 * @param nodeState the state of the node currently being parsed.
	 */
	public ReplyParser(NodeState nodeState) {
		this.nodeState = nodeState;
	}

	/**
	 * Parses the next reply from the given token stream. The iterator must be positioned at the
	 * {@link BodyToken.Type#REPLY_START} token. When this method returns, the iterator will be
	 * positioned after the corresponding {@link BodyToken.Type#REPLY_END} token.
	 *
	 * @param tokens the token iterator.
	 * @return the parsed {@link Reply}.
	 * @throws LineNumberParseException if a parse error is encountered.
	 */
	public Reply parse(CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		Sections sections = readSections(tokens);
		NodeBody statement = parseStatement(sections.statement());
		NodePointer nodePointer = parseNodePointer(sections.nodePointer());
		Reply reply = new Reply(nodeState.createNextReplyId(),
				statement, nodePointer);
		if (sections.command() != null)
			parseCommands(reply, sections.command());
		return reply;
	}

	private Sections readSections(CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		int maxSections = 3;
		// The iterator is positioned at the reply start token by contract.
		BodyToken startToken = Objects.requireNonNull(tokens.getCurrent());
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
		if (sections.size() == 1)
			return new Sections(null, sections.get(0), null);
		if (sections.size() == 2)
			return new Sections(sections.get(0), sections.get(1), null);
		return new Sections(sections.get(0), sections.get(1), sections.get(2));
	}

	private @Nullable NodeBody parseStatement(@Nullable ReplySection statementSection)
			throws LineNumberParseException {
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

	private NodePointer parseNodePointer(ReplySection nodePointerSection)
			throws LineNumberParseException {
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
		String nodePointerStr = (String) Objects.requireNonNull(nodePointerToken.getValue());
		String originNodeId = Objects.requireNonNull(nodeState.getTitle(),
				"Node title must be set before its replies are parsed");
		NodePointer result;
		if (nodePointerStr.matches(DialogueBranchParser.NODE_NAME_REGEX)) {
			result = new InternalNodePointer(originNodeId,nodePointerStr);
		} else if (nodePointerStr.matches(
				DialogueBranchParser.EXTERNAL_NODE_POINTER_REGEX)) {
			int sep = nodePointerStr.lastIndexOf('.');
			try {
				result = new ExternalNodePointer(
						nodeState.getDialogueName(),
						originNodeId,
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

	private void parseCommands(Reply reply, ReplySection commandSection)
			throws LineNumberParseException {
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

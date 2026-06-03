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

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.command.Command;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import com.dialoguebranch.model.VariableString;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link BodyParser} can be used to parse the body of a Dialogue Branch Node. This
 * {@link BodyParser} makes use of the {@link CommandParser}, and the {@link ReplyParser} for
 * parsing Dialogue Branch commands and replies respectively. Information about the state of the
 * current node that is being parsed is kept in the provided {@link NodeState} object.
 *
 * <p>The {@link BodyParser} can generate a {@link NodeBody} object from a given list of
 * {@link BodyToken}s and a list of command names that are valid in the given context. Which
 * commands are valid can differ depending on whether we are parsing the main part of a node body
 * (where we accept e.g. 'action', 'if', 'random' and 'set') or a statement section within a reply
 * (where we only accept 'action' or 'set' commands).</p>
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class BodyParser {

	private final NodeState nodeState;

	/**
	 * Creates an instance of a {@link BodyParser} that keeps track of the state of the node it is
	 * parsing in the given {@link NodeState}.
	 * @param nodeState the state of the node being parsed.
	 */
	public BodyParser(NodeState nodeState) {
		this.nodeState = nodeState;
	}

	/**
	 * Parses the given set of {@link BodyToken}s into a {@link NodeBody}.
	 * @param tokens the list of {@link BodyToken}s making up the node body.
	 * @param validCommands a list of command names that are valid within the current context.
	 * @return the {@link NodeBody} resulting from parsing all given tokens.
	 * @throws LineNumberParseException in case of any errors in the body.
	 */
	public NodeBody parse(List<BodyToken> tokens, List<String> validCommands)
			throws LineNumberParseException {
		CurrentIterator<BodyToken> it = new CurrentIterator<>(tokens.iterator());
		it.moveNext();
		ParseUntilCommandClauseResult result = parseUntilCommandClause(it, validCommands,
				new ArrayList<>());
		return result.body;
	}

	/**
	 * Parse the specified tokens until a sub-clause of a command is found. There are two commands
	 * that can have subclauses:
	 *
	 * <ul>
	 *   <li>if: has subclauses "elseif", "else" and "endif"</li>
	 *   <li>random: has subclauses "or" and "endrandom"</li>
	 * </ul>
	 *
	 * <p>If any command token is found that is not in "validCommands" or in "validCommandClauses",
	 * then this method throws a parse exception.</p>
	 *
	 * @param tokens the tokens
	 * @param validCommands valid commands
	 * @param validCommandClauses valid command clauses
	 * @return the result
	 * @throws LineNumberParseException if a parse error occurs
	 */
	public ParseUntilCommandClauseResult parseUntilCommandClause(
			CurrentIterator<BodyToken> tokens, List<String> validCommands,
            List<String> validCommandClauses) throws LineNumberParseException {
		ParseUntilCommandClauseResult result = new ParseUntilCommandClauseResult();
		result.body = new NodeBody();
		while (result.cmdClauseStartToken == null && tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			switch (token.getType()) {
			case TEXT:
			case VARIABLE:
				VariableString text = parseTextSegment(tokens);
				if (result.body.getReplies().isEmpty()) {
					result.body.addSegment(new NodeBody.TextSegment(text));
				} else if (text.hasContents()) {
					throw new LineNumberParseException(
							"Found content after reply", token.getLineNumber(),
							token.getColNumber());
				}
				break;
			case COMMAND_START:
				CommandParser cmdParser = new CommandParser(validCommands, nodeState);
				String name = cmdParser.readCommandName(tokens);
				if (validCommandClauses.contains(name)) {
					result.cmdClauseStartToken = token;
					result.cmdClauseName = name;
				} else if (!name.equals("if") && !name.equals("random") &&
						!result.body.getReplies().isEmpty()) {
					throw new LineNumberParseException(
							"Found << after reply", token.getLineNumber(), token.getColNumber());
				} else {
					Command command = cmdParser.parseFromName(token, tokens);
					result.body.addSegment(new NodeBody.CommandSegment(command));
				}
				break;
			case REPLY_START:
				if (nodeState == null) {
					throw new LineNumberParseException(
							"Unexpected start of reply [[", token.getLineNumber(),
							token.getColNumber());
				}
				ReplyParser replyParser = new ReplyParser(nodeState);
				Reply reply = replyParser.parse(tokens);
				if (reply.getStatement() == null && hasAutoForwardReply(result.body)) {
					throw new LineNumberParseException(
							"Found more than one autoforward reply",
							token.getLineNumber(), token.getColNumber());
				}
				result.body.addReply(reply);
				break;
			default:
				// If we get here, there must be a bug
				throw new LineNumberParseException("Unexpected token type: " +
						token.getType(), token.getLineNumber(), token.getColNumber());
			}
		}
		result.body.trimWhitespace();
		return result;
	}
	
	private boolean hasAutoForwardReply(NodeBody body) {
		for (Reply reply : body.getReplies()) {
			if (reply.getStatement() == null)
				return true;
		}
		return false;
	}

	public static class ParseUntilCommandClauseResult {
		public NodeBody body;
		public BodyToken cmdClauseStartToken = null;
		public String cmdClauseName = null;
	}
	
	private VariableString parseTextSegment(CurrentIterator<BodyToken> tokens) {
		VariableString string = new VariableString();
		boolean foundEnd = false;
		while (!foundEnd && tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			switch (token.getType()) {
			case TEXT:
				string.addSegment(new VariableString.TextSegment((String)token.getValue()));
				break;
			case VARIABLE:
				string.addSegment(new VariableString.VariableSegment((String)token.getValue()));
				break;
			default:
				foundEnd = true;
			}
			if (!foundEnd)
				tokens.moveNext();
		}
		return string;
	}
}

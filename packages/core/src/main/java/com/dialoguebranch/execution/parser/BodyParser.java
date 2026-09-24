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
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.VariableString;
import com.dialoguebranch.model.execute.command.Command;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * @author Dennis Hofs
 * @author Harm op den Akker
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
		NodeBody.Builder bodyBuilder = new NodeBody.Builder();
		@Nullable BodyToken cmdClauseStartToken = null;
		@Nullable String cmdClauseName = null;
		while (cmdClauseStartToken == null && tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			switch (token.getType()) {
			case TEXT:
			case VARIABLE:
				// Authoring mistakes below are recorded on nodeState and parsing continues
				// (accumulate-and-continue, #211); a genuinely impossible state (default: below)
				// still throws directly, since that indicates a bug in the parser itself, not a
				// mistake in the .dlb script being parsed.
				try {
					VariableString text = parseTextSegment(tokens);
					if (bodyBuilder.getReplies().isEmpty()) {
						bodyBuilder.addSegment(new NodeBody.TextSegment(text));
					} else if (text.hasContents()) {
						throw new LineNumberParseException(
								"Found content after reply", token.getLineNumber(),
								token.getColNumber());
					}
				} catch (LineNumberParseException ex) {
					nodeState.addError(ex);
				}
				break;
			case COMMAND_START:
				CommandParser cmdParser = new CommandParser(validCommands, nodeState);
				try {
					String name = cmdParser.readCommandName(tokens);
					if (validCommandClauses.contains(name)) {
						cmdClauseStartToken = token;
						cmdClauseName = name;
					} else if (!name.equals("if") && !name.equals("random") &&
							!bodyBuilder.getReplies().isEmpty()) {
						// Unlike a failure inside CommandParser, nothing has consumed this
						// command's tokens yet — skip to its own COMMAND_END before reporting it.
						BodyToken.skipTo(tokens, BodyToken.Type.COMMAND_END);
						throw new LineNumberParseException("Found << after reply",
								token.getLineNumber(), token.getColNumber());
					} else {
						Command command = cmdParser.parseFromName(token, tokens);
						bodyBuilder.addSegment(new NodeBody.CommandSegment(command));
					}
				} catch (LineNumberParseException ex) {
					nodeState.addError(ex);
				}
				break;
			case REPLY_START:
				if (nodeState == null) {
					throw new LineNumberParseException(
							"Unexpected start of reply [[", token.getLineNumber(),
							token.getColNumber());
				}
				ReplyParser replyParser = new ReplyParser(nodeState);
				try {
					Reply reply = replyParser.parse(tokens);
					if (reply.isAutoForward() && hasAutoForwardReply(bodyBuilder.getReplies())) {
						throw new LineNumberParseException(
								"Found more than one autoforward reply",
								token.getLineNumber(), token.getColNumber());
					}
					bodyBuilder.addReply(reply);
				} catch (LineNumberParseException ex) {
					nodeState.addError(ex);
				}
				break;
			default:
				// If we get here, there must be a bug
				throw new LineNumberParseException("Unexpected token type: " +
						token.getType(), token.getLineNumber(), token.getColNumber());
			}
		}
		return new ParseUntilCommandClauseResult(bodyBuilder.build(), cmdClauseStartToken,
				cmdClauseName);
	}

	private boolean hasAutoForwardReply(List<Reply> replies) {
		for (Reply reply : replies) {
			if (reply.isAutoForward())
				return true;
		}
		return false;
	}

	/**
	 * Return type of {@link BodyParser#parseUntilCommandClause}. Contains the parsed body up to
	 * the encountered command clause (or the end of the token stream), together with information
	 * about the clause token that caused parsing to stop.
	 */
	public static class ParseUntilCommandClauseResult {

		/** The node body parsed up to the command clause or end of tokens. */
		public final NodeBody body;
		/** The {@link BodyToken} at which the command clause started, or {@code null}. */
		public final @Nullable BodyToken cmdClauseStartToken;
		/** The name of the command clause that was encountered, or {@code null}. */
		public final @Nullable String cmdClauseName;

		/**
		 * Creates a {@link ParseUntilCommandClauseResult}.
		 *
		 * @param body the parsed node body.
		 * @param cmdClauseStartToken the token at which the command clause started, or {@code
		 * null}.
		 * @param cmdClauseName the name of the command clause encountered, or {@code null}.
		 */
		public ParseUntilCommandClauseResult(NodeBody body,
				@Nullable BodyToken cmdClauseStartToken, @Nullable String cmdClauseName) {
			this.body = body;
			this.cmdClauseStartToken = cmdClauseStartToken;
			this.cmdClauseName = cmdClauseName;
		}
	}

	private VariableString parseTextSegment(CurrentIterator<BodyToken> tokens) {
		VariableString string = new VariableString();
		boolean foundEnd = false;
		while (!foundEnd && tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			switch (token.getType()) {
			case TEXT:
				string.addSegment(new VariableString.TextSegment((String) Objects.requireNonNull(token.getValue())));
				break;
			case VARIABLE:
				string.addSegment(new VariableString.VariableSegment((String) Objects.requireNonNull(token.getValue())));
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

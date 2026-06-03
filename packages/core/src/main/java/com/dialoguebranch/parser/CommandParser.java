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

import java.util.List;

import com.dialoguebranch.model.command.*;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;

public class CommandParser {

	private final List<String> validCommands;
	private final NodeState nodeState;
	
	public CommandParser(List<String> validCommands, NodeState nodeState) {
		this.validCommands = validCommands;
		this.nodeState = nodeState;
	}

	/**
	 * Reads the command name from the start of a command. The specified iterator should be
	 * positioned at the command start token. When this method returns, it will be positioned at the
	 * token with the command name. This method does not validate the command name.
	 * 
	 * @param tokens the tokens
	 * @return the command name
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	public String readCommandName(CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		BodyToken startToken = tokens.getCurrent();
		tokens.moveNext();
		BodyToken.skipWhitespace(tokens);
		BodyToken token = tokens.getCurrent();
		return getCommandName(startToken, token);
	}
	
	/**
	 * Parses a command from the command name. The specified iterator should be positioned at the
	 * command name token. When this method returns it will be positioned after the command end
	 * token. This method can be called after {@link #readCommandName(CurrentIterator)}. This method
	 * validates the command name.
	 * 
	 * @param startToken the command start token
	 * @param tokens the tokens
	 * @return the command
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	public Command parseFromName(BodyToken startToken, CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		BodyToken token = tokens.getCurrent();
		String name = getCommandName(startToken, token);
		if (!validCommands.contains(name)) {
			throw new LineNumberParseException("Unexpected command: " + name,
					token.getLineNumber(), token.getColNumber());
		}
        return switch (name) {
            case "action" -> ActionCommand.parse(startToken, tokens, nodeState);
            case "if" -> IfCommand.parse(startToken, tokens, nodeState);
            case "input" -> InputCommand.parse(startToken, tokens, nodeState);
            case "random" -> RandomCommand.parse(startToken, tokens, nodeState);
            case "set" -> SetCommand.parse(startToken, tokens, nodeState);
            default -> throw new LineNumberParseException("Unknown command: " + name,
                    token.getLineNumber(), token.getColNumber());
        };
	}
	
	/**
	 * Parses a command from the start token. The specified iterator should be positioned at the
	 * command start token. When this method returns it will be positioned after the command end
	 * token. This method cannot be called after {@link #readCommandName(CurrentIterator)}. This
	 * method validates the command name.
	 * 
	 * @param tokens the tokens
	 * @return the command
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	public Command parseFromStart(CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		BodyToken startToken = tokens.getCurrent();
		tokens.moveNext();
		BodyToken.skipWhitespace(tokens);
		return parseFromName(startToken, tokens);
	}
	
	/**
	 * Tries to read the command name from the specified name token. This should be the first
	 * non-whitespace token after the command start token. It may be null.
	 * 
	 * @param startToken the start token
	 * @param nameToken the name token or null
	 * @return the command name
	 * @throws LineNumberParseException if the command name can't be read
	 */
	private String getCommandName(BodyToken startToken,
                                  BodyToken nameToken) throws LineNumberParseException {
		if (nameToken == null) {
			throw new LineNumberParseException("Command not terminated",
					startToken.getLineNumber(), startToken.getColNumber());
		}
		if (nameToken.getType() != BodyToken.Type.TEXT) {
			throw new LineNumberParseException(
					"Expected command name, found token: " +
					nameToken.getType(), nameToken.getLineNumber(),
					nameToken.getColNumber());
		}
		String name = nameToken.getText().trim();
		String[] split = name.split("\\s+", 2);
		return split[0];
	}
}

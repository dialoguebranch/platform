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

package com.dialoguebranch.model.execute.command;

import com.dialoguebranch.exception.LineNumberParseException;
import com.dialoguebranch.execution.parser.BodyToken;
import com.dialoguebranch.expression.Expression;
import com.dialoguebranch.expression.ExpressionParser;
import com.dialoguebranch.expression.Token;
import com.dialoguebranch.expression.Tokenizer;
import com.dialoguebranch.io.LineColumnNumberReader;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

/**
 * Base class for Dialogue Branch commands whose body consists of an expression (e.g. {@code <<if>>},
 * {@code <<set>>}). Provides shared helpers for reading, tokenizing, and parsing command content.
 *
 * @author Dennis Hofs
 */
public abstract class ExpressionCommand extends Command {

	/** Creates an instance of an {@link ExpressionCommand}. */
	protected ExpressionCommand() {
	}

	/**
	 * Reads the content of a command as a code string. When this method
	 * returns, the iterator will be positioned after the command end token.
	 *
	 * @param cmdStartToken the command start token
	 * @param tokens the token iterator positioned after the command start token
	 * @return the content
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	protected static ReadContentResult readCommandContent(
			BodyToken cmdStartToken, CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		// The iterator is positioned after the command start token by contract.
		BodyToken contentStart = Objects.requireNonNull(tokens.getCurrent());
		int lineNum = contentStart.getLineNumber();
		int colNum = contentStart.getColNumber();
		StringBuilder text = new StringBuilder();
		boolean foundEnd = false;
		while (!foundEnd && tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			if (token.getType() == BodyToken.Type.COMMAND_END) {
				foundEnd = true;
			} else {
				text.append(tokens.getCurrent().getText());
			}
			tokens.moveNext();
		}
		if (!foundEnd) {
			throw new LineNumberParseException("Command not terminated",
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		return new ReadContentResult(text.toString(), lineNum, colNum);
	}

	/** Holds the raw text content read from a command body together with its source position. */
	protected static class ReadContentResult {
		/** The raw text content of the command body. */
		public final String content;
		/** The source line number of the first content token. */
		public final int lineNum;
		/** The source column number of the first content token. */
		public final int colNum;

		/**
		 * Creates a {@link ReadContentResult}.
		 *
		 * @param content the raw text content of the command body.
		 * @param lineNum the source line number of the first content token.
		 * @param colNum the source column number of the first content token.
		 */
		public ReadContentResult(String content, int lineNum, int colNum) {
			this.content = content;
			this.lineNum = lineNum;
			this.colNum = colNum;
		}
	}

	/**
	 * Parses the specified command content. This method checks whether the
	 * command name is the specified name, and there is no expression.
	 *
	 * @param cmdStartToken the command start token
	 * @param content the command content
	 * @param name the command name
	 * @return the parsed content
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	protected static ParseContentResult parseCommandContentName(
			BodyToken cmdStartToken, ReadContentResult content, String name)
			throws LineNumberParseException {
		ParseContentResult result = parseCommandContent(cmdStartToken, content);
		if (!result.name.equals(name)) {
			throw new LineNumberParseException(String.format(
					"Expected command \"%s\", found: %s", name, result.name),
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		if (result.expression != null) {
			throw new LineNumberParseException(String.format(
					"Unexpected content after command name \"%s\"", name),
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		return result;
	}

	/**
	 * Parses the specified command content. This method checks whether the
	 * command name is the specified name, and there is an expression.
	 *
	 * @param cmdStartToken the command start token
	 * @param content the command content
	 * @param name the command name
	 * @return the parsed content
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	protected static ParseContentResult parseCommandContentExpression(
			BodyToken cmdStartToken, ReadContentResult content, String name)
			throws LineNumberParseException {
		ParseContentResult result = parseCommandContent(cmdStartToken, content);
		if (!result.name.equals(name)) {
			throw new LineNumberParseException(String.format(
					"Expected command \"%s\", found: %s", name, result.name),
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		if (result.expression == null) {
			throw new LineNumberParseException(String.format(
					"Expression not found in command \"%s\"", name),
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		return result;
	}

	/**
	 * Parses the specified command content. It tries to read a command name and
	 * an expression. If there is no expression, then the expression in the
	 * result will be null.
	 *
	 * @param cmdStartToken the command start token
	 * @param content the command content
	 * @return the parsed content
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	protected static ParseContentResult parseCommandContent(
			BodyToken cmdStartToken, ReadContentResult content)
			throws LineNumberParseException {
		int lineOff = content.lineNum;
		int colOff = content.colNum;
		LineColumnNumberReader reader = new LineColumnNumberReader(
				new StringReader(content.content));
		Tokenizer tokenizer = new Tokenizer(reader);
		ExpressionParser parser = new ExpressionParser(tokenizer);
		try {
			try {
				parser.getConfig().setAllowDollarVariables(true);
				parser.getConfig().setAllowPlainVariables(false);
				return parseCommandContent(cmdStartToken, content, tokenizer,
						parser, lineOff, colOff);
			} finally {
				parser.close();
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
	}

	private static ParseContentResult parseCommandContent(
			BodyToken cmdStartToken, ReadContentResult content,
			Tokenizer tokenizer, ExpressionParser parser, int lineOff,
			int colOff) throws LineNumberParseException, IOException {
		Token nameToken;
		try {
			nameToken = tokenizer.readToken();
		} catch (LineNumberParseException ex) {
			throw createParseException("Invalid command name: " +
					ex.getError(), ex, lineOff, colOff);
		}
		if (nameToken == null) {
			throw new LineNumberParseException("Found empty command",
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		if (nameToken.getType() != Token.Type.NAME) {
			throw createParseException("Expected command name, found token: " +
					nameToken.getType(), nameToken.getLineNum(),
					nameToken.getColNum(), lineOff, colOff);
		}
		String name = Objects.requireNonNull(nameToken.getValue()).toString();
		@Nullable Expression expression;
		try {
			expression = parser.readExpression();
		} catch (LineNumberParseException ex) {
			throw createParseException("Invalid expression in command: " +
					ex.getError(), ex, lineOff, colOff);
		}
		int postExprLine = tokenizer.getLineNum();
		int postExprCol = tokenizer.getColNum();
		Token nextToken;
		try {
			nextToken = tokenizer.readToken();
		} catch (LineNumberParseException ex) {
			throw createParseException(
					"Unexpected content after expression in command",
					postExprLine, postExprCol, lineOff, colOff);
		}
		if (nextToken != null) {
			throw createParseException(
					"Unexpected content after expression in command",
					postExprLine, postExprCol, lineOff, colOff);
		}
		return new ParseContentResult(name, expression);
	}

	private static LineNumberParseException createParseException(String message,
			LineNumberParseException ex, int lineOff, int colOff)
			throws LineNumberParseException {
		return createParseException(message, ex.getLineNum(), ex.getColNum(),
				lineOff, colOff);
	}

	private static LineNumberParseException createParseException(String message,
			int lineNum, int colNum, int lineOff, int colOff)
			throws LineNumberParseException {
		int exLineNum = lineOff - 1 + lineNum;
		int exColNum = colNum;
		if (exLineNum == lineOff)
			exColNum += colOff - 1;
		return new LineNumberParseException(message, exLineNum, exColNum);
	}

	/** Holds the parsed command name and optional expression extracted from a command body. */
	protected static class ParseContentResult {
		/** The command name token (e.g. {@code "if"}, {@code "set"}). */
		public final String name;
		/** The expression following the command name, or {@code null} if none was present. */
		public final @Nullable Expression expression;

		/**
		 * Creates a {@link ParseContentResult}.
		 *
		 * @param name the command name.
		 * @param expression the expression following the command name, or {@code null}.
		 */
		public ParseContentResult(String name, @Nullable Expression expression) {
			this.name = name;
			this.expression = expression;
		}
	}
}

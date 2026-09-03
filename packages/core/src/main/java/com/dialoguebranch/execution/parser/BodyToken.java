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

import com.dialoguebranch.model.execute.VariableString;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link BodyToken} is the smallest meaningful segment of a line of text of a Dialogue Branch
 * script and can be generated from the script text by the {@link BodyTokenizer}. A
 * {@link BodyToken} can be of the following types (as defined by {@link Type}):
 *
 * <ul>
 *     <li>{@link Type#TEXT}</li>
 *     <li>{@link Type#COMMAND_START}</li>
 *     <li>{@link Type#COMMAND_END}</li>
 *     <li>{@link Type#REPLY_START}</li>
 *     <li>{@link Type#REPLY_END}</li>
 *     <li>{@link Type#REPLY_SEPARATOR}</li>
 *     <li>{@link Type#QUOTED_STRING}</li>
 *     <li>{@link Type#VARIABLE}</li>
 * </ul>
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 */
public class BodyToken {

	/** Enumerates the possible token types produced by the {@link BodyTokenizer}. */
	public enum Type {
		/**
		 * Value: text with escaped characters resolved
		 */
		TEXT,

		/** Marks the opening {@code <<} of a command block. */
		COMMAND_START,
		/** Marks the closing {@code >>} of a command block. */
		COMMAND_END,
		/** Marks the opening {@code [[} of a reply block. */
		REPLY_START,
		/** Marks the closing {@code ]]} of a reply block. */
		REPLY_END,
		/** Marks the {@code |} separator between sections within a reply block. */
		REPLY_SEPARATOR,

		/**
		 * Value: VariableString
		 */
		QUOTED_STRING,

		/**
		 * Value: variable name
		 */
		VARIABLE
	}

	private final Type type;
	private final int lineNumber;
	private final int colNumber;
	private final String text;
	/**
	 * Non-null only for the types that carry one (TEXT, QUOTED_STRING, VARIABLE). Set in the
	 * constructor; {@link #setValue} exists only for the in-place whitespace trimming in
	 * {@link #removeLeadingWhitespace} / {@link #removeTrailingWhitespace}.
	 */
	private @Nullable Object value;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates a {@link BodyToken} of a type that carries no value.
	 *
	 * @param type the {@link Type} of this {@link BodyToken}.
	 * @param text the verbatim script text this token was formed from.
	 * @param lineNumber the line number on which this token starts (first line is 1).
	 * @param colNumber the column number on which this token starts (first column is 1).
	 */
	public BodyToken(BodyToken.Type type, String text, int lineNumber, int colNumber) {
		this(type, text, lineNumber, colNumber, null);
	}

	/**
	 * Creates a {@link BodyToken}.
	 *
	 * @param type the {@link Type} of this {@link BodyToken}.
	 * @param text the verbatim script text this token was formed from.
	 * @param lineNumber the line number on which this token starts (first line is 1).
	 * @param colNumber the column number on which this token starts (first column is 1).
	 * @param value the context-dependent value (see {@link #getValue()}), or {@code null} for a
	 *              type that carries none.
	 */
	public BodyToken(BodyToken.Type type, String text, int lineNumber, int colNumber,
			@Nullable Object value) {
		this.type = type;
		this.text = text;
		this.lineNumber = lineNumber;
		this.colNumber = colNumber;
		this.value = value;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the type of this {@link BodyToken} as a {@link Type}.
	 * @return the type of this {@link BodyToken} as a {@link Type}.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Returns the line number on which this {@link BodyToken} can be found within the DLB script.
	 * @return the line number on which this {@link BodyToken} can be found within the DLB script.
	 */
	public int getLineNumber() {
		return lineNumber;
	}

	/**
	 * Returns the column number on which this {@link BodyToken} may be found within the DLB script.
	 * @return the column number on which this {@link BodyToken} may be found within the DLB script.
	 */
	public int getColNumber() {
		return colNumber;
	}

	/**
	 * Returns the text representation of this {@link BodyToken}.
	 * @return the text representation of this {@link BodyToken}.
	 */
	public String getText() {
		return text;
	}

	/**
	 * <p>Returns the "value" of this {@link BodyToken} which has a different meaning depending on
	 * the {@link Type} of this token.</p>
	 *
	 * <ul>
	 *     <li>When the type is {@link Type#TEXT}, "value" is the text with escaped characters
	 *     resolved as a {@link String}.</li>
	 *     <li>When the type is {@link Type#QUOTED_STRING}, "value" is a
	 *     {@link VariableString}.</li>
	 *     <li>When the type is {@link Type#VARIABLE}, the "value" is the variable name as a
	 *     {@link String}.</li>
	 * </ul>
	 *
	 * @return the context-dependent value of this {@link BodyToken}, or {@code null} for a type
	 *         that carries none.
	 */
	public @Nullable Object getValue() {
		return value;
	}

	/**
	 * Replaces the context-dependent value. Used only to write back a {@link Type#TEXT} token's
	 * text after trimming whitespace from it in place.
	 *
	 * @param value the new value.
	 */
	private void setValue(@Nullable Object value) {
		this.value = value;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	@Override
	public String toString() {
		return "BodyToken{type=" + type + ", text=" + text + ", value=" + value
				+ ", lineNumber=" + lineNumber + ", colNumber=" + colNumber + "}";
	}

	/**
	 * Remove all whitespace from the given list of tokens through consecutive calls to
	 * {@link BodyToken#removeLeadingWhitespace(List)} and
	 * {@link BodyToken#removeTrailingWhitespace(List)}.
	 * @param tokens the list of {@link BodyToken}s to trim from white space.
	 */
	public static void trimWhitespace(List<BodyToken> tokens) {
		removeLeadingWhitespace(tokens);
		removeTrailingWhitespace(tokens);
	}

	/**
	 * Traverses each {@link BodyToken} in the given list. For every token of {@link Type#TEXT},
	 * removes all white space at the start of the text token.
	 * @param tokens the list of tokens to process.
	 */
	public static void removeLeadingWhitespace(List<BodyToken> tokens) {
		while (!tokens.isEmpty()) {
			BodyToken token = tokens.get(0);
			if (token.getType() != BodyToken.Type.TEXT)
				return;
			String text = (String) Objects.requireNonNull(token.getValue());
			text = text.replaceAll("^\\s+", "");
			token.setValue(text);
			if (!text.isEmpty())
				return;
			tokens.remove(0);
		}
	}

	/**
	 * Traverses each {@link BodyToken} in the given list. For every token of {@link Type#TEXT},
	 * removes all white space at the end of the text token.
	 * @param tokens the list of tokens to process.
	 */
	public static void removeTrailingWhitespace(List<BodyToken> tokens) {
		while (!tokens.isEmpty()) {
			BodyToken token = tokens.get(tokens.size() - 1);
			if (token.getType() != BodyToken.Type.TEXT)
				return;
			String text = (String) Objects.requireNonNull(token.getValue());
			text = text.replaceAll("\\s+$", "");
			token.setValue(text);
			if (!text.isEmpty())
				return;
			tokens.remove(tokens.size() - 1);
		}
	}

	/**
	 * Moves to the next token that is not a text token with only whitespace.
	 *
	 * @param tokens the tokens
	 * @return the skipped tokens
	 */
	public static List<BodyToken> skipWhitespace(CurrentIterator<BodyToken> tokens) {
		List<BodyToken> result = new ArrayList<>();
		while (tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			if (token.getType() != BodyToken.Type.TEXT)
				return result;
			String text = (String) Objects.requireNonNull(token.getValue());
			if (!text.trim().isEmpty())
				return result;
			result.add(token);
			tokens.moveNext();
		}
		return result;
	}
}

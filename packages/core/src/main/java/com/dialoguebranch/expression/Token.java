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
 * Portions of this file are vendored from the rrd-utils library
 * (https://github.com/RoessinghResearch/rrd-utils), used under the MIT License.
 *
 * Copyright (c) 2022 Roessingh Research and Development
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

package com.dialoguebranch.expression;

import org.jspecify.annotations.Nullable;

/**
 * A single lexical token produced by the {@link Tokenizer} from expression text: its {@link Type
 * type}, the source {@link #getText() text} it was matched from, its position in the input, and —
 * for atom tokens — the parsed {@link Value}.
 *
 * @author Dennis Hofs (RRD)
 */
public class Token {

	/** The kind of a {@link Token}: an operator, a grouping/punctuation character, or an atom. */
	public enum Type {
		// operator tokens

		/** The assignment operator {@code =}. */
		ASSIGN,
		/** The logical-or operator {@code ||}. */
		OR,
		/** The logical-and operator {@code &&}. */
		AND,
		/** The logical-not operator {@code !}. */
		NOT,
		/** The {@code in} operator (membership test). */
		IN,
		/** The less-than operator {@code <}. */
		LESS_THAN,
		/** The less-than-or-equal operator {@code <=}. */
		LESS_EQUAL,
		/** The equality operator {@code ==}. */
		EQUAL,
		/** The inequality operator {@code !=}. */
		NOT_EQUAL,
		/** The strict-equality operator {@code ===}. */
		STRICT_EQUAL,
		/** The strict-inequality operator {@code !==}. */
		NOT_STRICT_EQUAL,
		/** The greater-than-or-equal operator {@code >=}. */
		GREATER_EQUAL,
		/** The greater-than operator {@code >}. */
		GREATER_THAN,
		/** The addition / string-concatenation operator {@code +}. */
		ADD,
		/** The subtraction operator {@code -}. */
		SUBTRACT,
		/** The multiplication operator {@code *}. */
		MULTIPLY,
		/** The division operator {@code /}. */
		DIVIDE,
		/** The member-access operator {@code .}. */
		DOT,

		// group tokens

		/** An opening square bracket {@code [}. */
		BRACKET_OPEN,
		/** A closing square bracket {@code ]}. */
		BRACKET_CLOSE,
		/** An opening parenthesis {@code (}. */
		PARENTHESIS_OPEN,
		/** A closing parenthesis {@code )}. */
		PARENTHESIS_CLOSE,
		/** An opening brace <code>{</code>. */
		BRACE_OPEN,
		/** A closing brace <code>}</code>. */
		BRACE_CLOSE,
		/** A comma {@code ,} separating list or map entries. */
		COMMA,
		/** A colon {@code :} separating a map key from its value. */
		COLON,

		// atom tokens

		/** A quoted string literal; {@link #getValue()} holds the string. */
		STRING,
		/** A boolean literal ({@code true} / {@code false}); {@link #getValue()} holds the boolean. */
		BOOLEAN,
		/** A numeric literal; {@link #getValue()} holds the number. */
		NUMBER,
		/** The {@code null} literal; {@link #getValue()} holds a null value. */
		NULL,
		/** An unquoted identifier (variable or property name). */
		NAME,
		/** A {@code $}-prefixed variable reference. */
		DOLLAR_VARIABLE
	}

	private final Type type;
	private final String text;
	private final int lineNum;
	private final int colNum;
	private final long position;
	private final @Nullable Value value;

	/**
	 * Constructs a new token.
	 *
	 * @param type the token type.
	 * @param text the source text the token was matched from.
	 * @param lineNum the one-based line number of the token's first character.
	 * @param colNum the one-based column number of the token's first character.
	 * @param position the zero-based character offset of the token's first character.
	 * @param value the parsed value for an atom token, or {@code null} for any other type.
	 */
	public Token(Type type, String text, int lineNum, int colNum, long position,
			@Nullable Value value) {
		this.type = type;
		this.text = text;
		this.lineNum = lineNum;
		this.colNum = colNum;
		this.position = position;
		this.value = value;
	}

	/**
	 * Returns the token type.
	 *
	 * @return the token type.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Returns the source text this token was matched from.
	 *
	 * @return the source text.
	 */
	public String getText() {
		return text;
	}

	/**
	 * Returns the one-based line number of the token's first character.
	 *
	 * @return the line number.
	 */
	public int getLineNum() {
		return lineNum;
	}

	/**
	 * Returns the one-based column number of the token's first character.
	 *
	 * @return the column number.
	 */
	public int getColNum() {
		return colNum;
	}

	/**
	 * Returns the zero-based character offset of the token's first character.
	 *
	 * @return the character offset.
	 */
	public long getPosition() {
		return position;
	}

	/**
	 * Returns the parsed value for an atom token ({@link Type#STRING}, {@link Type#BOOLEAN},
	 * {@link Type#NUMBER}, {@link Type#NULL}), or {@code null} for any other token type.
	 *
	 * @return the parsed value, or {@code null}.
	 */
	public @Nullable Value getValue() {
		return value;
	}

	public String toString() {
		return "Token{type=" + type + ", text=" + text + ", lineNum=" + lineNum
				+ ", colNum=" + colNum + ", position=" + position + ", value=" + value + "}";
	}
}

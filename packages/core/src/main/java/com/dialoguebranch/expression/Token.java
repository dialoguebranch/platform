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

public class Token {
	public enum Type {
		// operator tokens
		ASSIGN,
		OR,
		AND,
		NOT,
		IN,
		LESS_THAN,
		LESS_EQUAL,
		EQUAL,
		NOT_EQUAL,
		STRICT_EQUAL,
		NOT_STRICT_EQUAL,
		GREATER_EQUAL,
		GREATER_THAN,
		ADD,
		SUBTRACT,
		MULTIPLY,
		DIVIDE,
		DOT,

		// group tokens
		BRACKET_OPEN,
		BRACKET_CLOSE,
		PARENTHESIS_OPEN,
		PARENTHESIS_CLOSE,
		BRACE_OPEN,
		BRACE_CLOSE,
		COMMA,
		COLON,

		// atom tokens
		STRING,
		BOOLEAN,
		NUMBER,
		NULL,
		NAME,
		DOLLAR_VARIABLE
	}

	private final Type type;
	private final String text;
	private final int lineNum;
	private final int colNum;
	private final long position;
	private final @Nullable Value value;

	public Token(Type type, String text, int lineNum, int colNum, long position,
			@Nullable Value value) {
		this.type = type;
		this.text = text;
		this.lineNum = lineNum;
		this.colNum = colNum;
		this.position = position;
		this.value = value;
	}

	public Type getType() {
		return type;
	}

	public String getText() {
		return text;
	}

	public int getLineNum() {
		return lineNum;
	}

	public int getColNum() {
		return colNum;
	}

	public long getPosition() {
		return position;
	}

	public @Nullable Value getValue() {
		return value;
	}

	public String toString() {
		return "Token{type=" + type + ", text=" + text + ", lineNum=" + lineNum
				+ ", colNum=" + colNum + ", position=" + position + ", value=" + value + "}";
	}
}

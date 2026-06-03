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

import com.dialoguebranch.model.VariableString;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.DataFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link BodyToken} is the smallest meaningful segment of a line of text of a DialogueBranch
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
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Dennis Hofs (Roessingh Research and Development)
 */
public class BodyToken {

	public enum Type {
		/**
		 * Value: text with escaped characters resolved
		 */
		TEXT,
	
		COMMAND_START,
		COMMAND_END,
		REPLY_START,
		REPLY_END,
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

	private Type type;
	private int lineNumber;
	private int colNumber;
	private String text;
	private Object value = null;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of a {@link BodyToken} with the given {@link Type}.
	 * @param type the {@link Type} of this {@link BodyToken}.
	 */
	public BodyToken(BodyToken.Type type) {
		this.type = type;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the type of this {@link BodyToken} as a {@link Type}.
	 * @return the type of this {@link BodyToken} as a {@link Type}.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Sets the type of this {@link BodyToken} as a {@link Type}.
	 * @param type the type of this {@link BodyToken} as a {@link Type}.
	 */
	public void setType(Type type) {
		this.type = type;
	}

	/**
	 * Returns the line number on which this {@link BodyToken} can be found within the DLB script.
	 * @return the line number on which this {@link BodyToken} can be found within the DLB script.
	 */
	public int getLineNumber() {
		return lineNumber;
	}

	/**
	 * Sets the line number on which this {@link BodyToken} can be found within the DLB script.
	 * @param lineNumber the line number on which this {@link BodyToken} can be found within the DLB
	 *                   script.
	 */
	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	/**
	 * Returns the column number on which this {@link BodyToken} may be found within the DLB script.
	 * @return the column number on which this {@link BodyToken} may be found within the DLB script.
	 */
	public int getColNumber() {
		return colNumber;
	}

	/**
	 * Sets the column number on which this {@link BodyToken} may be found within the DLB script.
	 * @param colNumber the column number on which this {@link BodyToken} may be found within the
	 *                  DLB script.
	 */
	public void setColNumber(int colNumber) {
		this.colNumber = colNumber;
	}

	/**
	 * Returns the text representation of this {@link BodyToken}.
	 * @return the text representation of this {@link BodyToken}.
	 */
	public String getText() {
		return text;
	}

	/**
	 * Sets the text representation of this {@link BodyToken}.
	 * @param text the text representation of this {@link BodyToken}.
	 */
	public void setText(String text) {
		this.text = text;
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
	 * @return the context-dependent value of this {@link BodyToken}.
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * Sets the context-dependent value of this {@link BodyToken}, see {@link BodyToken#getValue()}
	 * for additional details.
	 * @param value the context-dependent value of this {@link BodyToken}.
	 */
	public void setValue(Object value) {
		this.value = value;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	@Override
	public String toString() {
		DataFormatter formatter = new DataFormatter();
		return formatter.format(this, true, true);
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
			String text = (String)token.getValue();
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
			String text = (String)token.getValue();
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
			String text = (String)token.getValue();
			if (!text.trim().isEmpty())
				return result;
			result.add(token);
			tokens.moveNext();
		}
		return result;
	}
}

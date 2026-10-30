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
package com.dialoguebranch.model.common;

import java.util.regex.Pattern;

/**
 * The one definition of what a variable name is, used everywhere a name is recognised in a script
 * or checked in a request (#432).
 *
 * <p>A name starts with a letter or underscore, followed by letters, digits and underscores. It may
 * contain dots, so {@code settings.theme} is one name, but a dot belongs to the name only
 * when a letter or underscore follows it. So in {@code $score.} at the end of a sentence the
 * dot is a full stop, and in {@code $value.5} the name is {@code value}. A name never ends in a dot
 * and never contains two dots in a row. The variable store is flat: the dotted name is just the
 * key.</p>
 *
 * @author Harm op den Akker
 */
public final class VariableNames {

	private static final Pattern NAME = Pattern.compile(
			"[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");

	/**
	 * Private constructor; this class only holds static methods.
	 */
	private VariableNames() { }

	/**
	 * Returns whether the given text is a complete, valid variable name (without a leading
	 * {@code $}).
	 *
	 * @param name the text to check.
	 * @return {@code true} if it is a valid variable name.
	 */
	public static boolean isValid(String name) {
		return name != null && NAME.matcher(name).matches();
	}

	/**
	 * Returns whether a variable name, or a dot-separated part of one, can start with the given
	 * character.
	 *
	 * @param c the character.
	 * @return {@code true} for a letter or underscore.
	 */
	public static boolean isStart(char c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
	}

	/**
	 * Returns whether the given character can continue a part of a variable name (the dot that
	 * separates two parts is not included).
	 *
	 * @param c the character.
	 * @return {@code true} for a letter, digit or underscore.
	 */
	public static boolean isPart(char c) {
		return isStart(c) || (c >= '0' && c <= '9');
	}

	/**
	 * Finds the end of the variable name that starts at {@code start} in {@code text}, taking a dot
	 * into the name only when a letter or underscore follows it.
	 *
	 * @param text the text to read from.
	 * @param start the position where the name should start (after the {@code $}).
	 * @return the position just after the name, or {@code start} if no name starts there.
	 */
	public static int end(CharSequence text, int start) {
		if (start >= text.length() || !isStart(text.charAt(start)))
			return start;
		int i = start + 1;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (isPart(c)) {
				i++;
			} else if (c == '.' && i + 1 < text.length() && isStart(text.charAt(i + 1))) {
				i += 2;
			} else {
				break;
			}
		}
		return i;
	}
}

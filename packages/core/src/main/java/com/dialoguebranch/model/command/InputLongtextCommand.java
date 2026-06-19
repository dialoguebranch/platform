/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.model.command;

import nl.rrd.utils.exception.LineNumberParseException;
import com.dialoguebranch.parser.BodyToken;

import java.util.Map;

/**
 * Models the {@code <<input type="longtext" ...>>} command in Dialogue Branch, which prompts the
 * user to enter a longer, potentially multi-line text that is stored in a dialogue variable.
 *
 * @author Harm op den Akker
 */
public class InputLongtextCommand extends InputAbstractTextCommand {

	/**
	 * Creates an {@link InputLongtextCommand} that stores the user's text input in
	 * {@code variableName}.
	 *
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 */
	public InputLongtextCommand(String variableName) {
		super(TYPE_LONGTEXT, variableName);
	}

	/**
	 * Creates a deep copy of the given {@link InputLongtextCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputLongtextCommand(InputLongtextCommand other) {
		super(other);
	}

	@Override
	public InputLongtextCommand clone() {
		return new InputLongtextCommand(this);
	}

	/**
	 * Parses an {@link InputLongtextCommand} from the given pre-parsed attribute map.
	 *
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param attrs the parsed attribute map.
	 * @return the constructed {@link InputLongtextCommand}.
	 * @throws LineNumberParseException if the required {@code value} attribute is missing.
	 */
	public static InputCommand parse(BodyToken cmdStartToken,
									 Map<String, BodyToken> attrs) throws LineNumberParseException {
		String variableName = readVariableAttr("value", attrs, cmdStartToken,
				true);
		InputLongtextCommand command = new InputLongtextCommand(variableName);
		parseAttributes(command, cmdStartToken,
				attrs);
		return command;
	}
}

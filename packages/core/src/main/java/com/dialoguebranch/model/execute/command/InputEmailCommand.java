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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Models the {@code <<input type="email" ...>>} command in Dialogue Branch, which prompts the
 * user to enter a valid e-mail address that is stored in a dialogue variable.
 *
 * @author Harm op den Akker
 */
public class InputEmailCommand extends InputVariableCommand {

	/**
	 * Creates an {@link InputEmailCommand} that stores the user's e-mail input in
	 * {@code variableName}.
	 *
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 */
	public InputEmailCommand(String variableName) {
		super(TYPE_EMAIL, variableName);
	}

	/**
	 * Creates a deep copy of the given {@link InputEmailCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputEmailCommand(InputEmailCommand other) {
		super(other);
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("variableName", getVariableName());
		return result;
	}

	@Override
	public InputEmailCommand clone() {
		return new InputEmailCommand(this);
	}

	@Override
	public String toString() {
		String result = toStringStart();
		result += " value=\"$" + getVariableName() + "\">>";
		return result;
	}

	/**
	 * Parses an {@link InputEmailCommand} from the given pre-parsed attribute map.
	 *
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param attrs the parsed attribute map.
	 * @return the constructed {@link InputEmailCommand}.
	 * @throws LineNumberParseException if the required {@code value} attribute is missing.
	 */
	public static InputCommand parse(BodyToken cmdStartToken,
									 Map<String, BodyToken> attrs) throws LineNumberParseException {
		String variableName = requireVariableAttr("value", attrs, cmdStartToken);
		return new InputEmailCommand(variableName);
	}
}

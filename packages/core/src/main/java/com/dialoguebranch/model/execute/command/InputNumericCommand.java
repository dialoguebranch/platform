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
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Models the {@code <<input type="numeric" ...>>} command in Dialogue Branch, which prompts the
 * user to enter a number (optionally constrained by a minimum and/or maximum value) that is
 * stored in a dialogue variable.
 *
 * @author Harm op den Akker
 */
public class InputNumericCommand extends InputVariableCommand {
	private final @Nullable Integer min;
	private final @Nullable Integer max;

	/**
	 * Creates an {@link InputNumericCommand} that stores the user's numeric input in
	 * {@code variableName}, constrained by the given {@code min}/{@code max}.
	 *
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 * @param min the minimum value allowed, or {@code null} for no minimum.
	 * @param max the maximum value allowed, or {@code null} for no maximum.
	 */
	public InputNumericCommand(String variableName, @Nullable Integer min,
			@Nullable Integer max) {
		super(TYPE_NUMERIC, variableName);
		this.min = min;
		this.max = max;
	}

	/**
	 * Creates a deep copy of the given {@link InputNumericCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputNumericCommand(InputNumericCommand other) {
		super(other);
		this.min = other.min;
		this.max = other.max;
	}

	/**
	 * Returns the minimum numeric value allowed, or {@code null} if no minimum is set.
	 * @return the minimum value, or {@code null}.
	 */
	public @Nullable Integer getMin() {
		return min;
	}

	/**
	 * Returns the maximum numeric value allowed, or {@code null} if no maximum is set.
	 * @return the maximum value, or {@code null}.
	 */
	public @Nullable Integer getMax() {
		return max;
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("variableName", getVariableName());
		result.put("min", min);
		result.put("max", max);
		return result;
	}

	@Override
	public String toString() {
		String result = toStringStart();
		result += " value=\"$" + getVariableName() + "\"";
		if (min != null)
			result += " min=\"" + min + "\"";
		if (max != null)
			result += " max=\"" + max + "\"";
		result += ">>";
		return result;
	}

	@Override
	public InputNumericCommand clone() {
		return new InputNumericCommand(this);
	}

	/**
	 * Parses an {@link InputNumericCommand} from the given pre-parsed attribute map.
	 *
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param attrs the parsed attribute map.
	 * @return the constructed {@link InputNumericCommand}.
	 * @throws LineNumberParseException if the required {@code value} attribute is missing or
	 *         an optional range attribute has an invalid value.
	 */
	public static InputCommand parse(BodyToken cmdStartToken,
									 Map<String, BodyToken> attrs) throws LineNumberParseException {
		String variableName = requireVariableAttr("value", attrs, cmdStartToken);
		Integer min = readIntAttr("min", attrs, cmdStartToken, false, null,
				null);
		Integer max = readIntAttr("max", attrs, cmdStartToken, false, null,
				null);
		return new InputNumericCommand(variableName, min, max);
	}
}

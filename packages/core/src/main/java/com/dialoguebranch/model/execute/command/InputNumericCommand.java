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

package com.dialoguebranch.model.execute.command;

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.model.execute.NodeBody;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;
import nl.rrd.utils.expressions.Value;
import com.dialoguebranch.execution.parser.BodyToken;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Models the {@code <<input type="numeric" ...>>} command in Dialogue Branch, which prompts the
 * user to enter a number (optionally constrained by a minimum and/or maximum value) that is
 * stored in a dialogue variable.
 *
 * @author Harm op den Akker
 */
public class InputNumericCommand extends InputCommand {
	private String variableName;
	private Integer min = null;
	private Integer max = null;

	/**
	 * Creates an {@link InputNumericCommand} that stores the user's numeric input in
	 * {@code variableName}.
	 *
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 */
	public InputNumericCommand(String variableName) {
		super(TYPE_NUMERIC);
		this.variableName = variableName;
	}

	/**
	 * Creates a deep copy of the given {@link InputNumericCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputNumericCommand(InputNumericCommand other) {
		super(other);
		this.variableName = other.variableName;
		this.min = other.min;
		this.max = other.max;
	}

	/**
	 * Returns the name of the Dialogue Branch variable in which the user's numeric input is stored.
	 * @return the variable name.
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * Sets the name of the Dialogue Branch variable in which the user's numeric input is stored.
	 * @param variableName the variable name.
	 */
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	/**
	 * Returns the minimum numeric value allowed, or {@code null} if no minimum is set.
	 * @return the minimum value, or {@code null}.
	 */
	public Integer getMin() {
		return min;
	}

	/**
	 * Sets the minimum numeric value allowed, or {@code null} for no minimum.
	 * @param min the minimum value, or {@code null}.
	 */
	public void setMin(Integer min) {
		this.min = min;
	}

	/**
	 * Returns the maximum numeric value allowed, or {@code null} if no maximum is set.
	 * @return the maximum value, or {@code null}.
	 */
	public Integer getMax() {
		return max;
	}

	/**
	 * Sets the maximum numeric value allowed, or {@code null} for no maximum.
	 * @param max the maximum value, or {@code null}.
	 */
	public void setMax(Integer max) {
		this.max = max;
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("variableName", variableName);
		result.put("min", min);
		result.put("max", max);
		return result;
	}

	@Override
	public void getReadVariableNames(Set<String> varNames) {
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		varNames.add(variableName);
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			NodeBody processedBody) throws EvaluationException {
		processedBody.addSegment(new NodeBody.CommandSegment(this));
	}

	@Override
	public String getStatementLog(VariableStore varStore) {
		Variable variable = varStore.getVariable(variableName);
		Value value = new Value(variable.getValue());
		return value.toString();
	}

	@Override
	public String toString() {
		String result = toStringStart();
		result += " value=\"$" + variableName + "\"";
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
		String variableName = readVariableAttr("value", attrs, cmdStartToken,
				true);
		InputNumericCommand command = new InputNumericCommand(
				variableName);
		Integer min = readIntAttr("min", attrs, cmdStartToken, false, null,
				null);
		command.setMin(min);
		Integer max = readIntAttr("max", attrs, cmdStartToken, false, null,
				null);
		command.setMax(max);
		return command;
	}
}

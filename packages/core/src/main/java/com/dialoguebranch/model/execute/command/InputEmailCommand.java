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

import com.dialoguebranch.execution.VariableStore;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;
import nl.rrd.utils.expressions.Value;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.execution.parser.BodyToken;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Models the {@code <<input type="email" ...>>} command in Dialogue Branch, which prompts the
 * user to enter a valid e-mail address that is stored in a dialogue variable.
 *
 * @author Harm op den Akker
 */
public class InputEmailCommand extends InputCommand {
	private String variableName;

	/**
	 * Creates an {@link InputEmailCommand} that stores the user's e-mail input in
	 * {@code variableName}.
	 *
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 */
	public InputEmailCommand(String variableName) {
		super(TYPE_EMAIL);
		this.variableName = variableName;
	}

	/**
	 * Creates a deep copy of the given {@link InputEmailCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputEmailCommand(InputEmailCommand other) {
		super(other);
		this.variableName = other.variableName;
	}

	/**
	 * Returns the name of the Dialogue Branch variable in which the user's e-mail input is stored.
	 * @return the variable name.
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * Sets the name of the Dialogue Branch variable in which the user's e-mail input is stored.
	 * @param variableName the variable name.
	 */
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("variableName", variableName);
		return result;
	}

	@Override
	public String getStatementLog(VariableStore varStore) {
		Variable variable = varStore.getVariable(variableName);
		Value value = new Value(variable.getValue());
		return value.toString();
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
	public InputEmailCommand clone() {
		return new InputEmailCommand(this);
	}

	@Override
	public String toString() {
		String result = toStringStart();
		result += " value=\"$" + variableName + "\">>";
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
		String variableName = readVariableAttr("value", attrs, cmdStartToken,
				true);
		return new InputEmailCommand(variableName);
	}
}

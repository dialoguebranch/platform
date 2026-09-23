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

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.expression.Value;
import com.dialoguebranch.model.execute.NodeBody;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract base for an {@code <<input ...>>} command that stores the user's input directly in a
 * single named dialogue variable ({@link InputAbstractTextCommand}, {@link InputEmailCommand},
 * {@link InputNumericCommand}, {@link InputTimeCommand}) — every one of these read/wrote that
 * variable and logged its value the exact same way, so that part lives here once. {@link
 * InputSetCommand} does not extend this: its variable names live on its nested per-option model,
 * not on the command itself.
 *
 * @author Harm op den Akker
 */
public abstract class InputVariableCommand extends InputCommand {

	private String variableName;

	/**
	 * Creates an {@link InputVariableCommand} of the given {@code type} that stores the user's
	 * input in the dialogue variable named {@code variableName}.
	 *
	 * @param type the input command type (one of the {@code TYPE_*} constants in
	 *             {@link InputCommand}).
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 */
	protected InputVariableCommand(String type, String variableName) {
		super(type);
		this.variableName = variableName;
	}

	/**
	 * Creates a deep copy of the given {@link InputVariableCommand}.
	 *
	 * @param other the command to copy.
	 */
	protected InputVariableCommand(InputVariableCommand other) {
		super(other);
		this.variableName = other.variableName;
	}

	/**
	 * Returns the name of the Dialogue Branch variable in which the user's input is stored.
	 * @return the variable name.
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * Sets the name of the Dialogue Branch variable in which the user's input is stored.
	 * @param variableName the variable name.
	 */
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	/**
	 * Reads nothing beyond this command's own {@link #getVariableName() variableName} by default.
	 * Override where a subclass has its own expression-valued attributes (e.g. {@link
	 * InputTimeCommand}'s {@code startTime}/{@code minTime}/{@code maxTime}).
	 */
	@Override
	public void getReadVariableNames(Set<String> varNames) {
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		varNames.add(variableName);
	}

	@Override
	public String getStatementLog(VariableStore varStore) {
		Variable variable = Objects.requireNonNull(
				varStore.getVariable(variableName), variableName);
		Value value = new Value(variable.getValue());
		return value.toString();
	}

	/**
	 * Adds this command unchanged as the executed segment. Override where a subclass needs to
	 * resolve its own expression-valued attributes first (e.g. {@link InputTimeCommand}).
	 */
	@Override
	public void executeBodyCommand(Map<String, Object> variables, NodeBody processedBody)
			throws EvaluationException {
		processedBody.addSegment(new NodeBody.CommandSegment(this));
	}
}

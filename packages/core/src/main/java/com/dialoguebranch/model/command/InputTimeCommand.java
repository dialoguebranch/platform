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

import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.VariableString;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;
import nl.rrd.utils.expressions.Value;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.parser.BodyToken;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Models the {@code <<input type="time" ...>>} command in Dialogue Branch. Prompts the user to
 * select a time of day, stored in a dialogue variable as an ISO-8601 local-time string. Supports
 * optional granularity, start-time, min-time, and max-time constraints.
 *
 * @author Harm op den Akker
 */
public class InputTimeCommand extends InputCommand {
	/** Special time value meaning "the current time". */
	public static final String TIME_NOW = "now";

	private String variableName;
	private int granularityMinutes = 1;
	private VariableString startTime = null;
	private VariableString minTime = null;
	private VariableString maxTime = null;

	/**
	 * Creates an {@link InputTimeCommand} that stores the user's time input in
	 * {@code variableName}.
	 *
	 * @param variableName the Dialogue Branch variable name in which to store the input.
	 */
	public InputTimeCommand(String variableName) {
		super(TYPE_TIME);
		this.variableName = variableName;
	}

	/**
	 * Creates a deep copy of the given {@link InputTimeCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputTimeCommand(InputTimeCommand other) {
		super(other);
		this.variableName = other.variableName;
		this.granularityMinutes = other.granularityMinutes;
		if (other.startTime != null)
			this.startTime = new VariableString(other.startTime);
		if (other.minTime != null)
			this.minTime = new VariableString(other.minTime);
		if (other.maxTime != null)
			this.maxTime = new VariableString(other.maxTime);
	}

	/**
	 * Returns the name of the Dialogue Branch variable in which the user's time input is stored.
	 * @return the variable name.
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * Sets the name of the Dialogue Branch variable in which the user's time input is stored.
	 * @param variableName the variable name.
	 */
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	/**
	 * Returns the time picker granularity in minutes (e.g. {@code 1}, {@code 5}, {@code 15}).
	 * @return the granularity in minutes.
	 */
	public int getGranularityMinutes() {
		return granularityMinutes;
	}

	/**
	 * Sets the time picker granularity in minutes (e.g. {@code 1}, {@code 5}, {@code 15}).
	 * @param granularityMinutes the granularity in minutes.
	 */
	public void setGranularityMinutes(int granularityMinutes) {
		this.granularityMinutes = granularityMinutes;
	}

	/**
	 * Returns the initial time shown in the time picker, or {@code null} if not set.
	 * @return the start time, or {@code null}.
	 */
	public VariableString getStartTime() {
		return startTime;
	}

	/**
	 * Sets the initial time shown in the time picker, or {@code null} for no initial value.
	 * @param startTime the start time, or {@code null}.
	 */
	public void setStartTime(VariableString startTime) {
		this.startTime = startTime;
	}

	/**
	 * Returns the earliest time the user may select, or {@code null} if not set.
	 * @return the minimum time, or {@code null}.
	 */
	public VariableString getMinTime() {
		return minTime;
	}

	/**
	 * Sets the earliest time the user may select, or {@code null} for no lower bound.
	 * @param minTime the minimum time, or {@code null}.
	 */
	public void setMinTime(VariableString minTime) {
		this.minTime = minTime;
	}

	/**
	 * Returns the latest time the user may select, or {@code null} if not set.
	 * @return the maximum time, or {@code null}.
	 */
	public VariableString getMaxTime() {
		return maxTime;
	}

	/**
	 * Sets the latest time the user may select, or {@code null} for no upper bound.
	 * @param maxTime the maximum time, or {@code null}.
	 */
	public void setMaxTime(VariableString maxTime) {
		this.maxTime = maxTime;
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("variableName", variableName);
		result.put("granularityMinutes", granularityMinutes);
		if (startTime != null)
			result.put("startTime", startTime.evaluate(null));
		if (minTime != null)
			result.put("minTime", minTime.evaluate(null));
		if (maxTime != null)
			result.put("maxTime", maxTime.evaluate(null));
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
		if (startTime != null)
			startTime.getReadVariableNames(varNames);
		if (minTime != null)
			minTime.getReadVariableNames(varNames);
		if (maxTime != null)
			maxTime.getReadVariableNames(varNames);
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		varNames.add(variableName);
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			NodeBody processedBody) throws EvaluationException {
		InputTimeCommand processedCmd = new InputTimeCommand(
				variableName);
		processedCmd.granularityMinutes = granularityMinutes;
		if (startTime != null) {
			processedCmd.startTime = evaluateTime(startTime.evaluate(
					variables));
		}
		if (minTime != null) {
			processedCmd.minTime = evaluateTime(minTime.evaluate(variables));
		}
		if (maxTime != null) {
			processedCmd.maxTime = evaluateTime(maxTime.evaluate(variables));
		}
		processedBody.addSegment(new NodeBody.CommandSegment(processedCmd));
	}

	@Override
	public InputTimeCommand clone() {
		return new InputTimeCommand(this);
	}

	private static VariableString evaluateTime(String text)
			throws EvaluationException {
		if (text.equalsIgnoreCase(TIME_NOW))
			return new VariableString(TIME_NOW);
		DateTimeFormatter parser = DateTimeFormatter.ISO_LOCAL_TIME;
		LocalTime time;
		try {
			time = parser.parse(text, LocalTime::from);
		} catch (DateTimeParseException ex) {
			throw new EvaluationException("Invalid local time value: " + text);
		}
		return new VariableString(time.format(
				DateTimeFormatter.ofPattern("HH:mm")));
	}

	@Override
	public String toString() {
		char[] escapes = new char[] { '"' };
		StringBuilder builder = new StringBuilder(toStringStart());
		builder.append(" value=\"$" + variableName + "\"");
		builder.append(" granularityMinutes=\"" + granularityMinutes + "\"");
		if (startTime != null) {
			builder.append(" startTime=\"" + startTime.toString(escapes) +
					"\"");
		}
		if (minTime != null)
			builder.append(" minTime=\"" + minTime.toString(escapes) + "\"");
		if (maxTime != null)
			builder.append(" maxTime=\"" + maxTime.toString(escapes) + "\"");
		builder.append(">>");
		return builder.toString();
	}

	/**
	 * Parses an {@link InputTimeCommand} from the given pre-parsed attribute map.
	 *
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param attrs the parsed attribute map.
	 * @return the constructed {@link InputTimeCommand}.
	 * @throws LineNumberParseException if any attribute value is invalid.
	 */
	public static InputTimeCommand parse(BodyToken cmdStartToken,
										 Map<String, BodyToken> attrs) throws LineNumberParseException {
		String variableName = readVariableAttr("value", attrs, cmdStartToken,
				true);
		InputTimeCommand command = new InputTimeCommand(variableName);
		Integer granularityMinutes = readIntAttr("granularityMinutes", attrs,
				cmdStartToken, false, 1, null);
		if (granularityMinutes != null)
			command.granularityMinutes = granularityMinutes;
		command.startTime = readTimeAttribute("startTime", attrs,
				cmdStartToken);
		command.minTime = readTimeAttribute("minTime", attrs, cmdStartToken);
		command.maxTime = readTimeAttribute("maxTime", attrs, cmdStartToken);
		return command;
	}

	private static VariableString readTimeAttribute(String attrName,
													Map<String, BodyToken> attrs, BodyToken cmdStartToken)
			throws LineNumberParseException {
		VariableString result = readAttr(attrName, attrs, cmdStartToken,
				false);
		if (result == null || result.containsVariables())
			return result;
		BodyToken token = attrs.get(attrName);
		String value = result.evaluate(null);
		try {
			return evaluateTime(value);
		} catch (EvaluationException ex) {
			throw new LineNumberParseException(String.format(
					"Invalid value for attribute \"%s\"", attrName) + ": " +
					ex.getMessage(), token.getLineNumber(), token.getColNumber(), ex);
		}
	}
}

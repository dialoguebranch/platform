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

package com.dialoguebranch.model.command;

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.VariableString;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;
import nl.rrd.utils.expressions.Value;
import nl.rrd.utils.json.JsonMapper;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.parser.BodyToken;

import java.util.*;

public class InputSetCommand extends InputCommand {
	private List<Option> options = new ArrayList<>();

	public InputSetCommand() {
		super(TYPE_SET);
	}

	public InputSetCommand(InputSetCommand other) {
		super(other);
		for (Option option : other.options) {
			this.options.add(new Option(option));
		}
	}

	public List<Option> getOptions() {
		return options;
	}

	public void setOptions(List<Option> options) {
		this.options = options;
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		List<Map<String,String>> processedOptions = new ArrayList<>();
		result.put("options", processedOptions);
		for (Option option : options) {
			Map<String,String> processedOption = new LinkedHashMap<>();
			processedOption.put("variableName", option.getVariableName());
			processedOption.put("text", option.getText().evaluate(null));
			processedOptions.add(processedOption);
		}
		return result;
	}

	@Override
	public String getStatementLog(VariableStore varStore) {
		List<String> optionTexts = new ArrayList<>();
		for (Option option : options) {
			Variable variable = varStore.getVariable(option.getVariableName());
			Value value = new Value(variable.getValue());
			if (value.asBoolean())
				optionTexts.add(option.getText().evaluate(null));
		}
		return JsonMapper.generate(optionTexts);
	}

	@Override
	public void getReadVariableNames(Set<String> varNames) {
		for (Option option : options) {
			option.getText().getReadVariableNames(varNames);
		}
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
		for (Option option : options) {
			varNames.add(option.getVariableName());
		}
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			NodeBody processedBody) throws EvaluationException {
		InputSetCommand processedCmd = new InputSetCommand();
		for (Option option : options) {
			Option processedOption = new Option();
			processedOption.setVariableName(option.getVariableName());
			processedOption.setText(option.getText().execute(variables));
			processedCmd.options.add(processedOption);
		}
		processedBody.addSegment(new NodeBody.CommandSegment(processedCmd));
	}

	@Override
	public String toString() {
		char[] escapes = new char[] { '"' };
		StringBuilder builder = new StringBuilder(toStringStart());
		for (int i = 0; i < options.size(); i++) {
			Option option = options.get(i);
			builder.append(" value" + (i+1) + "=\"$" +
					option.getVariableName() + "\"");
			builder.append(" option" + (i+1) + "=\"" +
					option.getText().toString(escapes) + "\"");
		}
		builder.append(">>");
		return builder.toString();
	}

	@Override
	public InputSetCommand clone() {
		return new InputSetCommand(this);
	}

	public static InputSetCommand parse(BodyToken cmdStartToken,
										Map<String, BodyToken> attrs) throws LineNumberParseException {
		InputSetCommand result = new InputSetCommand();
		int index = 1;
		while (true) {
			BodyToken valueToken = attrs.get("value" + index);
			BodyToken optionToken = attrs.get("option" + index);
			if (valueToken == null && optionToken == null) {
				return result;
			} else if (valueToken != null && optionToken == null) {
				throw new LineNumberParseException(String.format(
						"Found attribute \"%s\" without attribute \"%s\"",
						"value" + index, "option" + index),
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			} else if (valueToken == null && optionToken != null) {
				throw new LineNumberParseException(String.format(
						"Found attribute \"%s\" without attribute \"%s\"",
						"option" + index, "value" + index),
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			}
			Option option = new Option();
			option.setVariableName(readVariableAttr("value" + index, attrs,
					cmdStartToken, true));
			option.setText(readAttr("option" + index, attrs, cmdStartToken,
					true));
			result.options.add(option);
			index++;
		}
	}

	public static class Option {
		private String variableName = null;
		private VariableString text = null;

		public Option() {
		}

		public Option(Option other) {
			this.variableName = other.variableName;
			if (other.text != null)
				this.text = new VariableString(other.text);
		}

		public String getVariableName() {
			return variableName;
		}

		public void setVariableName(String variableName) {
			this.variableName = variableName;
		}

		public VariableString getText() {
			return text;
		}

		public void setText(VariableString text) {
			this.text = text;
		}
	}
}

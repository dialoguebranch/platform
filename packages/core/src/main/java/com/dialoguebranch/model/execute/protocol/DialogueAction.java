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

package com.dialoguebranch.model.execute.protocol;

import com.dialoguebranch.model.execute.VariableString;
import com.dialoguebranch.model.execute.command.ActionCommand;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is used for dialogue actions that are sent to the client in the
 * web service protocol. It mirrors {@link ActionCommand}
 * except that any variables in strings have been resolved.
 *
 * @author Dennis Hofs
 */
public class DialogueAction {
	private final String type;
	private final String value;
	private final Map<String,String> parameters;

	/**
	 * Creates a {@link DialogueAction}. This is also the JSON deserialization entry point.
	 *
	 * @param type the action type.
	 * @param value the resolved action value.
	 * @param parameters the optional parameters map, or {@code null} for none.
	 */
	@JsonCreator
	public DialogueAction(
			@JsonProperty("type") String type,
			@JsonProperty("value") String value,
			@JsonProperty("parameters") @Nullable Map<String,String> parameters) {
		this.type = type;
		this.value = value;
		this.parameters = parameters != null ? parameters : new LinkedHashMap<>();
	}

	/**
	 * Creates a {@link DialogueAction} from the given {@link ActionCommand}, resolving all
	 * variable references in the command's value and parameters.
	 *
	 * @param actionCommand the action command with variables already resolved.
	 */
	public DialogueAction(ActionCommand actionCommand) {
		this(actionCommand.getType(), actionCommand.getValue().evaluate(null),
				resolveParameters(actionCommand));
	}

	private static Map<String,String> resolveParameters(ActionCommand actionCommand) {
		Map<String,String> result = new LinkedHashMap<>();
		Map<String, VariableString> cmdParams = actionCommand.getParameters();
		for (String key : cmdParams.keySet()) {
			result.put(key, cmdParams.get(key).evaluate(null));
		}
		return result;
	}

	/**
	 * Returns the action type (e.g. {@code "image"}, {@code "video"}, {@code "link"}, or
	 * {@code "generic"}).
	 * @return the action type.
	 */
	public String getType() {
		return type;
	}

	/**
	 * Returns the resolved value of this action (e.g. a URL for a link action).
	 * @return the action value.
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Returns the map of optional parameters for this action (key → resolved string value).
	 * @return the parameters map.
	 */
	public Map<String,String> getParameters() {
		return parameters;
	}
}

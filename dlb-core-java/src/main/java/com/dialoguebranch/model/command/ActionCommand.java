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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.VariableString;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.parser.BodyToken;
import com.dialoguebranch.parser.NodeState;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.expressions.EvaluationException;

/**
 * This command models the &lt;&lt;action ...&gt;&gt; command in Dialogue Branch. It specifies an
 * action that should be performed along with a statement. It can be part of a {@link NodeBody}
 * (along with an agent statement) or a {@link Reply} (to be performed when the user chooses the
 * reply).
 *
 * <p>Four different action commands are supported:</p>
 * <ul>
 *     <li>image</li>
 *     <li>video</li>
 *     <li>link</li>
 *     <li>generic</li>
 * </ul>
 *
 * <p>An example of an {@link ActionCommand} is as follows:
 *
 * <pre>
 *     &lt;&lt;action type="link" value="www.dialoguebranch.com/" text="website"&gt;&gt;
 * </pre>
 *
 * <p>In this example, the {@code type} is "link", the {@code value} is "www.dialoguebranch.com" and
 * the {@code parameters} is a set containing one entry for "text", with the value "website". An
 * {@link ActionCommand} may contain any number of optional parameters like this.</p>
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ActionCommand extends AttributesCommand {

	/** The reserved type of action for images. */
	public static final String TYPE_IMAGE = "image";

	/** The reserved type of action for video. */
	public static final String TYPE_VIDEO = "video";

	/** The reserved type of action for hyperlinks. */
	public static final String TYPE_LINK = "link";

	/** The reserved type of action for generic (user defined) actions. */
	public static final String TYPE_GENERIC = "generic";

	/** The list of all valid action types. */
	private static final List<String> VALID_TYPES = Arrays.asList(
			TYPE_IMAGE, TYPE_VIDEO, TYPE_LINK, TYPE_GENERIC);

	/** The specific type of this ActionCommand. */
	private String type;

	/** The contents of the ActionCommand modelled as a {@link VariableString}. */
	private VariableString value;

	/** The set of "other" free parameters defined in this ActionCommand. */
	private Map<String, VariableString> parameters = new LinkedHashMap<>();

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of an {@link ActionCommand} with given {@code type} and {@code value}.
	 *
	 * @param type the type of this {@link ActionCommand} as a String, which should be one of
	 *             "image", "video", "link", or "generic".
	 * @param value the value of this command
	 */
	public ActionCommand(String type, VariableString value) {
		this.type = type;
		this.value = value;
	}

	/**
	 * Creates an instance of an {@link ActionCommand} based on the contents of the given {@code
	 * other} {@link ActionCommand}.
	 *
	 * @param other the {@link ActionCommand} used to populate the contents of this {@link
	 *              ActionCommand}.
	 */
	public ActionCommand(ActionCommand other) {
		this.type = other.type;
		this.value = new VariableString(other.value);
		for (String key : other.parameters.keySet()) {
			this.parameters.put(key, new VariableString(other.parameters.get(key)));
		}
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the type of this {@link ActionCommand} as a String.
	 *
	 * @return the type of this {@link ActionCommand} as a String.
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the type of this {@link ActionCommand}, which should be one of "image", "video", "link",
	 * or "generic".
	 *
	 * @param type the type of this {@link ActionCommand}.
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Return the contents of the 'value' part of the ActionCommand as a VariableString.
	 *
	 * @return the contents of the 'value' part of the ActionCommand as a VariableString.
	 */
	public VariableString getValue() {
		return value;
	}

	/**
	 * Sets the contents of the 'value' part of the ActionCommand as a VariableString.
	 *
	 * @param value the contents of the 'value' part of the ActionCommand as a VariableString.
	 */
	public void setValue(VariableString value) {
		this.value = value;
	}

	/**
	 * Returns the map of optional parameters that are part of this ActionCommand.
	 *
	 * @return the map of optional parameters that are part of this ActionCommand.
	 */
	public Map<String, VariableString> getParameters() {
		return parameters;
	}

	/**
	 * Sets the optional parameters that are part of this ActionCommand.
	 *
	 * @param parameters the optional parameters that are part of this ActionCommand.
	 */
	public void setParameters(Map<String, VariableString> parameters) {
		this.parameters = parameters;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	/**
	 * Adds an optional parameter with the given {@code name} and {@code value} to the map of
	 * optional parameters for this {@link ActionCommand}.
	 *
	 * @param name the name of the optional parameter
	 * @param value the value of the optional parameter, which may includes Dialogue Branch
	 *              Variables.
	 */
	public void addParameter(String name, VariableString value) {
		parameters.put(name, value);
	}

	@Override
	public Reply findReplyById(int replyId) {
		return null;
	}

	@Override
	public void getReadVariableNames(Set<String> varNames) {
		value.getReadVariableNames(varNames);
		for (VariableString parameterValues : parameters.values()) {
			parameterValues.getReadVariableNames(varNames);
		}
	}

	@Override
	public void getWriteVariableNames(Set<String> varNames) {
	}

	@Override
	public void getNodePointers(Set<NodePointer> pointers) {
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			NodeBody processedBody) throws EvaluationException {
		ActionCommand processedCommand = executeReplyCommand(variables);
		processedBody.addSegment(new NodeBody.CommandSegment(
				processedCommand));
	}

	public ActionCommand executeReplyCommand(Map<String,Object> variables) {
		ActionCommand processedCommand = new ActionCommand(type,
				value.execute(variables));
		for (String param : parameters.keySet()) {
			VariableString value = parameters.get(param);
			processedCommand.addParameter(param, value.execute(variables));
		}
		return processedCommand;
	}

	@Override
	public String toString() {
		char[] escapes = new char[] { '"' };
		StringBuilder result = new StringBuilder(
				"<<action type=\"" + type +
				"\" value=\"" + value.toString(escapes) + "\"");
		for (String key : parameters.keySet()) {
			result.append(" ")
					.append(key)
					.append("=\"")
					.append(parameters.get(key).toString(escapes))
					.append("\"");
		}
		result.append(">>");
		return result.toString();
	}
	
	public static ActionCommand parse(BodyToken cmdStartToken,
									  CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		Map<String, BodyToken> attrs = parseAttributesCommand(cmdStartToken, tokens);
		String type = readPlainTextAttr("type", attrs, cmdStartToken, true);
		BodyToken token = attrs.get("type");
		if (!VALID_TYPES.contains(type)) {
			throw new LineNumberParseException(
					"Invalid value for attribute \"type\": " + type,
					token.getLineNumber(), token.getColNumber());
		}
		attrs.remove("type");
		VariableString value = readAttr("value", attrs, cmdStartToken, true);
		attrs.remove("value");
		ActionCommand command = new ActionCommand(type, value);
		for (String attr : attrs.keySet()) {
			token = attrs.get(attr);
			command.addParameter(attr, (VariableString)token.getValue());
		}
		return command;
	}

	@Override
	public ActionCommand clone() {
		return new ActionCommand(this);
	}

}

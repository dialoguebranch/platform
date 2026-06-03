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
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.VariableString;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.parser.NodeState;
import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.parser.BodyToken;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class models the &lt;&lt;input ...&gt;&gt; command in DialogueBranch. It can
 * be part of a {@link NodeBody} inside a reply.
 * 
 * @author Dennis Hofs (RRD)
 */
public abstract class InputCommand extends AttributesCommand {
	public static final String TYPE_EMAIL = "email";
	public static final String TYPE_TEXT = "text";
	public static final String TYPE_LONGTEXT = "longtext";
	public static final String TYPE_NUMERIC = "numeric";
	public static final String TYPE_SET = "set";
	public static final String TYPE_TIME = "time";

	private static final List<String> VALID_TYPES = Arrays.asList(TYPE_EMAIL,
			TYPE_TEXT, TYPE_LONGTEXT, TYPE_NUMERIC, TYPE_SET, TYPE_TIME);
	
	private String type;
	private String description = null;

	public InputCommand(String type) {
		this.type = type;
	}

	public InputCommand(InputCommand other) {
		this.type = other.type;
		this.description = other.description;
	}

	/**
	 * Returns the type of input command. This should be one of the TYPE_*
	 * constants defined in this class.
	 *
	 * @return the type of input command
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the type of input command. This should be one of the TYPE_*
	 * constants defined in this class.
	 *
	 * @param type the type of input command
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Returns the description of this input command. For example a client can
	 * use this in input validation messages ("You did not fill in [your
	 * name]."). The description is optional and may be null.
	 *
	 * @return the description or null
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description of this input command. For example a client can use
	 * this in input validation messages ("You did not fill in [your name].").
	 * The description is optional and may be null.
	 *
	 * @param description the description or null
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the parameters for this input command to send to the client. This
	 * is a map from parameter names to values. A value can be any JSON type.
	 * This method should only be called on a command that has already been
	 * executed with {@link #executeBodyCommand(Map, NodeBody)
	 * executeBodyCommand()}. This means that any variables in parameter values
	 * have already been resolved.
	 *
	 * @return the parameters for this input command to send to the client
	 */
	public abstract Map<String,?> getParameters();

	/**
	 * Returns the string to use in the user statement log in place of this
	 * input command. It can use variable values from the specified variable
	 * store. This method should only be called on a command that has already
	 * been executed with {@link #executeBodyCommand(Map, NodeBody)
	 * executeBodyCommand()}. This means that any variables in parameter values
	 * have already been resolved.
	 *
	 * @param varStore the variable store
	 * @return the statement log
	 */
	public abstract String getStatementLog(VariableStore varStore);

	@Override
	public Reply findReplyById(int replyId) {
		return null;
	}

	@Override
	public void getNodePointers(Set<NodePointer> pointers) {
	}

	public static InputCommand parse(BodyToken cmdStartToken,
									 CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		Map<String, BodyToken> attrs = parseAttributesCommand(cmdStartToken,
				tokens);
		String type = readPlainTextAttr("type", attrs, cmdStartToken, true);
		BodyToken token = attrs.get("type");
		if (!VALID_TYPES.contains(type)) {
			throw new LineNumberParseException(
					"Invalid value for attribute \"type\": " + type,
					token.getLineNumber(), token.getColNumber());
		}
		InputCommand result;
		switch (type) {
			case TYPE_EMAIL:
				result = InputEmailCommand.parse(cmdStartToken, attrs);
				break;
			case TYPE_TEXT:
				result = InputTextCommand.parse(cmdStartToken, attrs);
				break;
			case TYPE_LONGTEXT:
				result = InputLongtextCommand.parse(cmdStartToken, attrs);
				break;
			case TYPE_NUMERIC:
				result = InputNumericCommand.parse(cmdStartToken, attrs);
				break;
			case TYPE_SET:
				result = InputSetCommand.parse(cmdStartToken, attrs);
				break;
			case TYPE_TIME:
				result = InputTimeCommand.parse(cmdStartToken, attrs);
				break;
			default:
				throw new RuntimeException("Unsupported value for input type: " + type);
		}
		String description = readPlainTextAttr("description", attrs,
				cmdStartToken, false);
		if (description != null && !description.isEmpty())
			result.setDescription(description);
		return result;
	}

	protected String toStringStart() {
		String result = "<<input type=\"" + type + "\"";
		if (description != null) {
			char[] escapes = new char[] { '"' };
			String escapedDescr = new VariableString(description)
					.toString(escapes);
			result += " description=\"" + escapedDescr + "\"";
		}
		return result;
	}
}

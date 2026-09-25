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
import com.dialoguebranch.execution.parser.NodeState;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResolvedNodeBody;
import com.dialoguebranch.model.execute.VariableString;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.util.CurrentIterator;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class ActionCommand extends AttributesCommand {

	/** The specific type of this ActionCommand. */
	private final ActionType type;

	/** The contents of the ActionCommand modelled as a {@link VariableString}. */
	private final VariableString value;

	/** The set of "other" free parameters defined in this ActionCommand. */
	private final Map<String, VariableString> parameters = new LinkedHashMap<>();

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an {@link ActionCommand} with given {@code type} and {@code value}.
	 *
	 * @param type the type of this {@link ActionCommand}.
	 * @param value the value of this command
	 */
	public ActionCommand(ActionType type, VariableString value) {
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

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the type of this {@link ActionCommand}.
	 *
	 * @return the type of this {@link ActionCommand}.
	 */
	public ActionType getType() {
		return type;
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
	 * Returns the map of optional parameters that are part of this ActionCommand.
	 *
	 * @return the map of optional parameters that are part of this ActionCommand.
	 */
	public Map<String, VariableString> getParameters() {
		return parameters;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

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
	public @Nullable Reply findReplyById(int replyId) {
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
			ResolvedNodeBody.Builder processedBody) throws EvaluationException {
		ActionCommand processedCommand = executeReplyCommand(variables);
		processedBody.addSegment(new NodeBody.CommandSegment(
				processedCommand));
	}

	/**
	 * Executes this {@link ActionCommand} against the given variable map, returning a new
	 * {@link ActionCommand} with all variable references resolved to their current values.
	 *
	 * @param variables the variable map used to resolve variable references.
	 * @return a new {@link ActionCommand} with all variables resolved.
	 */
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

	/**
	 * Parses an {@link ActionCommand} from the token stream.
	 *
	 * @param cmdStartToken the token that started the {@code <<action>>} command.
	 * @param tokens the token iterator, positioned after the command-start token.
	 * @param nodeState the current node parse state.
	 * @return the parsed {@link ActionCommand}.
	 * @throws LineNumberParseException if the command is malformed.
	 */
	public static ActionCommand parse(BodyToken cmdStartToken,
									  CurrentIterator<BodyToken> tokens, NodeState nodeState)
			throws LineNumberParseException {
		Map<String, BodyToken> attrs = parseAttributesCommand(cmdStartToken, tokens);
		String typeAttr = requirePlainTextAttr("type", attrs, cmdStartToken);
		BodyToken token = presentToken(attrs, "type");
		ActionType type = ActionType.fromWireValue(typeAttr);
		if (type == null) {
			throw new LineNumberParseException(
					"Invalid value for attribute \"type\": " + typeAttr,
					token.getLineNumber(), token.getColNumber());
		}
		attrs.remove("type");
		VariableString value = requireAttr("value", attrs, cmdStartToken);
		attrs.remove("value");
		ActionCommand command = new ActionCommand(type, value);
		for (String attr : attrs.keySet()) {
			token = attrs.get(attr);
			command.addParameter(attr, (VariableString) Objects.requireNonNull(token.getValue()));
		}
		return command;
	}

	@Override
	public ActionCommand clone() {
		return new ActionCommand(this);
	}

}

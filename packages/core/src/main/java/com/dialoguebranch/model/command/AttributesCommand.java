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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.rrd.utils.CurrentIterator;
import nl.rrd.utils.exception.LineNumberParseException;
import com.dialoguebranch.model.VariableString;
import com.dialoguebranch.parser.BodyToken;

/**
 * Base class for Dialogue Branch commands that specify key="value" attribute pairs between
 * &lt;&lt;...&gt;&gt; delimiters. Subclasses call the protected static helpers to read individual
 * attributes from the parsed token map.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public abstract class AttributesCommand extends Command {

	/** Creates an instance of an {@link AttributesCommand}. */
	protected AttributesCommand() {
	}

	/**
	 * Parses a command that is formatted like:<br />
	 * &lt;&lt;command attr1="value1" attr2="value2"&gt;&gt;
	 * 
	 * <p>It returns a map where the keys are attribute names and the values
	 * are tokens with type QUOTED_STRING.</p>
	 * 
	 * @param cmdStartToken the command start token
	 * @param tokens the token iterator, positioned after the command start
	 * token
	 * @return the attributes
	 * @throws LineNumberParseException if a parsing error occurs
	 */
	protected static Map<String, BodyToken> parseAttributesCommand(
            BodyToken cmdStartToken, CurrentIterator<BodyToken> tokens)
			throws LineNumberParseException {
		Map<String, BodyToken> result = new LinkedHashMap<>();
		boolean first = true;
		while (tokens.getCurrent() != null) {
			BodyToken token = tokens.getCurrent();
			String text;
			if (first) {
				first = false;
				text = ((String)token.getValue()).trim();
				String[] split = text.split("\\s+", 2);
				if (split.length < 2) {
					tokens.moveNext();
					continue;
				}
				text = split[1];
			} else if (token.getType() == BodyToken.Type.COMMAND_END) {
				tokens.moveNext();
				return result;
			} else if (token.getType() != BodyToken.Type.TEXT) {
				throw new LineNumberParseException(
						"Expected attribute name, found token: " +
						token.getType(), token.getLineNumber(), token.getColNumber());
			} else {
				text = ((String)token.getValue()).trim();
			}
			// text is a trimmed string, length > 0
			// expect: attribute=
			int sep = text.indexOf('=');
			String attr;
			if (sep != -1)
				attr = text.substring(0, sep).trim();
			else
				attr = text;
			if (!attr.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
				throw new LineNumberParseException("Invalid attribute name: " +
						attr, token.getLineNumber(), token.getColNumber());
			}
			if (sep == -1) {
				throw new LineNumberParseException(
						"Character = not found after attribute name",
						token.getLineNumber(), token.getColNumber());
			}
			String post = text.substring(sep + 1).trim();
			if (!post.isEmpty()) {
				throw new LineNumberParseException("Unexpected text after =",
						token.getLineNumber(), token.getColNumber());
			}
			tokens.moveNext();
			BodyToken.skipWhitespace(tokens);
			token = tokens.getCurrent();
			if (token == null) {
				throw new LineNumberParseException("Command not terminated",
						cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
			}
			if (token.getType() != BodyToken.Type.QUOTED_STRING) {
				throw new LineNumberParseException(
						"Expected quoted string, found token: " +
						token.getType(), token.getLineNumber(), token.getColNumber());
			}
			result.put(attr, token);
			tokens.moveNext();
			BodyToken.skipWhitespace(tokens);
		}
		throw new LineNumberParseException("Command not terminated",
				cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
	}
	
	/**
	 * Reads an attribute value as a {@link VariableString} from the parsed attribute map.
	 *
	 * @param name the attribute name to look up.
	 * @param attrs the parsed attribute map produced by {@link #parseAttributesCommand}.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param require {@code true} if the attribute is mandatory.
	 * @return the attribute value, or {@code null} if absent and {@code require} is {@code false}.
	 * @throws LineNumberParseException if the attribute is required but absent.
	 */
	protected static VariableString readAttr(String name,
                                             Map<String, BodyToken> attrs, BodyToken cmdStartToken,
                                             boolean require) throws LineNumberParseException {
		if (!attrs.containsKey(name)) {
			if (!require)
				return null;
			throw new LineNumberParseException(String.format(
					"Required attribute \"%s\" not found", name),
					cmdStartToken.getLineNumber(), cmdStartToken.getColNumber());
		}
		BodyToken token = attrs.get(name);
		return (VariableString)token.getValue();
	}
	
	/**
	 * Reads an attribute value that must be plain text (no variable references).
	 *
	 * @param name the attribute name to look up.
	 * @param attrs the parsed attribute map produced by {@link #parseAttributesCommand}.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param require {@code true} if the attribute is mandatory.
	 * @return the plain-text value, or {@code null} if absent and {@code require} is {@code false}.
	 * @throws LineNumberParseException if the attribute is required but absent, or if its value
	 *         contains variable references.
	 */
	protected static String readPlainTextAttr(String name,
                                              Map<String, BodyToken> attrs, BodyToken cmdStartToken,
                                              boolean require) throws LineNumberParseException {
		VariableString varStr = readAttr(name, attrs, cmdStartToken,
				require);
		if (varStr == null)
			return null;
		BodyToken token = attrs.get(name);
		if (varStr.containsVariables()) {
			throw new LineNumberParseException(String.format(
					"Value for attribute \"%s\" is not plain text", name) +
					": " + token.getText(), token.getLineNumber(),
					token.getColNumber());
		}
		return varStr.evaluate(null);
	}
	
	/**
	 * Reads an attribute whose value must be a single variable reference (e.g. {@code "$varName"}).
	 *
	 * @param name the attribute name to look up.
	 * @param attrs the parsed attribute map produced by {@link #parseAttributesCommand}.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param require {@code true} if the attribute is mandatory.
	 * @return the variable name, or {@code null} if absent and {@code require} is {@code false}.
	 * @throws LineNumberParseException if the value is not a single variable reference.
	 */
	protected static String readVariableAttr(String name,
                                             Map<String, BodyToken> attrs, BodyToken cmdStartToken,
                                             boolean require) throws LineNumberParseException {
		VariableString varStr = readAttr(name, attrs, cmdStartToken,
				require);
		if (varStr == null)
			return null;
		BodyToken token = attrs.get(name);
		List<VariableString.Segment> segments = varStr.getSegments();
		if (segments.size() != 1 || !(segments.get(0) instanceof
				VariableString.VariableSegment)) {
			throw new LineNumberParseException(String.format(
					"Value for attribute \"%s\" is not a variable", name) +
					": " + token.getText(), token.getLineNumber(),
					token.getColNumber());
		}
		VariableString.VariableSegment segment =
				(VariableString.VariableSegment)segments.get(0);
		return segment.getVariableName();
	}
	
	/**
	 * Reads an attribute value as an {@link Integer}, with optional range validation.
	 *
	 * @param name the attribute name to look up.
	 * @param attrs the parsed attribute map produced by {@link #parseAttributesCommand}.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param require {@code true} if the attribute is mandatory.
	 * @param min inclusive lower bound, or {@code null} for no minimum.
	 * @param max inclusive upper bound, or {@code null} for no maximum.
	 * @return the integer value, or {@code null} if absent and {@code require} is {@code false}.
	 * @throws LineNumberParseException if the value is not a valid integer or is out of range.
	 */
	protected static Integer readIntAttr(String name,
                                         Map<String, BodyToken> attrs, BodyToken cmdStartToken,
                                         boolean require, Integer min, Integer max)
			throws LineNumberParseException {
		String s = readPlainTextAttr(name, attrs, cmdStartToken, require);
		if (s == null)
			return null;
		BodyToken token = attrs.get(name);
		int result;
		try {
			result = Integer.parseInt(s);
		} catch (NumberFormatException ex) {
			throw new LineNumberParseException(String.format(
					"Invalid value for attribute \"%s\"", name) + ": " + s,
					token.getLineNumber(), token.getColNumber());
		}
		if (min != null && result < min) {
			throw new LineNumberParseException(String.format(
					"Value for attribute \"%s\" < %s", name, min) + ": " +
					result, token.getLineNumber(), token.getColNumber());
		}
		if (max != null && result > max) {
			throw new LineNumberParseException(String.format(
					"Value for attribute \"%s\" > %s", name, max) + ": " +
					result, token.getLineNumber(), token.getColNumber());
		}
		return result;
	}

	/**
	 * Reads an attribute value as a {@link Float}, with optional range validation.
	 *
	 * @param name the attribute name to look up.
	 * @param attrs the parsed attribute map produced by {@link #parseAttributesCommand}.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param require {@code true} if the attribute is mandatory.
	 * @param min inclusive lower bound, or {@code null} for no minimum.
	 * @param max inclusive upper bound, or {@code null} for no maximum.
	 * @return the float value, or {@code null} if absent and {@code require} is {@code false}.
	 * @throws LineNumberParseException if the value is not a valid float or is out of range.
	 */
	protected static Float readFloatAttr(String name,
                                         Map<String, BodyToken> attrs, BodyToken cmdStartToken,
                                         boolean require, Float min, Float max)
			throws LineNumberParseException {
		String s = readPlainTextAttr(name, attrs, cmdStartToken, require);
		if (s == null)
			return null;
		BodyToken token = attrs.get(name);
		float result;
		try {
			result = Float.parseFloat(s);
		} catch (NumberFormatException ex) {
			throw new LineNumberParseException(String.format(
					"Invalid value for attribute \"%s\"", name) + ": " + s,
					token.getLineNumber(), token.getColNumber());
		}
		if (min != null && result < min) {
			throw new LineNumberParseException(String.format(
					"Value for attribute \"%s\" < %s", name, min) + ": " +
					result, token.getLineNumber(), token.getColNumber());
		}
		if (max != null && result > max) {
			throw new LineNumberParseException(String.format(
					"Value for attribute \"%s\" > %s", name, max) + ": " +
					result, token.getLineNumber(), token.getColNumber());
		}
		return result;
	}

	/**
	 * Reads an attribute value as a {@link Boolean} ({@code "true"} or {@code "false"}).
	 *
	 * @param name the attribute name to look up.
	 * @param attrs the parsed attribute map produced by {@link #parseAttributesCommand}.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param require {@code true} if the attribute is mandatory.
	 * @return the boolean value, or {@code null} if absent and {@code require} is {@code false}.
	 * @throws LineNumberParseException if the value is neither {@code "true"} nor {@code "false"}.
	 */
	protected static Boolean readBooleanAttr(String name,
                                             Map<String, BodyToken> attrs, BodyToken cmdStartToken,
                                             boolean require) throws LineNumberParseException {

		String s = readPlainTextAttr(name, attrs, cmdStartToken, require);
		if (s == null)
			return null;

		BodyToken token = attrs.get(name);

		if(s.toLowerCase().equals("true") || s.toLowerCase().equals("false")) {
			return Boolean.parseBoolean(s.toLowerCase());
		} else {
			throw new LineNumberParseException(String.format(
					"Invalid value for attribute \"%s\" (please use \"true\" or \"false\"", name) + ": " + s,
					token.getLineNumber(), token.getColNumber());
		}

	}
}

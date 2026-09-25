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
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract base for text-based input commands ({@link InputTextCommand},
 * {@link InputLongtextCommand}). Captures the common set of text input options such as length
 * constraints and capitalisation hints — see {@link InputVariableCommand} for the
 * variable-storage boilerplate shared more broadly across input command types.
 *
 * @author Harm op den Akker
 */
public abstract class InputAbstractTextCommand extends InputVariableCommand {
	private final @Nullable Integer min;
	private final @Nullable Integer max;
	private final Boolean allowNumbers;
	private final Boolean allowSpecialCharacters;
	private final Boolean allowSpaces;
	private final Boolean capCharacters;
	private final Boolean capWords;
	private final Boolean capSentences;
	private final Boolean forceCapCharacters;
	private final Boolean forceCapWords;
	private final Boolean forceCapSentences;

	/**
	 * Creates an {@link InputAbstractTextCommand} of the given {@code type}, populated from the
	 * given {@code builder}.
	 *
	 * @param type the input command type (one of the {@code TYPE_*} constants in {@link InputCommand}).
	 * @param builder the builder holding this command's configured fields.
	 */
	protected InputAbstractTextCommand(String type, Builder builder) {
		super(type, builder.variableName);
		this.min = builder.min;
		this.max = builder.max;
		this.allowNumbers = builder.allowNumbers;
		this.allowSpecialCharacters = builder.allowSpecialCharacters;
		this.allowSpaces = builder.allowSpaces;
		this.capCharacters = builder.capCharacters;
		this.capWords = builder.capWords;
		this.capSentences = builder.capSentences;
		this.forceCapCharacters = builder.forceCapCharacters;
		this.forceCapWords = builder.forceCapWords;
		this.forceCapSentences = builder.forceCapSentences;
	}

	/**
	 * Creates a deep copy of the given {@link InputAbstractTextCommand}.
	 *
	 * @param other the command to copy.
	 */
	public InputAbstractTextCommand(InputAbstractTextCommand other) {
		super(other);
		this.min = other.min;
		this.max = other.max;
		this.allowNumbers = other.allowNumbers;
		this.allowSpecialCharacters = other.allowSpecialCharacters;
		this.allowSpaces = other.allowSpaces;
		this.capCharacters = other.capCharacters;
		this.capWords = other.capWords;
		this.capSentences = other.capSentences;
		this.forceCapCharacters = other.forceCapCharacters;
		this.forceCapWords = other.forceCapWords;
		this.forceCapSentences = other.forceCapSentences;
	}

	/**
	 * Returns the minimum number of characters allowed for this text input command,
	 * or {@code null} if no minimum is set.
	 * @return the minimum number of characters allowed for this text input command.
	 */
	public @Nullable Integer getMin() {
		return min;
	}

	/**
	 * Returns the maximum number of characters allowed for this text input command,
	 * or {@code null} if no maximum is set.
	 * @return the maximum number of characters allowed for this text input command.
	 */
	public @Nullable Integer getMax() {
		return max;
	}

	/**
	 * Returns whether or not numbers are allowed in this text input command.
	 * @return whether or not numbers are allowed in this text input command.
	 */
	public Boolean getAllowNumbers() {
		return allowNumbers;
	}

	/**
	 * Returns whether or not special characters are allowed in this text input command.
	 * @return whether or not special characters are allowed in this text input command.
	 */
	public Boolean getAllowSpecialCharacters() {
		return allowSpecialCharacters;
	}

	/**
	 * Returns whether or not spaces are allowed in this text input command.
	 * @return whether or not spaces are allowed in this text input command.
	 */
	public Boolean getAllowSpaces() {
		return allowSpaces;
	}

	/**
	 * Returns whether or not to hint capitalization on character level.
	 * @return whether or not to hint capitalization on character level.
	 */
	public Boolean getCapCharacters() {
		return capCharacters;
	}

	/**
	 * Returns whether or not to hint capitalization on word level.
	 * @return whether or not to hint capitalization on word level.
	 */
	public Boolean getCapWords() {
		return capWords;
	}

	/**
	 * Returns whether or not to hint capitalization on sentence level.
	 * @return whether or not to hint capitalization on sentence level.
	 */
	public Boolean getCapSentences() {
		return capSentences;
	}

	/**
	 * Returns whether or not to force capitalization on character level.
	 * @return whether or not to force capitalization on character level.
	 */
	public Boolean getForceCapCharacters() {
		return forceCapCharacters;
	}

	/**
	 * Returns whether or not to force capitalization on word level.
	 * @return whether or not to force capitalization on word level.
	 */
	public Boolean getForceCapWords() {
		return forceCapWords;
	}

	/**
	 * Returns whether or not to force capitalization on sentence level.
	 * @return whether or not to force capitalization on sentence level.
	 */
	public Boolean getForceCapSentences() {
		return forceCapSentences;
	}

	@Override
	public Map<String, ?> getParameters() {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("variableName", getVariableName());
		if(min != null) result.put("min", min);
		if(max != null) result.put("max", max);
		result.put("allowNumbers",allowNumbers);
		result.put("allowSpecialCharacters",allowSpecialCharacters);
		result.put("allowSpaces",allowSpaces);
		result.put("capCharacters",capCharacters);
		result.put("capWords",capWords);
		result.put("capSentences",capSentences);
		result.put("forceCapCharacters",forceCapCharacters);
		result.put("forceCapWords",forceCapWords);
		result.put("forceCapSentences",forceCapSentences);
		return result;
	}

	@Override
	public String toString() {
		String result = toStringStart();
		result += " value=\"$" + getVariableName() + "\"";
		if (min != null)
			result += " min=\"" + min + "\"";

		if (max != null)
			result += " max=\"" + max + "\"";

		if (!allowNumbers) {
			result += " allowNumbers=\"false\"";
		}

		if (!allowSpecialCharacters) {
			result += " allowSpecialCharacters=\"false\"";
		}

		if (!allowSpaces) {
			result += " allowSpaces=\"false\"";
		}

		if (capCharacters) {
			result += " capCharacters=\"true\"";
		}

		if (capWords) {
			result += " capWords=\"true\"";
		}

		if (capSentences) {
			result += " capSentences=\"true\"";
		}

		if (forceCapCharacters) {
			result += " forceCapCharacters=\"true\"";
		}

		if (forceCapWords) {
			result += " forceCapWords=\"true\"";
		}

		if (forceCapSentences) {
			result += " forceCapSentences=\"true\"";
		}

		result += ">>";
		return result;
	}

	/**
	 * Parses all optional text-input attributes from the attribute token map into the given
	 * {@code builder}.
	 *
	 * @param builder the builder to configure.
	 * @param cmdStartToken the command-start token, used for error location.
	 * @param attrs the parsed attribute map.
	 * @throws LineNumberParseException if any attribute value is invalid.
	 */
	public static void parseAttributes(Builder builder,
									   BodyToken cmdStartToken, Map<String, BodyToken> attrs)
			throws LineNumberParseException {
		builder.setMin(readIntAttr("min", attrs, cmdStartToken, false, null, null));
		builder.setMax(readIntAttr("max", attrs, cmdStartToken, false, null, null));
		builder.setAllowNumbers(readBooleanAttr("allowNumbers",attrs,cmdStartToken,false));
		builder.setAllowSpecialCharacters(readBooleanAttr("allowSpecialCharacters",attrs,cmdStartToken,false));
		builder.setAllowSpaces(readBooleanAttr("allowSpaces",attrs,cmdStartToken,false));
		builder.setCapCharacters(readBooleanAttr("capCharacters",attrs,cmdStartToken,false));
		builder.setCapWords(readBooleanAttr("capWords",attrs,cmdStartToken,false));
		builder.setCapSentences(readBooleanAttr("capSentences",attrs,cmdStartToken,false));
		builder.setForceCapCharacters(readBooleanAttr("forceCapCharacters",attrs,cmdStartToken,false));
		builder.setForceCapWords(readBooleanAttr("forceCapWords",attrs,cmdStartToken,false));
		builder.setForceCapSentences(readBooleanAttr("forceCapSentences",attrs,cmdStartToken,false));
	}

	/**
	 * Accumulates an {@link InputAbstractTextCommand}'s optional fields before it is built.
	 * {@link InputTextCommand}/{@link InputLongtextCommand} each take a populated {@link Builder}
	 * in their own constructor, rather than {@link Builder} producing the command itself, since
	 * there's no single concrete return type this shared builder could build.
	 */
	public static class Builder {
		private final String variableName;
		private @Nullable Integer min = null;
		private @Nullable Integer max = null;
		private Boolean allowNumbers = Boolean.TRUE;
		private Boolean allowSpecialCharacters = Boolean.TRUE;
		private Boolean allowSpaces = Boolean.TRUE;
		private Boolean capCharacters = Boolean.FALSE;
		private Boolean capWords = Boolean.FALSE;
		private Boolean capSentences = Boolean.FALSE;
		private Boolean forceCapCharacters = Boolean.FALSE;
		private Boolean forceCapWords = Boolean.FALSE;
		private Boolean forceCapSentences = Boolean.FALSE;

		/**
		 * Creates a {@link Builder} that will configure a command storing its input in the
		 * dialogue variable named {@code variableName}.
		 *
		 * @param variableName the Dialogue Branch variable name in which to store the input.
		 */
		public Builder(String variableName) {
			this.variableName = variableName;
		}

		/**
		 * Sets the minimum number of characters needed for this text input command,
		 * or {@code null} if no minimum should be set.
		 * @param min the minimum number of characters needed for this text input command.
		 */
		public void setMin(@Nullable Integer min) {
			this.min = min;
		}

		/**
		 * Sets the maximum number of characters allowed for this text input command,
		 * or {@code null} if no maximum should be set.
		 * @param max the maximum number of characters allowed for this text input command.
		 */
		public void setMax(@Nullable Integer max) {
			this.max = max;
		}

		/**
		 * Sets whether or not numbers are allowed in this text input command. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.TRUE}.
		 * @param allowNumbers whether or not numbers are allowed in this text input command.
		 */
		public void setAllowNumbers(@Nullable Boolean allowNumbers) {
			this.allowNumbers = allowNumbers != null ? allowNumbers : Boolean.TRUE;
		}

		/**
		 * Sets whether or not special characters are allowed in this text input command.
		 * Special characters are defined as anything except letters [a-zA-Z], numbers
		 * [0-9] or the "space" character. If set to {@code null} the value reverts to its
		 * default value of {@code Boolean.TRUE}.
		 * @param allowSpecialCharacters whether or not special characters are allowed in this
		 * text input command.
		 */
		public void setAllowSpecialCharacters(@Nullable Boolean allowSpecialCharacters) {
			this.allowSpecialCharacters = allowSpecialCharacters != null
					? allowSpecialCharacters : Boolean.TRUE;
		}

		/**
		 * Sets whether or not spaces are allowed in this text input command. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.TRUE}.
		 * @param allowSpaces whether or not spaces are allowed in this text input command.
		 */
		public void setAllowSpaces(@Nullable Boolean allowSpaces) {
			this.allowSpaces = allowSpaces != null ? allowSpaces : Boolean.TRUE;
		}

		/**
		 * Sets whether or not to hint capitalization on character level. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.FALSE}.
		 * @param capCharacters whether or not to hint capitalization on character level.
		 */
		public void setCapCharacters(@Nullable Boolean capCharacters) {
			this.capCharacters = capCharacters != null ? capCharacters : Boolean.FALSE;
		}

		/**
		 * Sets whether or not to hint capitalization on word level. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.FALSE}.
		 * @param capWords whether or not to hint capitalization on word level.
		 */
		public void setCapWords(@Nullable Boolean capWords) {
			this.capWords = capWords != null ? capWords : Boolean.FALSE;
		}

		/**
		 * Sets whether or not to hint capitalization on sentence level. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.FALSE}.
		 * @param capSentences whether or not to hint capitalization on sentence level.
		 */
		public void setCapSentences(@Nullable Boolean capSentences) {
			this.capSentences = capSentences != null ? capSentences : Boolean.FALSE;
		}

		/**
		 * Sets whether or not to force capitalization on character level. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.FALSE}.
		 * @param forceCapCharacters whether or not to force capitalization on character level.
		 */
		public void setForceCapCharacters(@Nullable Boolean forceCapCharacters) {
			this.forceCapCharacters = forceCapCharacters != null
					? forceCapCharacters : Boolean.FALSE;
		}

		/**
		 * Sets whether or not to force capitalization on word level. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.FALSE}.
		 * @param forceCapWords whether or not to force capitalization on word level.
		 */
		public void setForceCapWords(@Nullable Boolean forceCapWords) {
			this.forceCapWords = forceCapWords != null ? forceCapWords : Boolean.FALSE;
		}

		/**
		 * Sets whether or not to force capitalization on sentence level. If set to
		 * {@code null} the value reverts to its default value of {@code Boolean.FALSE}.
		 * @param forceCapSentences whether or not to force capitalization on sentence level.
		 */
		public void setForceCapSentences(@Nullable Boolean forceCapSentences) {
			this.forceCapSentences = forceCapSentences != null
					? forceCapSentences : Boolean.FALSE;
		}
	}
}

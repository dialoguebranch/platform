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

package com.dialoguebranch.web.varservice.controller.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A {@link SupportedVariablePayload} represents one entry in the response of the
 * {@code /variables/supported} end-point: a Dialogue Branch Variable this External Variable
 * Service reports as supported for a given project, with the optional hints {@code type},
 * {@code values} and {@code readOnly} (#433). A hint left {@code null} is left out of the JSON.
 *
 * @author Harm op den Akker
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupportedVariablePayload {

	@Schema(description = "Name of the Dialogue Branch Variable",
			example = "dialogueBranchVariableName")
	private String name;

	@Schema(description = "A short, human-readable description of what the variable holds",
			example = "Today's date in the user's time zone")
	private String description;

	@Schema(description = "The variable's type: 'number', 'string' or 'boolean' (optional)",
			example = "string")
	private String type;

	@Schema(description = "The values the variable may take (optional)")
	private List<Object> values;

	@Schema(description = "Whether this service computes or controls the variable and doesn't " +
			"accept changes from a dialogue (optional)", example = "true")
	private Boolean readOnly;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link SupportedVariablePayload} (this constructor is used
	 * for serialization/deserialization purposes).
	 */
	public SupportedVariablePayload() { }

	/**
	 * Creates an instance of a {@link SupportedVariablePayload}.
	 *
	 * @param name the name of the Dialogue Branch Variable this service supports.
	 * @param description a short, human-readable description of what the variable holds.
	 */
	public SupportedVariablePayload(String name, String description) {
		this.name = name;
		this.description = description;
	}

	/**
	 * Creates an instance of a {@link SupportedVariablePayload} with hints.
	 *
	 * @param name the name of the Dialogue Branch Variable this service supports.
	 * @param description a short, human-readable description of what the variable holds.
	 * @param type {@code number}, {@code string} or {@code boolean}, or {@code null}.
	 * @param values the values the variable may take, or {@code null} if any value is allowed.
	 * @param readOnly whether this service doesn't accept changes from a dialogue, or
	 *                 {@code null}.
	 */
	public SupportedVariablePayload(String name, String description, String type,
			List<Object> values, Boolean readOnly) {
		this.name = name;
		this.description = description;
		this.type = type;
		this.values = values;
		this.readOnly = readOnly;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of this {@link SupportedVariablePayload}.
	 * @return the name of this {@link SupportedVariablePayload}.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this {@link SupportedVariablePayload}.
	 * @param name name of this {@link SupportedVariablePayload}.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the description of this {@link SupportedVariablePayload}.
	 * @return the description of this {@link SupportedVariablePayload}.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description of this {@link SupportedVariablePayload}.
	 * @param description the description of this {@link SupportedVariablePayload}.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the type hint of this {@link SupportedVariablePayload}.
	 * @return {@code number}, {@code string} or {@code boolean}, or {@code null}.
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the type hint of this {@link SupportedVariablePayload}.
	 * @param type {@code number}, {@code string} or {@code boolean}, or {@code null}.
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Returns the values the variable may take.
	 * @return the allowed values, or {@code null} if any value is allowed.
	 */
	public List<Object> getValues() {
		return values;
	}

	/**
	 * Sets the values the variable may take.
	 * @param values the allowed values, or {@code null} if any value is allowed.
	 */
	public void setValues(List<Object> values) {
		this.values = values;
	}

	/**
	 * Returns whether this service doesn't accept changes to the variable from a dialogue.
	 * @return {@code true} if read-only, or {@code null} if not said.
	 */
	public Boolean getReadOnly() {
		return readOnly;
	}

	/**
	 * Sets whether this service doesn't accept changes to the variable from a dialogue.
	 * @param readOnly {@code true} if read-only, or {@code null} if not said.
	 */
	public void setReadOnly(Boolean readOnly) {
		this.readOnly = readOnly;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	@Override
	public String toString() {
		return "SupportedVariablePayload{" +
				"name='" + name + "'" +
				", description='" + description + "'" +
				", type='" + type + "'" +
				", values=" + values +
				", readOnly=" + readOnly +
				'}';
	}

}

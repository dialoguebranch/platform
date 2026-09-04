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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@link SupportedVariablePayload} represents one entry in the response of the
 * {@code /variables/supported} end-point: a Dialogue Branch Variable this External Variable
 * Service reports as supported for a given project.
 *
 * @author Harm op den Akker
 */
public class SupportedVariablePayload {

	@Schema(description = "Name of the Dialogue Branch Variable",
			example = "dialogueBranchVariableName")
	private String name;

	@Schema(description = "A short, human-readable description of what the variable holds",
			example = "Today's date in the user's time zone")
	private String description;

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

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	@Override
	public String toString() {
		return "SupportedVariablePayload{" +
				"name='" + name + "'" +
				", description='" + description + "'" +
				'}';
	}

}

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
 * A {@link DLBVariableUpdatePayload} is one updated Dialogue Branch Variable in the response to
 * {@code retrieve-updates}: its name and new value only. The Dialogue Branch Web Service records
 * the moment it receives the update as the variable's updated time, so the response carries no
 * time of its own.
 *
 * @author Harm op den Akker
 */
public class DLBVariableUpdatePayload {

	@Schema(description = "Name of the Dialogue Branch Variable",
			example = "dialogueBranchVariableName")
	private String name;

	@Schema(description = "New value of the Dialogue Branch Variable, or null to remove it",
			example = "some value")
	private Object value;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link DLBVariableUpdatePayload} (this constructor is used
	 * for serialization/deserialization purposes).
	 */
	public DLBVariableUpdatePayload() { }

	/**
	 * Creates an instance of a {@link DLBVariableUpdatePayload}.
	 * @param name the name of the updated Dialogue Branch Variable.
	 * @param value the new value of the Dialogue Branch Variable, or {@code null} to remove it.
	 */
	public DLBVariableUpdatePayload(String name, Object value) {
		this.name = name;
		this.value = value;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of this {@link DLBVariableUpdatePayload}.
	 * @return the name of this {@link DLBVariableUpdatePayload}.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this {@link DLBVariableUpdatePayload}.
	 * @param name name of this {@link DLBVariableUpdatePayload}.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the new value of this {@link DLBVariableUpdatePayload}.
	 * @return the new value of this {@link DLBVariableUpdatePayload}, or {@code null} to remove
	 *         the variable.
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * Sets the new value of this {@link DLBVariableUpdatePayload}.
	 * @param value the new value, or {@code null} to remove the variable.
	 */
	public void setValue(Object value) {
		this.value = value;
	}

}

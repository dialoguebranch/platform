/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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
 * A {@link DLBVariablePayload} represents a DialogueBranch Variable that is returned as the result
 * of an api-call to the web service.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class DLBVariablePayload {

	@Schema(description = "Name of the DialogueBranch Variable",
			example = "dialogueBranchVariableName")
	private String name;

	@Schema(description = "Value of the DialogueBranch Variable",
			example = "some value")
	private Object value;

	@Schema(description = "UTC Timestamp in milliseconds representing the moment the variable " +
			"was last updated",
			example = "1665571549000")
	private Long updatedTime;

	@Schema(description = "The time zone in which the variable was last updated",
			example = "Europe/Lisbon")
	private String updatedTimeZone;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link DLBVariablePayload} (this constructor is used for
	 * serialization/deserialization purposes).
	 */
	public DLBVariablePayload() { }

	/**
	 * Creates an instance of a {@link DLBVariablePayload}
	 * @param name the name of the DialogueBranch Variable that is represented by this
	 * 			   {@link DLBVariablePayload}
	 * @param value the value of the DialogueBranch Variable as an {@link Object}
	 * @param updatedTime the UTC timestamp of when this DialogueBranch Variable was last updated
	 * @param updatedTimeZone the time zone (as IANA String, e.g. "Europe/Lisbon") in which this
	 *                        DialogueBranch variable was last updated.
	 */
	public DLBVariablePayload(String name, Object value,
							  Long updatedTime, String updatedTimeZone) {
		this.name = name;
		this.value = value;
		this.updatedTime = updatedTime;
		this.updatedTimeZone = updatedTimeZone;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of this {@link DLBVariablePayload}.
	 * @return the name of this {@link DLBVariablePayload}.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this {@link DLBVariablePayload}.
	 * @param name name of this {@link DLBVariablePayload}.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the value of this {@link DLBVariablePayload}.
	 * @return the value of this {@link DLBVariablePayload}.
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * Sets the value of this {@link DLBVariablePayload}.
	 * @param value the value of this {@link DLBVariablePayload}.
	 */
	public void setValue(Object value) {
		this.value = value;
	}

	/**
	 * Returns the UTC timestamp in milliseconds of when this DialogueBranch Variable was last
	 * updated.
	 * @return the UTC timestamp in milliseconds of when this DialogueBranch Variable was last
	 *         updated.
	 */
	public Long getUpdatedTime() {
		return updatedTime;
	}

	/**
	 * Sets the UTC timestamp in milliseconds of when this DialogueBranch Variable was last updated.
	 * @param updatedTime the UTC timestamp in milliseconds of when this DialogueBranch Variable was
	 *                    last updated.
	 */
	public void setUpdatedTime(Long updatedTime) {
		this.updatedTime = updatedTime;
	}

	/**
	 * Returns the timezone (as IANA String, e.g. "Europe/Lisbon") in which the DialogueBranch
	 * variable was last updated.
	 * @return the timezone (as IANA String, e.g. "Europe/Lisbon") in which the DialogueBranch
	 *         variable was last updated.
	 */
	public String getUpdatedTimeZone() {
		return updatedTimeZone;
	}

	/**
	 * Sets the timezone (as IANA String, e.g. "Europe/Lisbon") in which the DialogueBranch variable
	 * was last updated.
	 * @param updatedTimeZone the timezone (as IANA String, e.g. "Europe/Lisbon") in which the
	 *                        DialogueBranch variable was last updated.
	 */
	public void setUpdatedTimeZone(String updatedTimeZone) {
		this.updatedTimeZone = updatedTimeZone;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	@Override
	public String toString() {
		return "DLBVariablePayload{" +
				"name='" + name + "'" +
				", value='" + value + "'" +
				", updatedTime='" + updatedTime + "'" +
				", updatedTimeZone='" + updatedTimeZone + "'" +
				'}';
	}

}

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

package com.dialoguebranch.model.protocol;

import nl.rrd.utils.json.JsonObject;

/**
 * This class is used when a web service wants to return a value or {@code null}. This is to ensure
 * that the Spring Framework returns a valid JSON string. Normally if a Spring method returns
 * {@code null}, it results in an empty response, which may throw client applications off course.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 *
 * @param <T> the value type
 */
public class NullableResponse<T> extends JsonObject {
	private T value = null;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Constructs a new response with value {@code null}. This default constructor is needed for
	 * JSON serialization.
	 */
	public NullableResponse() { }
	
	/**
	 * Constructs a new response with the specified value.
	 * 
	 * @param value the value or {@code null}
	 */
	public NullableResponse(T value) {
		this.value = value;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the value. This can be {@code null}.
	 * 
	 * @return the value or {@code null}
	 */
	public T getValue() {
		return value;
	}

	/**
	 * Sets the value. This can be {@code null}.
	 * 
	 * @param value the value or {@code null}
	 */
	public void setValue(T value) {
		this.value = value;
	}
}

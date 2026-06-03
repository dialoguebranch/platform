/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.service.exception;

import java.util.ArrayList;
import java.util.List;

import nl.rrd.utils.json.JsonObject;

/**
 * An HTTP error can be included as a JSON string in the content of an HTTP error response. It has
 * an optional error code. The core error codes are defined as constants in {@link ErrorCode}.
 * 
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class HttpError extends JsonObject {

	private String code = null;
	private String message = "";
	private List<HttpFieldError> fieldErrors = new ArrayList<>();

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Constructs a new empty error.
	 */
	public HttpError() { }
	
	/**
	 * Constructs a new error without an error code, but with a given {@code message}..
	 * 
	 * @param message the error message describing the cause of this exception.
	 */
	public HttpError(String message) {
		this.message = message;
	}
	
	/**
	 * Creates an instance of a {@link HttpError} with a given error {@code code} and {@code
	 * message}.
	 * 
	 * @param code the error code or {@code null}.
	 * @param message the error message describing the cause of this exception.
	 */
	public HttpError(String code, String message) {
		this.code = code;
		this.message = message;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the error code.
	 * 
	 * @return the error code or {@code null}.
	 */
	public String getCode() {
		return code;
	}

	/**
	 * Sets the error code.
	 * 
	 * @param code the error code or null
	 */
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * Returns the error message.
	 * 
	 * @return the error message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the error message.
	 * 
	 * @param message the error message
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Returns errors for fields where invalid user input was provided.
	 * 
	 * @return the field errors
	 */
	public List<HttpFieldError> getFieldErrors() {
		return fieldErrors;
	}

	/**
	 * Sets errors for fields where invalid user input was provided.
	 * 
	 * @param fieldErrors the field errors
	 */
	public void setFieldErrors(List<HttpFieldError> fieldErrors) {
		this.fieldErrors = fieldErrors;
	}

	/**
	 * Adds an error for a field where invalid user input was provided.
	 * 
	 * @param error the field error
	 */
	public void addFieldError(HttpFieldError error) {
		fieldErrors.add(error);
	}

}

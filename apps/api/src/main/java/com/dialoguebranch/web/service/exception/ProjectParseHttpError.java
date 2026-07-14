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

package com.dialoguebranch.web.service.exception;

import java.util.List;
import java.util.Map;

/**
 * An {@link HttpError} for the case where a project's dialogue content currently fails to parse,
 * preventing some operation (e.g. test-running a draft dialogue) that needs the whole project to
 * parse successfully. Adds the underlying validation errors as a structured field, keyed per
 * dialogue, alongside the plain-text summary {@link #getMessage()} — mirroring the shape returned
 * by {@code PublishService.VerifyResult}, so clients can render a short message with an optional
 * expandable list of the specific errors, rather than one long concatenated string.
 *
 * @author Harm op den Akker
 */
public class ProjectParseHttpError extends HttpError {

	private Map<String, List<String>> errors;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Constructs a new empty error.
	 */
	public ProjectParseHttpError() { }

	/**
	 * Creates an instance of a {@link ProjectParseHttpError} with a given summary {@code message}
	 * and a map of {@code errors}, keyed by dialogue name.
	 *
	 * @param message the summary error message.
	 * @param errors  the underlying validation errors, keyed by dialogue name.
	 */
	public ProjectParseHttpError(String message, Map<String, List<String>> errors) {
		super(message);
		this.errors = errors;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the underlying validation errors that caused the project to fail to parse, keyed by
	 * dialogue name.
	 *
	 * @return the validation errors, keyed by dialogue name.
	 */
	public Map<String, List<String>> getErrors() {
		return errors;
	}

	/**
	 * Sets the underlying validation errors that caused the project to fail to parse, keyed by
	 * dialogue name.
	 *
	 * @param errors the validation errors, keyed by dialogue name.
	 */
	public void setErrors(Map<String, List<String>> errors) {
		this.errors = errors;
	}

}

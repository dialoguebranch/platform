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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * This exception results in a HTTP response with status 409 Conflict. The exception message
 * (default "Conflict") will be written to the response. It is handled by the
 * {@link com.dialoguebranch.web.service.controller.GlobalExceptionHandler}.
 *
 * <p>Use this for requests that are well-formed but conflict with the current state of a
 * resource — e.g. creating a project with a slug that is already in use.</p>
 *
 * @author Harm op den Akker
 */
@ResponseStatus(value=HttpStatus.CONFLICT)
public class ConflictException extends HttpException {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Creates an instance of a {@link ConflictException} with the simple message "Conflict".
	 */
	public ConflictException() {
		super("Conflict");
	}

	/**
	 * Creates an instance of a {@link ConflictException} with the given {@code message}.
	 *
	 * @param message the message describing the cause of the exception.
	 */
	public ConflictException(String message) {
		super(message);
	}

	/**
	 * Creates an instance of a {@link ConflictException} with the given {@code code} and {@code
	 * message}.
	 *
	 * @param code the error code for the exception.
	 * @param message the message describing the cause of the exception.
	 */
	public ConflictException(String code, String message) {
		super(code, message);
	}

	/**
	 * Creates an instance of a {@link ConflictException} as a wrapper around the given {@link
	 * HttpError}.
	 *
	 * @param error the {@link HttpError}.
	 */
	public ConflictException(HttpError error) {
		super(error);
	}

}

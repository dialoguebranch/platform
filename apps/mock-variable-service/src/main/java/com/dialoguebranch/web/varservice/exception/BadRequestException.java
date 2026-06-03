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

package com.dialoguebranch.web.varservice.exception;

import com.dialoguebranch.web.varservice.controller.ErrorController;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This exception results in an HTTP response with status 400 Bad Request. The exception message
 * (default "Bad Request") will be written to the response. It is handled by the {@link
 * ErrorController}.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
@ResponseStatus(value=HttpStatus.BAD_REQUEST)
public class BadRequestException extends HttpException {
	@Serial
	private static final long serialVersionUID = 1L;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link BadRequestException} with the simple message "Bad Request".
	 */
	public BadRequestException() {
		super("Bad Request");
	}

	/**
	 * Creates an instance of a {@link BadRequestException} with the given {@code message}.
	 *
	 * @param message the message of the exception.
	 */
	public BadRequestException(String message) {
		super(message);
	}

	/**
	 * Creates an instance of a {@link BadRequestException} with the given {@code code} and {@code
	 * message}.
	 *
	 * @param code the error code.
	 * @param message the message of the exception.
	 */
	public BadRequestException(String code, String message) {
		super(code, message);
	}

	/**
	 * Creates an instance of a {@link BadRequestException} as a wrapper around the given {@link
	 * HttpError}.
	 *
	 * @param error the {@link HttpError}.
	 */
	public BadRequestException(HttpError error) {
		super(error);
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Generate a {@link BadRequestException} by adding the {@link HttpFieldError}s of the given
	 * {@code other} {@link BadRequestException} to the existing list of field errors in this {@link
	 * BadRequestException}. The resulting {@link BadRequestException} will be a wrapper around a
	 * {@link HttpError} with error code {@link ErrorCode#INVALID_INPUT}, and an error message that
	 * is a JSON representation of the provided list of {@link HttpFieldError}s.
	 *
	 * @param other the list of {@link HttpFieldError}s to add to the existing list in this {@link
	 *              BadRequestException}.
	 * @return a new {@link BadRequestException} object.
	 */
	public BadRequestException appendInvalidInput(BadRequestException other) {
		List<HttpFieldError> errors = new ArrayList<>();
		errors.addAll(getError().getFieldErrors());
		errors.addAll(other.getError().getFieldErrors());
		return withInvalidInput(errors);
	}

	/**
	 * Generate a {@link BadRequestException} by adding the given array of {@link HttpFieldError}s
	 * to the existing list of field errors in this {@link BadRequestException}. The resulting
	 * {@link BadRequestException} will be a wrapper around a {@link HttpError} with error code
	 * {@link ErrorCode#INVALID_INPUT}, and an error message that is a JSON representation of the
	 * provided list of {@link HttpFieldError}s.
	 *
	 * @param fieldErrors the list of {@link HttpFieldError}s to add to the existing list in this
	 *                   {@link BadRequestException}.
	 * @return a new {@link BadRequestException} object.
	 */
	public BadRequestException appendInvalidInput(HttpFieldError... fieldErrors) {
		return appendInvalidInput(Arrays.asList(fieldErrors));
	}

	/**
	 * Generate a {@link BadRequestException} by adding the given {@link List} of {@link
	 * HttpFieldError}s to the existing list of field errors in this {@link BadRequestException}.
	 * The resulting {@link BadRequestException} will be a wrapper around a {@link HttpError} with
	 * error code {@link ErrorCode#INVALID_INPUT}, and an error message that is a JSON
	 * representation of the provided list of {@link HttpFieldError}s.
	 *
	 * @param fieldErrors the list of {@link HttpFieldError}s to add to the existing list in this
	 *                   {@link BadRequestException}.
	 * @return a new {@link BadRequestException} object.
	 */
	public BadRequestException appendInvalidInput(List<HttpFieldError> fieldErrors) {
		List<HttpFieldError> newErrors = new ArrayList<>();
		newErrors.addAll(getError().getFieldErrors());
		newErrors.addAll(fieldErrors);
		return withInvalidInput(newErrors);
	}

	/**
	 * Generate a {@link BadRequestException} from an array of {@link HttpFieldError}s. The
	 * resulting {@link BadRequestException} will be a wrapper around a {@link HttpError} with error
	 * code {@link ErrorCode#INVALID_INPUT}, and an error message that is a JSON representation of
	 * the provided list of {@link HttpFieldError}s.
	 *
	 * @param fieldErrors the list of {@link HttpFieldError}s that make up the {@link
	 *        BadRequestException}
	 * @return a new {@link BadRequestException} object.
	 */
	public static BadRequestException withInvalidInput(HttpFieldError... fieldErrors) {
		return withInvalidInput(Arrays.asList(fieldErrors));
	}

	/**
	 * Generate a {@link BadRequestException} from a {@link List} of {@link HttpFieldError}s. The
	 * resulting {@link BadRequestException} will be a wrapper around a {@link HttpError} with error
	 * code {@link ErrorCode#INVALID_INPUT}, and an error message that is a JSON representation of
	 * the provided list of {@link HttpFieldError}s.
	 *
	 * @param fieldErrors the list of {@link HttpFieldError}s that make up the {@link
	 *        BadRequestException}
	 * @return a new {@link BadRequestException} object.
	 */
	public static BadRequestException withInvalidInput(List<HttpFieldError> fieldErrors) {
		StringBuilder errorMsg = new StringBuilder();

		ObjectMapper mapper = new ObjectMapper();
		try {
			errorMsg.append(mapper.writeValueAsString(fieldErrors));
		} catch (JsonProcessingException e) {
			errorMsg.append("[]"); // Set the errorMsg to be an empty JSON list
		}

		HttpError error = new HttpError(ErrorCode.INVALID_INPUT, errorMsg.toString());
		for (HttpFieldError fieldError : fieldErrors) {
			error.addFieldError(fieldError);
		}
		return new BadRequestException(error);
	}

	/**
	 * Generate a {@link BadRequestException} with a specific error message and a {@link List} of
	 * {@link HttpFieldError}s. The resulting {@link BadRequestException} will be a wrapper around a
	 * {@link HttpError} with error code {@link ErrorCode#INVALID_INPUT}, and the given error
	 * message.
	 * @param errorMessage The (human-readable) error message.
	 * @param fieldErrors the list of {@link HttpFieldError}s that make up the {@link
	 *                    BadRequestException}
	 * @return a new {@link BadRequestException} object.
	 */
	public static BadRequestException withMessageAndInvalidInput(String errorMessage,
																 List<HttpFieldError> fieldErrors) {
		HttpError error = new HttpError(ErrorCode.INVALID_INPUT, errorMessage);
		for (HttpFieldError fieldError : fieldErrors) {
			error.addFieldError(fieldError);
		}
		return new BadRequestException(error);
	}

}

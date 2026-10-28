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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.HttpError;
import com.dialoguebranch.web.service.exception.HttpException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler that translates exceptions thrown by any controller into structured HTTP
 * responses.
 *
 * @author Dennis Hofs
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Creates a new {@link GlobalExceptionHandler}. Instantiated by Spring. */
	public GlobalExceptionHandler() { }

	/**
	 * Handles all {@link HttpException} subclasses (BadRequestException, UnauthorizedException,
	 * etc.). The HTTP status is read from the {@link ResponseStatus} annotation present on each
	 * subclass.
	 *
	 * @param ex the {@link HttpException} thrown by the controller.
	 * @return a response with the status from {@code ex}'s {@link ResponseStatus} annotation and
	 *         {@code ex}'s {@link HttpError} as the body.
	 */
	@ExceptionHandler(HttpException.class)
	public ResponseEntity<HttpError> handleHttpException(HttpException ex) {
		ResponseStatus annotation = ex.getClass().getAnnotation(ResponseStatus.class);
		HttpStatus status = annotation != null ? annotation.value() : HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status).body(ex.getError());
	}

	/**
	 * Handles malformed JSON request bodies. Returns 400 Bad Request with the parse error message.
	 *
	 * @param ex the exception thrown while reading the request body.
	 * @return a 400 Bad Request response with the parse error message, or a generic message if the
	 *         cause isn't a JSON parse error.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<String> handleNotReadable(HttpMessageNotReadableException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof JsonProcessingException) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid request body");
	}

	/**
	 * Handles requests for static resources or paths that don't exist. Returns 404 without logging.
	 *
	 * @param ex the exception thrown for the unresolved resource or path.
	 * @return an empty 404 Not Found response.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
		return ResponseEntity.notFound().build();
	}

	/**
	 * Handles a multipart request that lacks a required part, for example an import without its
	 * {@code files}. That usually means the upload's body was lost on the way, for instance by a
	 * proxy that doesn't pass it on unchanged, so the message says so.
	 *
	 * @param ex the exception thrown for the missing part.
	 * @return a 400 Bad Request response with the reason.
	 */
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<HttpError> handleMissingPart(MissingServletRequestPartException ex) {
		logger.warn("Request refused (400): {}", ex.getMessage());
		return clientError(HttpStatus.BAD_REQUEST, "The request has no '" + ex.getRequestPartName() +
				"' part. If it goes through a proxy, check that the proxy passes the upload on " +
				"unchanged.");
	}

	/**
	 * Handles an upload that is larger than the configured limit.
	 *
	 * @param ex the exception thrown for the oversized upload.
	 * @return a 413 Payload Too Large response with the limit, when it is known.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<HttpError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
		logger.warn("Request refused (413): {}", ex.getMessage());
		String limit = ex.getMaxUploadSize() >= 0 ? " (at most " + ex.getMaxUploadSize() + " bytes)" : "";
		return clientError(HttpStatus.PAYLOAD_TOO_LARGE,
				"The upload is larger than the service accepts" + limit + ".");
	}

	/**
	 * Handles a multipart request that can't be read, for example a body that doesn't match its
	 * boundary.
	 *
	 * @param ex the exception thrown while reading the multipart request.
	 * @return a 400 Bad Request response with the reason.
	 */
	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<HttpError> handleMultipart(MultipartException ex) {
		logger.warn("Request refused (400): {}", ex.getMessage());
		return clientError(HttpStatus.BAD_REQUEST,
				"The upload could not be read: " + ex.getMessage());
	}

	/**
	 * Handles a request parameter that doesn't have the expected type, for example {@code
	 * replace=maybe} where a boolean is expected.
	 *
	 * @param ex the exception thrown for the parameter that could not be converted.
	 * @return a 400 Bad Request response naming the parameter.
	 */
	@ExceptionHandler(TypeMismatchException.class)
	public ResponseEntity<HttpError> handleTypeMismatch(TypeMismatchException ex) {
		logger.warn("Request refused (400): {}", ex.getMessage());
		String name = ex.getPropertyName() != null ? ex.getPropertyName() : "a parameter";
		return clientError(HttpStatus.BAD_REQUEST,
				"Invalid value for '" + name + "': '" + ex.getValue() + "'.");
	}

	/**
	 * Catch-all for any unhandled exception. A client error that Spring itself raises (a missing
	 * request parameter, an unsupported content type or method, ...) keeps its own 4xx status and
	 * reason, since it says what the caller did wrong and has nothing to do with the server. Anything
	 * else is a real server error: it logs the full stack trace and returns 500 Internal Server Error
	 * without exposing internal details to the client.
	 *
	 * @param ex the unhandled exception.
	 * @return the 4xx response with the reason for a client error, or a 500 Internal Server Error
	 *         response with a generic message.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleGeneral(Exception ex) {
		if (ex instanceof ErrorResponse errorResponse
				&& errorResponse.getStatusCode().is4xxClientError()) {
			logger.warn("Request refused ({}): {}", errorResponse.getStatusCode().value(),
					ex.getMessage());
			String detail = errorResponse.getBody().getDetail();
			HttpStatusCode status = errorResponse.getStatusCode();
			HttpStatus known = HttpStatus.resolve(status.value());
			return ResponseEntity.status(status).headers(errorResponse.getHeaders())
					.body(clientErrorBody(status, detail != null ? detail
							: known != null ? known.getReasonPhrase() : "Request refused"));
		}
		logger.error("Internal Server Error: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
	}

	private static ResponseEntity<HttpError> clientError(HttpStatusCode status, String message) {
		return ResponseEntity.status(status).body(clientErrorBody(status, message));
	}

	/** A 400 carries the {@link ErrorCode#INVALID_INPUT} code, like the service's own 400s. */
	private static HttpError clientErrorBody(HttpStatusCode status, String message) {
		return status.value() == HttpStatus.BAD_REQUEST.value()
				? new HttpError(ErrorCode.INVALID_INPUT, message) : new HttpError(message);
	}

}

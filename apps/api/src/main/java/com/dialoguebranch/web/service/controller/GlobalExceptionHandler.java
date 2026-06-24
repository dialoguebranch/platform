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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.exception.HttpError;
import com.dialoguebranch.web.service.exception.HttpException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that translates exceptions thrown by any controller into structured HTTP
 * responses. Replaces the servlet error redirect pattern previously handled by
 * {@link com.dialoguebranch.web.service.controller.ErrorController}.
 *
 * @author Dennis Hofs
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles all {@link HttpException} subclasses (BadRequestException, UnauthorizedException,
     * etc.). The HTTP status is read from the {@link ResponseStatus} annotation present on each
     * subclass.
     */
    @ExceptionHandler(HttpException.class)
    public ResponseEntity<HttpError> handleHttpException(HttpException ex) {
        ResponseStatus annotation = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = annotation != null ? annotation.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(ex.getError());
    }

    /**
     * Handles malformed JSON request bodies. Returns 400 Bad Request with the parse error message.
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
     * Catch-all for any unhandled exception. Logs the full stack trace and returns 500 Internal
     * Server Error without exposing internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneral(Exception ex) {
        logger.error("Internal Server Error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
    }

}

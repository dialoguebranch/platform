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

import com.dialoguebranch.web.service.exception.HttpError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link GlobalExceptionHandler} on exceptions that are hard to raise through MockMvc. */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void anOversizedUploadIsPayloadTooLargeAndNamesTheLimit() {
		ResponseEntity<HttpError> response =
				handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1024));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
		assertTrue(response.getBody().getMessage().contains("1024 bytes"));
	}

	@Test
	void anOversizedUploadWithAnUnknownLimitStillSaysSo() {
		ResponseEntity<HttpError> response =
				handler.handleUploadTooLarge(new MaxUploadSizeExceededException(-1));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
		assertTrue(response.getBody().getMessage().contains("larger than the service accepts"));
	}

	@Test
	void anUnreadableUploadIsABadRequestWithTheReason() {
		ResponseEntity<HttpError> response =
				handler.handleMultipart(new MultipartException("boundary not found"));
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("INVALID_INPUT", response.getBody().getCode());
		assertTrue(response.getBody().getMessage().contains("boundary not found"));
	}

	@Test
	void anUnexpectedExceptionIsStillAGenericServerError() {
		ResponseEntity<?> response =
				handler.handleGeneral(new IllegalStateException("database exploded: secret detail"));
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("Internal Server Error", assertInstanceOf(String.class, response.getBody()));
	}
}

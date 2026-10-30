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
package com.dialoguebranch.web.service.controller.schema;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The External Variable Service part of {@code /info/technical} (#430), for admins: where it is
 * and how this service's own calls to it went since the Web Service started. Times are in epoch
 * milliseconds.
 *
 * @param enabled        whether an External Variable Service is configured.
 * @param url            its configured URL, or {@code null} if none is configured.
 * @param lastSuccess    when a call last succeeded, or {@code null} if none has.
 * @param lastFailure    when a call last failed, or {@code null} if none has.
 * @param lastError      why the last failed call failed, or {@code null} if none has.
 * @param failuresInARow how many calls failed since the last one that succeeded.
 */
public record ExternalVariableServiceInfo(
		@Schema(description = "Whether an External Variable Service is configured")
		boolean enabled,
		@Schema(description = "Its configured URL", example = "http://mock-variable-service:8090")
		@Nullable String url,
		@Schema(description = "When a call last succeeded, in epoch milliseconds")
		@Nullable Long lastSuccess,
		@Schema(description = "When a call last failed, in epoch milliseconds")
		@Nullable Long lastFailure,
		@Schema(description = "Why the last failed call failed")
		@Nullable String lastError,
		@Schema(description = "How many calls failed since the last one that succeeded")
		int failuresInARow) {
}

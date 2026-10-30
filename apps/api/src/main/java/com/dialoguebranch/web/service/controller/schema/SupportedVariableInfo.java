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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One entry in the {@code /variables/list-supported} response, and in the wire format of the
 * External Variable Service's own {@code /variables/supported} end-point: a variable a configured
 * External Variable Service owns for a given project. Extra fields a service sends are ignored
 * rather than rejected, since the contract may grow.
 *
 * <p>{@code type}, {@code values} and {@code readOnly} are optional hints for authors (#433):
 * Studio shows them and warns about them, but neither the core library nor the Web Service
 * enforces them. They are passed through as the service sent them; a hint the service leaves out
 * stays absent, never filled in with a default.</p>
 *
 * @param name        the variable name (without the {@code $} sigil).
 * @param description a short, human-readable description of what the variable holds, or
 *                    {@code null} if the service did not provide one.
 * @param type        {@code number}, {@code string} or {@code boolean}, or {@code null} if the
 *                    service did not say.
 * @param values      the values the variable may take, or {@code null} if any value is allowed.
 * @param readOnly    {@code true} if the service computes or controls the variable and doesn't
 *                    accept changes from a dialogue, or {@code null} if the service did not say.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportedVariableInfo(String name, String description, String type,
		List<Object> values, Boolean readOnly) {
}

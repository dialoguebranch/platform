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

package com.dialoguebranch.web.service.controller.schema;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@code TechnicalInfoPayload} object consolidates technical, operational metadata about this
 * Dialogue Branch Web Service instance, intended for {@code admin}-only diagnostics.
 *
 * @author Harm op den Akker
 */
public class TechnicalInfoPayload {

	@Schema(description = "The number of currently active (in-memory) UserService instances",
			example = "3")
	private int activeUserServiceCount;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link TechnicalInfoPayload}.
	 */
	public TechnicalInfoPayload() { }

	/**
	 * Creates an instance of a {@link TechnicalInfoPayload} with the given {@code
	 * activeUserServiceCount}.
	 *
	 * @param activeUserServiceCount the number of currently active (in-memory) UserService
	 *                               instances.
	 */
	public TechnicalInfoPayload(int activeUserServiceCount) {
		this.activeUserServiceCount = activeUserServiceCount;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the number of currently active (in-memory) UserService instances.
	 * @return the number of currently active (in-memory) UserService instances.
	 */
	public int getActiveUserServiceCount() {
		return activeUserServiceCount;
	}

	/**
	 * Sets the number of currently active (in-memory) UserService instances.
	 * @param activeUserServiceCount the number of currently active (in-memory) UserService
	 *                               instances.
	 */
	public void setActiveUserServiceCount(int activeUserServiceCount) {
		this.activeUserServiceCount = activeUserServiceCount;
	}

}

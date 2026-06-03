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

/**
 * Possible error codes that may be returned by the Dialogue Branch External Variable Service Dummy.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class ErrorCode {

	/**
	 * This class is used in a static context.
	 */
	public ErrorCode() { }

	/** In case no Access Token was provided in the header of a request (Authorization:
	 * Bearer &lt;token&gt; ). */
	public static final String ACCESS_TOKEN_NOT_FOUND = "ACCESS_TOKEN_NOT_FOUND";

	/** In case the provided Access Token was not valid. */
	public static final String ACCESS_TOKEN_INVALID = "ACCESS_TOKEN_INVALID";

	/** In case a required input parameter is missing, or an invalid value was provided. */
	public static final String INVALID_INPUT = "INVALID_INPUT";

}

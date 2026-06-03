/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.service.exception;

/**
 * Possible error codes that may be returned by the Dialogue Branch Web Service.
 * 
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ErrorCode {

	/** In case no authentication token was provided in the header of a request. */
	public static final String AUTH_TOKEN_NOT_FOUND = "AUTH_TOKEN_NOT_FOUND";

	/** In case the provided authentication token was not valid. */
	public static final String AUTH_TOKEN_INVALID = "AUTH_TOKEN_INVALID";

	/** In case the provided authentication token has expired. */
	public static final String AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";

	/** In case a valid refresh token is provided for an unknown user (weird...) */
	public static final String UNKNOWN_USER = "UNKNOWN_USER";

	/** In case a required input parameter is missing, or an invalid value was provided. */
	public static final String INVALID_INPUT = "INVALID_INPUT";

	/** In case a wrong username/password combination is provided upon login. */
	public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

	/** In case the currently logged-in user is not allowed to perform the operation. */
	public static final String INSUFFICIENT_PRIVILEGES = "INSUFFICIENT_PRIVILEGES";

	/** In case an error occurred related to the Keycloak service. */
	public static final String KEYCLOAK_ERROR = "KEYCLOAK_ERROR";

}

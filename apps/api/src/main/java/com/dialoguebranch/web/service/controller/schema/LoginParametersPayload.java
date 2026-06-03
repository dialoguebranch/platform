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

package com.dialoguebranch.web.service.controller.schema;

import com.dialoguebranch.web.service.controller.AuthController;
import nl.rrd.utils.json.JsonObject;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@link LoginParametersPayload} object models the information that is sent in the request body
 * of a call to the /auth/login end-point as handled by the {@link AuthController}, which can be
 * serialized / deserialized to the following JSON Format:
 *
 * <pre>
 * {
 *   "user": "string",
 *   "password": "string",
 * }</pre>
 *
 * @author Harm op den Akker
 */
public class LoginParametersPayload extends JsonObject {

	@Schema(description = "Username of the person or entity logging in",
			example = "user")
	private String user = null;

	@Schema(description = "Password for the given user",
			example = "password")
	private String password = null;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of an empty {@link LoginParametersPayload}.
	 */
	public LoginParametersPayload() { }

	/**
	 * Creates an instance of a {@link LoginParametersPayload} with the given {@code user}, and
	 * {@code password}.
	 *
	 * @param user the user who is trying to perform a login.
	 * @param password the password provided by the user performing a login.
	 */
	public LoginParametersPayload(String user, String password) {
		this.user = user;
		this.password = password;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the user who is trying to perform a login.
	 *
	 * @return the user who is trying to perform a login.
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Sets the user who is trying to perform a login.
	 *
	 * @param user the user who is trying to perform a login.
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Returns the password provided by the user performing a login.
	 *
	 * @return the password provided by the user performing a login.
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Sets the password provided by the user performing a login.
	 *
	 * @param password the password provided by the user performing a login.
	 */
	public void setPassword(String password) {
		this.password = password;
	}

}

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
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@link LoginResultPayload} is returned by the /auth/login end-point as handled by the {@link
 * AuthController} in case of a successful login and contains the username, the user's roles, a
 * JSON Web Token (JWT) access token as well as an associated refresh_token.
 *
 * <p>The contents can be serialized / deserialized to the following JSON Format:</p>
 * <pre>
 * {
 *   "user": "john",
 *   "role": "admin",
 *   "token": "See <a href="https://jwt.io/">jwt.io</a>"
 * }
 * </pre>
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class LoginResultPayload {

	@Schema(description = "Username associated with this authentication token",
			example = "john")
	private String user;

	@Schema(description = "The roles of the authenticated user",
			example = "editor,admin,client")
	private String roles;

	@Schema(description = "The JSON Web Token that was generated for the user",
			example = "See https://jwt.io/")
	private String access_token;

	@Schema(description = "The time (in seconds) in which the access token expires",
			example = "300")
	private int expires_in;

	@Schema(description = "The refresh token that may be used to refresh the access token",
			example = "See https://jwt.io/")
	private String refresh_token;

	@Schema(description = "The time (in seconds) in which the refresh token expires",
			example = "1800")
	private int refresh_expires_in;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an empty instance of a {@link LoginResultPayload}.
	 */
	public LoginResultPayload() { }

	/**
	 * Creates an instance of a {@link LoginResultPayload} with a given {@code user}, {@code roles}-
	 * string, and {@code access_token}.
	 *
	 * <p>NOTE: This is used for the legacy built-in authentication system, that doesn't use
	 * refresh tokens.</p>
	 *
	 * @param user the username of the user that performed a successful login.
	 * @param roles the roles of the user associated with this {@link LoginResultPayload} as a
	 *              string with comma-separated role values.
	 * @param access_token the JSON Web Token (JWT) access token generated for the user as a {@link
	 * 					   String}.
	 */
	public LoginResultPayload(String user, String roles, String access_token) {
		this.user = user;
		this.roles = roles;
		this.access_token = access_token;
	}

	/**
	 * Creates an instance of a {@link LoginResultPayload} with a given {@code user}, {@code roles}-
	 * string, {@code access_token}, time for the access token to expire ({@code expires_in}),
	 * refresh token ({@code refresh_token}) and time for the refresh token to expire ({@code
	 * refresh_expires_in}).
	 *
	 * @param user the username of the user that performed a successful login.
	 * @param roles the roles of the user associated with this {@link LoginResultPayload} as a
	 *              string with comma-separated role values.
	 * @param access_token the JSON Web Token (JWT) access token generated for the user as a {@link
	 * 					   String}.
	 * @param expires_in the time (in seconds) it takes for the access_token to expire.
	 * @param refresh_token the JWT refresh token that may be used to refresh the access token.
	 * @param refresh_expires_in the time (in seconds) it takes for the refresh token to expire.
	 */
	public LoginResultPayload(String user, String roles, String access_token, int expires_in,
							  String refresh_token, int refresh_expires_in) {
		this.user = user;
		this.roles = roles;
		this.access_token = access_token;
		this.expires_in = expires_in;
		this.refresh_token = refresh_token;
		this.refresh_expires_in = refresh_expires_in;
	}

	// -------------------------------------------------------------
	// -------------------- Getters and Setters --------------------
	// -------------------------------------------------------------

	/**
	 * Returns the username of the user that performed a successful login.
	 *
	 * @return the username of the user that performed a successful login.
	 */
	public String getUser() {
		return this.user;
	}

	/**
	 * Sets the username of the user that performed a successful login.
	 *
	 * @param user the username of the user that performed a successful login.
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Returns the roles of the user associated with this {@link LoginResultPayload}.
	 *
	 * @return the roles of the user associated with this {@link LoginResultPayload}.
	 */
	public String getRoles() {
		return this.roles;
	}

	/**
	 * Sets the roles of the user associated with this {@link LoginResultPayload} as a comma
	 * separated String.
	 *
	 * @param roles the role of the user associated with this {@link LoginResultPayload}.
	 */
	public void setRoles(String roles) {
		this.roles = roles;
	}

	/**
	 * Returns the JSON Web Token generated for the user as a {@link String}.
	 *
	 * @return the JSON Web Token generated for the user as a {@link String}.
	 */
	public String getAccessToken() {
		return access_token;
	}

	/**
	 * Sets the JSON Web Token generated for the user as a {@link String}.
	 *
	 * @param access_token the JSON Web Token generated for the user as a {@link String}.
	 */
	public void setAccessToken(String access_token) {
		this.access_token = access_token;
	}

	public int getExpiresIn() {
		return this.expires_in;
	}

	public void setExpiresIn(int expires_in) {
		this.expires_in = expires_in;
	}

	public String getRefreshToken() {
		return this.refresh_token;
	}

	public void setRefreshToken(String refresh_token) {
		this.refresh_token = refresh_token;
	}

	public int getRefreshExpiresIn() {
		return this.refresh_expires_in;
	}

	public void setRefreshExpiresIn(int refresh_expires_in) {
		this.refresh_expires_in = refresh_expires_in;
	}

}

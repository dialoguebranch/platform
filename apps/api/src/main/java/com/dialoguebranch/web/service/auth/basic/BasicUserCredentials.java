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

package com.dialoguebranch.web.service.auth.basic;

/**
 * Object to store the user credentials and role information of a user when using the native
 * (built-in) Authentication service.
 *
 * @author Harm op den Akker
 */
public class BasicUserCredentials {

	/** The name of the role defined as "client" */
	public static final String USER_ROLE_CLIENT = "client";

	/** The name of the role defined as "editor" */
	public static final String USER_ROLE_EDITOR = "editor";

	/** The name of the role defined as "admin" */
	public static final String USER_ROLE_ADMIN = "admin";

	/** The username of the user represented by this BasicUserCredentials object */
	private final String username;

	/** The password of the user represented by this BasicUserCredentials object */
	private String password = null;

	/** The list of roles of the user represented by this BasicUserCredentials object */
	private final String[] roles;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link BasicUserCredentials} object with given {@code username},
	 * {@code password}, and {@code roles}.
	 *
	 * @param username the username of this user.
	 * @param password the corresponding password of this user.
	 * @param roles the list of roles corresponding to this user.
	 */
	public BasicUserCredentials(String username, String password, String[] roles) {
		this.username = username;
		this.password = password;
		this.roles = roles;
	}

	/**
	 * Creates an instance of a {@link BasicUserCredentials} object without providing the user's
	 * password (e.g. only setting {@code username}, and {@code roles}.
	 *
	 * @param username the username of this user.
	 * @param roles the list of roles corresponding to this user.
	 */
	public BasicUserCredentials(String username, String[] roles) {
		this.username = username;
		this.roles = roles;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the username of this {@link BasicUserCredentials} object.
	 *
	 * @return the username of this {@link BasicUserCredentials} object.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Returns the password of this {@link BasicUserCredentials} object.
	 *
	 * @return the password of this {@link BasicUserCredentials} object.
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Returns the list of roles of this {@link BasicUserCredentials} object.
	 *
	 * @return the list of roles of this {@link BasicUserCredentials} object.
	 */
	public String[] getRoles() {
		return roles;
	}

	/**
	 * Returns the roles associated with this user as a comma-separated String (e.g.
	 * "client,editor,admin").
	 *
	 * @return the roles associated with this user as a comma-separated String.
	 */
	public String getCommaSeparatedRolesString() {
		StringBuilder result = new StringBuilder();

		for(int i=0; i<roles.length; i++) {
			result.append(roles[i]);
			if(i+1 < roles.length) result.append(",");
		}

		return result.toString();
	}

}

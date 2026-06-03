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

package com.dialoguebranch.web.service.auth;

import java.util.Date;

/**
 * The authentication details that are included in a JWT token. It contains the
 * username of the authenticated user, the date/time when the JWT token was
 * issued, and the date/time when the JWT token expires.
 * 
 * @author Dennis Hofs (RRD)
 */
public class AuthenticationInfo {

	private final String username;
	private final String[] roles;
	private final Date issuedAt;
	private final Date expiration;

	/**
	 * Constructs a new instance of an {@link AuthenticationInfo} object, containing the information
	 * resulting from a successful authentication of a user to the Dialogue Branch Web Service.
	 * 
	 * @param username the username of the authenticated user
	 * @param roles a list of roles associated with this user
	 * @param issuedAt the date/time when the JWT token was issued, with precision of seconds. Any
	 *                 milliseconds are discarded.
	 * @param expiration the date/time when the JWT token expires, with precision of seconds. Any
	 *                   milliseconds are discarded.
	 */
	public AuthenticationInfo(String username, String[] roles, Date issuedAt, Date expiration) {
		this.username = username;
		this.roles = roles;
		long seconds = issuedAt.getTime() / 1000;
		this.issuedAt = new Date(seconds * 1000);
		if (expiration == null) {
			this.expiration = null;
		} else {
			seconds = expiration.getTime() / 1000;
			this.expiration = new Date(seconds * 1000);
		}
	}

	/**
	 * Returns the username of the authenticated user.
	 * 
	 * @return the username of the authenticated user
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Returns the roles associated with this user as a {@link String} array.
	 *
	 * @return the roles associated with this user as a {@link String} array.
	 */
	public String[] getRoles() {
		return roles;
	}

	/**
	 * Returns the roles associated with this user as a comma-separated String.
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

	/**
	 * Returns true if the givem {@code role} is in the list of roles for this {@link
	 * AuthenticationInfo}, false otherwise.
	 *
	 * @param role the name of the role for which we want to check its existence
	 * @return true if the role exists, false otherwise
	 */
	public boolean hasRole(String role) {
		for(String s : roles) {
			if(s.equals(role)) return true;
		}
		return false;
	}
	
	/**
	 * Returns the date/time when the JWT token was issued, with precision of seconds.
	 * 
	 * @return the date/time when the JWT token was issued, with precision of
	 * seconds
	 */
	public Date getIssuedAt() {
		return issuedAt;
	}

	/**
	 * Returns the date/time when the JWT token expires, with precision of
	 * seconds.
	 * 
	 * @return the date/time when the JWT token expires, with precision of
	 * seconds
	 */
	public Date getExpiration() {
		return expiration;
	}

}

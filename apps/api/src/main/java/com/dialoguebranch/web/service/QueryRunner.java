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

package com.dialoguebranch.web.service;

import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.auth.jwt.JWTUtils;
import com.dialoguebranch.web.service.exception.*;
import nl.rrd.utils.AppComponents;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;

import jakarta.servlet.http.HttpServletResponse;

/**
 * This class can run queries. It can validate an authentication token.
 * 
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class QueryRunner {

	/**
	 * Runs a query on the authentication database. If the HTTP request is specified, it will
	 * validate the authentication token. If there is no token in the request, or the token is empty
	 * or invalid, it throws an HttpException with 401 Unauthorized. If the request is null, it will
	 * not validate anything. This can be used for a login or signup.
	 * 
	 * @param query the query
	 * @param versionName the protocol version name (see {@link ProtocolVersion})
	 * @param providedAccessToken the provided JWT access token
	 * @param response the HTTP response to add header WWW-Authenticate in case of 401 Unauthorized
	 * @param delegateUser the "DialogueBranch user" for which this query should be run, or ""
	 *                     if this should be for the currently authenticated user
	 * @param application the {@link Application} context used to access {@link BasicUserCredentials}
	 *                    in a non-static way.
	 * @return the query result
	 * @throws HttpException if the query should return an HTTP error status
	 * @throws HttpException if an unexpected error occurs. This results in HTTP error status 500
	 *                   Internal Server Error.
	 */
	public static <T> T runQuery(AuthQuery<T> query, String versionName, String providedAccessToken,
			HttpServletResponse response, String delegateUser, Application application)
			throws HttpException {
		ProtocolVersion version;
		try {
			version = ProtocolVersion.forVersionName(versionName);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Unknown protocol version: " + versionName);
		}
		try {
			AuthenticationInfo authenticationInfo = null;

			if (providedAccessToken != null)
				authenticationInfo = validateAccessToken(providedAccessToken, application);

			// If the request was made for "this" (authenticated) user
			if(delegateUser == null || delegateUser.isEmpty()) {
				String queryUserName = "";
				if(authenticationInfo != null) queryUserName = authenticationInfo.getUsername();
				return query.runQuery(version, queryUserName);

			// If the request was made for a specific delegateUser that happens to be "this"
			// (authenticated) user
			} else if((authenticationInfo != null) && delegateUser.equals(
					authenticationInfo.getUsername())) {
				return query.runQuery(version, authenticationInfo.getUsername());

			// If "this" user is an admin
			} else if((authenticationInfo != null) && (authenticationInfo.hasRole(
					BasicUserCredentials.USER_ROLE_ADMIN))) {
				return query.runQuery(version, authenticationInfo.getUsername());

			// Otherwise, something is wrong
			} else {
				String userIdentifier = "Unknown";
				if(authenticationInfo != null) userIdentifier = authenticationInfo.getUsername();
				throw new UnauthorizedException(ErrorCode.INSUFFICIENT_PRIVILEGES,
					"Attempting to run query for delegateUser '" + delegateUser +
					"', but currently logged in user '" + userIdentifier + "' is not an admin.");
			}
		} catch (UnauthorizedException ex) {
			response.addHeader("WWW-Authenticate", "None");
			throw ex;
		} catch (HttpException ex) {
			throw ex;
		} catch (Exception ex) {
			Logger logger = AppComponents.getLogger(QueryRunner.class.getSimpleName());
            logger.error("Internal Server Error: {}", ex.getMessage(), ex);
			throw new InternalServerErrorException();
		}
	}

	/**
	 * Validates the access token in the specified HTTP request. If no token is specified,
	 * or the token is empty or invalid, it will throw an HttpException with 401 Unauthorized.
	 * Otherwise, it will return the {@link AuthenticationInfo} object representing the information
	 * of the authenticated user.
	 *
	 * @param application the {@link Application} context used to access {@link
	 *                    BasicUserCredentials} in a non-static way.
	 * @return the {@link AuthenticationInfo} for the authenticated user
	 * @throws UnauthorizedException if no token is specified, or the token is empty or invalid
	 */
	public static AuthenticationInfo validateAccessToken(String providedAccessToken,
														 Application application)
			throws UnauthorizedException {

		if(application.getConfiguration().getAuthService().equals(Configuration.AUTH_SERVICE_KEYCLOAK))
			return validateKeycloakAccessToken(providedAccessToken, application);
		else
			return validateNativeAccessToken(providedAccessToken, application);

	}
	
	/**
	 * Validates the given token from request header, using the built-in user management and token
	 * system. If it's empty or invalid, it will throw an HttpException with 401 Unauthorized.
	 * Otherwise, it will return an {@link AuthenticationInfo} object representing the information
	 * of the authenticated user.
	 *
	 * @param token the authentication token (not null)
	 * @param application the {@link Application} context used to access {@link
	 *                    BasicUserCredentials} in a non-static way.
	 * @return the {@link AuthenticationInfo}, representing the authenticated user
	 * @throws UnauthorizedException if the token is empty or invalid
	 */
	private static AuthenticationInfo validateNativeAccessToken(String token, Application application)
			throws UnauthorizedException {
		Logger logger = AppComponents.getLogger(QueryRunner.class.getSimpleName());

		AuthenticationInfo authenticationInfo;
		try {
			authenticationInfo = JWTUtils.isAccessTokenValid(token);
		} catch (ExpiredJwtException ex) {
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED,
					"Authentication token expired");
		} catch (JwtException ex) {
            logger.info("Invalid authentication token: failed to parse: {}", ex.getMessage());
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
					"Authentication token invalid");
		}

		BasicUserCredentials userCredentials = application.getApplicationManager()
				.getUserCredentialsForUsername(authenticationInfo.getUsername());
		if (userCredentials == null) {
            logger.info("Invalid authentication token: user not found: {}",
					authenticationInfo.getUsername());
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
					"Authentication token invalid");
		}

		if (authenticationInfo.getExpiration() != null &&
				authenticationInfo.getExpiration().getTime() < System.currentTimeMillis()) {
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED,
					"Authentication token expired");
		}

		return authenticationInfo;
	}

	/**
	 * Validates the given Keycloak token from request header. If it's empty or invalid, it will
	 * throw an HttpException with 401 Unauthorized. Otherwise, it will return the user object for
	 * the authenticated user.
	 *
	 * @param token the authentication token (not null)
	 * @param application the {@link Application} context used to access the Keycloak manager in a
	 *                    non-static way.
	 * @return the {@link AuthenticationInfo} object representing the authenticated user
	 * @throws UnauthorizedException if the token is empty or invalid
	 */
	private static AuthenticationInfo validateKeycloakAccessToken(String token, Application application)
			throws UnauthorizedException{
		return application.getApplicationManager().getKeycloakManager().validateAccessToken(token);
	}

}

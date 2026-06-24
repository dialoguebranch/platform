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

package com.dialoguebranch.web.service;

import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.auth.jwt.JWTUtils;
import com.dialoguebranch.web.service.exception.*;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This class can run queries. It can validate an authentication token.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class QueryRunner {

	private static final Logger logger = LoggerFactory.getLogger(QueryRunner.class);

	/** Utility class — not instantiated. */
	private QueryRunner() { }

	/**
	 * Runs a query on the authentication database. If the HTTP request is specified, it will
	 * validate the authentication token. If there is no token in the request, or the token is empty
	 * or invalid, it throws an HttpException with 401 Unauthorized. If the request is null, it will
	 * not validate anything. This can be used for a login or signup.
	 *
	 * @param <T> the return type of the query result.
	 * @param query the query
	 * @param versionName the protocol version name (see {@link ProtocolVersion})
	 * @param providedAccessToken the provided JWT access token
	 * @param response the HTTP response to add header WWW-Authenticate in case of 401 Unauthorized
	 * @param delegateUser the "Dialogue Branch user" for which this query should be run, or ""
	 *                     if this should be for the currently authenticated user
	 * @param application the {@link Application} context used to access {@link BasicUserCredentials}
	 *                    in a non-static way.
	 * @return the query result
	 * @throws HttpException if the query should return an HTTP error status
	 * @throws HttpException if an unexpected error occurs. This results in HTTP error status 500
	 *                   Internal Server Error.
	 */
	public static <T> T runQuery(AuthQuery<T> query, String versionName, String providedAccessToken,
			HttpServletResponse response, String delegateUser, Application application,
			String... requiredRoles)
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

			// Check that the authenticated user has at least one of the required roles
			if (requiredRoles.length > 0) {
				boolean hasRequiredRole = false;
				if (authenticationInfo != null) {
					for (String role : requiredRoles) {
						if (authenticationInfo.hasRole(role)) {
							hasRequiredRole = true;
							break;
						}
					}
				}
				if (!hasRequiredRole) {
					String userIdentifier = authenticationInfo != null
							? authenticationInfo.getUsername() : "Unknown";
					throw new UnauthorizedException(ErrorCode.INSUFFICIENT_PRIVILEGES,
							"User '" + userIdentifier + "' does not have the required role to " +
							"access this endpoint.");
				}
			}

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
	 * @param providedAccessToken the JWT access token string to validate.
	 * @param application the {@link Application} context used to access {@link
	 *                    BasicUserCredentials} in a non-static way.
	 * @return the {@link AuthenticationInfo} for the authenticated user
	 * @throws UnauthorizedException if no token is specified, or the token is empty or invalid
	 */
	public static AuthenticationInfo validateAccessToken(String providedAccessToken,
														 Application application)
			throws UnauthorizedException {

		if(application.getDlbProperties().getAuth().getService().equals(DlbProperties.AUTH_SERVICE_KEYCLOAK))
			return validateKeycloakAccessToken();
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

		AuthenticationInfo authenticationInfo;
		try {
			authenticationInfo = application.getJwtUtils().isAccessTokenValid(token);
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
	 * Extracts the validated Keycloak {@link AuthenticationInfo} from the Spring Security context.
	 * Token validation has already been performed by the OAuth2 resource server filter before
	 * this method is called.
	 *
	 * @return the {@link AuthenticationInfo} extracted from the validated JWT
	 * @throws UnauthorizedException if no valid JWT authentication is found in the security context
	 */
	private static AuthenticationInfo validateKeycloakAccessToken() throws UnauthorizedException {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
					"Authentication token invalid");
		}
		return authenticationInfoFromKeycloakJwt(jwtAuth.getToken());
	}

	/**
	 * Converts a Spring Security {@link Jwt} (issued by Keycloak) into an {@link AuthenticationInfo}
	 * by extracting the {@code preferred_username} and {@code resource_access} claims.
	 *
	 * @param jwt the validated Keycloak JWT
	 * @return the corresponding {@link AuthenticationInfo}
	 */
	public static AuthenticationInfo authenticationInfoFromKeycloakJwt(Jwt jwt) {
		String username = jwt.getClaimAsString("preferred_username");
		String[] roles = extractKeycloakRoles(jwt);
		Date issuedAt = jwt.getIssuedAt() != null ? Date.from(jwt.getIssuedAt()) : new Date();
		Date expiration = jwt.getExpiresAt() != null ? Date.from(jwt.getExpiresAt()) : null;
		return new AuthenticationInfo(username, roles, issuedAt, expiration);
	}

	private static String[] extractKeycloakRoles(Jwt jwt) {
		Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
		if (resourceAccess == null) {
			logger.warn("No resource_access claim found in Keycloak JWT — user has no roles.");
			return new String[0];
		}

		Object serviceEntry = resourceAccess.get("dlb-web-service");
		if (!(serviceEntry instanceof Map<?, ?> serviceRoles)) {
			logger.warn("No dlb-web-service entry in resource_access claim — user has no roles.");
			return new String[0];
		}

		Object rolesList = serviceRoles.get("roles");
		if (!(rolesList instanceof List<?> rawList)) {
			logger.warn("No roles list found under dlb-web-service in resource_access claim — user has no roles.");
			return new String[0];
		}

		return rawList.stream()
				.filter(r -> r instanceof String)
				.map(r -> (String) r)
				.toArray(String[]::new);
	}

}

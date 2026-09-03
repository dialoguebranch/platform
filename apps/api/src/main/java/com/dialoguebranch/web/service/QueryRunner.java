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

package com.dialoguebranch.web.service;

import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.AuthorizationService;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.exception.*;
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
 * Runs {@link AuthQuery} instances behind a {@link Permission} check, and resolves the
 * authenticated caller from the security context.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class QueryRunner {

	private static final Logger logger = LoggerFactory.getLogger(QueryRunner.class);

	/**
	 * The Keycloak client whose {@code resource_access} entry carries this service's client
	 * roles — i.e. {@code dlb.auth.keycloak.client-id}. Seeded once by {@link SecurityConfig} at
	 * startup; the {@code "dlb-web-service"} default matches the property's own default, so an
	 * unconfigured deployment is unaffected.
	 */
	private static volatile String resourceAccessClientId = "dlb-web-service";

	/** Utility class — not instantiated. */
	private QueryRunner() { }

	/**
	 * Sets the Keycloak client id whose {@code resource_access} entry {@link #authenticationInfoFromKeycloakJwt}
	 * reads the caller's roles from. Called by {@link SecurityConfig} at startup with
	 * {@code dlb.auth.keycloak.client-id}.
	 *
	 * @param clientId the configured Keycloak client id.
	 */
	static void setResourceAccessClientId(String clientId) {
		resourceAccessClientId = clientId;
	}

	/**
	 * Runs a query, first requiring that the authenticated user holds {@code requiredPermission}
	 * (see {@link AuthorizationService}). An authenticated user without the permission gets a
	 * {@code 403 Forbidden}; a request with no authenticated caller gets a {@code 401
	 * Unauthorized} (normally the OAuth2 resource-server filter has already rejected it as such
	 * before this point).
	 *
	 * @param <T> the return type of the query result.
	 * @param query the query
	 * @param versionName the protocol version name (see {@link ProtocolVersion})
	 * @param response the HTTP response to add header WWW-Authenticate in case of 401 Unauthorized
	 * @param delegateUser the "Dialogue Branch user" for which this query should be run, or ""
	 *                     if this should be for the currently authenticated user
	 * @param requiredPermission the permission the caller must hold
	 * @return the query result
	 * @throws HttpException if the query should return an HTTP error status
	 * @throws HttpException if an unexpected error occurs. This results in HTTP error status 500
	 *                   Internal Server Error.
	 */
	public static <T> T runQuery(AuthQuery<T> query, String versionName,
			HttpServletResponse response, String delegateUser,
			Permission requiredPermission)
			throws HttpException {
		ProtocolVersion version;
		try {
			version = ProtocolVersion.forVersionName(versionName);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Unknown protocol version: " + versionName);
		}
		try {
			AuthenticationInfo authenticationInfo = null;

			// Resolve the caller from the validated security context: the OAuth2 resource-server
			// filter has already rejected (401) any request whose bearer token was missing or
			// invalid, so a JwtAuthenticationToken here is trustworthy.
			if (SecurityContextHolder.getContext().getAuthentication()
					instanceof JwtAuthenticationToken jwtAuth)
				authenticationInfo = authenticationInfoFromKeycloakJwt(jwtAuth.getToken());

			// Belt and braces: every endpoint routed through here is behind
			// .anyRequest().authenticated(), so an unauthenticated request is already a 401 before
			// this point. Should that ever stop holding (e.g. an endpoint added to the permitAll
			// list still calling runQuery), a missing caller is a 401, not the 403 that
			// AuthorizationService.require would otherwise raise for a null user.
			if (authenticationInfo == null)
				throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_NOT_FOUND,
						"No valid authentication token found.");

			AuthorizationService.require(authenticationInfo, requiredPermission);

			// authenticationInfo is non-null past the guard above.
			String callerIssuer = authenticationInfo.getIssuer();
			String callerSubject = authenticationInfo.getSubject();

			// The delegateUser parameter, when present, is the *subject* of the delegate within
			// the caller's own realm (#128) — there is no cross-realm delegation, so the effective
			// issuer is always the caller's.
			DialogueBranchUserId runAs;

			// If the request was made for "this" (authenticated) user
			if (delegateUser == null || delegateUser.isEmpty()) {
				runAs = new DialogueBranchUserId(callerIssuer, callerSubject,
						authenticationInfo.getUsername());

			// If the request names a delegate subject that is in fact "this" user
			} else if (delegateUser.equals(callerSubject)) {
				runAs = new DialogueBranchUserId(callerIssuer, callerSubject,
						authenticationInfo.getUsername());

			// If "this" user is allowed to act on behalf of another user in the same realm
			} else if (AuthorizationService.hasPermission(authenticationInfo,
					Permission.USER_DELEGATE)) {
				// The delegate's token is not in hand, so its username cannot be refreshed here.
				runAs = DialogueBranchUserId.withoutUsername(callerIssuer, delegateUser);

			// Otherwise the caller is trying to act on behalf of another user without the
			// USER_DELEGATE permission.
			} else {
				throw new ForbiddenException(ErrorCode.INSUFFICIENT_PRIVILEGES,
					"User '" + callerSubject + "' does not have the '" + Permission.USER_DELEGATE +
					"' permission required to run a query for delegate subject '" + delegateUser +
					"'.");
			}

			return query.runQuery(version, runAs);
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
	 * Resolves the authenticated caller from the validated security context. The OAuth2
	 * resource-server filter has already verified (or rejected, as a 401) the bearer token by the
	 * time this runs, so a {@link JwtAuthenticationToken} in the context is trustworthy; anything
	 * else means there is no authenticated caller and yields a 401.
	 *
	 * @return the {@link AuthenticationInfo} for the authenticated caller
	 * @throws UnauthorizedException if there is no authenticated caller in the security context
	 */
	public static AuthenticationInfo requireAuthenticatedUser() throws UnauthorizedException {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
					"Authentication token invalid");
		}
		return authenticationInfoFromKeycloakJwt(jwtAuth.getToken());
	}

	/**
	 * Converts a Spring Security {@link Jwt} (issued by Keycloak) into an {@link AuthenticationInfo}
	 * by extracting the {@code preferred_username} claim and the client roles from
	 * {@code resource_access.<client-id>.roles}, where {@code <client-id>} is the configured
	 * {@code dlb.auth.keycloak.client-id} (see {@link #setResourceAccessClientId}).
	 *
	 * @param jwt the validated Keycloak JWT
	 * @return the corresponding {@link AuthenticationInfo}
	 */
	public static AuthenticationInfo authenticationInfoFromKeycloakJwt(Jwt jwt) {
		// Identity is (iss, sub) — see #128. iss is the full validated issuer URL; sub is the
		// stable per-realm user id. preferred_username is kept for display/logging only.
		String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString()
				: jwt.getClaimAsString("iss");
		String subject = jwt.getSubject();
		String username = jwt.getClaimAsString("preferred_username");
		String[] roles = extractKeycloakRoles(jwt);
		Date issuedAt = jwt.getIssuedAt() != null ? Date.from(jwt.getIssuedAt()) : new Date();
		Date expiration = jwt.getExpiresAt() != null ? Date.from(jwt.getExpiresAt()) : null;
		return new AuthenticationInfo(issuer, subject, username, roles, issuedAt, expiration);
	}

	private static String[] extractKeycloakRoles(Jwt jwt) {
		Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
		if (resourceAccess == null) {
			logger.warn("No resource_access claim found in Keycloak JWT — user has no roles.");
			return new String[0];
		}

		Object serviceEntry = resourceAccess.get(resourceAccessClientId);
		if (!(serviceEntry instanceof Map<?, ?> serviceRoles)) {
			logger.warn("resource_access claim has no \"{}\" entry (present: {}) — user has no " +
					"roles. Check that dlb.auth.keycloak.client-id matches the Keycloak client " +
					"the token was issued for.", resourceAccessClientId, resourceAccess.keySet());
			return new String[0];
		}

		Object rolesList = serviceRoles.get("roles");
		if (!(rolesList instanceof List<?> rawList)) {
			logger.warn("resource_access.\"{}\" has no roles list — user has no roles.",
					resourceAccessClientId);
			return new String[0];
		}

		return rawList.stream()
				.filter(r -> r instanceof String)
				.map(r -> (String) r)
				.toArray(String[]::new);
	}

}

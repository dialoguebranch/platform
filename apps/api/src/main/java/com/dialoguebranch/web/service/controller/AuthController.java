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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.exception.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller for the /auth/... end-points of the Dialogue Branch Web Service.
 *
 * <p>The service is a pure OAuth2 resource server: it validates Bearer tokens issued by Keycloak
 * but plays no role in issuing or refreshing them (the client obtains tokens directly from
 * Keycloak via the Authorization Code + PKCE flow). This controller only exposes the endpoints
 * that remain meaningful for a resource server: logging out of the server-side session state and
 * validating a stored token.</p>
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 */
@RestController
@RequestMapping(value = {"/v{version}/auth", "/auth"})
@Tag(name = "1. Authentication", description = "End-points related to Authentication.")
public class AuthController {

	@Autowired
	Application application;

	/** Used for executing QueryRunner operations in a thread-safe manner */
	private static final Object AUTH_LOCK = new Object();

	/** Used for writing logging information */
	private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instances of this class are constructed through Spring.
	 */
	public AuthController() { }

	// -------------------------------------------------------------------- //
	// -------------------- END-POINT: "/auth/logout" -------------------- //
	// -------------------------------------------------------------------- //

	/**
	 * Log out the currently authenticated user.
	 *
	 * <p>Destroys the in-memory {@link com.dialoguebranch.web.service.execution.UserService}
	 * for the authenticated user, releasing any associated dialogue state. Token invalidation
	 * is the responsibility of the client.</p>
	 *
	 * @param request the HTTP request (used to extract the bearer token).
	 * @param response the HTTP response.
	 * @param version the API version, e.g. '1'.
	 * @throws HttpException if the token is missing or invalid.
	 */
	@SecurityRequirement(name = "bearerAuth")
	@SecurityRequirement(name = "oauth2")
	@Operation(summary = "Log out the currently authenticated user.",
		description = "Destroys the in-memory UserService for the authenticated user, releasing " +
			"any associated dialogue state.")
	@RequestMapping(value = "/logout", method = RequestMethod.POST)
	public void logout(
			HttpServletRequest request,
			HttpServletResponse response,

			@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
			@PathVariable(value = "version")
			String version
	) throws HttpException {

		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		logger.info("POST /v{}/auth/logout", version);

		String accessToken = ControllerFunctions.extractAccessToken(request);
		QueryRunner.runQuery(
				(protocolVersion, userId) -> doLogout(userId),
				version, accessToken, response, "", application);
	}

	private Void doLogout(String userId) {
		com.dialoguebranch.web.service.execution.UserService userService =
				application.getApplicationManager().getActiveUserService(userId);
		if (userService != null) {
			application.getApplicationManager().removeUserService(userService);
			logger.info("UserService for user '{}' removed on logout.", userId);
		} else {
			logger.info("Logout called for user '{}' but no active UserService found.", userId);
		}
		return null;
	}

	// --------------------------------------------------------------------- //
	// -------------------- END-POINT: "/auth/validate" -------------------- //
	// --------------------------------------------------------------------- //

	/**
	 * Validate a given authentication token.
	 *
	 * <p>If your client application has a stored authentication token you may use this method to
	 * check whether or not that is a valid token. This method will either return 'true', or throw
	 * an Authentication error.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @return 'true' if the token is correct, otherwise it will throw an exception.
	 * @throws UnauthorizedException if the given authentication token is not (or no longer) valid.
	 */
	@SecurityRequirement(name = "bearerAuth")
	@SecurityRequirement(name = "oauth2")
	@Operation(summary = "Validate a given authentication token.",
		description = "If your client application has a stored authentication token you may use" +
			"this method to check whether or not that is a valid token. This method will either" +
			"return 'true', or throw an Authentication error.")
	@RequestMapping(value="/validate", method= RequestMethod.POST)
	public boolean validate(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version

	) throws UnauthorizedException {

		// If no version is provided, or version is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
        logger.info("POST /v{}/auth/validate", version);

		synchronized (AUTH_LOCK) {
			QueryRunner.validateAccessToken(
					ControllerFunctions.extractAccessToken(request),application);
			return true;
		}
	}

}

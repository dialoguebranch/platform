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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.*;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.auth.basic.BasicUserFile;
import com.dialoguebranch.web.service.controller.schema.KeycloakTokenResponse;
import com.dialoguebranch.web.service.controller.schema.LoginParametersPayload;
import com.dialoguebranch.web.service.controller.schema.LoginResultPayload;
import com.dialoguebranch.web.service.controller.schema.RefreshParametersPayload;
import com.dialoguebranch.web.service.exception.*;
import com.dialoguebranch.web.service.auth.jwt.JWTUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import nl.rrd.utils.AppComponents;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the /auth/... end-points of the Dialogue Branch Web Service.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Dennis Hofs (Roessingh Research and Development)
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
	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());

	/** Used to access configuration parameters */
	private final Configuration config = Configuration.getInstance();

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instances of this class are constructed through Spring.
	 */
	public AuthController() { }

	// ------------------------------------------------------------------ //
	// -------------------- END-POINT: "/auth/login" -------------------- //
	// ------------------------------------------------------------------ //

	/**
	 * Obtain an authentication token by logging in.
	 *
	 * <p>Log in to the service by providing a username, password and indicating the desired
	 * duration of the authentication token in minutes. If you want to obtain an authentication
	 * token that does not expire, either provide '0' or 'never' as the value for
	 * '*tokenExpiration*'. This method returns a JSON object containing the provided '*user*' name,
	 * the '*role*' of the user, and the JWT '*token*' that may be used to authenticate subsequent
	 * API calls.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param loginParametersPayload the JSON payload containing the username, password and token
	 *                               expiration values.
	 * @return a {@link LoginResultPayload} object, containing the username, the corresponding role
	 *         and a JSON Web Token.
	 * @throws HttpException in case of a malformed request, or invalid login credentials
	 */
	@Operation(summary = "Obtain an authentication token by logging in.",
		description = "Log in to the service by providing a username, password and indicating " +
			"the desired duration of the authentication token in minutes. If you want to obtain " +
			"an authentication token that does not expire, either provide '0' or 'never' as the " +
			"value for '*tokenExpiration*'. This method returns a JSON object containing the " +
			"provided '*user*' name, the '*role*' of the user, and the JWT '*token*' that may be " +
			"used to authenticate subsequent API calls.")
	@RequestMapping(value="/login", method= RequestMethod.POST, consumes={
			MediaType.APPLICATION_JSON_VALUE })
	public LoginResultPayload login(
			HttpServletRequest request,
			HttpServletResponse response,

			@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
			@PathVariable(value = "version")
			String version,

			@RequestBody
			LoginParametersPayload loginParametersPayload) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		if(loginParametersPayload != null) {
            logger.info("POST /v{}/auth/login for user '{}'.",
					version, loginParametersPayload.getUser());
		} else {
            logger.info("POST /v{}/auth/login with empty login parameters.", version);
			throw new BadRequestException("Missing login parameters in request body.");
		}

		synchronized (AUTH_LOCK) {
			return QueryRunner.runQuery(
					(protocolVersion, user) -> doLogin(request, loginParametersPayload),
					version, null, response, "", application);
		}
	}

	/**
	 * Performs the operation after a call to the /auth/login end-point.
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param loginParametersPayload the JSON payload containing the username, password and token
	 * 	                             expiration values.
	 * @return a {@link LoginResultPayload} object, containing the username, the corresponding role
	 * 	       and a JSON Web Token.
	 * @throws BadRequestException in case of any error in the given {@link LoginParametersPayload}.
	 * @throws UnauthorizedException in case the username and passwords don't match.
	 */
	private LoginResultPayload doLogin(HttpServletRequest request,
									   LoginParametersPayload loginParametersPayload)
            throws BadRequestException, UnauthorizedException {

		// Perform some validations on the request and login parameters, throwing a
		// BadRequestException in case of errors
		validateLoginParameters(request, loginParametersPayload);

		if(config.getAuthService().equals(Configuration.AUTH_SERVICE_KEYCLOAK)) {
			logger.info("Keycloak authentication enabled.");
			return doLoginKeycloak(loginParametersPayload);
		} else {
			logger.info("Keycloak authentication disabled - using basic user management.");
			return doLoginNative(loginParametersPayload);
		}

	}

	/**
	 * Helper function for the doLogin method, validates the given {@code request} and
	 * {@code loginParametersPayload} parameters.
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 * 	              parameters).
	 * @param loginParametersPayload the JSON payload containing the username, password and token
	 * 	                             expiration values.
	 * @throws BadRequestException in case of any error in the given {@link LoginParametersPayload}.
	 */
	private void validateLoginParameters(HttpServletRequest request,
										 LoginParametersPayload loginParametersPayload)
			throws BadRequestException {
		ControllerFunctions.validateForbiddenQueryParams(request, "user",
				"password");
		String user = loginParametersPayload.getUser();
		String password = loginParametersPayload.getPassword();

		List<HttpFieldError> fieldErrors = new ArrayList<>();
		if (user == null || user.isEmpty()) {
			fieldErrors.add(new HttpFieldError("user",
					"Parameter 'user' not defined."));
		}
		if (password == null || password.isEmpty()) {
			fieldErrors.add(new HttpFieldError("password",
					"Parameter 'password' not defined."));
		}

		if (!fieldErrors.isEmpty()) {

			logger.info("Failed login attempt: {}", fieldErrors);
			throw BadRequestException.withMessageAndInvalidInput(
					"One or more login parameters were not correctly provided.",
					fieldErrors);

		}

	}

	private LoginResultPayload doLoginNative(LoginParametersPayload loginParametersPayload)
			throws UnauthorizedException {

		String user = loginParametersPayload.getUser();
		String password = loginParametersPayload.getPassword();

		BasicUserCredentials basicUserCredentials
				= application.getApplicationManager().getUserCredentialsForUsername(user);
		String invalidError = "Username or password is invalid";

		if (basicUserCredentials == null) {
			logger.info("Failed login attempt for user {}: user unknown.", user);
			throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, invalidError);
		}

		if (!basicUserCredentials.getPassword().equals(password)) {
			logger.info("Failed login attempt for user {}: invalid credentials.", user);
			throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, invalidError);
		}

		logger.info("User {} logged in successfully.", basicUserCredentials.getUsername());

		String accessToken = JWTUtils.generateAccessToken(basicUserCredentials);
		String refreshToken = JWTUtils.generateRefreshToken(basicUserCredentials);

		return new LoginResultPayload(
				basicUserCredentials.getUsername(),
				basicUserCredentials.getCommaSeparatedRolesString(),
				accessToken,
				config.getAccessTokenExpirationSeconds(),
				refreshToken,
				config.getRefreshTokenExpirationSeconds()
				);
	}

	/**
	 * Private method for handling the login procedure when the use of Keycloak is enabled in the
	 * Web Service.
	 *
	 * @param loginParametersPayload the JSON payload containing the username, password and token
	 * 	      expiration values provided at login.
	 * @return a {@link LoginResultPayload} object containing the results of the login attempt.
	 * @throws UnauthorizedException in case of any issue with the login parameters provided
	 */
	private LoginResultPayload doLoginKeycloak(LoginParametersPayload loginParametersPayload)
			throws UnauthorizedException {

		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		String keycloakLoginUrl = config.getKeycloakBaseUrl();
		if(!keycloakLoginUrl.endsWith("/")) keycloakLoginUrl += "/";
		keycloakLoginUrl += "realms/"
				+ config.getKeycloakRealm()
				+ "/protocol/openid-connect/token";

        logger.info("Redirecting login attempt to: {}", keycloakLoginUrl);

		MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
		requestParameters.add("client_id",config.getKeycloakClientId());
		requestParameters.add("client_secret",config.getKeycloakClientSecret());
		requestParameters.add("username",loginParametersPayload.getUser());
		requestParameters.add("password",loginParametersPayload.getPassword());
		requestParameters.add("grant_type","password");

		ResponseEntity<KeycloakTokenResponse> response;

		try {
			HttpEntity<MultiValueMap<String,String>> entity = new HttpEntity<>(requestParameters,
					headers);
			response = restTemplate.exchange(
					keycloakLoginUrl,
					HttpMethod.POST,
					entity,
					KeycloakTokenResponse.class);
		} catch(ResourceAccessException rae) {
			logger.error("Unable to reach keycloak service.",rae);
			throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
					"Unable to process login request. Unable to reach Keycloak service.");
		} catch(Exception ex) {
			logger.error("Exception while forwarding login attempt to keycloak.",ex);
			throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
					"Unable to process login request. An unknown error has occurred.");
			// TODO: Catch additional details and add as fieldErrors to the UnauthorizedException
		}

        if (response.getStatusCode() == HttpStatus.OK) {
            logger.info("Call to Keycloak token end-point successful.");
            KeycloakTokenResponse keyCloakResponse = response.getBody();
            if (keyCloakResponse != null) {
				// Validate the received token, in order to get the roles.
				String accessToken = keyCloakResponse.getAccessToken();
				AuthenticationInfo authenticationInfo =
						application.getApplicationManager().getKeycloakManager()
								.validateAccessToken(accessToken);

                return new LoginResultPayload(
						loginParametersPayload.getUser(),
						authenticationInfo.getCommaSeparatedRolesString(),
						keyCloakResponse.getAccessToken(),
						keyCloakResponse.getExpiresIn(),
						keyCloakResponse.getRefreshToken(),
						keyCloakResponse.getRefreshExpiresIn()
				);
            } else {
                logger.warn("Failed login attempt (empty response) for user {}.",
                        loginParametersPayload.getUser());
                throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
                        "Invalid response from Keycloak service.");
            }

        } else {
            logger.warn("Failed login attempt for user {}: invalid request, status code {}.",
                    loginParametersPayload.getUser(), response.getStatusCode());
            throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
                    "Keycloak service returned status code " + response.getStatusCode() + ".");
        }

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
	@SecurityRequirement(name = "X-Auth-Token")
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

	// -------------------------------------------------------------------- //
	// -------------------- END-POINT: "/auth/refresh" -------------------- //
	// -------------------------------------------------------------------- //

	/**
	 * Generate a new access token and refresh token pair from a given refresh token.
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @return 'true' if the token is correct, otherwise it will throw an exception.
	 * @throws UnauthorizedException if the given authentication token is not (or no longer) valid.
	 */
	@SecurityRequirement(name = "X-Auth-Token")
	@Operation(summary = "Use a refresh token to generate a new access token.",
			description = "If your client's access token has expired, it may use the refresh" +
					"token to generate a new access token (if the refresh token has not expired" +
					"itself).'")
	@RequestMapping(value="/refresh", method= RequestMethod.POST)
	public LoginResultPayload refresh(
			HttpServletRequest request,
			HttpServletResponse response,

			@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
			@PathVariable(value = "version")
			String version,

			@RequestBody
			RefreshParametersPayload refreshParametersPayload
	) throws UnauthorizedException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		logger.info("POST /v{}/auth/refresh", version);

		if(config.getAuthService().equals(Configuration.AUTH_SERVICE_KEYCLOAK)) {
			return doRefreshKeycloak(refreshParametersPayload);
		} else {
			return doRefreshNative(refreshParametersPayload);
		}

	}

	private LoginResultPayload doRefreshKeycloak(RefreshParametersPayload refreshParametersPayload)
			throws UnauthorizedException {

		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		String keycloakLoginUrl = config.getKeycloakBaseUrl();
		if(!keycloakLoginUrl.endsWith("/")) keycloakLoginUrl += "/";
		keycloakLoginUrl += "realms/"
				+ config.getKeycloakRealm()
				+ "/protocol/openid-connect/token";

		logger.info("Redirecting token refresh attempt to: {}", keycloakLoginUrl);

		MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
		requestParameters.add("client_id",config.getKeycloakClientId());
		requestParameters.add("client_secret",config.getKeycloakClientSecret());
		requestParameters.add("refresh_token",refreshParametersPayload.getRefreshToken());
		requestParameters.add("grant_type","refresh_token");

		ResponseEntity<KeycloakTokenResponse> response;

		try {
			HttpEntity<MultiValueMap<String,String>> entity = new HttpEntity<>(requestParameters,
					headers);
			response = restTemplate.exchange(
					keycloakLoginUrl,
					HttpMethod.POST,
					entity,
					KeycloakTokenResponse.class);
		} catch(ResourceAccessException rae) {
			logger.error("Unable to reach keycloak service.",rae);
			throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
					"Unable to process login request. Unable to reach Keycloak service.");
		} catch(Exception ex) {
			logger.error("Exception while forwarding login attempt to keycloak.",ex);
			throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
					"Unable to process login request. An unknown error has occurred.");
			// TODO: Catch additional details and add as fieldErrors to the UnauthorizedException
		}

		if (response.getStatusCode() == HttpStatus.OK) {
			logger.info("Call to Keycloak token end-point requesting access token refresh" +
					" successful.");
			KeycloakTokenResponse keyCloakResponse = response.getBody();
			if (keyCloakResponse != null) {
				// Validate the received token, in order to get the roles.
				String accessToken = keyCloakResponse.getAccessToken();
				AuthenticationInfo authenticationInfo =
						application.getApplicationManager().getKeycloakManager()
								.validateAccessToken(accessToken);

				return new LoginResultPayload(
						authenticationInfo.getUsername(),
						authenticationInfo.getCommaSeparatedRolesString(),
						keyCloakResponse.getAccessToken(),
						keyCloakResponse.getExpiresIn(),
						keyCloakResponse.getRefreshToken(),
						keyCloakResponse.getRefreshExpiresIn()
				);
			} else {
				logger.warn("Failed to refresh access token (empty response).");
				throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
						"Invalid response from Keycloak service.");
			}

		} else {
			logger.warn("Failed to refresh access token: invalid request, status code {}.",
					response.getStatusCode());
			throw new UnauthorizedException(ErrorCode.KEYCLOAK_ERROR,
					"Keycloak service returned status code " + response.getStatusCode() + ".");
		}
	}

	private LoginResultPayload doRefreshNative(RefreshParametersPayload refreshParametersPayload)
			throws UnauthorizedException {

		AuthenticationInfo authInfo;

		try {
			authInfo = JWTUtils.isRefreshTokenValid(refreshParametersPayload.getRefreshToken());
		} catch (ExpiredJwtException ex) {
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_EXPIRED,
					"Refresh token expired");
		} catch (JwtException ex) {
			throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
					"Refresh token invalid");
		}

		// If the refresh token was valid, issue a bunch of new tokens

		// First we retrieve the roles of this user, because it should be supplied with the
		// new LoginResultPayload
		BasicUserCredentials basicUserCredentials
				= application.getApplicationManager()
				.getUserCredentialsForUsername(authInfo.getUsername());
		String invalidError = "User '"+ authInfo.getUsername() +"' does not exist";

		if (basicUserCredentials == null) {
			logger.info("Failed to refresh access token for user {}: user unknown.", authInfo.getUsername());
			throw new UnauthorizedException(ErrorCode.UNKNOWN_USER, invalidError);
		}

		return new LoginResultPayload(
				authInfo.getUsername(),
				basicUserCredentials.getCommaSeparatedRolesString(),
				JWTUtils.generateAccessToken(basicUserCredentials),
				config.getAccessTokenExpirationSeconds(),
				JWTUtils.generateRefreshToken(basicUserCredentials),
				config.getRefreshTokenExpirationSeconds()
		);

	}

}

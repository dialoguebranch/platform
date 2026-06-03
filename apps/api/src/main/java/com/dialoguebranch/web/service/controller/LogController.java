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

import com.dialoguebranch.web.service.execution.UserService;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.storage.ServerLoggedDialogue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.exception.DatabaseException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Controller for the /log/... end-points of the Dialogue Branch Web Service. These end-points
 * provide external access to logged dialogue information.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
*/
@RestController
@SecurityRequirement(name = "X-Auth-Token")
@RequestMapping(value = {"/v{version}/log", "/log"})
@Tag(name = "4. Logging", description = "End-points for retrieving information about logged" +
		" dialogues.")
public class LogController {

	@Autowired
	Application application;

	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());

	// ----------------------------------------------------------------------- //
	// -------------------- END-POINT: "/log/get-session" -------------------- //
	// ----------------------------------------------------------------------- //

	@Operation(
		summary = "Retrieve all known logging information for a given session.",
		description = "This method will retrieve all know logging information associated with " +
				"the given sessionId")
	@RequestMapping(value="/get-session", method= RequestMethod.GET)
	public List<ServerLoggedDialogue> getSession(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "Session ID for which to check its existence.")
		@RequestParam(value="sessionId")
		String sessionId,

		@Parameter(description = "The user for which to check the session id (if left empty" +
				" it is assumed to be the currently logged in user.")
		@RequestParam(value="delegateUser", required = false)
		String delegateUser) throws Exception {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "GET /v" + version + "/log/get-session?sessionId=" + sessionId;
		if(delegateUser != null && !delegateUser.isEmpty())
			logInfo += "&delegateUser=" + delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
					(protocolVersion, user) -> doGetSession(user, sessionId),
					version, accessToken, response, delegateUser, application);
		} else {
			return QueryRunner.runQuery(
					(protocolVersion, user) -> doGetSession(delegateUser, sessionId),
					version, accessToken, response, delegateUser, application);
		}
	}

	private List<ServerLoggedDialogue> doGetSession(String userId, String sessionId)
            throws DatabaseException, IOException {

		// Get or create a UserService for the user in the default time zone
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId);

		return userService.getDialogueSessionLog(sessionId);
	}

	// --------------------------------------------------------------------- //
	// -------------------- END-POINT: "/log/verify-id" -------------------- //
	// --------------------------------------------------------------------- //

	@Operation(
		summary = "Verify whether a dialogue session identifier is already in use for a given " +
				"user.",
		description = "When starting a dialogue (through the /dialogue/start end-point), you can " +
				"provide an optional sessionId that will be stored in the dialogue logs. If you " +
				"do not provide one, an identifier is generated that is guaranteed to be unique. " +
				"If you do want to provide a sessionId, you can use this method first to verify " +
				"that the identifier is not yet in use.")
	@RequestMapping(value="/verify-id", method= RequestMethod.GET)
	public Boolean verifyId(
			HttpServletRequest request,
			HttpServletResponse response,

			@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
			@PathVariable(value = "version")
			String version,

			@Parameter(description = "Session ID for which to check its existence.")
			@RequestParam(value="sessionId")
			String sessionId,

			@Parameter(description = "The user for which to check the session id (if left empty" +
					" it is assumed to be the currently logged in user.")
			@RequestParam(value="delegateUser", required = false)
			String delegateUser) throws Exception {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "GET /v" + version + "/log/verify-id?sessionId=" + sessionId;
		if(delegateUser != null && !delegateUser.isEmpty())
			logInfo += "&delegateUser=" + delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
					(protocolVersion, user) -> doVerifyId(user, sessionId),
					version, accessToken, response, delegateUser, application);
		} else {
			return QueryRunner.runQuery(
					(protocolVersion, user) -> doVerifyId(delegateUser, sessionId),
					version, accessToken, response, delegateUser, application);
		}

	}

	private Boolean doVerifyId(String userId, String sessionId)
			throws DatabaseException, IOException {

		// Get or create a UserService for the user in the default time zone
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId);

		return userService.existsSessionId(sessionId);
	}

}

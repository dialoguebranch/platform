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

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.execute.LoggedInteraction;
import com.dialoguebranch.model.execute.MessageSource;
import com.dialoguebranch.model.execute.DialogueState;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.DialogueMessageFactory;
import com.dialoguebranch.model.execute.protocol.NullableResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.controller.schema.DialogueListPayload;
import com.dialoguebranch.web.service.controller.schema.OngoingDialoguePayload;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.UnauthorizedException;
import com.dialoguebranch.web.service.execution.UserService;
import com.dialoguebranch.web.service.storage.ServerLoggedDialogue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.io.FileUtils;
import nl.rrd.utils.json.JsonMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for the /dialogue/... end-points of the Dialogue Branch Web Service.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/dialogue", "/dialogue"})
@Tag(name = "2. Dialogue", description = "End-points for starting and controlling the lifecycle " +
		"of remotely executed dialogues.")
public class DialogueController {

	@Autowired
	Application application;

	private static final Logger logger = LoggerFactory.getLogger(DialogueController.class);

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instances of this class are constructed through Spring.
	 */
	public DialogueController() { }

	// ---------------------------------------------------------------------- //
	// -------------------- END-POINT: "/dialogue/start" -------------------- //
	// ---------------------------------------------------------------------- //

	/**
	 * Start the step-by-step execution of the dialogue identified by the given parameters.
	 *
	 * <p>A client application that wants to start executing a dialogue should use this end-point to
	 * do so. The dialogueName (which is the dialogue's filename without it's .dlb extension) and
	 * language are mandatory parameters. The 'userId' is an optional parameter that may be used if
	 * the currently authorized user is an admin and wants to execute a dialogue on behalf of
	 * another user. If the authenticated user is running a dialogue 'for himself' this should be
	 * left empty.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param dialogueName Name of the Dialogue Branch Dialogue to start (excluding .dlb)
	 * @param language Language code of the language in which to start the dialogue (e.g. 'en')
	 * @param timeZone The current time zone of the user (as IANA, e.g. 'Europe/Lisbon')
	 * @param delegateUser The user for which to execute the dialogue (leave empty if executing for
	 *                     the currently authenticated user)
	 * @param sessionId An optional identifier that is attached to the dialogue logs, allowing this
	 *                  dialogue session to be cross-referenced with external logs
	 * @return a {@link DialogueMessage} object containing the first step of the dialogue.
	 * @throws HttpException In case of a bad request, unauthorized user, or other service error.
	 */
	@Operation(
		summary = "Start the step-by-step execution of the dialogue identified by the given " +
				"parameters.",
		description = "A client application that wants to start executing a dialogue should use " +
			"this end-point to do so. The dialogueName (which is the dialogue's filename without " +
			"it's .dlb extension and language are mandatory parameters. The 'userId' is an " +
			"optional parameter that may be used if the currently authorized user is an admin " +
			"and wants to execute a dialogue on behalf of another user. If the authenticated " +
			"user is running a dialogue 'for himself' this should be left empty.")
	@RequestMapping(value="/start", method= RequestMethod.POST)
	public DialogueMessage startDialogue(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "Name of the project that contains the dialogue to start")
		@RequestParam(value="projectName")
		String projectName,

		@Parameter(description = "Name of the DialogueBranch Dialogue to start (excluding .dlb)")
		@RequestParam(value="dialogueName")
		String dialogueName,

		@Parameter(description = "Language code of the language in which to start the dialogue " +
				"(e.g. 'en')")
		@RequestParam(value="language")
		String language,

		@Parameter(description = "The current time zone of the user (as IANA, e.g. " +
				"'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "The user for which to execute the dialogue (leave empty if " +
				"executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required = false)
		String delegateUser,

		@Parameter(description = "An optional identifier that is attached to the dialogue logs, " +
				"allowing this dialogue session to be cross-referenced with external logs")
		@RequestParam(value="sessionId", required = false)
		String sessionId
	) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/dialogue/start?projectName=" + projectName +
				"&dialogueName=" + dialogueName + "&language=" + language + "&timeZone=" + timeZone;
		if(delegateUser != null && !delegateUser.isEmpty())
			logInfo += "&delegateUser=" + delegateUser;
		if(sessionId != null && !sessionId.isEmpty()) logInfo += "&sessionId=" + sessionId;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
					(protocolVersion, authenticatedUser) -> doStartDialogue(authenticatedUser,
							projectName, dialogueName, language, timeZone, sessionId),
					version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		} else {
			return QueryRunner.runQuery(
					(protocolVersion, authenticatedUser) -> doStartDialogue(delegateUser,
							projectName, dialogueName, language, timeZone, sessionId),
					version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		}
	}

	/**
	 * Processes a call to the /dialogue/start end-point.
	 *
	 * @param userId the {@link String} identifier of the user for whom to start a dialogue.
	 * @param dialogueName the name of the dialogue to start executing
	 * @param language the language in which to start the dialogue
	 * @param timeZone the timeZone of the client as one of {@code TimeZone.getAvailableIDs()}
	 *                 (IANA Codes)
	 * @param sessionId the (optional) identifier that should be added to the logging of dialogues
	 *                  for this started dialogue session (which may be {@code null}).
	 * @return the {@link DialogueMessage} that represents the start node of the dialogue.
	 * @throws HttpException in case of an error in the dialogue execution.
	 * @throws DatabaseException in case of an error in retrieving the current active user.
	 * @throws IOException in case of any network error.
	 */
	private DialogueMessage doStartDialogue(String userId, String projectName, String dialogueName,
			String language, String timeZone, String sessionId)
			throws HttpException, IOException, DatabaseException {

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId, timeZoneId);
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		// If no sessionId was provided, generate a unique one now
		if(sessionId == null || sessionId.isEmpty()) {
            do {
                sessionId = UUID.randomUUID().toString().toLowerCase();
            } while (userService.existsSessionId(sessionId));

		// If a sessionId was provided, check its uniqueness (for this user), or generate error
		} else {
			if(userService.existsSessionId(sessionId)) {
				throw new BadRequestException("The provided sessionId is already in use. When " +
					"starting a new dialogue session with a predefined sessionId, this " +
					"identifier has to be unique for this user ('"+userId+"').");
			}
		}

		try {
			ExecuteNodeResult node = userService.startDialogueSession(
					projectName, dialogueName, null, language, sessionId, System.currentTimeMillis());
			return DialogueMessageFactory.generateDialogueMessage(node);
		} catch (ExecutionException e) {
			throw ControllerFunctions.createHttpException(e);
		}
	}

	// ------------------------------------------------------------------------- //
	// -------------------- END-POINT: "/dialogue/progress" -------------------- //
	// ------------------------------------------------------------------------- //

	/**
	 * End point that returns the next statement by the agent and its corresponding replies (based
	 * on the reply selected for the previous statement). The request body may contain a JSON object
	 * with variables from input segments.
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param loggedDialogueId The identifier of the (in-progress) dialogue to progress.
	 * @param loggedInteractionIndex The interaction index is the step in the dialogue execution
	 *                               from which to progress the dialogue.
	 * @param replyId the id of the reply that was selected for the previous statement.
	 * @param delegateUser The user for which to execute the dialogue (leave empty if executing for
	 *                   the currently authenticated user)
	 * @return a {@link NullableResponse} containing either a {@link DialogueMessage} or {@code
	 *         null}.
	 * @throws HttpException in case of a bad request, unauthorized user, or internal service error.
	 */
	@Operation(
		summary = "Progresses a given dialogue from a given state with a given reply id.",
		description = "The client application that wants to progress a previously started " +
			"dialogue should use this end-point to do so. The loggedDialogueId identifies " +
			"the ongoing dialogue (and will have been provided by a call to start-dialogue) " +
			"and the loggedInteractionIndex identifies the current step in the dialogue " +
			"execution (also provided previously). The replyId depicts the reply that the user " +
			"has chosen to progress the dialogue. The 'delegateUser' is an optional parameter " +
			"that may be used if the currently authorized user is an admin and wants to execute " +
			"a dialogue on behalf of another user (the delegateUser). If the authenticated user " +
			"is running a dialogue 'for himself' this should be left empty.")
	@RequestMapping(value="/progress", method=RequestMethod.POST)
	public NullableResponse<DialogueMessage> progressDialogue(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "The identifier of the (in-progress) dialogue to progress")
		@RequestParam(value="loggedDialogueId")
		String loggedDialogueId,

		@Parameter(description = "The interaction index is the step in the dialogue " +
				"execution from which to progress the dialogue")
		@RequestParam(value="loggedInteractionIndex")
		int loggedInteractionIndex,

		@Parameter(description = "The identifier of the reply that the user has chose to " +
				"progress the dialogue")
		@RequestParam(value="replyId")
		int replyId,

		@Parameter(description = "The user for which to execute the dialogue (leave empty " +
				"if executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false)
		String delegateUser
	) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/dialogue/progress?loggedDialogueId="
				+ loggedDialogueId + "&loggedInteractionIndex=" + loggedInteractionIndex
				+ "&replyId=" + replyId;
		if(!(delegateUser == null) && (!delegateUser.isEmpty()))
			logInfo += "&delegateUser="+delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
				(protocolVersion, authenticatedUser) -> doProgressDialogue(authenticatedUser, request,
						loggedDialogueId, loggedInteractionIndex, replyId),
				version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		} else {
			return QueryRunner.runQuery(
				(protocolVersion, authenticatedUser) -> doProgressDialogue(delegateUser, request,
					loggedDialogueId, loggedInteractionIndex, replyId),
				version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		}
	}

	/**
	 * Processes a call to the /dialogue/progress end-point.
	 *
	 * @param userId the user for which to execute the dialogue (leave empty or {@code null} if
	 *               executing for the currently authenticated user).
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param loggedDialogueId the identifier of the (in-progress) dialogue to progress.
	 * @param loggedInteractionIndex the interaction index is the step in the dialogue execution
	 *                               from which to progress the dialogue.
	 * @param replyId the identifier of the reply that the user has chosen to progress the dialogue.
	 * @return a {@link NullableResponse} containing either a {@link DialogueMessage} or {@code
	 *         null}.
	 * @throws HttpException in case of a network error.
	 * @throws DatabaseException in case of an error retrieving the current dialogue state.
	 * @throws IOException in case of a network error.
	 */
	private NullableResponse<DialogueMessage> doProgressDialogue(String userId,
			HttpServletRequest request, String loggedDialogueId,
			int loggedInteractionIndex, int replyId) throws HttpException, DatabaseException,
			IOException {

		String body;
		try (InputStream input = request.getInputStream()) {
			body = FileUtils.readFileString(input);
		}
		Map<String,?> variables = new LinkedHashMap<>();
		if (!body.trim().isEmpty()) {
			try {
				variables = JsonMapper.parse(body,
						new TypeReference<>() {
						});
			} catch (ParseException ex) {
				throw new BadRequestException(
						"Request body is not a JSON object: " + ex.getMessage());
			}
		}
		try {
			UserService userService
					= application.getApplicationManager().getActiveUserService(userId);
			if(userService == null) {
				throw new BadRequestException("Attempting to progress a dialogue for a user ('" +
					userId + "') that isn't active. A session of interaction should start with a " +
					"call to the 'start' or 'continue' end-points.");
			}

			ZonedDateTime eventTime = DateTimeUtils.nowMs(userService.getDialogueBranchUser()
					.getTimeZone());

			// If variable data has been received in the progress call, update the values in the
			// Dialogue Branch Variable Store
			if (!variables.isEmpty()) userService.storeReplyInput(variables,eventTime);

			DialogueState state = userService.getDialogueState(loggedDialogueId,
					loggedInteractionIndex);
			ExecuteNodeResult nextNode = userService.progressDialogueSession(state, replyId);
			if (nextNode == null)
				return new NullableResponse<>(null);
			DialogueMessage reply = DialogueMessageFactory.generateDialogueMessage(nextNode);
			return new NullableResponse<>(reply);
		} catch (ExecutionException e) {
			throw ControllerFunctions.createHttpException(e);
		}
	}

	// ------------------------------------------------------------------------- //
	// -------------------- END-POINT: "/dialogue/continue" -------------------- //
	// ------------------------------------------------------------------------- //

	/**
	 * Continue the latest ongoing dialogue with a given name.
	 *
	 * <p>Pick up the conversation by providing a dialogue name. If there is an ongoing dialogue
	 * with the given name (that is not finished or canceled), this method will return the next
	 * step in that conversation. As with all methods that 'start' dialogue executions, a valid time
	 * zone in which the user currently resided must be provided so that time sensitive information
	 * may be processed correctly.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param dialogueName Name of the Dialogue Branch Dialogue to continue (excluding .dlb)
	 * @param timeZone The current time zone of the user (as IANA, e.g. 'Europe/Lisbon')
	 * @param delegateUser The user for which to continue executing the dialogue (leave empty if
	 *                     executing for the currently authenticated user)
	 * @return a {@link NullableResponse} object, containing either a {@link DialogueMessage} or
	 *         {@code null}.
	 * @throws HttpException In case of a bad request, unauthorized user, or internal service error.
	 */
	@Operation(
		summary = "Continue the latest ongoing dialogue with a given name.",
		description = "Pick up the conversation by providing a dialogue name. If there is an " +
			"ongoing dialogue with the given name (that is not finished or cancelled), this " +
			"method will return the next step in that conversation. As with all methods that " +
			"'start' dialogue executions, a valid time zone in which the user currently resided " +
			"must be provided so that time sensitive information may be processed correctly.")
	@RequestMapping(value="/continue", method=RequestMethod.POST)
	public NullableResponse<DialogueMessage> continueDialogue(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "Name of the project that contains the dialogue to continue")
		@RequestParam(value="projectName")
		String projectName,

		@Parameter(description = "Name of the DialogueBranch Dialogue to continue (excluding .dlb)")
		@RequestParam(value="dialogueName")
		String dialogueName,

		@Parameter(description = "The current time zone of the user (as IANA, e.g. " +
				"'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "The user for which to continue executing the dialogue (leave " +
				"empty if executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false, defaultValue="")
		String delegateUser
	) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/dialogue/continue?projectName=" + projectName +
				"&dialogueName=" + dialogueName + "&timeZone=" + timeZone;
		if(!(delegateUser == null) && (!delegateUser.isEmpty()))
			logInfo += "&delegateUser="+delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
					(protocolVersion, authenticatedUser) -> doContinueDialogue(authenticatedUser,
							projectName, dialogueName, timeZone),
					version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		} else {
			return QueryRunner.runQuery(
					(protocolVersion, authenticatedUser) ->
							doContinueDialogue(delegateUser, projectName, dialogueName, timeZone),
					version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		}
	}

	/**
	 * Processes a call to the /dialogue/continue end-point.
	 *
	 * @param userId the user for which to continue executing the dialogue (leave empty if
	 *               executing for the currently authenticated user).
	 * @param dialogueName name of the Dialogue Branch Dialogue to continue (excluding .dlb).
	 * @param timeZone the current time zone of the Dialogue Branch user (as IANA, e.g.
	 *                 'Europe/Lisbon').
	 * @return a {@link NullableResponse} object containing the {@link DialogueMessage} or {@code
	 *         null}.
	 * @throws HttpException in case of a network error.
	 * @throws DatabaseException in case of an error retrieving the ongoing dialogue from the
	 *                           database.
	 * @throws IOException in case of a file io error.
	 */
	private NullableResponse<DialogueMessage> doContinueDialogue(String userId, String projectName,
			String dialogueName, String timeZone)
			throws HttpException, DatabaseException, IOException {

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		// Determine the event timestamp
		ZonedDateTime continueDialogueEventTime =
				DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());

		ServerLoggedDialogue currentDialogue = userService.getLoggedDialogueStore().
				findLatestOngoingDialogue(projectName, dialogueName);
		LoggedInteraction lastInteraction = null;
		if (currentDialogue != null && !currentDialogue.getInteractionList().isEmpty()) {
			lastInteraction = currentDialogue.getInteractionList().get(
					currentDialogue.getInteractionList().size() - 1);
		}

		if (lastInteraction != null && lastInteraction.getMessageSource() ==
				MessageSource.AGENT) {
			ExecuteNodeResult node;
			try {
				DialogueState state = userService.getDialogueState(currentDialogue,
						currentDialogue.getInteractionList().size() - 1);
				node = userService.continueDialogueSession(state,continueDialogueEventTime);
			} catch (ExecutionException ex) {
				throw ControllerFunctions.createHttpException(ex);
			}
			DialogueMessage result =
					DialogueMessageFactory.generateDialogueMessage(node);
			return new NullableResponse<>(result);
		} else {
			return new NullableResponse<>(null);
		}
	}

	// ----------------------------------------------------------------------- //
	// -------------------- END-POINT: "/dialogue/cancel" -------------------- //
	// ----------------------------------------------------------------------- //

	/**
	 * Cancels a dialogue that is currently in progress, terminating its execution state.
	 *
	 * <p>If a client application detects that a user has navigated away, or has deliberately
	 * requested to stop an ongoing dialogue through a user interface action, this end-point should
	 * be called to explicitly mark the dialogue as canceled.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param loggedDialogueId The identifier of the (in-progress) dialogue to cancel
	 * @param delegateUser The user for which to cancel the dialogue (leave empty if executing for
	 *                     the currently authenticated user)
	 * @throws HttpException In case of a bad request, unauthorized user, or internal service error.
	 */
	@Operation(
		summary = "Cancels a dialogue that is currently in progress, terminating its execution " +
				"state.",
		description = "If a client application detects that a user has navigated away, or has " +
			"deliberately requested to stop an ongoing dialogue through a user interface action, " +
			"this end-point should be called so that the dialogue's state can be updated, " +
			"indicating that it is no longer ongoing.")
	@RequestMapping(value="/cancel", method=RequestMethod.POST)
	public void cancelDialogue(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "The identifier of the (in-progress) dialogue to cancel")
		@RequestParam(value="loggedDialogueId")
		String loggedDialogueId,

		@Parameter(description = "The user for which to cancel the dialogue (leave empty if " +
				"executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false)
		String delegateUser) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/dialogue/cancel?loggedDialogueId="
				+ loggedDialogueId;
		if(!(delegateUser == null) && (!delegateUser.isEmpty())) logInfo += "&delegateUser="
				+ delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			QueryRunner.runQuery((protocolVersion, authenticatedUser) -> doCancelDialogue(authenticatedUser,
				loggedDialogueId), version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		} else {
			QueryRunner.runQuery((protocolVersion, authenticatedUser) -> doCancelDialogue(delegateUser,
				loggedDialogueId), version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		}
	}

	/**
	 * Processes a call to the /dialogue/cancel end-point.
	 *
	 * @param userId the user for which to cancel the dialogue (leave empty or {@code null} if
	 *               executing for the currently authenticated user).
	 * @param loggedDialogueId the identifier of the (in-progress) dialogue to cancel.
	 * @return {@code null}
	 * @throws DatabaseException in case of an error in retrieving the specified dialogue.
	 * @throws IOException in case of any network error.
	 * @throws BadRequestException if attempting to cancel a dialogue for a user that isn't active
	 */
	private Object doCancelDialogue(String userId, String loggedDialogueId)
            throws DatabaseException, IOException, BadRequestException {

		UserService userService
				= application.getApplicationManager().getActiveUserService(userId);
		if(userService == null) {
			throw new BadRequestException("Attempting to cancel a dialogue for a user ('" +
					userId + "') that isn't active. A session of interaction should start with a " +
					"call to the 'start' or 'continue' end-points.");
		}

		userService.cancelDialogueSession(loggedDialogueId);
		return null;
	}

	// --------------------------------------------------------------------- //
	// -------------------- END-POINT: "/dialogue/back" -------------------- //
	// --------------------------------------------------------------------- //

	/**
	 * Go back to the previous step in an ongoing dialogue.
	 *
	 * <p>Use this end-point by providing a loggedDialogueId (specifying an ongoing dialogue) and
	 * the loggedInteractionIndex (identifying the current step in the dialogue). This end-point
	 * will return the previous dialogue step (based on the loggedInteractionIndex) by providing
	 * that previous DialogueMessage.</p>
	 *
	 * <p><b>Caution: Using this method takes the dialogue back to the previous step as if there was
	 * a regular reply option leading back to that step, but it will not undo any variable
	 * operations (i.e. setting Dialogue Branch Variables) that may have occurred in the execution
	 * of the current step. This may lead to unexpected results if the execution of the 'previous'
	 * dialogue step is affected by variables set in the 'current' step.</b></p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param loggedDialogueId The identifier of the (in-progress) dialogue to take a step back in
	 * @param loggedInteractionIndex The interaction index is the step in the dialogue execution
	 *                               from which to take a step back in the dialogue
	 * @param delegateUser The user for which to take a step back in the dialogue (leave empty if
	 *                     executing for the currently authenticated user)
	 * @return a {@link DialogueMessage} containing the previous dialogue step
	 * @throws HttpException in case of a bad request, unauthorized user, or internal service error.
	 */
	@Operation(
		summary = "Go back to the previous step in an ongoing dialogue.",
		description = "Use this end-point by providing a loggedDialogueId (specifying an ongoing " +
			"dialogue) and the loggedInteractionIndex (identifying the current step in the " +
			"dialogue). This end-point will return the previous dialogue step (based on the " +
			"loggedInteractionIndex) by providing that previous DialogueMessage. " +
			"<br/><br/><b>Caution: Using this method takes the dialogue back to the previous " +
			"step as if there was a regular reply option leading back to that step, but it will " +
			"not undo any variable operations (i.e. setting Dialogue Branch Variables) that may " +
			"have occurred in the execution of the current step. This may lead to unexpected " +
			"results if the execution of the 'previous' dialogue step is affected by variables " +
			"set in the 'current' step.</b>")
	@RequestMapping(value="/back", method=RequestMethod.POST)
	public DialogueMessage backDialogue(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "The identifier of the (in-progress) dialogue to take a step " +
				"back in")
		@RequestParam(value="loggedDialogueId")
		String loggedDialogueId,

		@Parameter(description = "The interaction index is the step in the dialogue execution " +
				"from which to take a step back in the dialogue")
		@RequestParam(value="loggedInteractionIndex")
		int loggedInteractionIndex,

		@Parameter(description = "The user for which to take a step back in the dialogue " +
				"(leave empty if executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false)
		String delegateUser
	) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/dialogue/back?loggedDialogueId="
				+ loggedDialogueId + "&loggedInteractionIndex=" + loggedInteractionIndex;
		if(!(delegateUser == null) && (!delegateUser.isEmpty())) logInfo += "&delegateUser="
				+ delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
				(protocolVersion, authenticatedUser) -> doBackDialogue(authenticatedUser, loggedDialogueId,
						loggedInteractionIndex),
				version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		} else {
			return QueryRunner.runQuery(
				(protocolVersion, authenticatedUser) -> doBackDialogue(delegateUser, loggedDialogueId,
						loggedInteractionIndex),
				version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		}
	}

	/**
	 * Processes a call to the /dialogue/back-dialogue end-point.
	 *
	 * @param userId the user for which to take a step back in the dialogue (leave empty if
	 *               executing for the currently authenticated user).
	 * @param loggedDialogueId the identifier of the (in-progress) dialogue to take a step back in.
	 * @param loggedInteractionIndex the interaction index is the step in the dialogue execution
	 *                               from which to take a step back in the dialogue.
	 * @return a {@link DialogueMessage} object depicting the previous step in the given ongoing
	 *         dialogue.
	 * @throws HttpException in case of any network error.
	 * @throws DatabaseException in case of an error retrieving the ongoing dialogue from the
	 *                           database.
	 * @throws IOException in case of a file IO error.
	 */
	private DialogueMessage doBackDialogue(String userId, String loggedDialogueId,
										   int loggedInteractionIndex)
			throws HttpException, DatabaseException, IOException {

		try {
			UserService userService
					= application.getApplicationManager().getActiveUserService(userId);
			if(userService == null) {
				throw new BadRequestException("Attempting to take a step back a dialogue for a " +
					"user ('" + userId + "') that isn't active. A session of interaction should " +
					"start with a call to the 'start' or 'continue' end-points.");
			}

			// Determine the event time stamp
			ZonedDateTime backDialogueEventTime =
					DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());

			DialogueState state = userService.getDialogueState(loggedDialogueId,
					loggedInteractionIndex);
			ExecuteNodeResult prevNode = userService.revertDialogueSession(state,
					backDialogueEventTime);
			return DialogueMessageFactory.generateDialogueMessage(prevNode);
		} catch (ExecutionException e) {
			throw ControllerFunctions.createHttpException(e);
		}
	}

	// ---------------------------------------------------------------------------- //
	// -------------------- END-POINT: "/dialogue/get-ongoing" -------------------- //
	// ---------------------------------------------------------------------------- //

	/**
	 * Get information about the latest ongoing dialogue for a given user.
	 *
	 * <p>This end-point answers the question 'was there any unfinished business? and if so, how
	 * long ago?'. As a client application, you may want to call this end-point at the start of a
	 * session to see if there was an ongoing dialogue left over from a previous session. If so, you
	 * will get the dialogue-name and the time (in seconds) since the last 'engagement' with that
	 * dialogue (the last time since either the user or the agent said something). If this wasn't
	 * too long ago, you may decide to continue the conversation by passing the dialogue name to the
	 * /dialogues/continue end-point.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @param timeZone The current time zone of the user (as IANA, e.g. 'Europe/Lisbon')
	 * @param delegateUser The user for which to retrieve the latest ongoing dialogue information
	 *                     (leave empty if retrieving for the currently authenticated user)
	 * @return a {@link NullableResponse} containing an {@link OngoingDialoguePayload} or {@code
	 *         null}
	 * @throws HttpException in case of a bad request, unauthorized user, or internal service error.
	 */
	@Operation(
		summary = "Get information about the latest ongoing dialogue for a given user.",
		description = "This end-point answers the question 'was there any unfinished business? " +
			"and if so, how long ago?'. As a client application, you may want to call this " +
			"end-point at the start of a session to see if there was an ongoing dialogue left " +
			"over from a previous session. If so, you will get the dialogue-name and the time " +
			"(in seconds) since the last 'engagement' with that dialogue (the last time since " +
			"either the user or the agent said something). If this wasn't too long ago, you may " +
			"decide to continue the conversation by passing the dialogue name to the " +
			"/dialogues/continue end-point.")
	@RequestMapping(value="/get-ongoing", method=RequestMethod.GET)
	public NullableResponse<OngoingDialoguePayload> getOngoingDialogue(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "The name of the project for which to check for an ongoing dialogue")
		@RequestParam(value="projectName")
		String projectName,

		@Parameter(description = "The current time zone of the user (as IANA, e.g. " +
				"'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "The user for which to retrieve the latest ongoing dialogue " +
				"information (leave empty if retrieving for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false, defaultValue="")
		String delegateUser
	) throws HttpException {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "GET /v" + version + "/dialogue/get-ongoing?projectName=" + projectName
				+ "&timeZone=" + timeZone;
		if(!(delegateUser == null) && (!delegateUser.isEmpty())) logInfo += "&delegateUser="
				+ delegateUser;
		logger.info(logInfo);

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
					(protocolVersion, authenticatedUser) -> doGetOngoingDialogue(authenticatedUser, projectName, timeZone),
					version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		} else {
			return QueryRunner.runQuery(
					(protocolVersion, authenticatedUser) -> doGetOngoingDialogue(delegateUser, projectName, timeZone),
					version, accessToken, response, delegateUser, application, AuthenticationInfo.USER_ROLE_CLIENT, AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
		}
	}

	/**
	 * Processes a call to the /dialogue/get-ongoing end-point.
	 *
	 * @param userId the user for which to retrieve the latest ongoing dialogue information
	 *               (leave empty if retrieving for the currently authenticated user).
	 * @param timeZone the timeZone of the client as one of {@code TimeZone.getAvailableIDs()}
	 * 	               (IANA Codes)
	 * @return a {@link NullableResponse} containing either a {@link OngoingDialoguePayload} object
	 *         or {@code null}.
	 * @throws DatabaseException in case of an error retrieving logged dialogues from the database.
	 * @throws IOException in case of any network error.
	 * @throws BadRequestException in case of a malformed or unknown {@code timeZone}
	 */
	private NullableResponse<OngoingDialoguePayload> doGetOngoingDialogue(String userId,
																		  String projectName,
																		  String timeZone)
            throws DatabaseException, IOException, BadRequestException {

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		ServerLoggedDialogue latestOngoingDialogue =
				userService.getLoggedDialogueStore().findLatestOngoingDialogueInProject(projectName);

		if(latestOngoingDialogue != null) {
			String dialogueName = latestOngoingDialogue.getDialogueName();
			long latestInteractionTimestamp = latestOngoingDialogue.getLatestInteractionTimestamp();
			long secondsSinceLastEngagement =
					(long) Math.floor((System.currentTimeMillis() -
							latestInteractionTimestamp) / 1000.0);
			OngoingDialoguePayload ongoingDialoguePayload =
					new OngoingDialoguePayload(dialogueName, latestOngoingDialogue.getId(),
							secondsSinceLastEngagement);
			return new NullableResponse<>(ongoingDialoguePayload);
		} else {
			return new NullableResponse<>(null);
		}
	}

	// ------------------------------------------------------------------------------ //
	// -------------------- END-POINT: "/dialogue/list-dialogues" -------------------- //
	// ------------------------------------------------------------------------------ //

	/**
	 * Retrieve a list of all available dialogues in the Web Service.
	 *
	 * <p>This method returns a JSON object encapsulating a list of all dialogue names that are
	 * hosted by the running instance of this Dialogue Branch Web Service.</p>
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version The API Version to use, e.g. '1'.
	 * @return a {@link DialogueListPayload} object containing a list of all available dialogues
	 *         in the Web Service.
	 * @throws UnauthorizedException in case the logged-in user does not have the 'editor' or
	 *                               'admin' role.
	 */
	@Operation(
		summary = "Retrieve a list of all available dialogues in the Web Service.",
		description = "This method returns a JSON object encapsulating a list of all dialogue " +
			"names available in the specified project. Accessible for users with the 'editor' " +
			"or 'admin' role.")
	@RequestMapping(value="/list-dialogues", method=RequestMethod.GET)
	public DialogueListPayload listDialogues(
		HttpServletRequest request,
		HttpServletResponse response,

        @PathVariable @Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		String version,

		@Parameter(description = "Name of the project to list dialogues for")
		@RequestParam(value="projectName")
		String projectName
	) throws UnauthorizedException {

		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		String logInfo = "GET /v" + version + "/dialogue/list-dialogues?projectName=" + projectName;
		logger.info(logInfo);

		AuthenticationInfo authenticationInfo = QueryRunner.validateAccessToken(
				ControllerFunctions.extractAccessToken(request), application);
		if (authenticationInfo.hasRole(AuthenticationInfo.USER_ROLE_EDITOR)
				|| authenticationInfo.hasRole(AuthenticationInfo.USER_ROLE_ADMIN)) {
			return doListDialogues(projectName);
		} else {
			throw new UnauthorizedException(ErrorCode.INSUFFICIENT_PRIVILEGES,
				"This user does not have the rights to access this function.");
		}
	}

	private DialogueListPayload doListDialogues(String projectName) {
		List<ResourcePointer> resources =
				application.getApplicationManager().getAvailableDialoguesForProject(projectName);
		List<String> scriptNames = new ArrayList<>();
		for (ResourcePointer resourcePointer : resources) {
			if (resourcePointer.getResourceType().equals(ResourceType.SCRIPT)) {
				scriptNames.add(resourcePointer.getDialogueName());
			}
		}
		return new DialogueListPayload(scriptNames.toArray(new String[0]));
	}

}

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
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.DialogueMessageFactory;
import com.dialoguebranch.model.execute.protocol.NullableResponse;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.controller.schema.DraftDialogueMessage;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.execution.DraftExecutionService;
import com.dialoguebranch.web.service.execution.DraftTestSession;
import com.dialoguebranch.web.service.execution.UserService;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.project.ProjectService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.io.FileUtils;
import nl.rrd.utils.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for the {@code /draft/...} end-points of the Dialogue Branch Web Service. These
 * end-points let authorised users (with the {@code editor} or {@code admin} role) test-run a
 * draft dialogue's current content without requiring it to be published first.
 *
 * <p>This is a deliberately separate, ephemeral execution path — see
 * {@link DraftExecutionService} — and does not touch the published-dialogue execution end-points
 * under {@code /dialogue/*}, nor {@link com.dialoguebranch.web.service.storage.LoggedDialogueStore}.
 * Draft test sessions do read and write the tester's real Dialogue Branch variables, but every
 * change can be undone via {@code /draft/revert-variables}.</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/draft", "/draft"})
@Tag(name = "9. Draft Execution", description = "End-points for test-running draft dialogues " +
		"without requiring them to be published first.")
public class DraftExecutionController {

	@Autowired
	Application application;

	private final ProjectService projectService;
	private final DraftDialogueService draftDialogueService;
	private final DraftExecutionService draftExecutionService;

	private static final Logger logger = LoggerFactory.getLogger(DraftExecutionController.class);

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param projectService service used to look up the {@link DBProject} that a draft dialogue
	 *                       test session belongs to.
	 * @param draftDialogueService service used to look up the {@link DBDraftDialogue} being
	 *                             test-run.
	 * @param draftExecutionService service that drives the ephemeral draft test session itself
	 *                              (start, progress, cancel, revert).
	 */
	public DraftExecutionController(ProjectService projectService,
									DraftDialogueService draftDialogueService,
									DraftExecutionService draftExecutionService) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
		this.draftExecutionService = draftExecutionService;
	}

	/**
	 * Starts a test-run of a draft dialogue's current (unpublished) content, creating a new
	 * ephemeral draft test session.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to test-run.
	 * @param language the language code to test-run the dialogue in.
	 * @param timeZone the current time zone of the user (as IANA, e.g. 'Europe/Lisbon').
	 * @param startNodeId an optional node title to start the test-run from, instead of the
	 *                    dialogue's default start node.
	 * @return the draft session id and the first {@link DialogueMessage} of the test-run.
	 * @throws HttpException if the project or dialogue does not exist, execution fails, or the
	 * user is not authorized.
	 */
	@Operation(summary = "Start a test-run of a draft dialogue's current content.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/start")
	public DraftDialogueMessage start(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "language") String language,
			@RequestParam(value = "timeZone") String timeZone,
			@RequestParam(value = "startNodeId", required = false) String startNodeId
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/draft/start [user: {}]", version, user);
					DBProject project = projectService.findBySlug(projectSlug)
							.orElseThrow(() -> new NotFoundException(
									"Project not found: " + projectSlug));
					DBDraftDialogue dialogue = draftDialogueService
							.findDialogue(project, dialogueName)
							.orElseThrow(() -> new NotFoundException(
									"Dialogue not found: " + dialogueName));
					AuthoringController.checkNotDeleted(dialogue);

					ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
					UserService userService = application.getApplicationManager()
							.getOrCreateActiveUserService(user, timeZoneId);

					DraftExecutionService.StartResult result;
					try {
						result = draftExecutionService.startSession(userService, project, dialogue,
								language, startNodeId);
					} catch (ExecutionException e) {
						throw ControllerFunctions.createHttpException(e);
					}
					DialogueMessage message =
							DialogueMessageFactory.generateDialogueMessage(result.executeNodeResult());
					return new DraftDialogueMessage(result.sessionId(), message);
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Progresses a draft test session by submitting the reply chosen by the tester, along with
	 * any variable updates collected on the client since the previous step.
	 *
	 * @param request the HTTP request (to retrieve authentication headers and the JSON body of
	 *                variable updates).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param draftSessionId the id of the draft test session to progress, as returned by
	 *                       {@link #start}.
	 * @param replyId the id of the reply chosen by the tester.
	 * @param timeZone the current time zone of the user (as IANA, e.g. 'Europe/Lisbon').
	 * @return the next {@link DialogueMessage} of the test-run, or a {@code null}-wrapping
	 * response if the dialogue has ended.
	 * @throws HttpException if the request body is not valid JSON, the session does not exist,
	 * execution fails, or the user is not authorized.
	 */
	@Operation(summary = "Progress a draft test session with a given reply id.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/progress")
	public NullableResponse<DialogueMessage> progress(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "draftSessionId") String draftSessionId,
			@RequestParam(value = "replyId") int replyId,
			@RequestParam(value = "timeZone") String timeZone
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/draft/progress [user: {}]", version, user);

					String body;
					try (InputStream input = request.getInputStream()) {
						body = FileUtils.readFileString(input);
					}
					Map<String, ?> variables = new LinkedHashMap<>();
					if (!body.trim().isEmpty()) {
						try {
							variables = JsonMapper.parse(body, new TypeReference<>() { });
						} catch (ParseException ex) {
							throw new BadRequestException(
									"Request body is not a JSON object: " + ex.getMessage());
						}
					}

					DraftTestSession session = draftExecutionService.getSession(draftSessionId);
					ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
					UserService userService = application.getApplicationManager()
							.getOrCreateActiveUserService(user, timeZoneId);
					ZonedDateTime eventTime = DateTimeUtils.nowMs(
							userService.getDialogueBranchUser().getTimeZone());

					ExecuteNodeResult nextNode;
					try {
						nextNode = draftExecutionService.progressSession(session, replyId, variables,
								eventTime);
					} catch (ExecutionException e) {
						throw ControllerFunctions.createHttpException(e);
					}

					if (nextNode == null) return new NullableResponse<>(null);
					return new NullableResponse<>(
							DialogueMessageFactory.generateDialogueMessage(nextNode));
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Cancels a draft test session, keeping any variable changes made during it.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param draftSessionId the id of the draft test session to cancel, as returned by
	 *                       {@link #start}.
	 * @throws HttpException if the session does not exist or the user is not authorized.
	 */
	@Operation(summary = "Cancel a draft test session, keeping any variable changes made during it.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "draftSessionId") String draftSessionId
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/draft/cancel [user: {}]", version, user);
					DraftTestSession session = draftExecutionService.getSession(draftSessionId);
					draftExecutionService.cancelSession(session);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

	/**
	 * Cancels a draft test session and reverts any variable changes made during it back to
	 * their values from before the session started.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param draftSessionId the id of the draft test session to cancel, as returned by
	 *                       {@link #start}.
	 * @param timeZone the current time zone of the user (as IANA, e.g. 'Europe/Lisbon').
	 * @throws HttpException if the session does not exist or the user is not authorized.
	 */
	@Operation(summary = "Cancel a draft test session and revert any variable changes made " +
			"during it back to their values from before the session started.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/revert-variables")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revertVariables(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "draftSessionId") String draftSessionId,
			@RequestParam(value = "timeZone") String timeZone
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/draft/revert-variables [user: {}]", version, user);
					DraftTestSession session = draftExecutionService.getSession(draftSessionId);
					ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
					UserService userService = application.getApplicationManager()
							.getOrCreateActiveUserService(user, timeZoneId);
					draftExecutionService.revertVariables(session, userService);
					return null;
				},
				version, ControllerFunctions.extractAccessToken(request), response, "", application,
				AuthenticationInfo.USER_ROLE_EDITOR, AuthenticationInfo.USER_ROLE_ADMIN);
	}

}

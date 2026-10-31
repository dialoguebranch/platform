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
package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.json.JsonMapper;
import com.dialoguebranch.model.common.VariableNames;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.DialogueMessageFactory;
import com.dialoguebranch.model.execute.protocol.NullableResponse;
import com.dialoguebranch.web.service.DateTimeUtils;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.controller.schema.TestSessionMessage;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.HttpException;
import com.dialoguebranch.web.service.exception.HttpFieldError;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.execution.TestSession;
import com.dialoguebranch.web.service.execution.TestSessionService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the {@code /test-session/...} end-points of the Dialogue Branch Web Service.
 * These end-points let authorised users (with the {@code editor} or {@code admin} role) test-run
 * a dialogue: its current draft content without requiring it to be published first, or its
 * published content (#411).
 *
 * <p>This is a deliberately separate, ephemeral execution path — see
 * {@link TestSessionService} — and does not touch the published-dialogue execution end-points
 * under {@code /dialogue/*}, nor {@link com.dialoguebranch.web.service.storage.LoggedDialogueStore}.
 * Test sessions run against the tester's own copy of the variables (#319), never the user's real
 * ones: {@code /test-session/variables}, {@code /test-session/set-variable} and
 * {@code /test-session/reset} read and change that copy, addressed by project (and
 * {@code delegateUser} when the copy is someone else's).</p>
 *
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/test-session", "/test-session"})
@Tag(name = "9. Test Sessions", description = "End-points for test-running draft dialogues " +
		"without requiring them to be published first, against a copy of the variables.")
public class TestSessionController {

	private final ProjectService projectService;
	private final DraftDialogueService draftDialogueService;
	private final TestSessionService testSessionService;

	private static final Logger logger = LoggerFactory.getLogger(TestSessionController.class);

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param projectService service used to look up the {@link DBProject} that a test session
	 *                       belongs to.
	 * @param draftDialogueService service used to look up the {@link DBDraftDialogue} being
	 *                             test-run.
	 * @param testSessionService service that drives the ephemeral test sessions and their copies
	 *                           of the variables.
	 */
	public TestSessionController(ProjectService projectService,
			DraftDialogueService draftDialogueService, TestSessionService testSessionService) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
		this.testSessionService = testSessionService;
	}

	/**
	 * Starts a test-run of a dialogue, creating a new ephemeral test session: of its current
	 * (unpublished) draft content, or with {@code content=published} of its latest published
	 * version (#411).
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @param dialogueName the name of the draft dialogue to test-run.
	 * @param language the language code to test-run the dialogue in.
	 * @param timeZone the current time zone of the tester (as IANA, e.g. 'Europe/Lisbon').
	 * @param startNodeId an optional node title to start the test-run from, instead of the
	 *                    dialogue's default start node.
	 * @param content the content to run: {@code draft} (the default) or {@code published}.
	 * @param delegateUser the user whose variables the test starts from (leave empty for the
	 *                     tester's own; only admins may name another user). Their variables are
	 *                     only read, to seed the tester's copy.
	 * @return the test session id and the first {@link DialogueMessage} of the test-run.
	 * @throws HttpException if the project, dialogue or delegated user does not exist, execution
	 * fails, or the user is not authorized.
	 */
	@Operation(summary = "Start a test-run of a dialogue's draft or published content.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/start")
	public TestSessionMessage start(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "dialogueName") String dialogueName,
			@RequestParam(value = "language") String language,
			@RequestParam(value = "timeZone") String timeZone,
			@RequestParam(value = "startNodeId", required = false) String startNodeId,
			@Parameter(description = "The content to run: 'draft' (the default), the current " +
					"unpublished content, or 'published', the latest published version")
			@RequestParam(value = "content", required = false, defaultValue = "draft")
			String content,
			@Parameter(description = "The user whose variables the test starts from (leave " +
					"empty for your own)")
			@RequestParam(value = "delegateUser", required = false) String delegateUser
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user, delegated) -> {
					logger.info("POST /v{}/test-session/start?content={} [user: {}]", version,
							content, user);
					if (!content.equals("draft") && !content.equals("published")) {
						throw BadRequestException.withInvalidInput(List.of(new HttpFieldError(
								"content", "Invalid content: " + content +
										" (expected 'draft' or 'published')")));
					}
					DBProject project = findProject(projectSlug);
					ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);

					TestSessionService.StartResult result;
					try {
						if (content.equals("published")) {
							result = testSessionService.startPublishedSession(tester(), user,
									delegated, project, dialogueName, language, startNodeId,
									timeZoneId);
						} else {
							DBDraftDialogue dialogue = draftDialogueService
									.findDialogue(project, dialogueName)
									.orElseThrow(() -> new NotFoundException(
											"Dialogue not found: " + dialogueName));
							AuthoringController.checkNotDeleted(dialogue);
							result = testSessionService.startSession(tester(), user, delegated,
									project, dialogue, language, startNodeId, timeZoneId);
						}
					} catch (ExecutionException e) {
						throw ControllerFunctions.createHttpException(e);
					}
					DialogueMessage message =
							DialogueMessageFactory.generateDialogueMessage(result.executeNodeResult());
					return new TestSessionMessage(result.sessionId(), message);
				},
				version, response, delegateUser, Permission.DIALOGUE_TEST);
	}

	/**
	 * Progresses a test session by submitting the reply chosen by the tester, along with any
	 * variable updates collected on the client since the previous step.
	 *
	 * @param request the HTTP request (to retrieve authentication headers and the JSON body of
	 *                variable updates).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param testSessionId the id of the test session to progress, as returned by {@link #start}.
	 * @param replyId the id of the reply chosen by the tester.
	 * @param timeZone the current time zone of the tester (as IANA, e.g. 'Europe/Lisbon').
	 * @return the next {@link DialogueMessage} of the test-run, or a {@code null}-wrapping
	 * response if the dialogue has ended.
	 * @throws HttpException if the request body is not valid JSON, the session does not exist,
	 * the session belongs to another tester, execution fails, or the user is not authorized.
	 */
	@Operation(summary = "Progress a test session with a given reply id.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/progress")
	public NullableResponse<DialogueMessage> progress(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "testSessionId") String testSessionId,
			@RequestParam(value = "replyId") int replyId,
			@RequestParam(value = "timeZone") String timeZone
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/test-session/progress [user: {}]", version, user);

					String body;
					try (InputStream input = request.getInputStream()) {
						body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
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

					ControllerFunctions.requireAllowedVariables(variables);
					TestSession session = testSessionService.getSession(testSessionId, user);
					ZonedDateTime eventTime =
							DateTimeUtils.nowMs(ControllerFunctions.parseTimeZone(timeZone));

					ExecuteNodeResult nextNode;
					try {
						nextNode = testSessionService.progressSession(session, replyId, variables,
								eventTime);
					} catch (ExecutionException e) {
						throw ControllerFunctions.createHttpException(e);
					}

					if (nextNode == null) return new NullableResponse<>(null);
					return new NullableResponse<>(
							DialogueMessageFactory.generateDialogueMessage(nextNode));
				},
				version, response, "", Permission.DIALOGUE_TEST);
	}

	/**
	 * Ends a test session. Its copy of the variables is kept, for the tester's other sessions.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param testSessionId the id of the test session to end, as returned by {@link #start}.
	 * @throws HttpException if the session does not exist, the session belongs to another tester,
	 * or the user is not authorized.
	 */
	@Operation(summary = "End a test session.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "testSessionId") String testSessionId
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user) -> {
					logger.info("POST /v{}/test-session/cancel [user: {}]", version, user);
					testSessionService.cancelSession(
							testSessionService.getSession(testSessionId, user));
					return null;
				},
				version, response, "", Permission.DIALOGUE_TEST);
	}

	/**
	 * Returns the tester's copy of the variables of a project, sorted by name: the values test
	 * sessions run against. Seeds the copy from the user's stored variables if there is none yet.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project.
	 * @param timeZone the tester's time zone, used when this call creates the copy (the server's
	 *                 own if left out).
	 * @param delegateUser the user whose variables were copied (leave empty for the tester's own).
	 * @return the copy's variables.
	 * @throws HttpException if the project or delegated user does not exist, or the user is not
	 * authorized.
	 */
	@Operation(summary = "Get the copy of the variables that test sessions run against.")
	@Parameter(name = "version", hidden = true)
	@GetMapping("/variables")
	public List<Variable> variables(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@Parameter(description = "The tester's time zone (as IANA, e.g. 'Europe/Lisbon'), " +
					"used when this call creates the copy; the server's own if left out")
			@RequestParam(value = "timeZone", required = false) String timeZone,
			@Parameter(description = "The user whose variables were copied (leave empty for " +
					"your own)")
			@RequestParam(value = "delegateUser", required = false) String delegateUser
	) throws HttpException {
		return QueryRunner.runQuery(
				(protocolVersion, user, delegated) -> {
					logger.info("GET /v{}/test-session/variables [user: {}]", version, user);
					VariableStore copy = testSessionService.getCopy(tester(), user, delegated,
							findProject(projectSlug), ControllerFunctions.parseTimeZone(timeZone));
					return Arrays.stream(copy.getVariables())
							.sorted(Comparator.comparing(Variable::getName))
							.toList();
				},
				version, response, delegateUser, Permission.DIALOGUE_TEST);
	}

	/**
	 * Sets, or removes, a variable in the tester's copy of the variables of a project. The user's
	 * real variables are left alone.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project.
	 * @param name the name of the variable.
	 * @param value the new value, or none to remove the variable.
	 * @param timeZone the current time zone of the tester (as IANA, e.g. 'Europe/Lisbon').
	 * @param delegateUser the user whose variables were copied (leave empty for the tester's own).
	 * @throws HttpException if the variable name is invalid, the project or delegated user does
	 * not exist, or the user is not authorized.
	 */
	@Operation(summary = "Set or remove a variable in the copy that test sessions run against.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/set-variable")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setVariable(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@RequestParam(value = "name") String name,
			@RequestParam(value = "value", required = false) String value,
			@RequestParam(value = "timeZone") String timeZone,
			@Parameter(description = "The user whose variables were copied (leave empty for " +
					"your own)")
			@RequestParam(value = "delegateUser", required = false) String delegateUser
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user, delegated) -> {
					logger.info("POST /v{}/test-session/set-variable?name={} [user: {}]",
							version, name, user);
					if (!VariableNames.isValid(name)) {
						throw BadRequestException.withInvalidInput(List.of(
								new HttpFieldError("name", "Invalid variable name: " + name)));
					}
					Map<String, Object> single = new HashMap<>();
					single.put(name, value);
					ControllerFunctions.requireAllowedVariables(single);
					ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
					VariableStore copy = testSessionService.getCopy(tester(), user, delegated,
							findProject(projectSlug), timeZoneId);
					ZonedDateTime eventTime = DateTimeUtils.nowMs(timeZoneId);
					if (value == null) {
						copy.removeByName(name, false, eventTime, VariableUpdatedSource.WEB_SERVICE);
					} else {
						copy.setValue(name, value, false, eventTime,
								VariableUpdatedSource.WEB_SERVICE);
					}
					return null;
				},
				version, response, delegateUser, Permission.DIALOGUE_TEST);
	}

	/**
	 * Makes the tester's copy of the variables of a project identical to the user's stored
	 * variables again. Test sessions running against it continue with the reset values.
	 *
	 * @param request the HTTP request (to retrieve authentication headers).
	 * @param response the HTTP response (to add header WWW-Authenticate in case of a 401
	 *                 Unauthorized error).
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the unique slug of the project.
	 * @param delegateUser the user whose variables were copied (leave empty for the tester's own).
	 * @throws HttpException if the project or delegated user does not exist, or the user is not
	 * authorized.
	 */
	@Operation(summary = "Reset the copy that test sessions run against to the stored variables.")
	@Parameter(name = "version", hidden = true)
	@PostMapping("/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reset(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden = true) @PathVariable(value = "version") String version,
			@RequestParam(value = "projectSlug") String projectSlug,
			@Parameter(description = "The user whose variables were copied (leave empty for " +
					"your own)")
			@RequestParam(value = "delegateUser", required = false) String delegateUser
	) throws HttpException {
		QueryRunner.runQuery(
				(protocolVersion, user, delegated) -> {
					logger.info("POST /v{}/test-session/reset [user: {}]", version, user);
					testSessionService.resetCopy(tester(), user, delegated,
							findProject(projectSlug));
					return null;
				},
				version, response, delegateUser, Permission.DIALOGUE_TEST);
	}

	/** The authenticated user running the test (who may be acting for someone else). */
	private static DialogueBranchUserId tester() throws HttpException {
		AuthenticationInfo caller = QueryRunner.requireAuthenticatedUser();
		return DialogueBranchUserId.withoutUsername(caller.getIssuer(), caller.getSubject());
	}

	private DBProject findProject(String projectSlug) throws NotFoundException {
		return projectService.findBySlug(projectSlug)
				.orElseThrow(() -> new NotFoundException("Project not found: " + projectSlug));
	}

}

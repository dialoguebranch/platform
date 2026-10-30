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

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.model.common.VariableNames;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.DateTimeUtils;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.auth.Permission;
import com.dialoguebranch.web.service.controller.schema.ProjectVariableInfo;
import com.dialoguebranch.web.service.controller.schema.SupportedVariableInfo;
import com.dialoguebranch.web.service.controller.schema.VariableHistoryEntry;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.DatabaseException;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.HttpError;
import com.dialoguebranch.web.service.exception.HttpFieldError;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.execution.UserService;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.storage.VariableHistoryStorageHandler;
import com.dialoguebranch.web.service.storage.VariableStoreDatabaseStorageHandler;
import com.dialoguebranch.web.service.storage.model.DBProject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the /variables/... end-points of the Dialogue Branch Web Service.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2")
@RequestMapping(value = {"/v{version}/variables", "/variables"})
@Tag(name = "3. Variables", description = "End-points for retrieving or setting Dialogue Branch " +
		"Variables.")
public class VariablesController {

	@Autowired
	Application application;

	@Autowired
	VariableHistoryStorageHandler historyStorageHandler;

	@Autowired
	DBProjectRepository projectRepository;

	@Autowired
	DBUserRepository userRepository;

	@Autowired
	VariableStoreDatabaseStorageHandler storageHandler;

	/** Upper bound on {@code pageSize} for {@code /variables/history}, as for {@code /users}. */
	private static final int MAX_HISTORY_PAGE_SIZE = 200;

	private static final Logger logger = LoggerFactory.getLogger(VariablesController.class);

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instances of this class are constructed through Spring.
	 */
	public VariablesController() { }

	// --------------------------------------------------------------------- //
	// -------------------- END-POINT: "/variables/get" -------------------- //
	// --------------------------------------------------------------------- //

	/**
	 * Retrieve all- or a subset of Dialogue Branch variables for a given user.
	 *
	 * <p>Use this end-point to get the latest known Dialogue Branch Variables values for a given
	 * user (either the currently logged in user, or the one provided through the end-point). Either
	 * provide a list of variable names for which to retrieve its values, or leave this empty to
	 * retrieve all known Dialogue Branch Variable data.</p>
	 *
	 * @param request the {@link HttpServletRequest} object containing information on the HTTP
	 *                request.
	 * @param response the {@link HttpServletResponse} object containing information on the HTTP
	 *                 response that can be returned to the client.
	 * @param version the API Version to use, e.g. '1'.
	 * @param variableNames A space-separated list of Dialogue Branch variable names, or leave empty
	 *                      to retrieve all known variables.
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 *                 String, e.g. 'Europe/Lisbon').
	 * @param delegateUser The user for which to request Dialogue Branch variable info (leave empty
	 *                     if executing for the currently authenticated user).
	 * @return a {@link List} of {@link Variable} objects that match the given input parameters.
	 * @throws Exception in case of a network error, internal error, or e.g. authentication error.
	 */
	@Operation(
		summary = "Retrieve all- or a subset of Dialogue Branch variables for a given user.",
		description = "Use this end-point to get the latest known Dialogue Branch Variables " +
			"values for a given user (either the currently logged in user, or the one provided " +
			"through the end-point). Either provide a list of variable names for which to " +
			"retrieve its values, or leave this empty to retrieve all known Dialogue Branch " +
			"Variable data.")
	@RequestMapping(value="/get", method=RequestMethod.GET)
	public List<Variable> getVariables(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "A space-separated list of Dialogue Branch variable names, or " +
			"leave empty to retrieve all known variables")
		@RequestParam(value="variableNames", required=false)
		String variableNames,

		@Parameter(description = "The current time zone of the Dialogue Branch user (as IANA, " +
				"e.g. 'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "The user for which to request Dialogue Branch variable info " +
			"(leave empty if executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false)
		String delegateUser,

		@Parameter(description = "The slug of the project the variables are scoped to")
		@RequestParam(value = "projectSlug")
		String projectSlug) throws Exception {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "GET /v" + version + "/variables/get?names=" + variableNames
				+ "&timeZone = " + timeZone;
		if(!(delegateUser == null) && (!delegateUser.isEmpty()))
			logInfo += "&delegateUser="+delegateUser;
		logger.info(logInfo);

		// Make sure the passed on String is not null
		String variableNameList = Objects.requireNonNullElse(variableNames, "");

		// QueryRunner resolves the effective user (authenticated caller or authorised delegate)
		// and passes it as the lambda argument.
		// A delegated read goes straight to storage: it must not change the delegated user's
		// in-memory state (their time zone, #327) or create a user for them (#328).
		return QueryRunner.runQuery(
			(protocolVersion, user, delegated) -> delegated
					? doGetVariablesDelegated(user, projectSlug, variableNameList)
					: doGetVariables(user, projectSlug, variableNameList, timeZone),
			version, response, delegateUser, Permission.VARIABLE_READ_OWN);
	}

	// ------------------------------------------------------------------------- //
	// -------------------- END-POINT: "/variables/history" -------------------- //
	// ------------------------------------------------------------------------- //

	/**
	 * Retrieve the history of Dialogue Branch Variable values for a given user within a project.
	 *
	 * <p>Every change to a user's variables — a {@code <<set>>} in a dialogue, an input reply, a
	 * call to {@code /variables/set|set-single}, or an update from the External Variable Service —
	 * is recorded as one entry per affected variable. A removed variable is recorded with a
	 * {@code null} value. Entries are returned newest first and paginated; repeated identical values
	 * are returned as they were recorded, not deduplicated (#259).</p>
	 *
	 * @param request the {@link HttpServletRequest}.
	 * @param response the {@link HttpServletResponse}.
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @param variableName the variable to return the history of, or leave empty for all variables.
	 * @param from only return entries recorded at or after this time, in epoch milliseconds
	 *             (inclusive; leave out for no lower bound).
	 * @param to only return entries recorded before this time, in epoch milliseconds (exclusive;
	 *           leave out for no upper bound).
	 * @param page the zero-based page index (default {@code 0}).
	 * @param pageSize the page size (default {@code 50}, capped at {@value
	 *                 #MAX_HISTORY_PAGE_SIZE}).
	 * @param delegateUser the user whose history to return (leave empty if executing for the
	 *                     currently authenticated user).
	 * @return the requested page of {@link VariableHistoryEntry} objects, newest first.
	 * @throws Exception in case of a network error, internal error, or e.g. authentication error.
	 */
	@Operation(
		summary = "Retrieve the history of Dialogue Branch Variable values for a given user.",
		description = "Returns every recorded change to the given user's Dialogue Branch " +
			"Variables in a project, newest first and paginated: one entry per variable per " +
			"change, with the value it was given (or null if it was removed), when the Web " +
			"Service recorded the change, and its source. Provide a variable name to return the " +
			"history of that variable only. Provide from and/or to (epoch milliseconds, the same unit " +
			"as recordedTime) to return only the changes recorded in the range [from, to): " +
			"from is inclusive, to is exclusive, so consecutive ranges never overlap or leave " +
			"gaps.")
	@RequestMapping(value="/history", method=RequestMethod.GET)
	public List<VariableHistoryEntry> getVariableHistory(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The slug of the project the variables are scoped to")
		@RequestParam(value = "projectSlug")
		String projectSlug,

		@Parameter(description = "The variable to return the history of, or leave empty for " +
			"all variables")
		@RequestParam(value = "variableName", required = false)
		String variableName,

		@Parameter(description = "Only return changes recorded at or after this time, in epoch " +
			"milliseconds (inclusive; leave out for no lower bound)")
		@RequestParam(value = "from", required = false)
		String from,

		@Parameter(description = "Only return changes recorded before this time, in epoch " +
			"milliseconds (exclusive; leave out for no upper bound)")
		@RequestParam(value = "to", required = false)
		String to,

		@Parameter(description = "The zero-based page index")
		@RequestParam(value = "page", required = false, defaultValue = "0")
		int page,

		@Parameter(description = "The page size (capped at " + MAX_HISTORY_PAGE_SIZE + ")")
		@RequestParam(value = "pageSize", required = false, defaultValue = "50")
		int pageSize,

		@Parameter(description = "The user for which to request the variable history (leave " +
			"empty if executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false)
		String delegateUser) throws Exception {

		if (version == null || version.isEmpty())
			version = ProtocolVersion.getLatestVersion().versionName();

		String logInfo = "GET /v" + version + "/variables/history?projectSlug=" + projectSlug
				+ "&variableName=" + variableName + "&from=" + from + "&to=" + to + "&page=" + page
				+ "&pageSize=" + pageSize;
		if (delegateUser != null && !delegateUser.isEmpty())
			logInfo += "&delegateUser=" + delegateUser;
		logger.info(logInfo);

		return QueryRunner.runQuery(
			(protocolVersion, user, delegated) -> {
				// For the caller's own identity an empty history is a normal answer; for a
				// delegated user it would hide a wrong subject (#328).
				if (delegated && userRepository.findByIssuerAndSubject(user.issuer(),
						user.subject()).isEmpty())
					throw ControllerFunctions.unknownDelegateUser(user);
				return doGetVariableHistory(user, projectSlug, variableName, from, to, page,
						pageSize);
			},
			version, response, delegateUser, Permission.VARIABLE_READ_OWN_HISTORY);
	}

	/**
	 * Returns a page of the variable history of the given user within the given project.
	 * Package-private (rather than private, like the other {@code do*} helpers) so that its input
	 * validation and page clamping can be unit-tested without the authentication layer.
	 *
	 * @param userId the Dialogue Branch user whose history to return.
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @param variableName the variable to return the history of, or {@code null}/empty for all.
	 * @param from the lower bound (inclusive) of the recorded time, in epoch milliseconds, or
	 *             {@code null}/blank for none.
	 * @param to the upper bound (exclusive) of the recorded time, in epoch milliseconds, or
	 *           {@code null}/blank for none.
	 * @param page the zero-based page index (negative values are treated as {@code 0}).
	 * @param pageSize the page size (clamped to {@code 1..}{@value #MAX_HISTORY_PAGE_SIZE}).
	 * @return the requested page of history entries, newest first.
	 * @throws BadRequestException in case of a malformed {@code variableName}, an unparseable
	 *                             {@code from} or {@code to}, or a {@code from} after {@code to}.
	 * @throws NotFoundException if no project with the given slug exists.
	 * @throws ParseException if a stored value cannot be parsed.
	 */
	List<VariableHistoryEntry> doGetVariableHistory(DialogueBranchUserId userId,
			String projectSlug, String variableName, String from, String to, int page,
			int pageSize) throws BadRequestException, NotFoundException, ParseException {
		String name = variableName == null || variableName.isBlank() ? null : variableName.trim();
		if (name != null && !VariableNames.isValid(name)) {
			throw BadRequestException.withInvalidInput(new HttpFieldError("variableName",
					"Invalid variable name: " + name));
		}
		List<HttpFieldError> rangeErrors = new ArrayList<>();
		Long fromMillis = parseEpochMillis("from", from, rangeErrors);
		Long toMillis = parseEpochMillis("to", to, rangeErrors);
		if (rangeErrors.isEmpty() && fromMillis != null && toMillis != null
				&& fromMillis > toMillis) {
			rangeErrors.add(new HttpFieldError("from", "'from' must not be after 'to'"));
		}
		if (!rangeErrors.isEmpty())
			throw BadRequestException.withInvalidInput(rangeErrors);
		DBProject project = projectRepository.findBySlug(projectSlug)
				.orElseThrow(() -> new NotFoundException("Project not found: " + projectSlug));
		int size = Math.max(1, Math.min(pageSize, MAX_HISTORY_PAGE_SIZE));
		return historyStorageHandler.read(userId, project, name, fromMillis, toMillis,
				Math.max(0, page), size);
	}

	private static Long parseEpochMillis(String field, String value, List<HttpFieldError> errors) {
		if (value == null || value.isBlank()) return null;
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			errors.add(new HttpFieldError(field,
					"Not a valid time in epoch milliseconds: " + value));
			return null;
		}
	}

	// ------------------------------------------------------------------------------ //
	// -------------------- END-POINT: "/variables/list-project" -------------------- //
	// ------------------------------------------------------------------------------ //

	/**
	 * Lists every Dialogue Branch variable name referenced anywhere in a project's dialogues —
	 * read or written — whether or not any user currently has a value stored for it. Intended as
	 * an authoring aid: it lets a client show the full set of variables a project uses, not just
	 * the subset the logged-in user happens to have values for. Accessible to users with the
	 * {@code editor} or {@code admin} role.
	 *
	 * @param request the {@link HttpServletRequest}.
	 * @param response the {@link HttpServletResponse}.
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the slug of the project whose variable names should be listed.
	 * @return the project's referenced variable names, sorted alphabetically.
	 * @throws Exception in case of a network, internal or authentication error.
	 */
	@Operation(
		summary = "List every variable used anywhere in a project's dialogues.",
		description = "Returns every Dialogue Branch variable read or written by any dialogue in " +
			"the given project, sorted by name and flagged read/written, regardless of whether a " +
			"value is stored for it. Accessible for users with the 'editor' or 'admin' role.")
	@RequestMapping(value="/list-project", method=RequestMethod.GET)
	public List<ProjectVariableInfo> listProjectVariables(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The slug of the project whose variable names should be listed")
		@RequestParam(value = "projectSlug")
		String projectSlug) throws Exception {

		if (version == null || version.isEmpty())
			version = ProtocolVersion.getLatestVersion().versionName();

		logger.info("GET /v" + version + "/variables/list-project?projectSlug=" + projectSlug);

		return QueryRunner.runQuery(
			(protocolVersion, authenticatedUser) ->
				application.getApplicationManager().getProjectVariables(projectSlug),
			version, response, "", Permission.VARIABLE_INSPECT_PROJECT);
	}

	// -------------------------------------------------------------------------------- //
	// -------------------- END-POINT: "/variables/list-supported" -------------------- //
	// -------------------------------------------------------------------------------- //

	/**
	 * Lists the variables the project's configured External Variable Service (EVS) reports as
	 * supported, via the EVS's own {@code /variables/supported} end-point. Intended as an
	 * authoring aid alongside {@code /variables/list-project}: a dialogue author referencing
	 * {@code $someVariable} expecting an EVS to fill it in has no other way to confirm the EVS
	 * actually knows that name. Every call is proxied through to the EVS live, never cached.
	 * Accessible to users with the {@code editor} or {@code admin} role.
	 *
	 * @param request the {@link HttpServletRequest}.
	 * @param response the {@link HttpServletResponse}.
	 * @param version the API version to use, e.g. '1'.
	 * @param projectSlug the slug of the project to query supported variables for.
	 * @return the External Variable Service's reported supported variables for the project.
	 * @throws Exception in case of a network, internal or authentication error, or if no External
	 *                    Variable Service is configured for this deployment.
	 */
	@Operation(
		summary = "List the variables the project's configured External Variable Service supports.",
		description = "Queries the project's configured External Variable Service's own " +
			"'/variables/supported' end-point live (never cached) and returns its answer. " +
			"Accessible for users with the 'editor' or 'admin' role.")
	@RequestMapping(value="/list-supported", method=RequestMethod.GET)
	public List<SupportedVariableInfo> listSupportedVariables(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The slug of the project to query supported variables for")
		@RequestParam(value = "projectSlug")
		String projectSlug) throws Exception {

		if (version == null || version.isEmpty())
			version = ProtocolVersion.getLatestVersion().versionName();

		logger.info("GET /v" + version + "/variables/list-supported?projectSlug=" + projectSlug);

		return QueryRunner.runQuery(
			(protocolVersion, authenticatedUser) ->
				application.getApplicationManager()
						.getSupportedVariablesFromExternalService(projectSlug),
			version, response, "", Permission.VARIABLE_INSPECT_PROJECT);
	}

	/**
	 * For the given {@code userId} returns a mapping from names to value of Dialogue Branch
	 * Variables for those variables provided in the given {@code variableNames} string. The {@code
	 * variableNames} string should be a 'space-separated' list of valid Dialogue Branch Variable
	 * names (e.g. "variable1 variable_two variable-three").
	 *
	 * @param userId the Dialogue Branch user for which to retrieve variable data.
	 * @param variableNames a space-separated list of variable names, or the empty string
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 *                 String, e.g. 'Europe/Lisbon').
	 * @return a mapping of variable names to variable values
	 * @throws IOException in case of an error in generating the UserService.
	 * @throws DatabaseException in case of an error in generating the UserService.
	 * @throws BadRequestException in case of a malformed or unknown {@code timeZone}.
	 */
	private List<Variable> doGetVariables(DialogueBranchUserId userId, String projectSlug,
			String variableNames, String timeZone)
			throws IOException, DatabaseException, BadRequestException {

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		VariableStore variableStore = userService.getVariableStore(projectSlug);

		List<String> nameList = parseVariableNames(variableNames);
		if (nameList == null) nameList = variableStore.getSortedVariableNames();

		List<Variable> result = new ArrayList<>();

		for(String variableName : nameList) {
			result.add(variableStore.getVariable(variableName));
		}

		return result;
	}

	/**
	 * As {@link #doGetVariables}, for a delegated read: reads the delegated user's stored
	 * variables directly, without going through (or creating) their {@link UserService}, so their
	 * in-memory time zone is left alone and nothing is created for an unknown user.
	 *
	 * @param userId the delegated user.
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @param variableNames a space-separated list of variable names, or the empty string for all.
	 * @return the requested variables, in the same shape as {@link #doGetVariables}.
	 * @throws BadRequestException in case of a malformed variable name.
	 * @throws NotFoundException if the project or the delegated user is unknown.
	 * @throws ParseException if a stored value cannot be parsed.
	 */
	private List<Variable> doGetVariablesDelegated(DialogueBranchUserId userId, String projectSlug,
			String variableNames) throws BadRequestException, NotFoundException, ParseException {
		List<String> nameList = parseVariableNames(variableNames);
		DBProject project = projectRepository.findBySlug(projectSlug)
				.orElseThrow(() -> new NotFoundException("Project not found: " + projectSlug));
		Map<String, Variable> byName = new TreeMap<>();
		for (Variable variable : storageHandler.readExisting(userId, project)
				.orElseThrow(() -> ControllerFunctions.unknownDelegateUser(userId))) {
			byName.put(variable.getName(), variable);
		}
		if (nameList == null) return new ArrayList<>(byName.values());
		List<Variable> result = new ArrayList<>();
		for (String name : nameList) {
			result.add(byName.get(name));
		}
		return result;
	}

	/**
	 * Parses a space-separated list of variable names.
	 *
	 * @param variableNames the list, or the empty string.
	 * @return the names, or {@code null} for "all variables" (an empty list).
	 * @throws BadRequestException if any name is not a valid variable name.
	 */
	private static List<String> parseVariableNames(String variableNames) throws BadRequestException {
		String trimmed = variableNames.trim();
		if (trimmed.isEmpty()) return null;
		String[] nameArray = trimmed.split("\\s+");
		List<String> invalidNames = new ArrayList<>();
		for (String name : nameArray) {
			if (!VariableNames.isValid(name))
				invalidNames.add(name);
		}
		if (!invalidNames.isEmpty()) {
			HttpFieldError error = new HttpFieldError("names",
					"Invalid variable names: " + String.join(", ", invalidNames));
			throw BadRequestException.withInvalidInput(error);
		}
		return Arrays.asList(nameArray);
	}

	// ---------------------------------------------------------------------------- //
	// -------------------- END-POINT: "/variables/set-single" -------------------- //
	// ---------------------------------------------------------------------------- //

	/**
	 * Set the value of a single Dialogue Branch Variable for a given user.
	 *
	 * <p>Use this end-point to get set a Dialogue Branch Variable to a specific value (or to remove
	 * the stored value by setting it to the empty string.</p>
	 *
	 * @param request the {@link HttpServletRequest} object containing information on the HTTP
	 *                request.
	 * @param response the {@link HttpServletResponse} object containing information on the HTTP
	 *                 response that can be returned to the client.
	 * @param version the API Version to use, e.g. '1'.
	 * @param name The name of the Dialogue Branch Variable to set.
	 * @param value The value for the Dialogue Branch Variable (or leave empty to erase the Dialogue
	 *              Branch variable)
	 * @param delegateUser The user for which to set the Dialogue Branch variable (leave empty if
	 *                     setting for the currently authenticated user)
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 *                 String, e.g. 'Europe/Lisbon').
	 * @throws Exception in case of a network error, internal error, or e.g. authentication error.
	 */
	@Operation(
		summary = "Set the value of a single Dialogue Branch Variable for a given user.",
		description = "Use this end-point to get set a Dialogue Branch Variable to a specific " +
			"value (or to remove the stored value by setting it to the empty string.")
	@RequestMapping(value="/set-single", method=RequestMethod.POST)
	public void setVariable(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The name of the Dialogue Branch Variable to set")
		@RequestParam(value="name")
		String name,

		@Parameter(description = "The value for the Dialogue Branch Variable (or leave empty to " +
			"erase the Dialogue Branch variable)")
		@RequestParam(value="value", required=false)
		String value,

		@Parameter(description = "The user for which to set the Dialogue Branch variable (leave " +
			"empty if setting for the currently authenticated user)")
		@RequestParam(value="delegateUser",required=false,defaultValue="")
		String delegateUser,

		@Parameter(description = "The current time zone of the Dialogue Branch user (as IANA, " +
			"e.g. 'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "The slug of the project the variables are scoped to")
		@RequestParam(value = "projectSlug")
		String projectSlug
	) throws Exception {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/variables/set-single?name=" + name;
		if(!(value == null) && (!value.isEmpty())) logInfo += "&value="+value;
		if(!(delegateUser == null) && (!delegateUser.isEmpty())) logInfo += "&delegateUser="
				+ delegateUser;
		if(!(timeZone == null)) logInfo += "&timeZone=" + timeZone;
		logger.info(logInfo);

		QueryRunner.runQuery((protocolVersion, user, delegated) ->
			doSetVariable(user, delegated, projectSlug, name, value, timeZone),
			version, response, delegateUser, Permission.VARIABLE_WRITE_OWN);
	}

	/**
	 * Sets a Dialogue Branch Variable defined by the given {@code name} to the given {@code value}
	 * for the specified {@code userId} in the given {@code timeZone}.
	 *
	 * @param userId the {@link String} identifier of the user for whom to set the variable.
	 * @param delegated {@code true} if the request acts on behalf of another user, whose time zone
	 *                  it then leaves unchanged.
	 * @param name the name of the Dialogue Branch Variable
	 * @param value the value for the Dialogue Branch Variable (or {@code null} in case the variable
	 *              should be removed).
	 * @return {@code null}
	 * @throws Exception in case of an invalid variable name, invalid timezone, or an error
	 * 					 accessing the variable store.
	 */
	private Object doSetVariable(DialogueBranchUserId userId, boolean delegated,
								 String projectSlug, String name, String value, String timeZone)
			throws Exception {
		List<HttpFieldError> errors = new ArrayList<>();

		if (!VariableNames.isValid(name)) {
			errors.add(new HttpFieldError("name",
					"Invalid variable name: " + name));
			throw BadRequestException.withInvalidInput(errors);
		}

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		// A delegated request never changes the user's own time zone (#331).
		if (!delegated) userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		ZonedDateTime eventTime =
				DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss Z");

		if(value == null) {
			logger.info("Received request to remove Dialogue Branch Variable '{}' at eventTime" +
					" '{}' in time zone '{}'", name, eventTime.format(formatter), timeZone);
			userService.getVariableStore(projectSlug).removeByName(name,true,
					eventTime, VariableUpdatedSource.WEB_SERVICE);
		} else {
			userService.getVariableStore(projectSlug).setValue(name, value, true, eventTime,
					VariableUpdatedSource.WEB_SERVICE);
		}
		return null;
	}

	// --------------------------------------------------------------------- //
	// -------------------- END-POINT: "/variables/set" -------------------- //
	// --------------------------------------------------------------------- //

	/**
	 * End-point definition for setting the value of one or multiple Dialogue Branch Variables for a
	 * given user.
	 *
	 * <p>Use this end-point to get set one or many Dialogue Branch Variables to their given values
	 * (or to remove the stored value by setting it to the empty string.</p>
	 *
	 * @param request the {@link HttpServletRequest} object containing information on the HTTP
	 *                request.
	 * @param response the {@link HttpServletResponse} object containing information on the HTTP
	 *                 response that can be returned to the client.
	 * @param version the API Version to use, e.g. '1'.
	 * @param delegateUser The user for which to set the Dialogue Branch variables (which may be
	 *                     empty or {@code null} if setting for the currently authenticated user).
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 *                 String, e.g. 'Europe/Lisbon').
	 * @param variables A JSON mapping of Dialogue Branch Variable names to values, representing the
	 *                  variables that should be updated.
	 * @throws Exception in case of a network error, internal error, or e.g. authentication error.
	 */
	@Operation(
		summary = "Set the value of one or multiple Dialogue Branch Variables for a given user.",
		description = "Use this end-point to get set one or many Dialogue Branch Variables to " +
			"their given values (or to remove the stored value by setting it to the empty string.")
	@RequestMapping(value="/set", method=RequestMethod.POST)
	public void setVariables(
			HttpServletRequest request,
			HttpServletResponse response,

			@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
			@PathVariable(value = "version")
			String version,

			@Parameter(description = "The user for which to set the Dialogue Branch variables " +
				"(leave empty if setting for the currently authenticated user)")
			@RequestParam(value="delegateUser",required=false)
			String delegateUser,

			@Parameter(description = "The current time zone of the Dialogue Branch user (as " +
					"IANA string, e.g. 'Europe/Lisbon')")
			@RequestParam(value="timeZone")
			String timeZone,

			@Parameter(description = "The slug of the project the variables are scoped to")
			@RequestParam(value = "projectSlug")
			String projectSlug,

			@Parameter(description = "A JSON mapping of Dialogue Branch Variable names to values")
			@RequestBody
			Map<String,Object> variables) throws Exception {

		// If no versionName is provided, or versionName is empty, assume the latest version
		if (version == null || version.isEmpty()) {
			version = ProtocolVersion.getLatestVersion().versionName();
		}

		// Log this call to the service log
		String logInfo = "POST /v" + version + "/variables/set";
		if(!(delegateUser == null) && (!delegateUser.isEmpty()))
			logInfo += "?delegateUser=" + delegateUser;
		logger.info(logInfo);

		QueryRunner.runQuery((protocolVersion, user, delegated) ->
						doSetVariables(user, delegated, projectSlug, variables, timeZone),
			version, response, delegateUser, Permission.VARIABLE_WRITE_OWN);
	}

	/**
	 * Sets the Dialogue Branch Variables in the given {@code variables} map for the give {@code
	 * userId} in the given {@code timeZone}.
	 *
	 * @param userId the {@link String} identifier of the user for whom to set the variables.
	 * @param delegated {@code true} if the request acts on behalf of another user, whose time zone
	 *                  it then leaves unchanged.
	 * @param variables a mapping of Dialogue Branch Variable names to value.
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 * 	               String, e.g. 'Europe/Lisbon').
	 * @return {@code null}
	 * @throws Exception in case of an invalid variable name, or an error writing variables to the
	 * 					 database.
	 */
	private Object doSetVariables(DialogueBranchUserId userId, boolean delegated,
								  String projectSlug, Map<String,Object> variables, String timeZone)
			throws Exception {

		List<String> invalidNames = new ArrayList<>();
		for (String name : variables.keySet()) {
			if (!VariableNames.isValid(name))
				invalidNames.add(name);
		}

		if (!invalidNames.isEmpty()) {
			HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
					"Invalid variable names: " + String.join(", ", invalidNames));
			throw new BadRequestException(error);
		}

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		// A delegated request never changes the user's own time zone (#331).
		if (!delegated) userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		VariableStore variableStore = userService.getVariableStore(projectSlug);
		for(Map.Entry<String, Object> entry : variables.entrySet()) {
			variableStore.setValue(
					entry.getKey(),
					entry.getValue(),
					true,
					DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone()),
					VariableUpdatedSource.WEB_SERVICE);
		}
		return null;
	}

}

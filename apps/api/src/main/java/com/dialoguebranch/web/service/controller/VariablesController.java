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

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.ProtocolVersion;
import com.dialoguebranch.web.service.QueryRunner;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.HttpError;
import com.dialoguebranch.web.service.exception.HttpFieldError;
import com.dialoguebranch.web.service.execution.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.exception.DatabaseException;
import org.slf4j.Logger;
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
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
@RestController
@SecurityRequirement(name = "X-Auth-Token")
@RequestMapping(value = {"/v{version}/variables", "/variables"})
@Tag(name = "3. Variables", description = "End-points for retrieving or setting DialogueBranch " +
		"Variables.")
public class VariablesController {

	@Autowired
	Application application;

	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());

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

		@Parameter(description = "The current time zone of the DialogueBranch user (as IANA, " +
				"e.g. 'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "The user for which to request Dialogue Branch variable info " +
			"(leave empty if executing for the currently authenticated user)")
		@RequestParam(value="delegateUser", required=false)
		String delegateUser) throws Exception {

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

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			return QueryRunner.runQuery(
				(protocolVersion, user) -> doGetVariables(user, variableNameList, timeZone),
				version, accessToken, response, delegateUser, application);
		} else {
			return QueryRunner.runQuery(
				(protocolVersion, user) -> doGetVariables(delegateUser, variableNameList, timeZone),
				version, accessToken, response, delegateUser, application);
		}
	}

	/**
	 * For the given {@code userId} returns a mapping from names to value of Dialogue Branch
	 * Variables for those variables provided in the given {@code variableNames} string. The {@code
	 * variableNames} string should be a 'space-separated' list of valid DialogueBranch Variable
	 * names (e.g. "variable1 variable_two variable-three").
	 *
	 * @param userId the DialogueBranch user for which to retrieve variable data.
	 * @param variableNames a space-separated list of variable names, or the empty string
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 *                 String, e.g. 'Europe/Lisbon').
	 * @return a mapping of variable names to variable values
	 * @throws IOException in case of an error in generating the UserService.
	 * @throws DatabaseException in case of an error in generating the UserService.
	 * @throws BadRequestException in case of a malformed or unknown {@code timeZone}.
	 */
	private List<Variable> doGetVariables(String userId, String variableNames, String timeZone)
            throws IOException, DatabaseException, BadRequestException {

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		VariableStore variableStore = userService.getVariableStore();

		variableNames = variableNames.trim();

		List<String> nameList;
		if (variableNames.isEmpty()) {
			nameList = variableStore.getSortedVariableNames();
		} else {
			List<String> invalidNames = new ArrayList<>();
			String[] nameArray = variableNames.split("\\s+");
			for (String name : nameArray) {
				if (!name.matches("[A-Za-z]\\w*"))
					invalidNames.add(name);
			}
			if (!invalidNames.isEmpty()) {
				HttpFieldError error = new HttpFieldError("names",
						"Invalid variable names: " +
								String.join(", ", invalidNames));
				throw BadRequestException.withInvalidInput(error);
			}
			nameList = Arrays.asList(nameArray);
		}

		List<Variable> result = new ArrayList<>();

		for(String variableName : nameList) {
			result.add(variableStore.getVariable(variableName));
		}

		return result;
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

		@Parameter(description = "The current time zone of the DialogueBranch user (as IANA, " +
			"e.g. 'Europe/Lisbon')")
		@RequestParam(value="timeZone")
		String timeZone
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

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			QueryRunner.runQuery((protocolVersion, user) ->
				doSetVariable(user, name, value, timeZone),
				version, accessToken, response, delegateUser, application);
		} else {
			QueryRunner.runQuery((protocolVersion, user) ->
				doSetVariable(delegateUser, name, value, timeZone),
				version, accessToken, response, delegateUser, application);
		}
	}

	/**
	 * Sets a Dialogue Branch Variable defined by the given {@code name} to the given {@code value}
	 * for the specified {@code userId} in the given {@code timeZone}.
	 *
	 * @param userId the {@link String} identifier of the user for whom to set the variable.
	 * @param name the name of the DialogueBranch Variable
	 * @param value the value for the Dialogue Branch Variable (or {@code null} in case the variable
	 *              should be removed).
	 * @return {@code null}
	 * @throws Exception in case of an invalid variable name, invalid timezone, or an error
	 * 					 accessing the variable store.
	 */
	private Object doSetVariable(String userId, String name, String value,
								 String timeZone) throws Exception {
		List<HttpFieldError> errors = new ArrayList<>();

		if (!name.matches("[A-Za-z]\\w*")) {
			errors.add(new HttpFieldError("name",
					"Invalid variable name: " + name));
			throw BadRequestException.withInvalidInput(errors);
		}

		// Get or create a UserService for the user in the given time zone
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);
		UserService userService = application.getApplicationManager()
				.getOrCreateActiveUserService(userId,timeZoneId);
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		ZonedDateTime eventTime = DateTimeUtils.nowMs(timeZoneId);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss Z");

		if(value == null) {
            logger.info("Received request to remove Dialogue Branch Variable '{}' at eventTime" +
					" '{}' in time zone '{}'", name, eventTime.format(formatter), timeZone);
			userService.getVariableStore().removeByName(name,true,
					eventTime, VariableStoreChange.Source.WEB_SERVICE);
		} else {
			userService.getVariableStore().setValue(name, value, true, eventTime,
					VariableStoreChange.Source.WEB_SERVICE);
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
	 * <p>Use this end-point to get set one or many DialogueBranch Variables to their given values
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
		description = "Use this end-point to get set one or many DialogueBranch Variables to " +
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

		// Extract the access token, and throw an exception if it is not provided correctly
		String accessToken = ControllerFunctions.extractAccessToken(request);

		if(delegateUser == null || delegateUser.isEmpty()) {
			QueryRunner.runQuery((protocolVersion, user) ->
							doSetVariables(user, variables, timeZone),
				version, accessToken, response, delegateUser, application);
		} else {
			QueryRunner.runQuery((protocolVersion, user) ->
							doSetVariables(delegateUser, variables, timeZone),
				version, accessToken, response, delegateUser, application);
		}
	}

	/**
	 * Sets the Dialogue Branch Variables in the given {@code variables} map for the give {@code
	 * userId} in the given {@code timeZone}.
	 *
	 * @param userId the {@link String} identifier of the user for whom to set the variables.
	 * @param variables a mapping of DialogueBranch Variable names to value.
	 * @param timeZone The current time zone of the Dialogue Branch user (presented as an IANA
	 * 	               String, e.g. 'Europe/Lisbon').
	 * @return {@code null}
	 * @throws Exception in case of an invalid variable name, or an error writing variables to the
	 * 					 database.
	 */
	private Object doSetVariables(String userId, Map<String,Object> variables,
								  String timeZone) throws Exception {

		List<String> invalidNames = new ArrayList<>();
		for (String name : variables.keySet()) {
			if (!name.matches("[A-Za-z]\\w*"))
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
		userService.getDialogueBranchUser().setTimeZone(timeZoneId);

		VariableStore variableStore = userService.getVariableStore();
		for(Map.Entry<String, Object> entry : variables.entrySet()) {
			variableStore.setValue(
					entry.getKey(),
					entry.getValue(),
					true,
					DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone()),
					VariableStoreChange.Source.WEB_SERVICE);
		}
		return null;
	}

}

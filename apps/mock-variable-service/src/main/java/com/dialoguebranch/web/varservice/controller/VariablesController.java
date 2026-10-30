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

package com.dialoguebranch.web.varservice.controller;

import com.dialoguebranch.web.varservice.ExampleVariables;
import com.dialoguebranch.web.varservice.ProtocolVersion;
import com.dialoguebranch.web.varservice.controller.schema.DLBVariablePayload;
import com.dialoguebranch.web.varservice.controller.schema.DLBVariableUpdatePayload;
import com.dialoguebranch.web.varservice.controller.schema.SupportedVariablePayload;
import com.dialoguebranch.web.varservice.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the /variables/ -end-points of the Dialogue Branch External Variable Service
 * dummy implementation.
 *
 * @author Harm op den Akker
 * @author Tessa Beinema
 */
@RestController
@SecurityRequirement(name = "Authorization")
@RequestMapping(value = {"/v{version}/variables", "/variables"})
@Tag(name = "1. Variables",
	 description = "End-points for retrieving variables from- and sending to the service")
public class VariablesController {

	/** The logger used for logging (debug) info to log files. */
	private static final Logger logger = LoggerFactory.getLogger(VariablesController.class);

	/** This service's values for the example variables, which dialogues may change. */
	private final ExampleVariables exampleVariables;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param exampleVariables this service's values for the example variables.
	 */
	public VariablesController(ExampleVariables exampleVariables) {
		this.exampleVariables = exampleVariables;
	}

	// -----------------------------------------------------------------------
	// -------------------- END-POINT: "retrieve-updates" --------------------
	// -----------------------------------------------------------------------

	/**
	 * Retrieve updates for a given list of Dialogue Branch Variables. The use case for this
	 * end-point is as follows. Before executing a Dialogue Branch Dialogue, you (or e.g. the
	 * Dialogue Branch Web Service) may gather a list of all the Dialogue Branch Variables used in its
	 * execution. Before starting the execution, you may call this end-point with the list of
	 * Dialogue Branch Variables in order to verify that you have the latest values for all
	 * variables. In return, you will receive a list - which is a subset of the list you provided -
	 * that contains all Dialogue Branch Variables for which an updated value is available. You are
	 * basically asking: 'Hey, I have this list of Dialogue Branch Variables for this user, is this
	 * up-to-date?'.
	 *
	 * <p>You must pass along the current timezone of the user (client) so that certain time
	 * sensitive variables may be correctly set according to the timezone of the user</p>
	 *
	 * <p>In this dummy implementation, only the variables this service reports as supported get a
	 * value (see {@code /variables/supported}): {@code currentDate}, {@code currentTime} and
	 * {@code dayPart} from the current time in the user's time zone, and the {@code example.*}
	 * variables the value this service keeps for the user, which starts as a random value of
	 * their declared type. Any other variable in the request is not
	 * this service's to answer for, and is never returned.</p>
	 *
	 * <p>The response carries each updated variable's name and value only: the Web Service
	 * records when it received an update as the variable's updated time.</p>
	 *
	 * @param request the {@link HttpServletRequest} that generated the request.
	 * @param response the {@link HttpServletResponse} that generated the request.
	 * @param version the API Version to use, e.g. '1'.
	 * @param subject the subject of the Dialogue Branch user.
	 * @param timeZone the current time zone of the Dialogue Branch user (e.g. "Europe/Lisbon").
	 * @param dlbVariables the List of Dialogue Branch Variables for which to check for updates.
	 * @return A list of {@link DLBVariableUpdatePayload}s representing all updated variables.
	 * @throws Exception in case of a network or service error.
	 */
	@Operation(summary = "Retrieve updates for a given list of Dialogue Branch Variables",
		description = "The use case for this end-point is as follows. Before executing a " +
			"Dialogue Branch Dialogue, you (or e.g. the Dialogue Branch Web Service) may gather " +
			"a list of all the Dialogue Branch Variables used in its execution. Before starting " +
			"the execution, you may call this end-point with the list of Dialogue Branch " +
			"Variables in order to verify that you have the latest values for all variables. In " +
			"return, you will receive a list - which is a subset of the list you provided - " +
			"that contains all Dialogue Branch Variables for which an updated value is " +
			"available. You are basically asking: 'Hey, I have this list of Dialogue Branch " +
			"Variables for this user, is this up-to-date?'." +
			"<br/><br/>You must pass along the current timezone of the user (client) so that " +
			"certain time sensitive variables may be correctly set according to the timezone of " +
			"the user." +
			"<br/><br/>In this dummy implementation, only the variables this service reports as " +
			"supported (see '/variables/supported') get a value: 'currentDate', 'currentTime' " +
			"and 'dayPart' (one of 'morning', 'afternoon', 'evening', or 'night', based on the " +
			"current hour) from the current time in the user's time zone, and the 'example.*' " +
			"variables the value this service keeps for the user, which starts as a random value " +
			"of their declared type. Any other variable in the request is never returned." +
			"<br/><br/>The response carries each updated variable's name and value only (a null " +
			"value removes the variable): the Web Service records when it received an update as " +
			"the variable's updated time.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200",
			description = "Successful operation",
			content = @Content(
				array = @ArraySchema(
					schema = @Schema(implementation = DLBVariableUpdatePayload.class)))) })
	@RequestMapping(value="/retrieve-updates", method= RequestMethod.POST, consumes={
			MediaType.APPLICATION_JSON_VALUE })
	public List<DLBVariableUpdatePayload> retrieveUpdates (
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The OIDC subject of the Dialogue Branch user")
		@RequestParam(value="subject")
		String subject,

		@Parameter(description = "The token issuer (OIDC iss URL) of the Dialogue Branch user")
		@RequestParam(value="issuer", required=false, defaultValue="")
		String issuer,

		@Parameter(description = "The slug of the project the variables are scoped to")
		@RequestParam(value="projectSlug", required=false, defaultValue="")
		String projectSlug,

		@Parameter(description = "The current time zone of the Dialogue Branch user")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(description = "Whether the request comes from a test session (an author " +
				"testing a dialogue, or an administrator simulating a user) rather than real " +
				"playback. A test session's values are discarded afterwards.")
		@RequestParam(value="testSession", required=false, defaultValue="false")
		boolean testSession,

		@Parameter(description = "List of Dialogue Branch Variables for which to check for updates.",
				required = true,
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(implementation = DLBVariablePayload.class))))
		@RequestBody List<DLBVariablePayload> dlbVariables) throws Exception {

		// If no explicit protocol version is provided, assume the latest version
		if(version == null) version = ProtocolVersion.getLatestVersion().versionName();

		// Log this call to the service log
		logger.info("POST /v{}/variables/retrieve-updates?subject={}&issuer={}&projectSlug={}&timeZone={}" +
				"&testSession={} with the following variables:", version, subject, issuer, projectSlug,
				timeZone, testSession);

		// Execute the request if a subject was provided.
		if(subject.isEmpty()) {
			throw new BadRequestException("Missing 'subject' in request.");
		} else {
			return executeRetrieveUpdates(issuer, subject, projectSlug, timeZone, testSession,
					dlbVariables);
		}
	}

	/**
	 * This method performs the "dummy" updating of the requested list of Dialogue Branch Variables.
	 * For a real-world implementation you should replace this method and make sure it does
	 * something useful.
	 *
	 * <p>In this dummy implementation, the method does the following:</p>
	 *
	 * <p>If an update is requested for a variable with name 'currentDate', 'currentTime', and/or
	 * 'dayPart', those variables will be updated to reflect the current date, time, and part of
	 * the day (one of 'morning', 'afternoon', 'evening', or 'night') in the user's provided time
	 * zone. Each {@code example.*} variable gets this service's value for the user, which starts
	 * as a random value of its declared type (see {@link ExampleVariables}).</p>
	 *
	 * <p>A service only answers for the variables it reports as supported, so every other
	 * variable in the request is left out of the result.</p>
	 *
	 * @param issuer the token issuer of the user whose variable updates are requested.
	 * @param subject the {@code String} identifier of the user who's variable updates are requested.
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @param timeZone the time zone of the user as one of {@code TimeZone.getAvailableIDs()}
	 *                 (IANA Codes)
	 * @param testSession whether the request comes from a test session.
	 * @param parameters the {@code List} of {@link DLBVariablePayload}s for which it should be
	 *               verified if an update is needed.
	 * @return a {@code List} of {@link DLBVariableUpdatePayload}s with each of the parameters for
	 * which an updated value has been found (note that this may be an empty list).
	 */
	private List<DLBVariableUpdatePayload> executeRetrieveUpdates(String issuer, String subject,
			String projectSlug, String timeZone, boolean testSession,
			List<DLBVariablePayload> parameters)
			throws BadRequestException {

		// Parse the timeZone String into a ZoneId to verify it was given in the right format
		ZoneId timeZoneId = ControllerFunctions.parseTimeZone(timeZone);

		List<DLBVariableUpdatePayload> result = new ArrayList<>();

		for (DLBVariablePayload parameterToUpdate : parameters) {

			// Log info about all variables in the request
			if(parameterToUpdate.getUpdatedTime() != null
					&& parameterToUpdate.getUpdatedTimeZone() != null) {
				ZonedDateTime paramZonedDateTime =
						ZonedDateTime.ofInstant(
								Instant.ofEpochMilli(
										parameterToUpdate.getUpdatedTime()),
								ControllerFunctions.parseTimeZone(
										parameterToUpdate.getUpdatedTimeZone()));

				DateTimeFormatter formatter =
						DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss Z");
				String readableTimeString = paramZonedDateTime.format(formatter);
				logger.info("The Dialogue Branch Variable '{}' with value '{}' for user '{}' " +
						"was last updated at '{}', which was '{}' in time zone: '{}'.",
						parameterToUpdate.getName(), parameterToUpdate.getValue(), subject,
						parameterToUpdate.getUpdatedTime(), readableTimeString,
						parameterToUpdate.getUpdatedTimeZone());
			} else {
				logger.info("The Dialogue Branch Variable '{}' with value '{}' for user '{}' " +
						"was last updated at an unknown time.", parameterToUpdate.getName(),
						parameterToUpdate.getValue(), subject);
			}

			// Handle the 'currentDate' case
			if(parameterToUpdate.getName().equals("currentDate")) {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				ZonedDateTime currentDateTime = ZonedDateTime.ofInstant(Instant.now(),timeZoneId);
				String readableDateString = currentDateTime.format(formatter);
				DLBVariableUpdatePayload newParam = new DLBVariableUpdatePayload(
						parameterToUpdate.getName(),
						readableDateString);
				result.add(newParam);
			}

			// Handle the 'currentTime' case
			else if(parameterToUpdate.getName().equals("currentTime")) {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
				ZonedDateTime currentDateTime = ZonedDateTime.ofInstant(Instant.now(),timeZoneId);
				String readableTimeString = currentDateTime.format(formatter);
				DLBVariableUpdatePayload newParam = new DLBVariableUpdatePayload(
						parameterToUpdate.getName(),
						readableTimeString);
				result.add(newParam);
			}

			// Handle the 'dayPart' case
			else if(parameterToUpdate.getName().equals("dayPart")) {
				ZonedDateTime currentDateTime = ZonedDateTime.ofInstant(Instant.now(),timeZoneId);
				String dayPart = determineDayPart(currentDateTime.getHour());
				DLBVariableUpdatePayload newParam = new DLBVariableUpdatePayload(
						parameterToUpdate.getName(),
						dayPart);
				result.add(newParam);
			}

			// The example.* variables get this service's value for the user
			else if(exampleVariables.isExample(parameterToUpdate.getName())) {
				result.add(new DLBVariableUpdatePayload(parameterToUpdate.getName(),
						exampleVariables.get(issuer, subject, projectSlug,
								parameterToUpdate.getName(), testSession)));
			}

			// Any other variable isn't one this service supports, so it never answers for it

		}

		return result;
	}

	/**
	 * Determines the part of the day for the given {@code hour} (in 24-hour format): 'morning'
	 * (6-11), 'afternoon' (12-17), 'evening' (18-21), or 'night' (22-5).
	 *
	 * @param hour the hour of the day, in 24-hour format (0-23).
	 * @return one of 'morning', 'afternoon', 'evening', or 'night'.
	 */
	private String determineDayPart(int hour) {
		if(hour >= 6 && hour < 12) return "morning";
		else if(hour >= 12 && hour < 18) return "afternoon";
		else if(hour >= 18 && hour < 22) return "evening";
		else return "night";
	}

	// ---------------------------------------------------------------------
	// -------------------- END-POINT: "notify-updated" --------------------
	// ---------------------------------------------------------------------

	/**
	 * Inform that the given list of Dialogue Branch Variables have been updated. With this end-point
	 * you can inform this Dialogue Branch External Variable Service that a list of Dialogue Branch
	 * Variables have been updated (e.g. during dialogue execution) for a particular user.
	 *
	 * <p>You must pass along the current timezone of the user (client) so that certain time
	 * sensitive variables may be correctly set according to the timezone of the user.</p>
	 *
	 * <p>In this dummy implementation, the service keeps a change to one of the {@code example.*}
	 * variables, which it reports as supported and which dialogues may change, and hands it out
	 * again on the next {@code retrieve-updates}. Changes to any other variable are only
	 * logged.</p>
	 *
	 * @param request the {@link HttpServletRequest} that generated the request.
	 * @param response the {@link HttpServletResponse} that generated the request.
	 * @param version the API Version to use, e.g. '1'.
	 * @param subject the subject of the Dialogue Branch user.
	 * @param timeZone the current time zone of the Dialogue Branch user (e.g. "Europe/Lisbon").
	 * @param dlbVariables the List of Dialogue Branch Variables for which to check for updates.
	 * @return A status code of 200 (OK).
	 * @throws Exception in case of a network or service error.
	 */
	@Operation(summary = "Inform that the given list of Dialogue Branch Variables have been updated",
		description = "With this end-point you can inform this Dialogue Branch External Variable " +
			"Service that a list of Dialogue Branch Variables have been updated (e.g. during " +
			"dialogue execution) for a particular user." +
			"<br/><br/>You must pass along the current timezone of the user (client) so that " +
			"certain time sensitive variables may be correctly set according to the timezone of " +
			"the user." +
			"<br/><br/>In this dummy implementation, the service keeps a change to one of the " +
			"'example.*' variables, which it reports as supported and which dialogues may " +
			"change, and hands it out again on the next 'retrieve-updates'. Changes to any other " +
			"variable are only logged.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation") })
	@RequestMapping(value="/notify-updated", method= RequestMethod.POST, consumes={
			MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<?> notifyUpdated(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The OIDC subject of the Dialogue Branch user")
		@RequestParam(value="subject")
		String subject,

		@Parameter(description = "The token issuer (OIDC iss URL) of the Dialogue Branch user")
		@RequestParam(value="issuer", required=false, defaultValue="")
		String issuer,

		@Parameter(description = "The slug of the project the variables are scoped to")
		@RequestParam(value="projectSlug", required=false, defaultValue="")
		String projectSlug,

		@Parameter(description = "The current time zone of the Dialogue Branch user")
		@RequestParam(value="timeZone")
		String timeZone,

		@Parameter(
			description = "List of Dialogue Branch Variables for which to check for updates.",
			required = true,
			content = @Content(
				array = @ArraySchema(
					schema = @Schema(
						implementation = DLBVariablePayload.class)
				)
			)
		)
		@RequestBody List<DLBVariablePayload> dlbVariables
	) throws Exception {

		// If no explicit protocol version is provided, assume the latest version
		if(version == null) version = ProtocolVersion.getLatestVersion().versionName();

		// Log this call to the service log
		logger.info("POST /v{}/variables/notify-updated?subject={}&issuer={}&projectSlug={}&timeZone={} " +
				"with the following variables:", version, subject, issuer, projectSlug, timeZone);
		for(DLBVariablePayload dlbVariableResultParam : dlbVariables) {
			logger.info(dlbVariableResultParam.toString());
		}

		// Execute the request if a subject was provided.
		if(subject.isEmpty()) {
			throw new BadRequestException("Missing 'subject' in request.");
		} else {
			return executeNotifyUpdated(issuer, subject, projectSlug, timeZone, dlbVariables);
		}

	}

	/**
	 * This method performs the Dummy implementation for updating the given variables in the
	 * external service: it keeps the new values of the {@code example.*} variables, and only logs
	 * the others.
	 *
	 * @param issuer the token issuer of the user for whom variable updates are available.
	 * @param subject the {@code String} identifier of the user for whom variable updates are
	 *               available.
	 * @param timeZone the timeZone of the user as one of {@code TimeZone.getAvailableIDs()} (IANA
	 *                 Codes)
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @param params the {@code List} of {@link DLBVariablePayload}s that were updated and may need
	 *               to be processed in the external service.
	 * @return a {@link ResponseEntity} to indicate whether the update was executed successfully.
	 */
	private ResponseEntity<?> executeNotifyUpdated(String issuer, String subject,
			String projectSlug, String timeZone, List<DLBVariablePayload> params)
			throws BadRequestException {

		// Parse the timeZone String into a ZoneId to verify it was given in the right format
		ControllerFunctions.parseTimeZone(timeZone);

		logger.info("The following parameters, for user '{}' in timeZone '{}' " +
				"have been successfully updated: ", subject, timeZone);

		for(DLBVariablePayload dlbVariableResultParam : params) {
			logger.info(dlbVariableResultParam.toString());
			if (exampleVariables.isExample(dlbVariableResultParam.getName())) {
				exampleVariables.set(issuer, subject, projectSlug,
						dlbVariableResultParam.getName(), dlbVariableResultParam.getValue());
			}
		}

		return new ResponseEntity<ResponseEntity<?>>(HttpStatus.OK);
	}

	// ---------------------------------------------------------------------
	// -------------------- END-POINT: "notify-cleared" --------------------
	// ---------------------------------------------------------------------

	/**
	 * Inform that the Dialogue Branch Variable store has been completed cleared. With this end-point
	 * you can inform this Dialogue Branch External Variable Service that a full clear of the
	 * Dialogue Branch Variable Store has occurred for a particular user.
	 *
	 * <p>You must pass along the current timezone of the user (client) so that the clearing of the
	 * database event may be correctly time logged.</p>
	 *
	 * <p>In this dummy implementation, the service forgets the user's values of the
	 * {@code example.*} variables in the project, so they start again with random values.</p>
	 *
	 * @param request the {@link HttpServletRequest} that generated the request.
	 * @param response the {@link HttpServletResponse} that generated the request.
	 * @param version the API Version to use, e.g. '1'.
	 * @param subject the subject of the Dialogue Branch user.
	 * @param timeZone the current time zone of the Dialogue Branch user (e.g. "Europe/Lisbon").
	 * @return A status code of 200 (OK).
	 * @throws Exception in case of a network or service error.
	 */
	@Operation(summary = "Inform that the Dialogue Branch Variable store has been completed cleared",
		description = "With this end-point you can inform this Dialogue Branch External Variable " +
			"Service that a full clear of the Dialogue Branch Variable Store has occurred for a " +
			"particular user." +
			"<br/><br/>You must pass along the current timezone of the user (client) so that " +
			"the clearing of the database event may be correctly time logged." +
			"<br/><br/>In this dummy implementation, the service forgets the user's values of the " +
			"'example.*' variables in the project, so they start again with random values.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successful operation") })
	@RequestMapping(value="/notify-cleared", method= RequestMethod.POST)
	public ResponseEntity<?> notifyCleared(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The OIDC subject of the Dialogue Branch user")
		@RequestParam(value="subject")
		String subject,

		@Parameter(description = "The token issuer (OIDC iss URL) of the Dialogue Branch user")
		@RequestParam(value="issuer", required=false, defaultValue="")
		String issuer,

		@Parameter(description = "The slug of the project the variables are scoped to")
		@RequestParam(value="projectSlug", required=false, defaultValue="")
		String projectSlug,

		@Parameter(description = "The current time zone of the Dialogue Branch user")
		@RequestParam(value="timeZone")
		String timeZone
	) throws Exception {

		// If no explicit protocol version is provided, assume the latest version
		if(version == null) version = ProtocolVersion.getLatestVersion().versionName();

		// Log this call to the service log
		logger.info("POST /v{}/variables/notify-cleared?subject={}&issuer={}&projectSlug={}&timeZone={}",
				version, subject, issuer, projectSlug, timeZone);

		// Execute the request if a subject was provided.
		if(subject.isEmpty()) {
			throw new BadRequestException("Missing 'subject' in request.");
		} else {
			return executeNotifyCleared(issuer, subject, projectSlug, timeZone);
		}

	}

	/**
	 * This method performs the Dummy implementation for receiving the notification that a
	 * Dialogue Branch Variable store has been completely cleared: it forgets the user's values of
	 * the {@code example.*} variables in the project.
	 *
	 * @param issuer the token issuer of the user whose variables were cleared.
	 * @param subject the {@code String} identifier of the user for whom variable updates are
	 *               available.
	 * @param timeZone the timeZone of the user as one of {@code TimeZone.getAvailableIDs()} (IANA
	 *                 Codes)
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @return a {@link ResponseEntity} to indicate whether the update was executed successfully.
	 */
	private ResponseEntity<?> executeNotifyCleared(String issuer, String subject,
			String projectSlug, String timeZone)
			throws BadRequestException {

		// Parse the timeZone String into a ZoneId to verify it was given in the right format
		ControllerFunctions.parseTimeZone(timeZone);
		exampleVariables.clear(issuer, subject, projectSlug);

		logger.info("Variable information for user '{}' in timeZone '{}' " +
				"was successfully cleared.", subject, timeZone);

		return new ResponseEntity<ResponseEntity<?>>(HttpStatus.OK);
	}

	// -------------------------------------------------------------------
	// -------------------- END-POINT: "supported" --------------------
	// -------------------------------------------------------------------

	/**
	 * Reports the Dialogue Branch Variables this service supports for a given project. This lets
	 * an authoring tool (e.g. Dialogue Branch Studio, via the Web Service's
	 * {@code /variables/list-supported} end-point) confirm that a variable referenced in a
	 * dialogue is actually one this service knows about, rather than a naming mismatch that would
	 * otherwise silently never receive a value.
	 *
	 * <p>The capability list this end-point reports is static and project-independent: it answers
	 * "can this service compute this variable at all", not "does this specific user have a value
	 * right now" (that distinction is answered by {@code retrieve-updates} instead). The
	 * {@code projectSlug} parameter is required by the contract even though this dummy
	 * implementation ignores it and always returns the same list &mdash; a real, multi-project
	 * External Variable Service may support different variables per project.</p>
	 *
	 * <p>In this dummy implementation, the reported list is the three variables
	 * {@code retrieveUpdates} always knows how to compute: {@code currentDate}, {@code
	 * currentTime}, and {@code dayPart}, each with the hints {@code type: string} and
	 * {@code readOnly: true}, and for {@code dayPart} its allowed {@code values}; followed by four
	 * {@code example.*} variables that show each hint once. This service keeps a value for each of
	 * them per user, starting with a random value of the declared type.</p>
	 *
	 * @param request the {@link HttpServletRequest} that generated the request.
	 * @param response the {@link HttpServletResponse} that generated the request.
	 * @param version the API Version to use, e.g. '1'.
	 * @param projectSlug the slug of the project to report supported variables for.
	 * @return the list of Dialogue Branch Variables this service supports.
	 * @throws Exception in case of a network or service error.
	 */
	@Operation(summary = "Report the Dialogue Branch Variables this service supports for a project",
		description = "Lets an authoring tool confirm that a variable referenced in a dialogue is " +
			"actually one this External Variable Service knows about." +
			"<br/><br/>The reported list is static and project-independent capability information " +
			"(\"can this service compute this variable at all\"), not a per-user value check " +
			"(that is what 'retrieve-updates' answers)." +
			"<br/><br/>In this dummy implementation, the reported list is always the three " +
			"variables that 'retrieve-updates' knows how to compute: 'currentDate', " +
			"'currentTime', and 'dayPart', each declared as a read-only string, with 'dayPart' " +
			"listing its allowed values; followed by four 'example.*' variables that show each " +
			"hint once, for which this service keeps a value per user, starting with a random " +
			"value of the declared type.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200",
			description = "Successful operation",
			content = @Content(
				array = @ArraySchema(
					schema = @Schema(implementation = SupportedVariablePayload.class)))) })
	@RequestMapping(value="/supported", method= RequestMethod.GET)
	public List<SupportedVariablePayload> getSupportedVariables(
		HttpServletRequest request,
		HttpServletResponse response,

		@Parameter(hidden = true, description = "API Version to use, e.g. '1'")
		@PathVariable(value = "version")
		String version,

		@Parameter(description = "The slug of the project to report supported variables for")
		@RequestParam(value="projectSlug")
		String projectSlug) throws Exception {

		// If no explicit protocol version is provided, assume the latest version
		if(version == null) version = ProtocolVersion.getLatestVersion().versionName();

		// Log this call to the service log
		logger.info("GET /v{}/variables/supported?projectSlug={}", version, projectSlug);

		if(projectSlug == null || projectSlug.isEmpty()) {
			throw new BadRequestException("Missing 'projectSlug' in request.");
		}

		// The first three are computed by this service, so a dialogue can't change them
		// (readOnly). The example.* variables show each hint once; dialogues may change them, and
		// this service keeps their values (see ExampleVariables).
		return List.of(
				new SupportedVariablePayload("currentDate",
						"Today's date (yyyy-MM-dd) in the user's time zone",
						"string", null, true),
				new SupportedVariablePayload("currentTime",
						"The current time (HH:mm:ss) in the user's time zone",
						"string", null, true),
				new SupportedVariablePayload("dayPart",
						"One of 'morning', 'afternoon', 'evening', or 'night', based on the " +
								"current hour in the user's time zone",
						"string", List.<Object>of("morning", "afternoon", "evening", "night"), true),
				new SupportedVariablePayload("example.number",
						"An example of a number that dialogues may change",
						"number", null, false),
				new SupportedVariablePayload("example.flag",
						"An example of a boolean that dialogues may change",
						"boolean", null, false),
				new SupportedVariablePayload("example.choice",
						"An example of a string limited to a fixed set of values",
						"string", List.<Object>of("a", "b", "c"), false),
				new SupportedVariablePayload("example.noHints",
						"An example of a variable declared without any hints"));
	}

}

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
import com.dialoguebranch.model.common.ReservedVariables;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.exception.*;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link ControllerFunctions} class offers a set of public, static methods that can be used by
 * the service's various REST controllers.
 *
 * @author Harm op den Akker
 */
public class ControllerFunctions {

	/** Utility class — not instantiated. */
	private ControllerFunctions() { }

	/**
	 * Returns the {@code 404} for a delegated read naming a user this service doesn't know. A
	 * delegated read never creates the user, unlike a request for the caller's own identity.
	 *
	 * @param userId the delegated user's identity.
	 * @return the exception to throw.
	 */
	public static NotFoundException unknownDelegateUser(DialogueBranchUserId userId) {
		return new NotFoundException(ErrorCode.UNKNOWN_USER,
				"No Dialogue Branch user with subject '" + userId.subject()
				+ "' is known in this realm.");
	}

	/**
	 * Parses a given String into a {@link ZoneId} time zone object. The given {@code timeZone}
	 * String should be formatted according to the rules defined in {@link ZoneId#of(String)}}.
	 * When given an empty string, or {@code null}, this method returns the default time zone as
	 * given by {@link ZoneId#systemDefault()}.
 	 *
	 * @param timeZone a String representation of a time zone.
	 * @return the time zone as a {@link ZoneId}
	 * @throws BadRequestException in case of a wrongly formatted {@code timeZone} string.
	 */
	public static ZoneId parseTimeZone(String timeZone) throws BadRequestException {

		if (timeZone == null || timeZone.isEmpty()) {
			return ZoneId.systemDefault();
		}

		List<HttpFieldError> errors = new ArrayList<>();

		ZoneId result = null;

		try {
			result = ZoneId.of(timeZone);
		} catch (ZoneRulesException zoneRulesException) {
			errors.add(new HttpFieldError("timeZone",
		"Invalid value for field \"timeZone\": " + timeZone + " (zone not recognized)."));
		} catch(DateTimeException dateTimeException) {
			errors.add(new HttpFieldError("timeZone",
		"Invalid value for field \"timeZone\": " + timeZone + " (format incorrect)."));
		}

		if(!errors.isEmpty()) {
			throw BadRequestException.withInvalidInput(errors);
		} else {
			return result;
		}

	}

	/**
	 * Refuses values that a reserved {@code $dlb.} variable doesn't allow, or an undefined
	 * reserved variable (#400), before anything is stored.
	 *
	 * @param variables the variables to set, by name.
	 * @throws BadRequestException with a field error per refused variable.
	 */
	public static void requireAllowedVariables(Map<String, ?> variables)
			throws BadRequestException {
		List<HttpFieldError> errors = new ArrayList<>();
		for (Map.Entry<String, ?> entry : variables.entrySet()) {
			String problem = ReservedVariables.check(entry.getKey(), entry.getValue());
			if (problem != null) errors.add(new HttpFieldError(entry.getKey(), problem));
		}
		if (!errors.isEmpty()) throw BadRequestException.withInvalidInput(errors);
	}

	/**
	 * Generates a {@link HttpException} with a valid HTTP Status Code from the given
	 * {@link ExecutionException}.
	 *
	 * @param exception the {@link ExecutionException} that should be "wrapped" into an {@link
	 *                  HttpException}.
	 * @return the {@link HttpException} object representing the error including a valid status
	 * 		   code.
	 */
	public static HttpException createHttpException(ExecutionException exception) {
		return switch (exception.getType()) {
			case AGENT_NOT_FOUND,
				DIALOGUE_NOT_FOUND,
				NODE_NOT_FOUND,
				REPLY_NOT_FOUND,
				NO_ACTIVE_DIALOGUE ->
					new NotFoundException(exception.getMessage());
			case DIALOGUE_CANCELLED,
				DIALOGUE_COMPLETED ->
					new BadRequestException(exception.getMessage());
			default ->
					throw new RuntimeException("Unexpected ExecutionException: " +
							exception.getMessage(), exception);
		};
	}

	/**
	 * Checks if the given {@code parameterNames} appear within the query string of the given {@link
	 * HttpServletRequest} object, and throws an {@link BadRequestException} if that is the case.
	 *
	 * @param request the HTTPRequest object (to retrieve authentication headers and optional body
	 *                parameters).
	 * @param parameterNames a list of parameter names for which to check their appearance.
	 * @throws BadRequestException if any of the given parameter names appear in the query string.
	 */
	public static void validateForbiddenQueryParams(HttpServletRequest request,
													String... parameterNames)
			throws BadRequestException {
		if (request.getQueryString() == null)
			return;
		Set<String> presentParams = new HashSet<>();
		for (String pair : request.getQueryString().split("&")) {
			if (pair.isEmpty())
				continue;
			int eq = pair.indexOf('=');
			String rawName = eq >= 0 ? pair.substring(0, eq) : pair;
			try {
				presentParams.add(URLDecoder.decode(rawName, StandardCharsets.UTF_8));
			} catch (IllegalArgumentException ex) {
				throw new BadRequestException("Malformed query string: " + ex.getMessage());
			}
		}
		for (String name : parameterNames) {
			if (presentParams.contains(name)) {
				throw new BadRequestException(
					"Query parameters not accepted, parameters must be set in the request body.");
			}
		}
	}

}

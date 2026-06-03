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

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.web.service.Configuration;
import com.dialoguebranch.web.service.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.http.URLParameters;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@link ControllerFunctions} class offers a set of public, static methods that can be used by
 * the service's various REST controllers.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ControllerFunctions {

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
		Map<String,String> params;
		try {
			params = URLParameters.parseParameterString(request.getQueryString());
		} catch (ParseException ex) {
			throw new BadRequestException(ex.getMessage());
		}
		for (String name : parameterNames) {
			if (params.containsKey(name)) {
				throw new BadRequestException(
					"Query parameters not accepted, parameters must be set in the request body.");
			}
		}
	}

	public static String extractAccessToken(HttpServletRequest request) throws UnauthorizedException {
		String token = request.getHeader("X-Auth-Token");

		// If we don't have the custom "X-Auth-Token", check if we have a standard authentication
		// token in the "Authorization" header.
		if(token == null || token.trim().isEmpty()) {
			String standardToken = request.getHeader("Authorization");
			if(standardToken != null) {
				if (standardToken.startsWith("Bearer ")) {
					standardToken = standardToken.substring(7);
					if(!standardToken.isEmpty()) token = standardToken;
				}
			}
		}

		if (token != null) {

			if (token.trim().isEmpty()) {
				throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_INVALID,
						"Authentication token invalid");
			} else {

				return token;
			}

		}

		throw new UnauthorizedException(ErrorCode.AUTH_TOKEN_NOT_FOUND,
				"Authentication token not found");
	}

}

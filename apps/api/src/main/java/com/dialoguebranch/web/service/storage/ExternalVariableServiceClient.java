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

package com.dialoguebranch.web.service.storage;

import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.controller.schema.SupportedVariableInfo;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.InternalServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Queries the configured External Variable Service (EVS) for the variables it supports for a
 * given project, via the EVS's {@code GET /v{version}/variables/supported?projectSlug=<slug>}
 * end-point. Every call is proxied through live, never cached: this query is not on the
 * dialogue-execution hot path (unlike {@code retrieve-updates}), so there is no latency pressure
 * to justify a second, potentially stale copy of the EVS's own answer.
 *
 * @author Harm op den Akker
 */
public class ExternalVariableServiceClient {

	private static final Logger logger =
			LoggerFactory.getLogger(ExternalVariableServiceClient.class);

	private final DlbProperties dlbProperties;

	/**
	 * Creates an instance of {@link ExternalVariableServiceClient}.
	 *
	 * @param dlbProperties the application configuration properties (external service URL,
	 *                      version and credentials).
	 */
	public ExternalVariableServiceClient(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
	}

	/**
	 * Returns the variables the configured External Variable Service reports as supported for the
	 * given project, fetched live on every call (see class Javadoc for why this is not cached).
	 *
	 * @param projectSlug the slug of the project to query supported variables for.
	 * @return the EVS's reported list of supported variables for {@code projectSlug}, in the order
	 *         the EVS returned them.
	 * @throws InternalServerErrorException if no External Variable Service is configured, or the
	 *                                       configured EVS could not be reached or returned an
	 *                                       error. This deliberately does not degrade to an empty
	 *                                       list: a client asking "what does the EVS support"
	 *                                       needs to distinguish "supports nothing" from "could not
	 *                                       find out", which are not the same fact.
	 */
	public List<SupportedVariableInfo> getSupportedVariables(String projectSlug)
			throws InternalServerErrorException {
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();

		if (!evs.isEnabled()) {
			throw new InternalServerErrorException(
					ErrorCode.EXTERNAL_VARIABLE_SERVICE_NOT_ENABLED,
					"No External Variable Service is configured for this deployment.");
		}

		String supportedUrl = evs.getUrl() + "/v" + evs.getApiVersion() + "/variables/supported";

		LinkedMultiValueMap<String, String> requestParams = new LinkedMultiValueMap<>();
		requestParams.put("projectSlug", Collections.singletonList(projectSlug));

		UriComponents uriComponents = UriComponentsBuilder.fromUriString(supportedUrl)
				.queryParams(requestParams)
				.build()
				.encode();

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.set("Authorization", "Bearer " + evs.getApiKey());
		HttpEntity<?> entity = new HttpEntity<>(requestHeaders);

		try {
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<SupportedVariableInfo[]> response = restTemplate.exchange(
					uriComponents.toUri(), HttpMethod.GET, entity,
					SupportedVariableInfo[].class);

			if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
				throw new InternalServerErrorException(
						ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
						"The External Variable Service did not return a supported-variables list "
								+ "for project '" + projectSlug + "'.");
			}
			return List.of(response.getBody());
		} catch (RestClientException ex) {
			logger.error("Error retrieving supported variables from the External Variable " +
					"Service for project '{}'.", projectSlug, ex);
			throw new InternalServerErrorException(
					ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
					"Could not reach the External Variable Service to determine supported "
							+ "variables for project '" + projectSlug + "'.");
		}
	}
}

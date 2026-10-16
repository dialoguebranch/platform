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

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.controller.schema.SupportedVariableInfo;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.NotImplementedException;
import com.dialoguebranch.web.service.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Makes the Web Service's requests to the configured External Variable Service (EVS):
 * <ul>
 * <li>{@link #retrieveUpdates}: asks for updated values of a dialogue's variables when it starts,
 * in real playback and in test sessions (#411);</li>
 * <li>{@link #notifyCleared}: tells it a user's variables in a project were cleared, also when a
 * user's data is erased (#320);</li>
 * <li>{@link #getSupportedVariables}: the variables it supports for a project, via its {@code GET
 * /v{version}/variables/supported?projectSlug=<slug>} end-point. Every call is proxied through
 * live, never cached: this query is not on the dialogue-execution hot path (unlike {@code
 * retrieve-updates}), so there is no latency pressure to justify a second, potentially stale copy
 * of the EVS's own answer.</li>
 * </ul>
 * Changes to a user's variables are sent by {@link ExternalVariableServiceUpdater}, the variable
 * store listener, using the same identity parameters.
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
	 * @throws NotImplementedException if no External Variable Service is configured.
	 * @throws ServiceUnavailableException if the configured EVS could not be reached or returned
	 *                                     an error. This deliberately does not degrade to an empty
	 *                                     list: a client asking "what does the EVS support" needs
	 *                                     to distinguish "supports nothing" from "could not find
	 *                                     out", which are not the same fact.
	 */
	public List<SupportedVariableInfo> getSupportedVariables(String projectSlug)
			throws NotImplementedException, ServiceUnavailableException {
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();

		if (!evs.isEnabled()) {
			throw new NotImplementedException(
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
				throw new ServiceUnavailableException(
						ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
						"The External Variable Service did not return a supported-variables list "
								+ "for project '" + projectSlug + "'.");
			}
			return List.of(response.getBody());
		} catch (RestClientException ex) {
			logger.error("Error retrieving supported variables from the External Variable " +
					"Service for project '{}'.", projectSlug, ex);
			throw new ServiceUnavailableException(
					ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
					"Could not reach the External Variable Service to determine supported "
							+ "variables for project '" + projectSlug + "'.");
		}
	}

	/**
	 * Tells the configured External Variable Service that a user's variables in a project were
	 * cleared ({@code notify-cleared}). Also used when a user's data is erased (#320).
	 *
	 * @param issuer the token issuer of the user.
	 * @param subject the OIDC {@code sub} of the user.
	 * @param projectSlug the slug of the project the variables were scoped to.
	 * @param timeZone the user's IANA time zone.
	 */
	public void notifyCleared(String issuer, String subject, String projectSlug, String timeZone) {
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.set("Authorization", "Bearer " + evs.getApiKey());

		String notifyClearedUrl = evs.getUrl()
				+ "/v" + evs.getApiVersion()
				+ "/variables/notify-cleared";

		LinkedMultiValueMap<String, String> allRequestParams = new LinkedMultiValueMap<>();
		addIdentityParams(allRequestParams, issuer, subject, projectSlug, timeZone);

		HttpEntity<?> entity = new HttpEntity<>(requestHeaders);
		UriComponentsBuilder builder =
				UriComponentsBuilder.fromUriString(notifyClearedUrl)
						.queryParams(allRequestParams);
		UriComponents uriComponents = builder.build().encode();

		restTemplate.exchange(uriComponents.toUri(), HttpMethod.POST, entity, Object.class);
	}

	/**
	 * Asks the configured External Variable Service for updated values of the given variables
	 * ({@code retrieve-updates}), sending the values currently known for them. Used at every
	 * dialogue start, both in real playback and in test sessions (#411), which the
	 * {@code testSession} parameter tells the service. A failure is logged, and treated as "no
	 * updates", so the dialogue continues with the values it has.
	 *
	 * @param issuer the token issuer of the user.
	 * @param subject the OIDC {@code sub} of the user.
	 * @param projectSlug the slug of the project the variables belong to.
	 * @param timeZone the user's IANA time zone.
	 * @param current the variables to ask about, with their currently known values (or {@link
	 *                Variable#unknown(String)} for one without a value).
	 * @param testSession whether the request comes from a test session rather than real playback.
	 * @return the variables with updated values; empty if there are none, or the request failed.
	 */
	public Variable[] retrieveUpdates(String issuer, String subject, String projectSlug,
			String timeZone, List<Variable> current, boolean testSession) {
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.valueOf("application/json"));
		requestHeaders.set("Authorization", "Bearer " + evs.getApiKey());

		String retrieveUpdatesUrl = evs.getUrl()
				+ "/v" + evs.getApiVersion()
				+ "/variables/retrieve-updates";

		LinkedMultiValueMap<String, String> allRequestParams = new LinkedMultiValueMap<>();
		addIdentityParams(allRequestParams, issuer, subject, projectSlug, timeZone);
		allRequestParams.put("testSession", Arrays.asList(String.valueOf(testSession)));

		HttpEntity<?> entity = new HttpEntity<>(current, requestHeaders);
		UriComponents uriComponents = UriComponentsBuilder.fromUriString(retrieveUpdatesUrl)
				.queryParams(allRequestParams).build().encode();

		try {
			ResponseEntity<Variable[]> response = new RestTemplate().exchange(
					uriComponents.toUri(), HttpMethod.POST, entity, Variable[].class);
			if (response.getStatusCode() != HttpStatus.OK) {
				logger.warn("Error retrieving updates from external variable service.");
			}
			Variable[] retrieved = response.getBody();
			return retrieved != null ? retrieved : new Variable[0];
		} catch (Exception e) {
			logger.error("Critical Error retrieving updates for Dialogue Branch Variables. " +
					"Continuing operation while assuming no updates were needed.", e);
			return new Variable[0];
		}
	}

	/**
	 * Writes updated values received from the External Variable Service into a variable store,
	 * with source {@link VariableUpdatedSource#EXTERNAL}: a {@code null} value removes the
	 * variable.
	 *
	 * @param store the variable store to update.
	 * @param updates the updated variables, as returned by {@link #retrieveUpdates}.
	 */
	public static void applyUpdates(VariableStore store, Variable[] updates) {
		for (Variable variable : updates) {
			logger.info(variable.toString());
			if (variable.getValue() != null) {
				store.setValue(variable.getName(), variable.getValue(), true,
						variable.getZonedUpdatedTime(), VariableUpdatedSource.EXTERNAL);
			} else {
				store.removeByName(variable.getName(), true, variable.getZonedUpdatedTime(),
						VariableUpdatedSource.EXTERNAL);
			}
		}
	}

	/** Adds the {@code (issuer, subject, projectSlug)} identity parameters to an EVS request. */
	static void addIdentityParams(LinkedMultiValueMap<String, String> params,
			String issuer, String subject, String projectSlug, String userTimeZoneString) {
		params.put("issuer", Arrays.asList(issuer));
		params.put("subject", Arrays.asList(subject));
		params.put("projectSlug", Arrays.asList(projectSlug));
		params.put("timeZone", Arrays.asList(userTimeZoneString));
	}
}

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
import com.dialoguebranch.web.service.DateTimeUtils;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.controller.schema.SupportedVariableInfo;
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.NotImplementedException;
import com.dialoguebranch.web.service.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Makes every request of the Web Service to the configured External Variable Service (EVS),
 * through one HTTP client with a connect and a read timeout ({@code
 * dlb.external-variable-service.connect-timeout} and {@code read-timeout}):
 * <ul>
 * <li>{@link #retrieveUpdates}: asks for updated values of a dialogue's variables when it starts,
 * in real playback and in test sessions (#411);</li>
 * <li>{@link #notifyUpdated}: tells it a user's variables were set or removed;</li>
 * <li>{@link #notifyCleared}: tells it a user's variables in a project were cleared, also when a
 * user's data is erased (#320);</li>
 * <li>{@link #getSupportedVariables}: the variables it supports for a project, via its {@code GET
 * /v{version}/variables/supported?projectSlug=<slug>} end-point. Every call is proxied through
 * live, never cached: this query is not on the dialogue-execution hot path (unlike {@code
 * retrieve-updates}), so there is no latency pressure to justify a second, potentially stale copy
 * of the EVS's own answer.</li>
 * </ul>
 * The service is an outside system that may be slow or down, and must never break a dialogue
 * (#219): a failed or timed-out {@code retrieve-updates}, {@code notify-updated} or {@code
 * notify-cleared} is logged, and the dialogue, the change or the erase goes ahead. Only {@link
 * #getSupportedVariables}, an explicit question, reports a failure to its caller.
 *
 * @author Harm op den Akker
 */
@Component
public class ExternalVariableServiceClient {

	private static final Logger logger =
			LoggerFactory.getLogger(ExternalVariableServiceClient.class);

	private final DlbProperties dlbProperties;
	private final RestClient restClient;

	/**
	 * Creates an instance of {@link ExternalVariableServiceClient}.
	 *
	 * @param dlbProperties the application configuration properties (external service URL,
	 *                      version, credentials and timeouts).
	 */
	public ExternalVariableServiceClient(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(evs.getConnectTimeout());
		requestFactory.setReadTimeout(evs.getReadTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/**
	 * Returns whether an External Variable Service is configured.
	 *
	 * @return {@code true} if the integration is enabled.
	 */
	public boolean isEnabled() {
		return dlbProperties.getExternalVariableService().isEnabled();
	}

	/**
	 * Returns the variables the configured External Variable Service reports as supported for the
	 * given project, fetched live on every call (see class Javadoc for why this is not cached).
	 *
	 * @param projectSlug the slug of the project to query supported variables for.
	 * @return the EVS's reported list of supported variables for {@code projectSlug}, in the order
	 *         the EVS returned them.
	 * @throws NotImplementedException if no External Variable Service is configured.
	 * @throws ServiceUnavailableException if the configured EVS could not be reached, did not
	 *                                     answer in time or returned an error. This deliberately
	 *                                     does not degrade to an empty list: a client asking "what
	 *                                     does the EVS support" needs to distinguish "supports
	 *                                     nothing" from "could not find out", which are not the
	 *                                     same fact.
	 */
	public List<SupportedVariableInfo> getSupportedVariables(String projectSlug)
			throws NotImplementedException, ServiceUnavailableException {
		if (!isEnabled()) {
			throw new NotImplementedException(
					ErrorCode.EXTERNAL_VARIABLE_SERVICE_NOT_ENABLED,
					"No External Variable Service is configured for this deployment.");
		}

		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.put("projectSlug", Collections.singletonList(projectSlug));

		SupportedVariableInfo[] supported;
		try {
			supported = restClient.get()
					.uri(uri("supported", params))
					.header(HttpHeaders.AUTHORIZATION, authorization())
					.retrieve()
					.body(SupportedVariableInfo[].class);
		} catch (RestClientException ex) {
			logger.error("Error retrieving supported variables from the External Variable " +
					"Service for project '{}'.", projectSlug, ex);
			throw new ServiceUnavailableException(
					ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
					"Could not reach the External Variable Service to determine supported "
							+ "variables for project '" + projectSlug + "'.");
		}
		if (supported == null) {
			throw new ServiceUnavailableException(
					ErrorCode.EXTERNAL_VARIABLE_SERVICE_UNREACHABLE,
					"The External Variable Service did not return a supported-variables list "
							+ "for project '" + projectSlug + "'.");
		}
		return List.of(supported);
	}

	/**
	 * Tells the configured External Variable Service that a user's variables were set or removed
	 * ({@code notify-updated}): a removed variable is sent with a {@code null} value. A failure is
	 * logged, and the change stays in effect in the Web Service.
	 *
	 * @param issuer the token issuer of the user.
	 * @param subject the OIDC {@code sub} of the user.
	 * @param projectSlug the slug of the project the variables belong to.
	 * @param timeZone the user's IANA time zone.
	 * @param variables the changed variables.
	 */
	public void notifyUpdated(String issuer, String subject, String projectSlug, String timeZone,
			List<Variable> variables) {
		try {
			restClient.post()
					.uri(uri("notify-updated", identityParams(issuer, subject, projectSlug,
							timeZone)))
					.header(HttpHeaders.AUTHORIZATION, authorization())
					.contentType(MediaType.APPLICATION_JSON)
					.body(variables)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException ex) {
			logger.warn("Could not notify the External Variable Service of {} changed " +
					"variable(s) of user '{}' in project '{}'; the change stays in effect here: {}",
					variables.size(), subject, projectSlug, ex.getMessage());
		}
	}

	/**
	 * Tells the configured External Variable Service that a user's variables in a project were
	 * cleared ({@code notify-cleared}). Also used when a user's data is erased (#320). A failure
	 * is logged, and the clear or erase stays in effect.
	 *
	 * @param issuer the token issuer of the user.
	 * @param subject the OIDC {@code sub} of the user.
	 * @param projectSlug the slug of the project the variables were scoped to.
	 * @param timeZone the user's IANA time zone.
	 */
	public void notifyCleared(String issuer, String subject, String projectSlug, String timeZone) {
		try {
			restClient.post()
					.uri(uri("notify-cleared", identityParams(issuer, subject, projectSlug,
							timeZone)))
					.header(HttpHeaders.AUTHORIZATION, authorization())
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException ex) {
			logger.warn("Could not notify the External Variable Service that the variables of " +
					"user '{}' in project '{}' were cleared; they stay cleared here: {}",
					subject, projectSlug, ex.getMessage());
		}
	}

	/**
	 * Asks the configured External Variable Service for updated values of the given variables
	 * ({@code retrieve-updates}), sending the values currently known for them. Used at every
	 * dialogue start, both in real playback and in test sessions (#411), which the
	 * {@code testSession} parameter tells the service. A failure or timeout is logged, and treated
	 * as "no updates", so the dialogue starts with the values it has.
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
		LinkedMultiValueMap<String, String> params =
				identityParams(issuer, subject, projectSlug, timeZone);
		params.put("testSession", Collections.singletonList(String.valueOf(testSession)));
		try {
			Variable[] retrieved = restClient.post()
					.uri(uri("retrieve-updates", params))
					.header(HttpHeaders.AUTHORIZATION, authorization())
					.contentType(MediaType.APPLICATION_JSON)
					.body(current)
					.retrieve()
					.body(Variable[].class);
			return retrieved != null ? retrieved : new Variable[0];
		} catch (RestClientException ex) {
			logger.warn("Could not retrieve variable updates from the External Variable Service " +
					"for user '{}' in project '{}'; the dialogue starts with the values it has: {}",
					subject, projectSlug, ex.getMessage());
			return new Variable[0];
		}
	}

	/**
	 * Writes updated values received from the External Variable Service into a variable store,
	 * with source {@link VariableUpdatedSource#EXTERNAL}: a {@code null} value removes the
	 * variable. Only the name and value of an update are used: it is stored as updated now, when
	 * this service received it, in the user's time zone, as for every other source (#321). An
	 * {@code updatedTime} the External Variable Service sends is ignored.
	 *
	 * @param store the variable store to update.
	 * @param updates the updated variables, as returned by {@link #retrieveUpdates}.
	 */
	public static void applyUpdates(VariableStore store, Variable[] updates) {
		if (updates.length == 0) return;
		ZonedDateTime received = DateTimeUtils.nowMs(store.getUser().getTimeZone());
		for (Variable variable : updates) {
			logger.info(variable.toString());
			if (variable.getValue() != null) {
				store.setValue(variable.getName(), variable.getValue(), true, received,
						VariableUpdatedSource.EXTERNAL);
			} else {
				store.removeByName(variable.getName(), true, received,
						VariableUpdatedSource.EXTERNAL);
			}
		}
	}

	private URI uri(String endPoint, LinkedMultiValueMap<String, String> params) {
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		return UriComponentsBuilder
				.fromUriString(evs.getUrl() + "/v" + evs.getApiVersion() + "/variables/" + endPoint)
				.queryParams(params)
				.build()
				.encode()
				.toUri();
	}

	private String authorization() {
		return "Bearer " + dlbProperties.getExternalVariableService().getApiKey();
	}

	/** The {@code (issuer, subject, projectSlug, timeZone)} identity parameters of a request. */
	private static LinkedMultiValueMap<String, String> identityParams(String issuer,
			String subject, String projectSlug, String timeZone) {
		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.put("issuer", Collections.singletonList(issuer));
		params.put("subject", Collections.singletonList(subject));
		params.put("projectSlug", Collections.singletonList(projectSlug));
		params.put("timeZone", Collections.singletonList(timeZone));
		return params;
	}
}

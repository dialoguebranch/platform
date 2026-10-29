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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
 * <p>Every call carries an OAuth2 access token as {@code Authorization: Bearer}, which this client
 * gets with the client-credentials grant ({@code dlb.external-variable-service.oauth2.*}), caches,
 * and fetches again shortly before it expires (#428). Getting a token is part of the call: if the
 * authorization server can't be reached or refuses, the call fails like any other. A {@code 401}
 * from the service discards the cached token, so the next call gets a fresh one.</p>
 *
 * @author Harm op den Akker
 */
@Component
public class ExternalVariableServiceClient {

	private static final Logger logger =
			LoggerFactory.getLogger(ExternalVariableServiceClient.class);

	/** The registration id of the client-credentials client; internal to this class. */
	private static final String REGISTRATION_ID = "external-variable-service";

	/** The principal the token is cached under: the Web Service itself, not a user. */
	private static final String PRINCIPAL = "dlb-web-service";

	private final DlbProperties dlbProperties;
	private final SimpleClientHttpRequestFactory requestFactory;
	private final RestClient restClient;
	private TokenSource tokenSource;

	/**
	 * Creates an instance of {@link ExternalVariableServiceClient}.
	 *
	 * @param dlbProperties the application configuration properties (external service URL,
	 *                      version, OAuth2 client and timeouts).
	 * @throws IllegalStateException if the integration is enabled without its required OAuth2
	 *                               settings, which stops the Web Service at startup.
	 */
	public ExternalVariableServiceClient(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		if (evs.isEnabled()) checkOAuth2Settings(evs.getOauth2());
		requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(evs.getConnectTimeout());
		requestFactory.setReadTimeout(evs.getReadTimeout());
		this.restClient = RestClient.builder()
				.requestFactory(requestFactory)
				.requestInterceptor((request, body, execution) -> {
					ClientHttpResponse response = execution.execute(request, body);
					if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
						forgetToken();
					}
					return response;
				})
				.build();
	}

	/**
	 * Checks that the required OAuth2 settings are there.
	 *
	 * @param oauth2 the OAuth2 settings.
	 * @throws IllegalStateException naming the settings that are missing.
	 */
	static void checkOAuth2Settings(DlbProperties.ExternalVariableService.OAuth2 oauth2) {
		List<String> missing = new ArrayList<>();
		if (!StringUtils.hasText(oauth2.getTokenUri())) missing.add("token-uri");
		if (!StringUtils.hasText(oauth2.getClientId())) missing.add("client-id");
		if (!StringUtils.hasText(oauth2.getClientSecret())) missing.add("client-secret");
		if (!missing.isEmpty()) {
			throw new IllegalStateException("The External Variable Service is enabled " +
					"(dlb.external-variable-service.enabled), but dlb.external-variable-service" +
					".oauth2." + String.join(", ", missing) + " is not set: the Web Service " +
					"needs an OAuth2 client to get the access token for its calls.");
		}
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

	/**
	 * The {@code Authorization} header of a call: the cached access token, or a new one if there
	 * is none or it is about to expire.
	 *
	 * @throws RestClientException if no token could be got, so that every call handles it as it
	 *                             handles any other failure.
	 */
	private String authorization() {
		DlbProperties.ExternalVariableService.OAuth2 oauth2 =
				dlbProperties.getExternalVariableService().getOauth2();
		try {
			checkOAuth2Settings(oauth2);
			OAuth2AuthorizedClient client = tokenSource(oauth2).manager().authorize(
					OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
							.principal(PRINCIPAL)
							.build());
			if (client == null) throw new RestClientException("No access token was issued");
			return "Bearer " + client.getAccessToken().getTokenValue();
		} catch (OAuth2AuthorizationException | IllegalStateException ex) {
			throw new RestClientException("Could not get an access token for the External " +
					"Variable Service from " + oauth2.getTokenUri() + ": " + ex.getMessage(), ex);
		}
	}

	/** Discards the cached access token, so that the next call gets a new one. */
	private synchronized void forgetToken() {
		if (tokenSource != null) {
			tokenSource.clients().removeAuthorizedClient(REGISTRATION_ID, PRINCIPAL);
		}
	}

	/**
	 * The token source for the current OAuth2 settings, made on first use, and made again when
	 * the settings change.
	 */
	private synchronized TokenSource tokenSource(DlbProperties.ExternalVariableService.OAuth2 oauth2) {
		TokenSettings settings = new TokenSettings(oauth2.getTokenUri(), oauth2.getClientId(),
				oauth2.getClientSecret(), oauth2.getScope());
		if (tokenSource == null || !tokenSource.settings().equals(settings)) {
			tokenSource = TokenSource.create(settings, requestFactory);
		}
		return tokenSource;
	}

	/** The OAuth2 settings a {@link TokenSource} was made for. */
	private record TokenSettings(String tokenUri, String clientId, String clientSecret,
			String scope) { }

	/**
	 * Gets, caches and refreshes the client-credentials access token: Spring Security's {@link
	 * OAuth2AuthorizedClientManager} fetches a new token when there is none, or when the cached one
	 * expires within a minute.
	 */
	private record TokenSource(TokenSettings settings, OAuth2AuthorizedClientManager manager,
			OAuth2AuthorizedClientService clients) {

		static TokenSource create(TokenSettings settings,
				SimpleClientHttpRequestFactory requestFactory) {
			ClientRegistration.Builder registration = ClientRegistration
					.withRegistrationId(REGISTRATION_ID)
					.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
					.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
					.tokenUri(settings.tokenUri())
					.clientId(settings.clientId())
					.clientSecret(settings.clientSecret());
			if (StringUtils.hasText(settings.scope())) {
				registration.scope(StringUtils.tokenizeToStringArray(settings.scope(), " ,"));
			}
			ClientRegistrationRepository registrations =
					new InMemoryClientRegistrationRepository(registration.build());
			OAuth2AuthorizedClientService clients =
					new InMemoryOAuth2AuthorizedClientService(registrations);

			// The token request gets the same timeouts as the calls themselves.
			RestClientClientCredentialsTokenResponseClient tokenResponseClient =
					new RestClientClientCredentialsTokenResponseClient();
			tokenResponseClient.setRestClient(RestClient.builder()
					.requestFactory(requestFactory)
					.messageConverters(converters -> {
						converters.clear();
						converters.add(new FormHttpMessageConverter());
						converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
					})
					.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
					.build());
			ClientCredentialsOAuth2AuthorizedClientProvider provider =
					new ClientCredentialsOAuth2AuthorizedClientProvider();
			provider.setAccessTokenResponseClient(tokenResponseClient);

			AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
					new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
			manager.setAuthorizedClientProvider(provider);
			return new TokenSource(settings, manager, clients);
		}
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

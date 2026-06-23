/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.execution.VariableStoreOnChangeListener;
import com.dialoguebranch.web.service.DlbProperties;
import nl.rrd.utils.AppComponents;
import org.slf4j.Logger;
import org.springframework.http.*;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * A {@link VariableStoreOnChangeListener} that forwards Dialogue Branch Variable changes to the
 * configured external variable service. When a variable is set, removed, or the store is cleared,
 * this updater notifies the external service via the appropriate REST endpoint so that downstream
 * systems remain in sync.
 *
 * @author Harm op den Akker
 */
public class ExternalVariableServiceUpdater implements VariableStoreOnChangeListener {

	private final Logger logger =
			AppComponents.getLogger(ClassUtils.getUserClass(getClass()).getSimpleName());
	private final DlbProperties dlbProperties;

	/**
	 * Creates an instance of {@link ExternalVariableServiceUpdater} using the given application
	 * configuration properties to determine the external service URL and credentials.
	 *
	 * @param dlbProperties the application configuration properties.
	 */
	public ExternalVariableServiceUpdater(DlbProperties dlbProperties) {
		this.dlbProperties = dlbProperties;
	}

	/**
	 * Processes a list of variable store changes and notifies the external variable service of
	 * any additions, removals, or clears that did not originate from the external service itself.
	 *
	 * @param variableStore the variable store in which the changes occurred.
	 * @param changes the list of changes that have been applied to the variable store.
	 */
	@Override
	public void onChange(VariableStore variableStore, List<VariableStoreChange> changes) {

		DlbProperties.ExternalVariableService evs = dlbProperties.getExternalVariableService();
		String userId = variableStore.getUser().getId();
		String userTimeZoneString = variableStore.getUser().getTimeZone().toString();

		List<Variable> variablesToUpdate = new ArrayList<>();

		for (VariableStoreChange change : changes) {
			VariableStoreChange.Source source = change.getSource();

			if (!source.equals(VariableStoreChange.Source.EXTERNAL_VARIABLE_SERVICE)) {

				if (evs.isEnabled()) {
					logger.info("An external Dialogue Branch Variable Service is enabled at {}/v{}/",
							evs.getUrl(), evs.getApiVersion());

					if (change instanceof VariableStoreChange.Clear) {
						RestTemplate restTemplate = new RestTemplate();
						HttpHeaders requestHeaders = new HttpHeaders();
						requestHeaders.set("Authorization", "Bearer " + evs.getApiKey());

						String notifyClearedUrl = evs.getUrl()
								+ "/v" + evs.getApiVersion()
								+ "/variables/notify-cleared";

						LinkedMultiValueMap<String, String> allRequestParams =
								new LinkedMultiValueMap<>();
						allRequestParams.put("userId", Arrays.asList(userId));
						allRequestParams.put("timeZone", Arrays.asList(userTimeZoneString));

						HttpEntity<?> entity = new HttpEntity<>(requestHeaders);
						UriComponentsBuilder builder =
								UriComponentsBuilder.fromUriString(notifyClearedUrl)
										.queryParams(
												(LinkedMultiValueMap<String, String>) allRequestParams);
						UriComponents uriComponents = builder.build().encode();

						ResponseEntity<Object> response = restTemplate.exchange(
								uriComponents.toUri(),
								HttpMethod.POST,
								entity,
								Object.class);

					} else if (change instanceof VariableStoreChange.Remove) {
						Collection<String> variableNames =
								((VariableStoreChange.Remove) change).getVariableNames();

						for (String variableName : variableNames) {
							long updatedTime = change.getTime().toEpochSecond() * 1000;
							variablesToUpdate.add(
									new Variable(variableName, null, updatedTime,
											userTimeZoneString));
						}
					} else if (change instanceof VariableStoreChange.Put) {
						Map<String, Object> changedVariables =
								((VariableStoreChange.Put) change).getVariables();

						long updatedTime = change.getTime().toEpochSecond() * 1000;

						for (String variableName : changedVariables.keySet()) {
							Object variableValue = changedVariables.get(variableName);
							variablesToUpdate.add(
									new Variable(variableName, variableValue, updatedTime,
											userTimeZoneString));
						}
					}
				}
			}
		}

		if (!variablesToUpdate.isEmpty()) {
			RestTemplate restTemplate = new RestTemplate();
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.valueOf("application/json"));
			requestHeaders.set("Authorization", "Bearer " + evs.getApiKey());

			String notifyUpdatesUrl = evs.getUrl()
					+ "/v" + evs.getApiVersion()
					+ "/variables/notify-updated";

			LinkedMultiValueMap<String, String> allRequestParams = new LinkedMultiValueMap<>();
			allRequestParams.put("userId", Arrays.asList(userId));
			allRequestParams.put("timeZone", Arrays.asList(userTimeZoneString));

			HttpEntity<?> entity = new HttpEntity<>(variablesToUpdate, requestHeaders);
			UriComponentsBuilder builder =
					UriComponentsBuilder.fromUriString(notifyUpdatesUrl)
							.queryParams((LinkedMultiValueMap<String, String>) allRequestParams);
			UriComponents uriComponents = builder.build().encode();

			ResponseEntity<Object> response = restTemplate.exchange(
					uriComponents.toUri(),
					HttpMethod.POST,
					entity,
					Object.class);
		}
	}
}

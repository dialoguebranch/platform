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
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.execution.VariableStoreOnChangeListener;
import com.dialoguebranch.execution.VariableUpdatedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link VariableStoreOnChangeListener} that forwards Dialogue Branch Variable changes to the
 * configured external variable service. When a variable is set, removed, or the store is cleared,
 * this updater notifies the external service through the {@link ExternalVariableServiceClient},
 * so that downstream systems remain in sync. Changes that came from the external service itself
 * are not sent back.
 *
 * <p>It runs inside a dialogue turn, so it never lets a failure escape: the client already logs a
 * failed or timed-out call, and anything else is logged here (#219).</p>
 *
 * @author Harm op den Akker
 */
public class ExternalVariableServiceUpdater implements VariableStoreOnChangeListener {

	private static final Logger logger =
			LoggerFactory.getLogger(ExternalVariableServiceUpdater.class);
	private final ExternalVariableServiceClient client;
	private final String issuer;
	private final String subject;
	private final String projectSlug;

	/**
	 * Creates an instance of {@link ExternalVariableServiceUpdater} scoped to one user's variable
	 * store within one project.
	 *
	 * @param client the client that makes the requests to the external variable service.
	 * @param issuer the token issuer (full OIDC {@code iss} URL) of the user who owns the store.
	 * @param subject the OIDC {@code sub} of that user.
	 * @param projectSlug the slug of the project the variable store is scoped to.
	 */
	public ExternalVariableServiceUpdater(ExternalVariableServiceClient client, String issuer,
			String subject, String projectSlug) {
		this.client = client;
		this.issuer = issuer;
		this.subject = subject;
		this.projectSlug = projectSlug;
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
		if (!client.isEnabled()) return;
		try {
			notify(variableStore.getUser().getTimeZone().toString(), changes);
		} catch (RuntimeException ex) {
			logger.error("Failed to notify the External Variable Service of changed variables of " +
					"user '{}' in project '{}': {}", subject, projectSlug, ex.getMessage(), ex);
		}
	}

	private void notify(String timeZone, List<VariableStoreChange> changes) {
		List<Variable> variablesToUpdate = new ArrayList<>();
		for (VariableStoreChange change : changes) {
			if (change.getSource().equals(VariableUpdatedSource.EXTERNAL)) continue;
			long updatedTime = change.getTime().toEpochSecond() * 1000;

			if (change instanceof VariableStoreChange.Clear) {
				client.notifyCleared(issuer, subject, projectSlug, timeZone);
			} else if (change instanceof VariableStoreChange.Remove remove) {
				for (String variableName : remove.getVariableNames()) {
					variablesToUpdate.add(new Variable(variableName, null, updatedTime, timeZone,
							change.getSource()));
				}
			} else if (change instanceof VariableStoreChange.Put put) {
				for (Map.Entry<String, Object> entry : put.getVariables().entrySet()) {
					variablesToUpdate.add(new Variable(entry.getKey(), entry.getValue(),
							updatedTime, timeZone, change.getSource()));
				}
			}
		}
		if (!variablesToUpdate.isEmpty()) {
			client.notifyUpdated(issuer, subject, projectSlug, timeZone, variablesToUpdate);
		}
	}
}

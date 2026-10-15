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

import com.dialoguebranch.web.service.controller.schema.ErasedUserSummary;
import com.dialoguebranch.web.service.repository.DBLoggedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
import com.dialoguebranch.web.service.repository.DBVariableRepository;
import com.dialoguebranch.web.service.storage.model.DBUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Erases everything this service stores about one user (#320): their variables, variable history
 * and dialogue logs in every project, and the user itself. Versions the user published stay, but
 * no longer say who published them. Can't be undone.
 *
 * @author Harm op den Akker
 */
@Service
public class UserDataEraseService {

	/** The time zone to send the External Variable Service when a user's is unknown. */
	private static final String DEFAULT_TIME_ZONE = "UTC";

	private final DBUserRepository userRepository;
	private final DBVariableRepository variableRepository;
	private final DBVariableHistoryRepository variableHistoryRepository;
	private final DBLoggedDialogueRepository loggedDialogueRepository;
	private final DBProjectVersionRepository projectVersionRepository;

	/**
	 * The result of erasing a user.
	 *
	 * @param summary how much was erased.
	 * @param clearedProjects the slugs of the projects the user had variables in, each with the
	 *                        user's last known time zone there: the External Variable Service is
	 *                        told these were cleared.
	 */
	public record ErasedUser(ErasedUserSummary summary, Map<String, String> clearedProjects) {
	}

	/**
	 * Instances of this class are constructed through Spring.
	 *
	 * @param userRepository the users.
	 * @param variableRepository the users' variables.
	 * @param variableHistoryRepository the users' variable history.
	 * @param loggedDialogueRepository the users' dialogue logs.
	 * @param projectVersionRepository the published project versions.
	 */
	public UserDataEraseService(DBUserRepository userRepository,
			DBVariableRepository variableRepository,
			DBVariableHistoryRepository variableHistoryRepository,
			DBLoggedDialogueRepository loggedDialogueRepository,
			DBProjectVersionRepository projectVersionRepository) {
		this.userRepository = userRepository;
		this.variableRepository = variableRepository;
		this.variableHistoryRepository = variableHistoryRepository;
		this.loggedDialogueRepository = loggedDialogueRepository;
		this.projectVersionRepository = projectVersionRepository;
	}

	/**
	 * Erases the user with the given identity and all of their data. All or nothing.
	 *
	 * @param issuer the user's token issuer.
	 * @param subject the user's OIDC {@code sub}.
	 * @return what was erased, or empty if there is no such user.
	 */
	@Transactional
	public Optional<ErasedUser> erase(String issuer, String subject) {
		Optional<DBUser> found = userRepository.findByIssuerAndSubject(issuer, subject);
		if (found.isEmpty()) return Optional.empty();
		DBUser user = found.get();

		Map<String, String> clearedProjects = new LinkedHashMap<>();
		for (DBVariableRepository.ProjectTimeZone variable :
				variableRepository.findProjectTimeZonesByUser(user)) {
			String timeZone = variable.getTimeZone();
			clearedProjects.merge(variable.getProjectSlug(),
					timeZone != null ? timeZone : DEFAULT_TIME_ZONE,
					(kept, other) -> kept.equals(DEFAULT_TIME_ZONE) ? other : kept);
		}

		int history = variableHistoryRepository.deleteByUser(user);
		int variables = variableRepository.deleteByUser(user);
		int logs = loggedDialogueRepository.deleteByUser(user);
		projectVersionRepository.clearPublishedBy(user);
		userRepository.delete(user);

		return Optional.of(new ErasedUser(
				new ErasedUserSummary(variables, history, logs), clearedProjects));
	}

}

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

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.json.JsonMapper;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBUser;
import com.dialoguebranch.web.service.storage.model.DBVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link VariableStoreDatabaseStorageHandler} can manage reading and writing
 * {@link VariableStore}s to and from a MariaDB database via Hibernate. Each Dialogue Branch
 * {@link User} is mapped to a {@link DBUser} row (created on first access), and each
 * {@link Variable} in the user's store is persisted as one {@link DBVariable} row linked to
 * that user.
 *
 * @author Harm op den Akker
 */
@Service
public class VariableStoreDatabaseStorageHandler {

	private static final Logger logger =
			LoggerFactory.getLogger(VariableStoreDatabaseStorageHandler.class);

	private final DBUserRepository userRepository;
	private final DBVariableRepository variableRepository;
	private final DBProjectRepository projectRepository;

	/**
	 * Creates an instance of a {@link VariableStoreDatabaseStorageHandler} that reads and writes
	 * variable stores through the given repositories.
	 *
	 * @param userRepository repository used to look up or create the {@link DBUser} that owns
	 *                        the variables being read or written.
	 * @param variableRepository repository used to read, create, update, and delete
	 *                            {@link DBVariable} rows.
	 * @param projectRepository repository used to resolve the {@link DBProject} a variable store
	 *                          is scoped to.
	 */
	public VariableStoreDatabaseStorageHandler(DBUserRepository userRepository,
											   DBVariableRepository variableRepository,
											   DBProjectRepository projectRepository) {
		this.userRepository = userRepository;
		this.variableRepository = variableRepository;
		this.projectRepository = projectRepository;
	}

	/**
	 * Reads the stored Dialogue Branch Variables for the given user within the given project and
	 * returns them as a populated {@link VariableStore}.
	 *
	 * @param id the identity of the user for whom to load the variable store.
	 * @param projectSlug the slug of the project the variables are scoped to.
	 * @param user the core {@link User} the returned store is bound to.
	 * @return a {@link VariableStore} populated with the user's project variables.
	 * @throws ParseException if a variable value cannot be deserialized from its stored JSON
	 * representation.
	 */
	@Transactional
	public VariableStore read(DialogueBranchUserId id, String projectSlug, User user)
			throws ParseException {
		DBUser dbUser = getOrCreateUser(id);
		DBProject dbProject = getProject(projectSlug);
		List<DBVariable> dbVariables = variableRepository.findByUserAndProject(dbUser, dbProject);

		List<Variable> variables = new ArrayList<>();
		for (DBVariable dbVariable : dbVariables) {
			VariableUpdatedSource source = dbVariable.getUpdatedSource() != null
					? dbVariable.getUpdatedSource() : VariableUpdatedSource.UNKNOWN;
			variables.add(new Variable(
					dbVariable.getName(),
					JsonMapper.parse(dbVariable.getValue(), Object.class),
					dbVariable.getUpdatedTime(),
					dbVariable.getUpdatedTimeZone(),
					source));
		}
		return new VariableStore(user, variables.toArray(new Variable[0]));
	}

	/**
	 * Writes the full contents of the given {@link VariableStore} to the database, deleting any
	 * existing rows for variables no longer present in the store and creating or updating a row
	 * for each current variable.
	 *
	 * @param id the identity of the user who owns the variable store.
	 * @param projectSlug the slug of the project the variable store is scoped to.
	 * @param variableStore the variable store to persist.
	 * @throws IOException if a variable value cannot be serialized to JSON for storage.
	 */
	@Transactional
	public void write(DialogueBranchUserId id, String projectSlug, VariableStore variableStore)
			throws IOException {
		DBUser dbUser = getOrCreateUser(id);
		DBProject dbProject = getProject(projectSlug);
		List<DBVariable> existingVars = variableRepository.findByUserAndProject(dbUser, dbProject);

		Set<String> newVarNames = Arrays.stream(variableStore.getVariables())
				.map(Variable::getName)
				.collect(Collectors.toSet());

		// delete variables no longer present in the store
		existingVars.stream()
				.filter(v -> !newVarNames.contains(v.getName()))
				.forEach(variableRepository::delete);

		// create or update current variables
		for (Variable variable : variableStore.getVariables()) {
			DBVariable dbVariable = variableRepository
					.findByUserAndProjectAndName(dbUser, dbProject, variable.getName())
					.orElse(new DBVariable(variable.getName(), null));
			dbVariable.setUser(dbUser);
			dbVariable.setProject(dbProject);
			dbVariable.setValue(JsonMapper.generate(variable.getValue()));
			dbVariable.setUpdatedTime(variable.getUpdatedTime());
			dbVariable.setUpdatedTimeZone(variable.getUpdatedTimeZone());
			dbVariable.setUpdatedSource(variable.getUpdatedSource() != null
					? variable.getUpdatedSource() : VariableUpdatedSource.UNKNOWN);
			variableRepository.save(dbVariable);
		}
	}

	/**
	 * Resolves the {@link DBUser} row for {@code id}, creating it on first access. When the row
	 * exists and the token carried a {@code preferred_username} that differs from the stored one,
	 * the stored value is refreshed (a rename). Paths with no user token in hand — a delegated
	 * user, the External Variable Service callback — pass a {@code null} username and never
	 * trigger a refresh.
	 */
	/**
	 * Resolves the {@link DBProject} a variable store is scoped to.
	 *
	 * @param projectSlug the project slug.
	 * @return the project.
	 * @throws IllegalStateException if no project with that slug exists (the caller has already
	 *     validated the slug against a loaded project by this point).
	 */
	private DBProject getProject(String projectSlug) {
		return projectRepository.findBySlug(projectSlug).orElseThrow(() ->
				new IllegalStateException("No project with slug '" + projectSlug + "'"));
	}

	private DBUser getOrCreateUser(DialogueBranchUserId id) {
		return userRepository.findByIssuerAndSubject(id.issuer(), id.subject())
				.map(existing -> {
					if (id.username() != null && !id.username().equals(existing.getUsername())) {
						existing.setUsername(id.username());
						return userRepository.save(existing);
					}
					return existing;
				})
				.orElseGet(() -> userRepository.save(
						new DBUser(id.issuer(), id.subject(), id.username())));
	}
}

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

import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.storage.model.DBUser;
import com.dialoguebranch.web.service.storage.model.DBVariable;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableRepository;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.json.JsonMapper;
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
public class VariableStoreDatabaseStorageHandler implements VariableStoreStorageHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(VariableStoreDatabaseStorageHandler.class);

    private final DBUserRepository userRepository;
    private final DBVariableRepository variableRepository;

    /**
     * Creates an instance of a {@link VariableStoreDatabaseStorageHandler} that reads and writes
     * variable stores through the given repositories.
     *
     * @param userRepository repository used to look up or create the {@link DBUser} that owns
     *                        the variables being read or written.
     * @param variableRepository repository used to read, create, update, and delete
     *                            {@link DBVariable} rows.
     */
    public VariableStoreDatabaseStorageHandler(DBUserRepository userRepository,
                                               DBVariableRepository variableRepository) {
        this.userRepository = userRepository;
        this.variableRepository = variableRepository;
    }

    /**
     * Reads all stored Dialogue Branch Variables for the given user from the database and
     * returns them as a populated {@link VariableStore}.
     *
     * @param user the user for whom to load the variable store.
     * @return a {@link VariableStore} populated with the user's variables.
     * @throws ParseException if a variable value cannot be deserialized from its stored JSON
     * representation.
     */
    @Override
    @Transactional
    public VariableStore read(User user) throws ParseException {
        DBUser dbUser = getOrCreateUser(user.getId());
        List<DBVariable> dbVariables = variableRepository.findByUser(dbUser);

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
     * @param variableStore the variable store to persist.
     * @throws IOException if a variable value cannot be serialized to JSON for storage.
     */
    @Override
    @Transactional
    public void write(VariableStore variableStore) throws IOException {
        DBUser dbUser = getOrCreateUser(variableStore.getUser().getId());
        List<DBVariable> existingVars = variableRepository.findByUser(dbUser);

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
                    .findByUserAndName(dbUser, variable.getName())
                    .orElse(new DBVariable(variable.getName(), null));
            dbVariable.setUser(dbUser);
            dbVariable.setValue(JsonMapper.generate(variable.getValue()));
            dbVariable.setUpdatedTime(variable.getUpdatedTime());
            dbVariable.setUpdatedTimeZone(variable.getUpdatedTimeZone());
            dbVariable.setUpdatedSource(variable.getUpdatedSource() != null
                    ? variable.getUpdatedSource() : VariableUpdatedSource.UNKNOWN);
            variableRepository.save(dbVariable);
        }
    }

    /**
     * Called when the variable store changes; immediately persists the full updated store to
     * the database by delegating to {@link #write(VariableStore)}.
     *
     * @param variableStore the variable store that has changed.
     * @param changes the list of changes that were applied.
     */
    @Override
    public void onChange(VariableStore variableStore, List<VariableStoreChange> changes) {
        try {
            write(variableStore);
        } catch (IOException e) {
            logger.error("Failed to write variable store changes: {}", e.getMessage(), e);
        }
    }

    private DBUser getOrCreateUser(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(new DBUser(username)));
    }
}

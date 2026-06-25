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

@Service
public class VariableStoreDatabaseStorageHandler implements VariableStoreStorageHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(VariableStoreDatabaseStorageHandler.class);

    private final DBUserRepository userRepository;
    private final DBVariableRepository variableRepository;

    public VariableStoreDatabaseStorageHandler(DBUserRepository userRepository,
                                               DBVariableRepository variableRepository) {
        this.userRepository = userRepository;
        this.variableRepository = variableRepository;
    }

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

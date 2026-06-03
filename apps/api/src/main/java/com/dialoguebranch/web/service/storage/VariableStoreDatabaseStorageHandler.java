/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
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

import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.web.service.models.DBUser;
import com.dialoguebranch.web.service.models.DBVariable;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.json.JsonMapper;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class VariableStoreDatabaseStorageHandler implements VariableStoreStorageHandler {

    private final Logger logger =
            AppComponents.getLogger(ClassUtils.getUserClass(getClass()).getSimpleName());

    @Override
    public VariableStore read(User user) throws IOException, ParseException {
		final List<DBVariable> dbVariables = new ArrayList<>();

		getSessionFactory().inTransaction(session -> {
			DBUser dbUser = getDBUser(session, user.getId());

			dbVariables.addAll(session.createSelectionQuery(
					"from DBVariable where user.id = :userId", DBVariable.class)
					.setParameter("userId", dbUser.getId())
					.getResultList());
		});

		List<Variable> variables = new ArrayList<>();
		for (DBVariable dbVariable : dbVariables) {
			ZonedDateTime now = DateTimeUtils.nowMs();
			variables.add(new Variable(dbVariable.getName(),
					JsonMapper.parse(dbVariable.getValue(), Object.class),
					now.toInstant().toEpochMilli(),
					now.getZone().getId()));
		}

		return new VariableStore(user, variables.toArray(new Variable[0]));
    }

    @Override
    public void write(VariableStore variableStore) throws IOException {
		getSessionFactory().inTransaction(session -> {
			DBUser dbUser = getDBUser(session, variableStore.getUser().getId());

			List<DBVariable> prevDbVariables = session.createSelectionQuery(
					"from DBVariable where user.id = :userId", DBVariable.class)
					.setParameter("userId", dbUser.getId())
					.getResultList();

			// create or update current variables
			for (Variable variable : variableStore.getVariables()) {
				DBVariable dbVariable = prevDbVariables.stream()
						.filter(prevDbVariable -> prevDbVariable.getName().equals(variable.getName()))
						.findFirst()
						.orElseGet(() -> {
							DBVariable newDbVariable = new DBVariable(variable.getName(), null);
							newDbVariable.setUser(dbUser);
							return newDbVariable;
						});
				dbVariable.setValue(JsonMapper.generate(variable.getValue()));
				session.persist(dbVariable);
			}

			// delete old variables
			List<String> varNames = Arrays.stream(variableStore.getVariables())
					.map(Variable::getName)
					.toList();
			for (DBVariable prevDbVariable : prevDbVariables) {
				if (!varNames.contains(prevDbVariable.getName())) {
					session.createMutationQuery("delete from DBVariable where id = :id")
							.setParameter("id", prevDbVariable.getId())
							.executeUpdate();
				}
			}
		});
    }

    @Override
    public void onChange(VariableStore variableStore, List<VariableStoreChange> changes) {
        try {
            write(variableStore);
        } catch(IOException e) {
            logger.error("Failed to write variable store changes: " + e.getMessage(), e);
        }
    }

	private SessionFactory getSessionFactory() {
		return AppComponents.get(SessionFactory.class);
	}

	private DBUser getDBUser(Session session, String username) {
		DBUser dbUser = session.createSelectionQuery("from DBUser where username = :username",
						DBUser.class)
				.setParameter("username", username)
				.getSingleResultOrNull();

		if (dbUser != null) {
			return dbUser;
		}

		dbUser = new DBUser(username);
		session.persist(dbUser);

		return dbUser;
	}
}

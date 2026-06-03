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

package com.dialoguebranch.web.service.services;

import jakarta.annotation.PostConstruct;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatabaseTestService {
	@Autowired
	private SessionFactory sessionFactory;

	@PostConstruct
	public void start() {
//		sessionFactory.inTransaction(session -> {
//			session.createMutationQuery("delete from DBVariable").executeUpdate();
//			session.createMutationQuery("delete from DBUser").executeUpdate();
//
//			DBUser user = new DBUser("user");
//			session.persist(user);
//
//			for (int i = 1; i <= 10; i++) {
//				DBVariable variable = new DBVariable("varname" + i, UUID.randomUUID().toString());
//				variable.setUser(user);
//				session.persist(variable);
//			}
//		});
//
//		sessionFactory.inTransaction(session -> {
//			Logger logger = AppComponents.getLogger(getClass().getSimpleName());
//
//			List<DBVariable> variables = session.createSelectionQuery(
//					"from DBVariable v where v.user.username = :username", DBVariable.class)
//					.setParameter("username", "user")
//					.getResultList();
//			for (DBVariable readVariable : variables) {
//				logger.info("VARIABLE: " + JsonMapper.generate(readVariable));
//			}
//
//			List<DBUser> users = session.createSelectionQuery(
//					"from DBUser u join fetch u.variables where u.username = :username", DBUser.class)
//					.setParameter("username", "user")
//					.getResultList();
//			for (DBVariable readVariable : users.get(0).getVariables()) {
//				logger.info("USER VARIABLE: " + JsonMapper.generate(readVariable));
//			}
//		});
	}
}

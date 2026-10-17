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

package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBUser;
import com.dialoguebranch.web.service.storage.model.DBVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DBVariable} entities.
 *
 * @author Harm op den Akker
 */
public interface DBVariableRepository extends JpaRepository<DBVariable, UUID> {

	/**
	 * Finds all variables belonging to the given user within the given project.
	 *
	 * @param user the user whose variables should be retrieved.
	 * @param project the project the variables are scoped to.
	 * @return the list of matching variables.
	 */
	List<DBVariable> findByUserAndProject(DBUser user, DBProject project);

	/**
	 * Finds the variable with the given name belonging to the given user within the given project.
	 *
	 * @param user the user the variable belongs to.
	 * @param project the project the variable is scoped to.
	 * @param name the name of the variable.
	 * @return an {@link Optional} containing the matching {@link DBVariable}, or empty if none exists.
	 */
	Optional<DBVariable> findByUserAndProjectAndName(DBUser user, DBProject project, String name);


	/**
	 * Deletes all users' variables in the given project, when a project is deleted with its users'
	 * variables (#315).
	 *
	 * @param project the project.
	 * @return the number of variables deleted.
	 */
	@Modifying
	@Query("delete from DBVariable v where v.project = :project")
	int deleteByProject(@Param("project") DBProject project);


	/**
	 * Counts the users that have variables in the given project (#403).
	 *
	 * @param project the project.
	 * @return the number of users with variables in it.
	 */
	@Query("select count(distinct v.user) from DBVariable v where v.project = :project")
	long countUsersByProject(@Param("project") DBProject project);

	/** The project and time zone of one of a user's variables. */
	interface ProjectTimeZone {

		/** @return the slug of the variable's project. */
		String getProjectSlug();

		/** @return the time zone the variable was last updated in, if known. */
		String getTimeZone();
	}

	/**
	 * Lists the project and time zone of each of the given user's variables, in every project,
	 * when the user's data is erased (#320).
	 *
	 * @param user the user.
	 * @return one entry per variable.
	 */
	@Query("select v.project.slug as projectSlug, v.updatedTimeZone as timeZone "
			+ "from DBVariable v where v.user = :user")
	List<ProjectTimeZone> findProjectTimeZonesByUser(@Param("user") DBUser user);

	/**
	 * Deletes all variables of the given user, in every project, when the user's data is erased
	 * (#320).
	 *
	 * @param user the user.
	 * @return the number of variables deleted.
	 */
	@Modifying
	@Query("delete from DBVariable v where v.user = :user")
	int deleteByUser(@Param("user") DBUser user);

	/**
	 * Counts the variables of each of the given users, in every project, for the {@code /users}
	 * listing.
	 *
	 * @param users the users.
	 * @return one {@code [user id, count]} row per user that has variables.
	 */
	@Query("select v.user.id, count(v) from DBVariable v where v.user in :users group by v.user.id")
	List<Object[]> countByUsers(@Param("users") Collection<DBUser> users);

}

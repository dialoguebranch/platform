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

package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBLoggedDialogue;
import com.dialoguebranch.web.service.storage.model.DBUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DBLoggedDialogue} entities.
 *
 * @author Harm op den Akker
 */
public interface DBLoggedDialogueRepository extends JpaRepository<DBLoggedDialogue, String> {

	/**
	 * Finds the most recently active, still-ongoing (not completed, not cancelled) logged
	 * dialogue for the given user, regardless of project or dialogue name.
	 *
	 * @param user the user to search for.
	 * @return the most recently active ongoing logged dialogue, or empty if none exists.
	 */
	Optional<DBLoggedDialogue> findFirstByUserAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
			DBUser user);

	/**
	 * Finds the most recently active, still-ongoing logged dialogue for the given user within the
	 * given project.
	 *
	 * @param user the user to search for.
	 * @param projectSlug the project to search within.
	 * @return the most recently active ongoing logged dialogue, or empty if none exists.
	 */
	Optional<DBLoggedDialogue> findFirstByUserAndProjectSlugAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
			DBUser user, String projectSlug);

	/**
	 * Finds the most recently active, still-ongoing logged dialogue for the given user with the
	 * given dialogue name, regardless of project.
	 *
	 * @param user the user to search for.
	 * @param dialogueName the dialogue name to search for.
	 * @return the most recently active ongoing logged dialogue, or empty if none exists.
	 */
	Optional<DBLoggedDialogue> findFirstByUserAndDialogueNameAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
			DBUser user, String dialogueName);

	/**
	 * Finds the most recently active, still-ongoing logged dialogue for the given user within the
	 * given project, with the given dialogue name.
	 *
	 * @param user the user to search for.
	 * @param projectSlug the project to search within.
	 * @param dialogueName the dialogue name to search for.
	 * @return the most recently active ongoing logged dialogue, or empty if none exists.
	 */
	Optional<DBLoggedDialogue> findFirstByUserAndProjectSlugAndDialogueNameAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
			DBUser user, String projectSlug, String dialogueName);

	/**
	 * Finds the logged dialogue with the given id, scoped to the given user (a user may only look
	 * up their own logged dialogues).
	 *
	 * @param id the logged dialogue identifier.
	 * @param user the user the logged dialogue must belong to.
	 * @return the matching logged dialogue, or empty if none exists for this user.
	 */
	Optional<DBLoggedDialogue> findByIdAndUser(String id, DBUser user);

	/**
	 * Finds all logged dialogues belonging to the given session, ordered from earliest to latest.
	 *
	 * @param user the user the session belongs to.
	 * @param sessionId the session identifier.
	 * @return the logged dialogues in the session, ordered by start time.
	 */
	List<DBLoggedDialogue> findByUserAndSessionIdOrderByUtcTimeAsc(DBUser user, String sessionId);

	/**
	 * Checks whether a session with the given identifier already exists for the given user.
	 *
	 * @param user the user to check for.
	 * @param sessionId the session identifier to check.
	 * @return {@code true} if a logged dialogue with this session identifier already exists.
	 */
	boolean existsByUserAndSessionId(DBUser user, String sessionId);

}

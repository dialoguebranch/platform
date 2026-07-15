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

import com.dialoguebranch.model.execute.LoggedInteraction;
import com.dialoguebranch.web.service.execution.UserService;
import com.dialoguebranch.web.service.repository.DBLoggedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.storage.model.DBLoggedDialogue;
import com.dialoguebranch.web.service.storage.model.DBUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.rrd.utils.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@link LoggedDialogueStore} is a class that acts as the storage for {@link
 * ServerLoggedDialogue} objects, used in the execution of dialogues by the Dialogue Branch Web
 * Service. It persists dialogues to the {@code logged_dialogues} database table via {@link
 * DBLoggedDialogueRepository}.
 *
 * <p>A {@link LoggedDialogueStore} does not maintain any data in-memory, but immediately stores
 * any changes made to the configured storage mechanism.</p>
 *
 * @author Harm op den Akker
 */
public class LoggedDialogueStore {

	private static final Logger logger = LoggerFactory.getLogger(LoggedDialogueStore.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final UserService userService;
	private final String userId;
	private final DBUserRepository userRepository;
	private final DBLoggedDialogueRepository loggedDialogueRepository;
	private final DBUser dbUser;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link LoggedDialogueStore} for the user identified by the given
	 * {@code userId}, with a reference to that user's {@link UserService}.
	 *
	 * @param userId the identifier of the Dialogue Branch User for which to instantiate this
	 *               {@link LoggedDialogueStore}.
	 * @param userService the {@link UserService} associated with this LoggedDialogueStore.
	 * @param userRepository repository used to look up or create the {@link DBUser} that owns the
	 *                        logged dialogues being read or written.
	 * @param loggedDialogueRepository repository used to read, create, and update {@link
	 *                                 DBLoggedDialogue} rows.
	 */
	public LoggedDialogueStore(String userId, UserService userService,
							   DBUserRepository userRepository,
							   DBLoggedDialogueRepository loggedDialogueRepository) {
		logger.info("Initializing LoggedDialogueStore for user '" + userId + "'.");

		this.userService = userService;
		this.userId = userId;
		this.userRepository = userRepository;
		this.loggedDialogueRepository = loggedDialogueRepository;
		this.dbUser = getOrCreateUser(userId);
	}

	// ----------------------------------------------------------- //
	// -------------------- Public Operations -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Finds and returns the {@link ServerLoggedDialogue} with the given {@code id} for this user,
	 * or {@code null} if no such logged dialogue can be found.
	 *
	 * @param id the identifier of the logged dialogue to find.
	 * @return the matching {@link ServerLoggedDialogue}, or {@code null}.
	 * @throws DatabaseException in case of an error reading the logged dialogue.
	 */
	public ServerLoggedDialogue findLoggedDialogue(String id) throws DatabaseException {
		return toServerLoggedDialogue(
				loggedDialogueRepository.findByIdAndUser(id, dbUser).orElse(null));
	}

	/**
	 * Finds and returns the most recent ongoing (not cancelled, not completed) {@link
	 * ServerLoggedDialogue} for this user, or {@code null} if none is found.
	 *
	 * @return the latest ongoing {@link ServerLoggedDialogue}, or {@code null}.
	 * @throws DatabaseException in case of an error reading the logged dialogue.
	 */
	public ServerLoggedDialogue findLatestOngoingDialogue() throws DatabaseException {
		return toServerLoggedDialogue(loggedDialogueRepository
				.findFirstByUserAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
						dbUser)
				.orElse(null));
	}

	/**
	 * Finds and returns the most recent ongoing (not cancelled, not completed) {@link
	 * ServerLoggedDialogue} for this user with the given {@code dialogueName}, or {@code null} if
	 * none is found.
	 *
	 * @param dialogueName the dialogue name to filter on.
	 * @return the latest ongoing {@link ServerLoggedDialogue} with the given name, or {@code null}.
	 * @throws DatabaseException in case of an error reading the logged dialogue.
	 */
	public ServerLoggedDialogue findLatestOngoingDialogue(String dialogueName)
			throws DatabaseException {
		return toServerLoggedDialogue(loggedDialogueRepository
				.findFirstByUserAndDialogueNameAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
						dbUser, dialogueName)
				.orElse(null));
	}

	/**
	 * Finds and returns the most recent ongoing (not cancelled, not completed) {@link
	 * ServerLoggedDialogue} for this user within the given {@code projectSlug}, or {@code null} if
	 * none is found.
	 *
	 * @param projectSlug the project name to filter on.
	 * @return the latest ongoing {@link ServerLoggedDialogue} within the given project, or {@code null}.
	 * @throws DatabaseException in case of an error reading the logged dialogue.
	 */
	public ServerLoggedDialogue findLatestOngoingDialogueInProject(String projectSlug)
			throws DatabaseException {
		return toServerLoggedDialogue(loggedDialogueRepository
				.findFirstByUserAndProjectSlugAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
						dbUser, projectSlug)
				.orElse(null));
	}

	/**
	 * Finds and returns the most recent ongoing {@link ServerLoggedDialogue} for this user with
	 * the given {@code projectSlug} and {@code dialogueName}, or {@code null} if none is found.
	 *
	 * @param projectSlug the project name to filter on.
	 * @param dialogueName the dialogue name to filter on.
	 * @return the latest ongoing {@link ServerLoggedDialogue}, or {@code null}.
	 * @throws DatabaseException in case of an error reading the logged dialogue.
	 */
	public ServerLoggedDialogue findLatestOngoingDialogue(String projectSlug, String dialogueName)
			throws DatabaseException {
		return toServerLoggedDialogue(loggedDialogueRepository
				.findFirstByUserAndProjectSlugAndDialogueNameAndCompletedFalseAndCancelledFalseOrderByLatestInteractionTimestampDesc(
						dbUser, projectSlug, dialogueName)
				.orElse(null));
	}

	/**
	 * Marks the given {@link ServerLoggedDialogue} as canceled and persists the change.
	 *
	 * @param serverLoggedDialogue the dialogue to mark as canceled.
	 * @throws DatabaseException in case of an error writing the logged dialogue.
	 */
	public void setDialogueCancelled(ServerLoggedDialogue serverLoggedDialogue)
			throws DatabaseException {
		serverLoggedDialogue.setCancelled(true);
		saveToSession(serverLoggedDialogue);
	}

	/**
	 * Persists the given {@link ServerLoggedDialogue}, creating or updating its row as needed.
	 *
	 * @param dialogue the {@link ServerLoggedDialogue} to save.
	 * @throws DatabaseException in case of an error writing the logged dialogue.
	 */
	public void saveToSession(ServerLoggedDialogue dialogue) throws DatabaseException {
		loggedDialogueRepository.save(toDBLoggedDialogue(dialogue));
	}

	/**
	 * Checks whether the given {@code sessionId} exists for this user.
	 *
	 * @param sessionId the sessionId for which to check.
	 * @return true if the sessionId exists, false otherwise.
	 */
	public boolean existsSessionId(String sessionId) {
		return loggedDialogueRepository.existsByUserAndSessionId(dbUser, sessionId);
	}

	/**
	 * Reads and returns all {@link ServerLoggedDialogue} entries associated with the given session ID.
	 *
	 * @param sessionId the session ID for which to read all logged dialogues.
	 * @return the list of {@link ServerLoggedDialogue} entries for the given session.
	 * @throws DatabaseException in case of an error reading a logged dialogue.
	 */
	public List<ServerLoggedDialogue> readSession(String sessionId) throws DatabaseException {
		List<ServerLoggedDialogue> result = new ArrayList<>();
		for (DBLoggedDialogue dbLoggedDialogue :
				loggedDialogueRepository.findByUserAndSessionIdOrderByUtcTimeAsc(dbUser, sessionId)) {
			result.add(toServerLoggedDialogue(dbLoggedDialogue));
		}
		return result;
	}

	// ---------------------------------------------------------------------- //
	// -------------------- Private Conversion Methods ----------------------- //
	// ---------------------------------------------------------------------- //

	private DBUser getOrCreateUser(String username) {
		return userRepository.findByUsername(username)
				.orElseGet(() -> userRepository.save(new DBUser(username)));
	}

	private ServerLoggedDialogue toServerLoggedDialogue(DBLoggedDialogue db)
			throws DatabaseException {
		if (db == null) return null;
		ServerLoggedDialogue dialogue = new ServerLoggedDialogue();
		dialogue.setId(db.getId());
		dialogue.setSessionId(db.getSessionId());
		dialogue.setSessionStartTime(db.getSessionStartTime());
		// Every DBLoggedDialogue returned by this store's queries belongs to this.userId (they're
		// all scoped to dbUser) — read that directly rather than db.getUser().getUsername(), which
		// would try to lazily initialize the user association outside its fetch transaction
		// (open-in-view is disabled) and throw LazyInitializationException.
		dialogue.setUser(userId);
		dialogue.setLocalTime(db.getLocalTime());
		dialogue.setUtcTime(db.getUtcTime());
		dialogue.setTimezone(db.getTimezone());
		dialogue.setProjectName(db.getProjectSlug());
		dialogue.setDialogueName(db.getDialogueName());
		dialogue.setLanguage(db.getLanguage());
		dialogue.setPublishedVersionNumber(db.getPublishedVersionNumber());
		dialogue.setCompleted(db.isCompleted());
		dialogue.setCancelled(db.isCancelled());
		dialogue.setInteractionList(parseInteractions(db.getInteractions()));
		return dialogue;
	}

	private DBLoggedDialogue toDBLoggedDialogue(ServerLoggedDialogue dialogue)
			throws DatabaseException {
		DBLoggedDialogue db = new DBLoggedDialogue();
		db.setId(dialogue.getId());
		db.setUser(dbUser);
		db.setSessionId(dialogue.getSessionId());
		db.setSessionStartTime(dialogue.getSessionStartTime());
		db.setLocalTime(dialogue.getLocalTime());
		db.setUtcTime(dialogue.getUtcTime());
		db.setTimezone(dialogue.getTimezone());
		db.setProjectSlug(dialogue.getProjectName());
		db.setDialogueName(dialogue.getDialogueName());
		db.setLanguage(dialogue.getLanguage());
		db.setPublishedVersionNumber(dialogue.getPublishedVersionNumber());
		db.setCompleted(dialogue.isCompleted());
		db.setCancelled(dialogue.isCancelled());
		db.setLatestInteractionTimestamp(dialogue.getLatestInteractionTimestamp());
		db.setInteractions(generateInteractions(dialogue.getInteractionList()));
		return db;
	}

	private String generateInteractions(List<LoggedInteraction> interactions)
			throws DatabaseException {
		try {
			return OBJECT_MAPPER.writeValueAsString(interactions);
		} catch (JsonProcessingException ex) {
			throw new DatabaseException(
					"Failed to serialize logged interactions: " + ex.getMessage(), ex);
		}
	}

	private List<LoggedInteraction> parseInteractions(String json) throws DatabaseException {
		try {
			return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
			});
		} catch (JsonProcessingException ex) {
			throw new DatabaseException(
					"Failed to parse logged interactions: " + ex.getMessage(), ex);
		}
	}

}

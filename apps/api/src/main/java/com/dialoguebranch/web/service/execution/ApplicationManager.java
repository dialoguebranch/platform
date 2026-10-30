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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.common.DialogueBranchProject;
import com.dialoguebranch.model.common.Speaker;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.controller.schema.ProjectVariableInfo;
import com.dialoguebranch.web.service.controller.schema.SupportedVariableInfo;
import com.dialoguebranch.web.service.exception.DatabaseException;
import com.dialoguebranch.web.service.exception.NotImplementedException;
import com.dialoguebranch.web.service.exception.ServiceUnavailableException;
import com.dialoguebranch.web.service.repository.DBLoggedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.storage.ExternalVariableServiceClient;
import com.dialoguebranch.web.service.storage.VariableHistoryStorageHandler;
import com.dialoguebranch.web.service.storage.VariableStoreDatabaseStorageHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The Dialogue Branch Web Service maintains one instance of an {@link ApplicationManager}. This
 * class keeps track of the different active {@link UserService} instances that are needed to serve
 * individual users of the Dialogue Branch Web Service, as well as other application-wide objects.
 *
 * <p>Dialogue Branch projects are loaded into memory via {@link #loadProject(String, ScriptLoader, int)},
 * which is called by {@link com.dialoguebranch.web.service.project.ProjectSeedService} on
 * application startup after all projects have been seeded in the database. The projects map is
 * keyed by project name (slug).</p>
 *
 * @author Harm op den Akker
 * @author Tessa Beinema
 */
public class ApplicationManager {

	// --------------------------------------------------- //
	// -------------------- Fields -------------------- //
	// --------------------------------------------------- //

	private static final Logger logger = LoggerFactory.getLogger(ApplicationManager.class);

	/** Loaded Dialogue Branch projects, keyed by project name (folder name). */
	private final Map<String, DialogueBranchProject> projects = new LinkedHashMap<>();

	/** The published version number currently loaded for each project, keyed by project slug. */
	private final Map<String, Integer> projectVersions = new LinkedHashMap<>();

	/**
	 * The source language code of each loaded project, keyed by slug: what a dialogue is started in
	 * when the caller doesn't name a language (#333).
	 */
	private final Map<String, String> projectSourceLanguages = new LinkedHashMap<>();

	/**
	 * Every language code of each loaded project (source and translation languages), keyed by
	 * slug: used to warn when a dialogue is started in a language the project doesn't have
	 * (#335).
	 */
	private final Map<String, Set<String>> projectLanguages = new LinkedHashMap<>();

	private final DlbProperties dlbProperties;
	private final ExternalVariableServiceClient externalVariableServiceClient;

	/**
	 * Active {@link UserService}s, keyed by the user's {@code (issuer, subject)}. Request-handling
	 * threads read and add to it, and the scheduled {@link UserServiceExpirationService} sweep
	 * removes from it, concurrently. A user has at most one {@link UserService}: creating one
	 * happens in {@link #createActiveUserService}, which checks again under a lock (#214), since a
	 * second {@link UserService} would also bring its own per-project locks (see {@link
	 * UserService#lockFor}).
	 */
	private final Map<UserKey, UserService> activeUserServices = new ConcurrentHashMap<>();

	/** The part of a {@link DialogueBranchUserId} that identifies a user. */
	private record UserKey(String issuer, String subject) {
		static UserKey of(DialogueBranchUserId userId) {
			return new UserKey(userId.issuer(), userId.subject());
		}
	}
	private final UserServiceFactory userServiceFactory;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an {@link ApplicationManager}. Projects are not loaded here;
	 * they are populated later via {@link #loadProject(String, ScriptLoader, int)} by the
	 * {@link com.dialoguebranch.web.service.project.ProjectSeedService} once the database is ready.
	 *
	 * @param dlbProperties  the application configuration properties.
	 * @param storageHandler the variable-store storage handler for user sessions.
	 * @param historyStorageHandler the handler recording every variable change to the variable
	 *                              history (#259).
	 * @param userRepository repository used to look up or create the {@link
	 *                        com.dialoguebranch.web.service.storage.model.DBUser} that owns each
	 *                       user's logged dialogues.
	 * @param loggedDialogueRepository repository used to read, create, and update logged dialogues.
	 * @param externalVariableServiceClient the client for the External Variable Service, shared
	 *                                      by all user sessions (#219).
	 */
	public ApplicationManager(DlbProperties dlbProperties,
							  VariableStoreDatabaseStorageHandler storageHandler,
							  VariableHistoryStorageHandler historyStorageHandler,
							  DBUserRepository userRepository,
							  DBLoggedDialogueRepository loggedDialogueRepository,
							  ExternalVariableServiceClient externalVariableServiceClient) {
		this.dlbProperties = dlbProperties;
		this.externalVariableServiceClient = externalVariableServiceClient;
		this.userServiceFactory = new UserServiceFactory(this, storageHandler,
				historyStorageHandler, userRepository, loggedDialogueRepository);
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the map of loaded Dialogue Branch projects, keyed by project name.
	 *
	 * @return the loaded projects.
	 */
	public Map<String, DialogueBranchProject> getProjects() {
		return projects;
	}

	/**
	 * Returns the {@link DlbProperties} configuration object for this service.
	 *
	 * @return the application configuration properties.
	 */
	public DlbProperties getDlbProperties() {
		return dlbProperties;
	}

	/**
	 * Returns the client for the External Variable Service, shared by all user sessions.
	 *
	 * @return the External Variable Service client.
	 */
	public ExternalVariableServiceClient getExternalVariableServiceClient() {
		return externalVariableServiceClient;
	}

	// ------------------------------------------------------------ //
	// -------------------- Service Management -------------------- //
	// ------------------------------------------------------------ //

	/**
	 * Returns an active {@link UserService} object for the given {@code userId} in the given
	 * {@code timeZone}. Retrieves from an internal list of active {@link UserService}s, or
	 * instantiates a new one if none is active for the given user.
	 *
	 * @param userId   the {@code (issuer, subject)} identity of the user for which to retrieve a
	 *                 {@link UserService}.
	 * @param timeZone the time zone as {@link ZoneId} in which the user resides.
	 * @return a {@link UserService} object that can handle the communication with the user.
	 * @throws IOException       in case of an error loading the known variables for the User.
	 * @throws DatabaseException in case of an error loading the known variables for the User.
	 */
	public UserService getOrCreateActiveUserService(DialogueBranchUserId userId, ZoneId timeZone)
			throws IOException, DatabaseException {
		UserService result = getActiveUserService(userId);
		if (result != null) return result;
		else return createActiveUserService(userId, timeZone);
	}

	/**
	 * Returns an active {@link UserService} object for the given {@code userId} in the system's
	 * default time zone. Retrieves from an internal list of active {@link UserService}s, or
	 * instantiates a new one if none is active for the given user.
	 *
	 * @param userId the {@code (issuer, subject)} identity of the user for which to retrieve a
	 *               {@link UserService}.
	 * @return a {@link UserService} object that can handle the communication with the user.
	 * @throws IOException       in case of an error loading the known variables for the User.
	 * @throws DatabaseException in case of an error loading the known variables for the User.
	 */
	public UserService getOrCreateActiveUserService(DialogueBranchUserId userId)
			throws IOException, DatabaseException {
		UserService result = getActiveUserService(userId);
		if (result != null) return result;
		else return createActiveUserService(userId, null);
	}

	/**
	 * Returns the {@link UserService} object for a user with the given {@code userId}, or
	 * {@code null} if there is no currently active user service running. Records activity on the
	 * returned {@link UserService} (see {@link UserService#recordActivity()}), so any endpoint
	 * resolving an existing {@link UserService} resets its idle-eviction timer.
	 *
	 * @param userId the {@code (issuer, subject)} identity of the user for which to retrieve a
	 *               {@link UserService}.
	 * @return a {@link UserService} object, or {@code null} if none exists.
	 */
	public UserService getActiveUserService(DialogueBranchUserId userId) {
		UserService userService = activeUserServices.get(UserKey.of(userId));
		if (userService != null) userService.recordActivity();
		return userService;
	}

	/**
	 * Returns the number of currently active (in-memory) {@link UserService} instances.
	 *
	 * @return the number of currently active {@link UserService} instances.
	 */
	public int getActiveUserServiceCount() {
		return activeUserServices.size();
	}

	/**
	 * Creates a new {@link UserService} object for a new user with the given {@code userId} in the
	 * given {@code timeZone}. If successfully created, keeps a record of this active user service.
	 * Two requests for a user who isn't active yet may both get here: the check under this lock
	 * makes the second one get the first one's {@link UserService} instead of a second one (#214).
	 *
	 * @param userId   the {@code (issuer, subject)} identity of the user for which to create a
	 *                 {@link UserService}.
	 * @param timeZone the time zone as {@link ZoneId} in which the user resides.
	 * @return the newly created {@link UserService} object.
	 * @throws IOException       in case of an error loading the known variables for the User.
	 * @throws DatabaseException in case of an error loading the known variables for the User.
	 */
	private synchronized UserService createActiveUserService(DialogueBranchUserId userId,
			ZoneId timeZone) throws IOException, DatabaseException {
		UserService existing = getActiveUserService(userId);
		if (existing != null) return existing;
		UserService newUserService;
		if (timeZone == null) {
			newUserService = userServiceFactory.createUserService(userId);
		} else {
			newUserService = userServiceFactory.createUserService(userId, timeZone);
		}
		activeUserServices.put(UserKey.of(userId), newUserService);
		logger.info("Created a new UserService for user '{}' (total active users: {}).",
				userId.subject(), activeUserServices.size());
		return newUserService;
	}

	/**
	 * Removes the given {@link UserService} from the set of active {@link UserService}s in this
	 * {@link ApplicationManager}.
	 *
	 * @param userService the {@link UserService} to remove.
	 * @return {@code true} if the given {@link UserService} was successfully removed, or
	 * {@code false} if it was not present on the list of active {@link UserService}s.
	 */
	public boolean removeUserService(UserService userService) {
		return activeUserServices.remove(UserKey.of(userService.getUserId()), userService);
	}

	/**
	 * Removes the active {@link UserService} of the given user, if there is one, so that nothing it
	 * holds in memory is written back after the user's data was erased (#320).
	 *
	 * @param userId the {@code (issuer, subject)} identity of the user.
	 */
	public void removeUserService(DialogueBranchUserId userId) {
		activeUserServices.remove(UserKey.of(userId));
	}

	/**
	 * Evicts every active {@link UserService} whose {@link UserService#getLastActivityTime()} is
	 * older than {@code idleTimeoutMillis}. Called periodically by {@link
	 * UserServiceExpirationService} to bound memory for clients that disconnect without an explicit
	 * {@code /auth/logout} (closed tab, killed app, expired token, dropped connection).
	 *
	 * @param idleTimeoutMillis how long, in milliseconds, a {@link UserService} may sit idle before
	 *                          being evicted.
	 * @return the number of {@link UserService}s evicted.
	 */
	public int removeIdleUserServices(long idleTimeoutMillis) {
		long cutoff = System.currentTimeMillis() - idleTimeoutMillis;
		int sizeBefore = activeUserServices.size();
		activeUserServices.values().removeIf(
				userService -> userService.getLastActivityTime() < cutoff);
		return sizeBefore - activeUserServices.size();
	}

	// ------------------------------------------------------------- //
	// -------------------- Dialogue Management -------------------- //
	// ------------------------------------------------------------- //

	/**
	 * Returns the {@link ResourcePointer}s of all dialogues available in the named project, or an
	 * empty list if no project with that name is loaded.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @return a list of {@link ResourcePointer}s for the named project.
	 */
	public List<ResourcePointer> getDialogueDescriptionsForProject(String projectSlug) {
		DialogueBranchProject project = projects.get(projectSlug);
		if (project == null) return new ArrayList<>();
		return new ArrayList<>(project.getResourcePointers());
	}

	/**
	 * Returns every Dialogue Branch variable referenced anywhere in the named project's loaded
	 * (published) dialogues, sorted by name, each flagged as read and/or written across the
	 * project. This is the set of variables the project's content uses, independent of whether any
	 * user has a value for them. Returns an empty list if no project with that slug is loaded, or
	 * it has no executable dialogues.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @return the project's referenced variables, sorted by name.
	 */
	public List<ProjectVariableInfo> getProjectVariables(String projectSlug) {
		DialogueBranchProject project = projects.get(projectSlug);
		if (!(project instanceof ExecutableProject executableProject))
			return new ArrayList<>();
		// value: [read, written]
		Map<String, boolean[]> byName = new TreeMap<>();
		for (Dialogue dialogue : executableProject.getDialogues().values()) {
			for (String name : dialogue.getVariablesNeeded())
				byName.computeIfAbsent(name, k -> new boolean[2])[0] = true;
			for (String name : dialogue.getVariablesWritten())
				byName.computeIfAbsent(name, k -> new boolean[2])[1] = true;
		}
		List<ProjectVariableInfo> result = new ArrayList<>();
		byName.forEach((name, flags) ->
				result.add(new ProjectVariableInfo(name, flags[0], flags[1])));
		return result;
	}

	/**
	 * Returns the variables the configured External Variable Service reports as supported for the
	 * given project, queried live on every call — see {@link ExternalVariableServiceClient} for
	 * why this is not cached.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @return the External Variable Service's reported supported variables for {@code projectSlug}.
	 * @throws NotImplementedException if no External Variable Service is configured.
	 * @throws ServiceUnavailableException if the configured EVS could not be reached or returned
	 *                                     an error.
	 */
	public List<SupportedVariableInfo> getSupportedVariablesFromExternalService(String projectSlug)
			throws NotImplementedException, ServiceUnavailableException {
		return externalVariableServiceClient.getSupportedVariables(projectSlug);
	}

	/**
	 * Returns the published version number currently loaded in memory for the named project, or
	 * {@code null} if no project with that slug is currently loaded.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @return the currently loaded published version number, or {@code null}.
	 */
	public Integer getProjectVersion(String projectSlug) {
		return projectVersions.get(projectSlug);
	}

	/**
	 * Returns the source language code of a loaded project.
	 *
	 * @param projectSlug the project's slug.
	 * @return the source language code, or {@code null} if no such project is loaded or its source
	 *         language is unknown.
	 */
	public String getSourceLanguage(String projectSlug) {
		return projectSourceLanguages.get(projectSlug);
	}

	/**
	 * Determines a just-loaded project's source language: from the database loader, which is
	 * given it explicitly, or otherwise from the project's own metadata ({@code dlb-project.xml}).
	 */
	private static String sourceLanguageOf(ScriptLoader scriptLoader, DialogueBranchProject project) {
		if (scriptLoader instanceof DatabasePublishedScriptLoader dbLoader)
			return dbLoader.getSourceLanguage();
		if (project instanceof ExecutableProject executable && executable.getMetaData() != null
				&& executable.getMetaData().getLanguageMap() != null
				&& executable.getMetaData().getLanguageMap().getSourceLanguage() != null)
			return executable.getMetaData().getLanguageMap().getSourceLanguage().getCode();
		return null;
	}

	/**
	 * Returns whether the given {@code language} is one of the named project's languages (its
	 * source language or one of its translation languages). A dialogue started in any other
	 * language still runs, in the project's source language: a deliberate fallback, so a client
	 * with a slightly different language setting still gets a dialogue rather than an error.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @param language    the language code to check.
	 * @return {@code false} only if the project is loaded and {@code language} isn't one of its
	 *         languages; {@code true} otherwise (including for a project that isn't loaded, whose
	 *         dialogue lookup reports that more specifically).
	 */
	public boolean isSupportedLanguage(String projectSlug, String language) {
		Set<String> languages = projectLanguages.get(projectSlug);
		return languages == null || languages.contains(language);
	}

	/**
	 * Returns the language codes of a loaded project: its source language, followed by its
	 * translation languages.
	 *
	 * @param projectSlug the project's slug.
	 * @return the project's language codes, or an empty set if no such project is loaded.
	 */
	public Set<String> getLanguages(String projectSlug) {
		Set<String> languages = projectLanguages.get(projectSlug);
		return languages == null ? Set.of() : Collections.unmodifiableSet(languages);
	}

	/**
	 * Determines a just-loaded project's language codes: from the database loader, which is given
	 * them explicitly (including translation languages without any translations yet); otherwise
	 * from the project's own metadata ({@code dlb-project.xml}); otherwise from the languages its
	 * dialogue files are found in.
	 */
	private static Set<String> languagesOf(ScriptLoader scriptLoader, DialogueBranchProject project) {
		if (scriptLoader instanceof DatabasePublishedScriptLoader dbLoader)
			return new LinkedHashSet<>(dbLoader.getLanguageCodes());
		if (project instanceof ExecutableProject executable)
			return new LinkedHashSet<>(executable.getLanguages());
		return new LinkedHashSet<>();
	}

	/**
	 * Returns all available dialogues in the named project, or an empty list if no project with
	 * that name is loaded.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @return a list of {@link ResourcePointer}s for the named project.
	 */
	public List<ResourcePointer> getAvailableDialoguesForProject(String projectSlug) {
		return getDialogueDescriptionsForProject(projectSlug);
	}

	/**
	 * Returns the given dialogue of a loaded project as translated for each gender of the end user
	 * (#400), for {@link com.dialoguebranch.execution.ActiveDialogue#setUserGenderTranslations}.
	 *
	 * @param projectSlug the project's slug.
	 * @param dialogueDescription the dialogue (name and language).
	 * @return the translations by the end user's gender, or {@code null} if the project isn't
	 *         loaded.
	 */
	public @Nullable Function<TranslationContext.Gender, Dialogue> getUserGenderTranslations(
			String projectSlug, ResourcePointer dialogueDescription) {
		if (!(projects.get(projectSlug) instanceof ExecutableProject execProject))
			return null;
		return (gender) -> execProject.getDialogueForUserGender(dialogueDescription, gender);
	}

	/**
	 * Returns the {@link Dialogue} identified by the given {@code dialogueDescription} and
	 * {@code translationContext}, searching only within the named project.
	 *
	 * @param projectSlug         the project folder name / slug.
	 * @param dialogueDescription the {@link ResourcePointer} identifying the requested dialogue.
	 * @param translationContext  the translation context to apply, or {@code null} for the source.
	 * @return the requested {@link Dialogue}.
	 * @throws ExecutionException with type {@link ExecutionException.Type#DIALOGUE_NOT_FOUND} if the
	 *                            project is not loaded or the dialogue is not found within it.
	 */
	public Dialogue getDialogueDefinitionForProject(String projectSlug,
			ResourcePointer dialogueDescription, TranslationContext translationContext)
			throws ExecutionException {
		DialogueBranchProject project = projects.get(projectSlug);
		if (project instanceof ExecutableProject execProject) {
			Dialogue dialogue;
			if (translationContext == null) {
				dialogue = execProject.getDialogues().get(dialogueDescription);
			} else {
				dialogue = execProject.getTranslatedDialogue(dialogueDescription, translationContext);
			}
			if (dialogue != null) return dialogue;
		}
		throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
				"Dialogue '" + dialogueDescription.getDialogueName() + "' not found in project '" +
						projectSlug + "' for language '" + dialogueDescription.getLanguage() + "'.");
	}

	// ---------------------------------------------------------------- //
	// -------------------- Project Loading -------------------- //
	// ---------------------------------------------------------------- //

	/**
	 * Removes a deleted project from the running service (#315): it is no longer listed or
	 * playable, and its variable stores are evicted from every active user session, so a new
	 * project that takes over its slug starts from what is stored for it (nothing), not from the
	 * deleted project's cached variables (#316).
	 *
	 * @param projectSlug the slug the project had.
	 */
	public void unloadProject(String projectSlug) {
		projects.remove(projectSlug);
		projectVersions.remove(projectSlug);
		projectSourceLanguages.remove(projectSlug);
		projectLanguages.remove(projectSlug);
		for (UserService userService : activeUserServices.values()) {
			userService.evictVariableStore(projectSlug);
		}
		logger.info("Unloaded Dialogue Branch project '{}'.", projectSlug);
	}

	/**
	 * Loads a Dialogue Branch project into memory using the supplied {@link ScriptLoader} and stores
	 * the resulting {@link DialogueBranchProject} in {@link #projects}. Any previously loaded
	 * project with the same name is replaced.
	 *
	 * <p>This method is called by
	 * {@link com.dialoguebranch.web.service.project.ProjectSeedService} on application startup,
	 * after the database has been seeded, to populate the in-memory project map from the published
	 * database content.</p>
	 *
	 * @param projectSlug the unique slug name of the project.
	 * @param scriptLoader  the {@link ScriptLoader} that provides script and translation content.
	 * @param versionNumber the published version number being loaded, recorded so that logged
	 *                      dialogues can be pinned to the version they were started against.
	 * @param speakers      the published version's speakers, whose genders select
	 *                      gender-specific translations (#399).
	 */
	public void loadProject(String projectSlug, ScriptLoader scriptLoader, int versionNumber,
			List<Speaker> speakers) {
		logger.info("Loading Dialogue Branch project '{}' into memory.", projectSlug);

		ProjectParser projectParser = new ProjectParser(scriptLoader, speakers);
		ProjectParserResult result;
		try {
			result = projectParser.parse();
		} catch (IOException e) {
			logger.error("Error reading Dialogue Branch project '{}': {}", projectSlug,
					e.getMessage());
			return;
		}

		for (String path : result.getParseErrors().keySet()) {
			logger.error("Project '{}': failed to parse {}:", projectSlug, path);
			for (ParseException ex : result.getParseErrors().get(path)) {
				logger.error("*** {}", ex.getMessage());
			}
		}
		for (String path : result.getWarnings().keySet()) {
			logger.warn("Project '{}': warning at parsing {}:", projectSlug, path);
			for (String warning : result.getWarnings().get(path)) {
				logger.warn("*** {}", warning);
			}
		}

		if (!result.getParseErrors().isEmpty()) {
			logger.error("Dialogue Branch project '{}' could not be loaded due to parse errors.",
					projectSlug);
			return;
		}

		projects.put(projectSlug, result.getProject());
		projectVersions.put(projectSlug, versionNumber);
		String sourceLanguage = sourceLanguageOf(scriptLoader, result.getProject());
		if (sourceLanguage != null) projectSourceLanguages.put(projectSlug, sourceLanguage);
		else projectSourceLanguages.remove(projectSlug);
		projectLanguages.put(projectSlug, languagesOf(scriptLoader, result.getProject()));
		logger.info("Successfully loaded Dialogue Branch project '{}' ({} dialogues).",
				projectSlug, result.getProject().getDialogues().size());
	}

}

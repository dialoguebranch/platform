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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.exception.UnknownLanguageCodeException;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.common.DialogueBranchProject;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.repository.DBLoggedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.storage.VariableStoreStorageHandler;
import org.slf4j.LoggerFactory;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	private final DlbProperties dlbProperties;
	private final List<UserService> activeUserServices = new ArrayList<>();
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
	 * @param userRepository repository used to look up or create the {@link
	 *                        com.dialoguebranch.web.service.storage.model.DBUser} that owns each
	 *                       user's logged dialogues.
	 * @param loggedDialogueRepository repository used to read, create, and update logged dialogues.
	 */
	public ApplicationManager(DlbProperties dlbProperties,
							  VariableStoreStorageHandler storageHandler,
							  DBUserRepository userRepository,
							  DBLoggedDialogueRepository loggedDialogueRepository) {
		this.dlbProperties = dlbProperties;
		this.userServiceFactory = new UserServiceFactory(this, storageHandler, userRepository,
				loggedDialogueRepository);
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

	// ------------------------------------------------------------ //
	// -------------------- Service Management -------------------- //
	// ------------------------------------------------------------ //

	/**
	 * Returns an active {@link UserService} object for the given {@code userId} in the given
	 * {@code timeZone}. Retrieves from an internal list of active {@link UserService}s, or
	 * instantiates a new one if none is active for the given user.
	 *
	 * @param userId   the identifier of the user for which to retrieve a {@link UserService}.
	 * @param timeZone the time zone as {@link ZoneId} in which the user resides.
	 * @return a {@link UserService} object that can handle the communication with the user.
	 * @throws IOException       in case of an error loading the known variables for the User.
	 * @throws DatabaseException in case of an error loading the known variables for the User.
	 */
	public UserService getOrCreateActiveUserService(String userId, ZoneId timeZone)
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
	 * @param userId the identifier of the user for which to retrieve a {@link UserService}.
	 * @return a {@link UserService} object that can handle the communication with the user.
	 * @throws IOException       in case of an error loading the known variables for the User.
	 * @throws DatabaseException in case of an error loading the known variables for the User.
	 */
	public UserService getOrCreateActiveUserService(String userId)
			throws IOException, DatabaseException {
		UserService result = getActiveUserService(userId);
		if (result != null) return result;
		else return createActiveUserService(userId, null);
	}

	/**
	 * Returns the {@link UserService} object for a user with the given {@code userId}, or
	 * {@code null} if there is no currently active user service running.
	 *
	 * @param userId the identifier of the user for which to retrieve a {@link UserService}.
	 * @return a {@link UserService} object, or {@code null} if none exists.
	 */
	public UserService getActiveUserService(String userId) {
		for (UserService userService : activeUserServices) {
			if (userService.getDialogueBranchUser().getId().equals(userId)) {
				return userService;
			}
		}
		return null;
	}

	/**
	 * Creates a new {@link UserService} object for a new user with the given {@code userId} in the
	 * given {@code timeZone}. If successfully created, keeps a record of this active user service.
	 *
	 * @param userId   the identifier of the user for which to create a {@link UserService}.
	 * @param timeZone the time zone as {@link ZoneId} in which the user resides.
	 * @return the newly created {@link UserService} object.
	 * @throws IOException       in case of an error loading the known variables for the User.
	 * @throws DatabaseException in case of an error loading the known variables for the User.
	 */
	private UserService createActiveUserService(String userId, ZoneId timeZone)
			throws IOException, DatabaseException {
		UserService newUserService;
		if (timeZone == null) {
			newUserService = userServiceFactory.createUserService(userId);
		} else {
			newUserService = userServiceFactory.createUserService(userId, timeZone);
		}
		activeUserServices.add(newUserService);
		logger.info("Created a new UserService for userId '{}' (total active users: {}).",
				userId, activeUserServices.size());
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
		return activeUserServices.remove(userService);
	}

	// ------------------------------------------------------------- //
	// -------------------- Dialogue Management -------------------- //
	// ------------------------------------------------------------- //

	/**
	 * Returns the {@link ResourcePointer}s of all dialogues available across all loaded projects.
	 *
	 * @return a list of all available dialogue descriptors.
	 */
	public List<ResourcePointer> getDialogueDescriptions() {
		List<ResourcePointer> result = new ArrayList<>();
		for (DialogueBranchProject project : projects.values()) {
			result.addAll(project.getResourcePointers());
		}
		return result;
	}

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
	 * Checks whether the given {@code language} is supported (as either the source language or a
	 * translation language) by the named project's metadata. If the project isn't loaded or has no
	 * metadata, this is a no-op — dialogue lookup will fail with a more specific error in that case.
	 *
	 * @param projectSlug the project folder name / slug.
	 * @param language    the language code to validate.
	 * @throws UnknownLanguageCodeException if the project's metadata is present and does not list
	 *                                       {@code language} as a supported language.
	 */
	public void validateLanguage(String projectSlug, String language)
			throws UnknownLanguageCodeException {
		DialogueBranchProject project = projects.get(projectSlug);
		if (project instanceof ExecutableProject execProject && execProject.getMetaData() != null) {
			execProject.getMetaData().validateLanguageCode(language);
		}
	}

	/**
	 * Returns all available dialogues across all loaded Dialogue Branch projects.
	 *
	 * @return a list of {@link ResourcePointer}s for all available dialogues.
	 */
	public List<ResourcePointer> getAvailableDialogues() {
		return getDialogueDescriptions();
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
	 * Returns the {@link Dialogue} identified by the given {@code dialogueDescription} and
	 * {@code translationContext}, searching across all loaded Dialogue Branch projects.
	 *
	 * @param dialogueDescription the {@link ResourcePointer} identifying the requested dialogue.
	 * @param translationContext  the translation context to apply, or {@code null} for the source.
	 * @return the requested {@link Dialogue}.
	 * @throws ExecutionException with type {@link ExecutionException.Type#DIALOGUE_NOT_FOUND} if no
	 *                            matching dialogue is found in any loaded project.
	 */
	public Dialogue getDialogueDefinition(ResourcePointer dialogueDescription,
                                          TranslationContext translationContext)
			throws ExecutionException {
		for (DialogueBranchProject project : projects.values()) {
			if (!(project instanceof ExecutableProject execProject)) continue;
			Dialogue dialogue;
			if (translationContext == null) {
				dialogue = execProject.getDialogues().get(dialogueDescription);
			} else {
				dialogue = execProject.getTranslatedDialogue(dialogueDescription, translationContext);
			}
			if (dialogue != null) return dialogue;
		}
		throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
				"Pre-loaded dialogue not found for dialogue '" +
						dialogueDescription.getDialogueName() + "' in language '" +
						dialogueDescription.getLanguage() + "'.");
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
	 */
	public void loadProject(String projectSlug, ScriptLoader scriptLoader, int versionNumber) {
		logger.info("Loading Dialogue Branch project '{}' into memory.", projectSlug);

		ProjectParser projectParser = new ProjectParser(scriptLoader);
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
		logger.info("Successfully loaded Dialogue Branch project '{}' ({} dialogues).",
				projectSlug, result.getProject().getDialogues().size());
	}

}

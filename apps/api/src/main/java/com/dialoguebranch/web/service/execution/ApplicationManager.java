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
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.common.DialogueBranchProject;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.auth.basic.BasicUserFile;
import com.dialoguebranch.web.service.exception.DLBServiceConfigurationException;
import com.dialoguebranch.web.service.storage.VariableStoreStorageHandler;
import org.slf4j.LoggerFactory;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URI;
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
 * <p>On startup it scans the classpath for Dialogue Branch projects by looking for
 * {@code dlb-project.xml} files in direct sub-folders of {@code dlb-projects/}. Each sub-folder
 * that contains a {@code dlb-project.xml} is loaded as a {@link DialogueBranchProject} using a
 * {@link SpringResourceFileLoader}.</p>
 *
 * @author Harm op den Akker
 * @author Tessa Beinema
 */
public class ApplicationManager {

	// ---------------------------------------------------- //
	// -------------------- Constants -------------------- //
	// ---------------------------------------------------- //

	/** Classpath resource path that contains all Dialogue Branch project sub-folders. */
	private static final String DLB_PROJECTS_ROOT = "dlb-projects";

	/** Name of the marker file that identifies a folder as a Dialogue Branch project. */
	private static final String PROJECT_MARKER_FILE = "dlb-project.xml";

	// --------------------------------------------------- //
	// -------------------- Fields -------------------- //
	// --------------------------------------------------- //

	private static final Logger logger = LoggerFactory.getLogger(ApplicationManager.class);

	/** Loaded Dialogue Branch projects, keyed by project name (folder name). */
	private final Map<String, DialogueBranchProject> projects = new LinkedHashMap<>();

	private final DlbProperties dlbProperties;
	private final List<UserService> activeUserServices = new ArrayList<>();
	private final List<BasicUserCredentials> basicUserCredentials;
	private final UserServiceFactory userServiceFactory;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an {@link ApplicationManager}. On construction this scans
	 * {@code dlb-projects/} on the classpath for sub-folders that contain a
	 * {@code dlb-project.xml} file. Each such folder is loaded as a separate Dialogue Branch
	 * project.
	 *
	 * @param dlbProperties the application configuration properties.
	 * @throws DLBServiceConfigurationException if any project cannot be loaded due to parse errors
	 *                                          or configuration problems.
	 */
	public ApplicationManager(DlbProperties dlbProperties,
							  VariableStoreStorageHandler storageHandler)
			throws DLBServiceConfigurationException {
		this.dlbProperties = dlbProperties;

		loadAllProjects();

		this.userServiceFactory = new UserServiceFactory(this, storageHandler);

		if (dlbProperties.getAuth().getService().equals(DlbProperties.AUTH_SERVICE_KEYCLOAK)) {
			basicUserCredentials = new ArrayList<>();
		} else {
			try {
				basicUserCredentials = BasicUserFile.read(dlbProperties.getDataDir());
			} catch (ParseException | IOException e) {
				throw new RuntimeException(e);
			}
		}
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
	 * Returns the list of {@link BasicUserCredentials} available for this {@link ApplicationManager}.
	 *
	 * @return the list of {@link BasicUserCredentials} available for this {@link ApplicationManager}.
	 */
	public List<BasicUserCredentials> getUserCredentials() {
		return basicUserCredentials;
	}

	/**
	 * Returns the {@link BasicUserCredentials} object associated with the given {@code username},
	 * or {@code null} if no such user is known.
	 *
	 * @param username the username of the user to look for.
	 * @return the {@link BasicUserCredentials} object or {@code null}.
	 */
	public BasicUserCredentials getUserCredentialsForUsername(String username) {
		for (BasicUserCredentials uc : basicUserCredentials) {
			if (uc.getUsername().equals(username)) return uc;
		}
		return null;
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
	 * Returns all available dialogues across all loaded Dialogue Branch projects.
	 *
	 * @return a list of {@link ResourcePointer}s for all available dialogues.
	 */
	public List<ResourcePointer> getAvailableDialogues() {
		return getDialogueDescriptions();
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

	// ---------------------------------------------------------------- //
	// -------------------- Private Helper Methods -------------------- //
	// ---------------------------------------------------------------- //

	/**
	 * Scans the classpath for {@code dlb-project.xml} files in direct sub-folders of
	 * {@code dlb-projects/} and loads each discovered project.
	 *
	 * @throws DLBServiceConfigurationException if any discovered project fails to load.
	 */
	private void loadAllProjects() throws DLBServiceConfigurationException {
		PathMatchingResourcePatternResolver resolver =
				new PathMatchingResourcePatternResolver(getClass().getClassLoader());

		String pattern = "classpath*:" + DLB_PROJECTS_ROOT + "/*/" + PROJECT_MARKER_FILE;
		Resource[] markerResources;
		try {
			markerResources = resolver.getResources(pattern);
		} catch (IOException e) {
			throw new DLBServiceConfigurationException(
					"Failed to scan for Dialogue Branch projects under '" +
							DLB_PROJECTS_ROOT + "': " + e.getMessage());
		}

		if (markerResources.length == 0) {
			logger.warn("No Dialogue Branch projects found under classpath resource '{}'. " +
					"The service will start with no dialogues loaded.", DLB_PROJECTS_ROOT);
			return;
		}

		for (Resource markerResource : markerResources) {
			String projectName = deriveProjectName(markerResource);
			if (projectName == null) {
				logger.warn("Could not determine project name from resource: {}", markerResource);
				continue;
			}
			loadProject(projectName);
		}
	}

	/**
	 * Derives the project folder name from the URI of its {@code dlb-project.xml} marker
	 * resource. The folder name is the path segment immediately before the file name.
	 *
	 * @param markerResource the {@code dlb-project.xml} {@link Resource}.
	 * @return the project folder name, or {@code null} if it cannot be determined.
	 */
	private String deriveProjectName(Resource markerResource) {
		try {
			URI uri = markerResource.getURI();
			String uriString = uri.toString();
			// Strip the "/dlb-project.xml" suffix, then take the last path segment
			String withoutFile = uriString.substring(0,
					uriString.length() - ("/" + PROJECT_MARKER_FILE).length());
			return withoutFile.substring(withoutFile.lastIndexOf('/') + 1);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Loads the Dialogue Branch project from the classpath sub-folder
	 * {@code dlb-projects/{projectName}/} using a {@link SpringResourceFileLoader} and stores the
	 * resulting {@link DialogueBranchProject} in {@link #projects}.
	 *
	 * @param projectName the name of the project folder to load.
	 * @throws DLBServiceConfigurationException if the project contains parse errors.
	 */
	private void loadProject(String projectName) throws DLBServiceConfigurationException {
		String resourcePath = DLB_PROJECTS_ROOT + "/" + projectName;
		logger.info("Loading Dialogue Branch project '{}' from classpath resource '{}'.",
				projectName, resourcePath);

		SpringResourceFileLoader fileLoader = new SpringResourceFileLoader(resourcePath);
		ProjectParser projectParser = new ProjectParser(fileLoader);
		ProjectParserResult result;
		try {
			result = projectParser.parse();
		} catch (IOException e) {
			throw new DLBServiceConfigurationException(
					"Error reading Dialogue Branch project '" + projectName +
							"': " + e.getMessage());
		}

		for (String path : result.getParseErrors().keySet()) {
			logger.error("Project '{}': failed to parse {}:", projectName, path);
			for (ParseException ex : result.getParseErrors().get(path)) {
				logger.error("*** {}", ex.getMessage());
			}
		}
		for (String path : result.getWarnings().keySet()) {
			logger.warn("Project '{}': warning at parsing {}:", projectName, path);
			for (String warning : result.getWarnings().get(path)) {
				logger.warn("*** {}", warning);
			}
		}

		if (!result.getParseErrors().isEmpty()) {
			throw new DLBServiceConfigurationException(
					"Dialogue Branch project '" + projectName +
							"' could not be fully loaded due to parse errors.");
		}

		projects.put(projectName, result.getProject());
		logger.info("Successfully loaded Dialogue Branch project '{}' ({} dialogues).",
				projectName, result.getProject().getDialogues().size());
	}

}

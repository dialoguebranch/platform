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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.Dialogue;
import com.dialoguebranch.model.FileDescriptor;
import com.dialoguebranch.model.Project;
import com.dialoguebranch.parser.FileLoader;
import com.dialoguebranch.parser.ProjectParser;
import com.dialoguebranch.parser.ProjectParserResult;
import com.dialoguebranch.web.service.Configuration;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import com.dialoguebranch.web.service.auth.basic.BasicUserFile;
import com.dialoguebranch.web.service.auth.keycloak.KeycloakManager;
import com.dialoguebranch.web.service.exception.DLBServiceConfigurationException;
import com.dialoguebranch.web.service.storage.AzureDataLakeStore;
import com.dialoguebranch.web.service.storage.VariableStoreDatabaseStorageHandler;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The Dialogue Branch Web Service maintains one instance of an {@link ApplicationManager}. This
 * class keeps track of the different active {@link UserService} instances that are needed to serve
 * individual user's of the Dialogue Branch Web Service, as well as other application wide objects.
 *
 * @author Harm op den Akker
 * @author Tessa Beinema
 */
public class ApplicationManager {

	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());
	private final Project project;
	private final List<UserService> activeUserServices = new ArrayList<>();
	private final List<BasicUserCredentials> basicUserCredentials;
	private AzureDataLakeStore azureDataLakeStore = null;
	private KeycloakManager keycloakManager = null;
	private final UserServiceFactory userServiceFactory;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //
	
	/**
	 * Creates an instance of an {@link ApplicationManager}, that loads in a predefined list of
	 * Dialogue Branch dialogues.
	 *
	 * @throws DLBServiceConfigurationException In case any part of the application could not be
	 *                                          initialized due to an incorrectly set config
	 *                                          parameter.
	 */
	public ApplicationManager(FileLoader fileLoader) throws DLBServiceConfigurationException {

		ProjectParser projectParser = new ProjectParser(fileLoader);
		ProjectParserResult readResult;
		try {
			readResult = projectParser.parse();
		} catch (IOException ex) {
			throw new RuntimeException("Error while reading DialogueBranch project: "
					+ ex.getMessage(), ex);
		}
		for (String path : readResult.getParseErrors().keySet()) {
            logger.error("Failed to parse {}:", path);
			for (ParseException ex : readResult.getParseErrors().get(path)) {
                logger.error("*** {}", ex.getMessage());
			}
		}
		for (String path : readResult.getWarnings().keySet()) {
            logger.warn("Warning at parsing {}:", path);
			for (String warning : readResult.getWarnings().get(path)) {
                logger.warn("*** {}", warning);
			}
		}
		if (!readResult.getParseErrors().isEmpty())
			throw new RuntimeException("Failed to load all dialogues.");
		project = readResult.getProject();

		this.userServiceFactory = new UserServiceFactory(this,
				new VariableStoreDatabaseStorageHandler());


		// Load in configuration values
		Configuration config = AppComponents.get(Configuration.class);

		// Initialize User Manager
		if(config.getAuthService().equals(Configuration.AUTH_SERVICE_KEYCLOAK)) {
			keycloakManager = new KeycloakManager();
			basicUserCredentials = new ArrayList<>(); // This is the (now unused) built-in list
		} else {
			// Read all BasicUserCredentials from users.xml
			try {
				basicUserCredentials = BasicUserFile.read();
			} catch (ParseException | IOException e) {
				throw new RuntimeException(e);
			}
		}

		if(Configuration.getInstance().getAzureDataLakeEnabled()) {
			try {
				azureDataLakeStore = new AzureDataLakeStore();
			} catch(DLBServiceConfigurationException e) {
                logger.error("Error configuring Azure Data Lake: {}", e.getMessage());
				throw e;
			}
		}
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //
	
	public List<FileDescriptor> getDialogueDescriptions() {
		return new ArrayList<>(project.getDialogues().keySet());
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
		for(BasicUserCredentials uc : basicUserCredentials) {
			if(uc.getUsername().equals(username)) return uc;
		}
		return null;
	}

	public AzureDataLakeStore getAzureDataLakeStore() {
		return azureDataLakeStore;
	}

	public KeycloakManager getKeycloakManager() {
		return keycloakManager;
	}

	// ------------------------------------------------------------ //
	// -------------------- Service Management -------------------- //
	// ------------------------------------------------------------ //

	/**
	 * Returns an active {@link UserService} object for the given {@code userId} in the given {@code
	 * timeZoneId}. Retrieves from an internal list of active {@link UserService}s, or instantiates
	 * a new {@link UserService} if no {@link UserService} is active for the given user.
	 *
	 * @param userId the identifier of the user for which to retrieve a {@link UserService}.
	 * @param timeZone the time zone as {@link ZoneId} in which the user resides.
	 * @return a {@link UserService} object that can handle the communication with the user.
	 * @throws IOException In case of an error loading in the known variables for the User.
	 * @throws DatabaseException In case of an error loading in the known variables for the User.
	 */
	public UserService getOrCreateActiveUserService(String userId, ZoneId timeZone)
			throws IOException, DatabaseException {
		UserService result = getActiveUserService(userId);
		if(result != null) {
			return result;
		} else {
			return createActiveUserService(userId, timeZone);
		}
	}

	/**
	 * Returns an active {@link UserService} object for the given {@code userId} in the system's
	 * default time zone. Retrieves from an internal list of active {@link UserService}s, or
	 * instantiates a new {@link UserService} if no {@link UserService} is active for the given
	 * user.
	 *
	 * @param userId the identifier of the user for which to retrieve a {@link UserService}.
	 * @return a {@link UserService} object that can handle the communication with the user.
	 * @throws IOException In case of an error loading in the known variables for the User.
	 * @throws DatabaseException In case of an error loading in the known variables for the User.
	 */
	public UserService getOrCreateActiveUserService(String userId)
			throws IOException, DatabaseException {
		UserService result = getActiveUserService(userId);
		if(result != null) return result;
		else {
			return createActiveUserService(userId,null);
		}
	}

	/**
	 * Returns the {@link UserService} object for a user with the given {@code userId}, or {@code
	 * null} if there is no currently active user service running.
	 *
	 * @param userId the identifier of the user for which to retrieve a {@link UserService}.
	 * @return a {@link UserService} object, or {@code null} if none exists.
	 */
	public UserService getActiveUserService(String userId) {
		for(UserService userService : activeUserServices) {
			if(userService.getDialogueBranchUser().getId().equals(userId)) {
				return userService;
			}
		}
		return null;
	}

	/**
	 * Creates a new {@link UserService} object for a new user with the given {@code userId} in the
	 * given {@code timeZone}. If successfully created, keeps a record of this active user service.
	 *
	 * @param userId the identifier of the user for which to create a {@link UserService}.
	 * @param timeZone the time zone as {@link ZoneId} in which the user resides.
	 * @return the newly created {@link UserService} object.
	 * @throws IOException In case of an error loading in the known variables for the User.
	 * @throws DatabaseException In case of an error loading in the known variables for the User.
	 */
	private UserService createActiveUserService(String userId, ZoneId timeZone)
			throws IOException, DatabaseException {

		UserService newUserService;

		if(timeZone == null) {
			newUserService = userServiceFactory.createUserService(userId);
		} else {
			newUserService = userServiceFactory.createUserService(userId, timeZone);
		}

		activeUserServices.add(newUserService);

		logger.info("Created a new UserService for userId '{}' (total active users: {}).",
				userId,activeUserServices.size());

		return newUserService;
	}
	
	/**
	 * Removes the given {@link UserService} from the set of active {@link UserService}s in this
	 * {@link ApplicationManager}.
	 *
	 * @param userService the {@link UserService} to remove.
	 * @return {@code true} if the given {@link UserService} was successfully removed, or {@code
	 * false} if it was not present on the list of active {@link UserService}s in the first place.
	 */
	public boolean removeUserService(UserService userService) {
		return activeUserServices.remove(userService);
	}
	
	// ---------- Dialogue Management:

	public Dialogue getDialogueDefinition(FileDescriptor dialogueDescription,
                                          TranslationContext translationContext)
			throws ExecutionException {
		Dialogue dialogue;
		if (translationContext == null)
			dialogue = project.getDialogues().get(dialogueDescription);
		else
			dialogue = project.getTranslatedDialogue(dialogueDescription, translationContext);
		if (dialogue != null)
			return dialogue;
		throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
			"Pre-loaded dialogue not found for dialogue '" +
					dialogueDescription.getDialogueName() + "' in language '" +
					dialogueDescription.getLanguage() + "'.");
	}
	
	public List<FileDescriptor> getAvailableDialogues() {
		return new ArrayList<>(project.getDialogues().keySet());
	}

}

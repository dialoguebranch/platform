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
import com.dialoguebranch.execution.*;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.*;
import com.dialoguebranch.web.service.Configuration;
import com.dialoguebranch.web.service.storage.*;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.i18n.I18nLanguageFinder;
import nl.rrd.utils.i18n.I18nUtils;
import org.slf4j.Logger;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * A {@link UserService} is a service class that handles all communication with the Dialogue Branch
 * Web Service for a specific {@link User}.
 * 
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Tessa Beinema (University of Twente)
 */
public class UserService {

	/** The dialogue branch user associated with this UserService */
	private final User dialogueBranchUser;

	/** The general ApplicationManager object that governs this UserService */
	private final ApplicationManager applicationManager;
	private final VariableStore variableStore;
	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());
	private final LoggedDialogueStore loggedDialogueStore;
	private final DialogueExecutor dialogueExecutor;

	private TranslationContext translationContext = null;

	// dialogueLanguageMap: map from dialogue name -> language -> dialogue description
	protected Map<String, Map<String, FileDescriptor>> dialogueLanguageMap = new LinkedHashMap<>();

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------
	
	/**
	 * Instantiates a {@link UserService} for a given {@link User}. The UserService creates a {@link
	 * VariableStore} instance and loads in all known variables for the user.
	 *
	 * @param dialogueBranchUser The {@link User} for which this {@link UserService} is handling the
	 *                           interactions.
	 * @param applicationManager the server's {@link ApplicationManager} instance.
	 * @param onVarChangeListener the {@link VariableStoreOnChangeListener} that will be added
	 *                            to the {@link VariableStore} instance that this
	 *                            {@link UserService} creates.
	 */
	public UserService(User dialogueBranchUser, ApplicationManager applicationManager,
					   VariableStoreOnChangeListener onVarChangeListener)
			throws DatabaseException, IOException {

		this.dialogueBranchUser = dialogueBranchUser;
		this.applicationManager = applicationManager;

		VariableStoreStorageHandler storageHandler = new VariableStoreDatabaseStorageHandler();
		try {
			this.variableStore = storageHandler.read(dialogueBranchUser);
		} catch (ParseException ex) {
			throw new DatabaseException("Failed to read initial variables for user '"
					+ dialogueBranchUser.getId() + "': " + ex.getMessage(), ex);
		}

		Configuration config = AppComponents.get(Configuration.class);

		this.variableStore.addOnChangeListener(onVarChangeListener);

		if(config.getExternalVariableServiceEnabled()) {
			this.variableStore.addOnChangeListener(new ExternalVariableServiceUpdater());
		}

		dialogueExecutor = new DialogueExecutor(this);

		loggedDialogueStore = new LoggedDialogueStore(dialogueBranchUser.getId(), this);

		// create dialogueLanguageMap
		List<FileDescriptor> dialogues = applicationManager.getDialogueDescriptions();
		for (FileDescriptor dialogue : dialogues) {
			Map<String, FileDescriptor> langMap =
				dialogueLanguageMap.computeIfAbsent(dialogue.getDialogueName(),
						k -> new LinkedHashMap<>());
			langMap.put(dialogue.getLanguage(), dialogue);
		}
	}


	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------
	
	/**
	 * Returns the {@link User} which this {@link UserService} is serving.
	 * @return the {@link User} which this {@link UserService} is serving.
	 */
	public User getDialogueBranchUser() {
		return dialogueBranchUser;
	}

	/**
	 * Returns the {@link TranslationContext} describing the relevant contextual parameters
	 * needed to select the right translations.
	 * @return the {@link TranslationContext}.
	 */
	public TranslationContext getTranslationContext() {
		return translationContext;
	}

	/**
	 * Sets the {@link TranslationContext} describing the relevant contextual parameters
	 * needed to select the right translations.
	 * @param translationContext the {@link TranslationContext}.
	 */
	public void setTranslationContext(TranslationContext translationContext) {
		this.translationContext = translationContext;
	}
	
	/**
	 * Returns the application's {@link ApplicationManager} that is governing this
	 * {@link UserService}.
	 * @return the application's {@link ApplicationManager} that is governing this
	 *         {@link UserService}.
	 */
	public ApplicationManager getApplicationManager() {
		return applicationManager;
	}

	/**
	 * Returns the {@link VariableStore} for the {@link User} governed by this
	 * {@link UserService}.
	 * @return the {@link VariableStore} for the {@link User} governed by this
	 *         {@link UserService}.
	 */
	public VariableStore getVariableStore() {
		return variableStore;
	}

	/**
	 * Returns the {@link LoggedDialogueStore} associated with this {@link UserService}.
	 * @return the {@link LoggedDialogueStore} associated with this {@link UserService}.
	 */
	public LoggedDialogueStore getLoggedDialogueStore() {
		return loggedDialogueStore;
	}

	// ---------------------------------------------------------------------------
	// -------------------- Other Methods: Dialogue Execution --------------------
	// ---------------------------------------------------------------------------

	/**
	 * Starts a dialogue session with the given {@code dialogueId} and preferred language, returning
	 * the first step of the dialogue. If you specify a {@code nodeId}, it will start at that node.
	 * Otherwise, it starts at the "Start" node.
	 *
	 * <p>This method is called as a result of a user action (i.e. a call to the /dialogue/start
	 * end-point).</p>
	 *
	 * <p>You can specify an ISO language tag such as "en-US" or "en".</p>
	 *
	 * @param dialogueId the dialogue ID
	 * @param nodeId a node ID or null
	 * @param language an ISO language tag
	 * @param sessionId the unique identifier that should be added to the logging of dialogues
	 *                  for this started dialogue session.
	 * @param sessionStartTime the utc timestamp for when this dialogue session started.
	 * @return the dialogue node result with the start node or specified node
	 */
	public ExecuteNodeResult startDialogueSession(String dialogueId, String nodeId, String language,
												  String sessionId, long sessionStartTime)
			throws DatabaseException, IOException, ExecutionException {

		// This should not happen as this method should only be called by
		// DialogueController.doStartDialogue() that already ensures a unique sessionId
		if(existsSessionId(sessionId))
			throw new DatabaseException("The provided sessionId for a new dialogue session is " +
					"already in use.");

        logger.info("User '{}' is starting dialogue '{}'", dialogueBranchUser.getId(), dialogueId);

		FileDescriptor dialogueDescription =
				getDialogueDescriptionFromId(dialogueId, language);

		if (dialogueDescription == null) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
					"Dialogue not found: " + dialogueId);
		}
		Dialogue dialogue = getDialogueDefinition(dialogueDescription);

		return dialogueExecutor.startDialogue(dialogueDescription, dialogue, nodeId, sessionId,
				sessionStartTime);
	}

	/**
	 * Continues the dialogue session after the user selected the specified reply. This method
	 * stores the reply as a user action in the database, and it performs any "set" actions
	 * associated with the reply. Then it determines the next node, if any.
	 *
	 * <p>If there is no next node, this method will complete the current dialogue, and this method
	 * returns null.</p>
	 *
	 * <p>If the reply points to another dialogue, this method will complete the current dialogue
	 * and start the other dialogue.</p>
	 *
	 * <p>For the returned node, this method executes the agent statement and reply statements using
	 * the variable store. It executes ("if" and "set") commands and resolves variables. The
	 * returned node contains any content that should be sent to the client. This content can be
	 * text or client commands, with all variables resolved.</p>
	 *
	 * @param state the state from which the dialogue should progress
	 * @param replyId the reply ID
	 * @return the next node or null
	 * @throws DatabaseException if a database error occurs
	 * @throws IOException if a communication error occurs
	 * @throws ExecutionException if the request is invalid
	 */
	public ExecuteNodeResult progressDialogueSession(DialogueState state, int replyId)
			throws DatabaseException, IOException, ExecutionException {
		ActiveDialogue dialogue = state.getActiveDialogue();
		String dialogueName = dialogue.getDialogueFileDescription().getDialogueName();
		String nodeName = dialogue.getCurrentNode().getTitle();
		logger.info("User {} progresses dialogue with reply {}.{}.{}",
				dialogueBranchUser.getId(), dialogueName, nodeName, replyId);
		return dialogueExecutor.progressDialogue(state, replyId);
	}

	public ExecuteNodeResult revertDialogueSession(DialogueState state, ZonedDateTime eventTime)
			throws ExecutionException {
		ActiveDialogue dialogue = state.getActiveDialogue();
		String dialogueName = dialogue.getDialogueDefinition().getDialogueName();
		String nodeName = dialogue.getCurrentNode().getTitle();
		logger.info("User {} goes back in dialogue from node {}.{}",
				dialogueBranchUser.getId(), dialogueName, nodeName);
		return dialogueExecutor.backDialogue(state, eventTime);
	}

	public ExecuteNodeResult continueDialogueSession(DialogueState state, ZonedDateTime eventTime)
		throws ExecutionException {
		return dialogueExecutor.executeCurrentNode(state,eventTime);
	}

	/**
	 * Cancels the current dialogue.
	 *
	 * @throws DatabaseException if a database error occurs
	 * @throws IOException if a communication error occurs
	 */
	public void cancelDialogueSession(String loggedDialogueId)
			throws DatabaseException, IOException {
        logger.info("User '{}' cancels dialogue with Id '{}'.",
				dialogueBranchUser.getId(), loggedDialogueId);
		ServerLoggedDialogue serverLoggedDialogue =
				loggedDialogueStore.findLoggedDialogue(loggedDialogueId);
		if(serverLoggedDialogue != null)
			loggedDialogueStore.setDialogueCancelled(serverLoggedDialogue);
		else
            logger.warn("User '{}' attempted to cancel dialogue with Id '{}', but no such " +
					"dialogue could be found.", dialogueBranchUser.getId(), loggedDialogueId);
	}

	// --------------------------------------------------------------------------
	// -------------------- Other Methods: Variable Handling --------------------
	// --------------------------------------------------------------------------

	/**
	 * Stores a given set of variables that have been set as part of a user's reply in a dialogue in
	 * the variable store.
	 *
	 * @param variables the set of variables
	 * @param eventTime the timestamp (in the time zone of the user) of the event that triggered
	 *                  this change of DialogueBranch Variables
	 */
	public void storeReplyInput(Map<String,?> variables, ZonedDateTime eventTime)
			throws ExecutionException {
		variableStore.addAll(variables,true,eventTime,
				VariableStoreChange.Source.INPUT_REPLY);
	}


	/**
	 * This function ensures that for all DialogueBranch Variables in the given {@link Set}, of
	 * {@code variableNames} an up-to-date value is loaded into the {@link VariableStore}
	 * for this user represented by this {@link UserService} through an external Dialogue Branch
	 * Variable Service if, and only if one has been configured. If {@code
	 * config.getExternalVariableServiceEnabled() == false} this method will cause no changes to
	 * occur.
	 *
	 * @param variableNames the set of DialogueBranch Variables that need to have their values
	 *                      updated.
	 */
	public void updateVariablesFromExternalService(Set<String> variableNames) {
        logger.info("Attempting to update values from external service for the following set " +
				"of variables: {}", variableNames);

		Configuration config = AppComponents.get(Configuration.class);

		if(config.getExternalVariableServiceEnabled()) {
			logger.info("An external Dialogue Branch Variable Service is enabled at {}/v{}/",
					config.getExternalVariableServiceURL(),
					config.getExternalVariableServiceAPIVersion());

			List<Variable> varsToUpdate = new ArrayList<>();
			for(String variableName : variableNames) {
				Variable variable = variableStore.getVariable(variableName);
				if(variable != null) {
                    logger.info("A DialogueBranch Variable '{}' exists for User '{}': {}",
							variableName, dialogueBranchUser.getId(), variable);
					varsToUpdate.add(variable);
				} else {
					varsToUpdate.add(
							new Variable(
									variableName,
									null,
									null,
									null));
				}
			}

			RestTemplate restTemplate = new RestTemplate();
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.valueOf("application/json"));
			requestHeaders.set("Authorization", "Bearer "
					+ config.getExternalVariableServiceAPIKey());

			String retrieveUpdatesUrl = config.getExternalVariableServiceURL()
					+ "/v"+config.getExternalVariableServiceAPIVersion()
					+ "/variables/retrieve-updates";

            logger.info("RetrieveUpdatesURL: {}", retrieveUpdatesUrl);

			LinkedMultiValueMap<String,String> allRequestParams = new LinkedMultiValueMap<>();
			allRequestParams.put("userId", Collections.singletonList(dialogueBranchUser.getId()));
			allRequestParams.put("timeZone", Collections.singletonList(
					dialogueBranchUser.getTimeZone().toString()));

			// requestBody is of string type and requestHeaders is of type HttpHeaders
			HttpEntity<?> entity = new HttpEntity<>(varsToUpdate, requestHeaders);

			// rawValidURl = http://example.com/hotels
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(retrieveUpdatesUrl)
					.queryParams(allRequestParams); // The allRequestParams must have been built
			                                        // for all the query params

			// encode() is to ensure that characters like {, }, are preserved and not encoded.
			// Skip if not needed.
			UriComponents uriComponents = builder.build().encode();

			Variable[] retrievedVariables = null;
			ResponseEntity<Variable[]> response = null;
			try {
				response = restTemplate.exchange(uriComponents.toUri(), HttpMethod.POST,
						entity, Variable[].class);

				// If call not successful
				if (response.getStatusCode() != HttpStatus.OK) {
					logger.warn("Error retrieving updates from external variable service.");
				}
			} catch (Exception e) {
				logger.error("Critical Error retrieving updates for Dialogue Branch Variables. " +
						"Continuing operation while assuming no updates were needed.",e);
			}

			if(response != null) retrievedVariables = response.getBody();

			if (retrievedVariables != null) {
				if (retrievedVariables.length == 0) {
					logger.info("Received response from Dialogue Branch Variable Service: " +
							"no variable updates needed.");
				} else {
					logger.info("Received response from Dialogue Branch Variable Service: " +
							"the following variables have updated values:");
					for (Variable variable : retrievedVariables) {
						logger.info(variable.toString());
						String varName = variable.getName();
						Object varValue = variable.getValue();
						ZonedDateTime varUpdated = variable.getZonedUpdatedTime();

						if(varValue != null) {
							variableStore.setValue(varName, varValue, true,
									varUpdated,
									VariableStoreChange.Source.EXTERNAL_VARIABLE_SERVICE);
						// If a 'null' value is received, we delete the variable
						} else {
							variableStore.removeByName(varName, true, varUpdated,
									VariableStoreChange.Source.EXTERNAL_VARIABLE_SERVICE);
						}
					}
				}
			}
		} else {
			logger.info("No external Dialogue Branch Variable Service has been configured, " +
					"no variables have been updated.");
		}
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}

	// ----- Methods (Retrieval)

	/**
	 * Returns the available dialogues for all agents in the specified preferred
	 * language. You can specify an ISO language tag such as "en-US".
	 *
	 * @param language an ISO language tag
	 * @return a list of dialogue names
	 */
	public List<FileDescriptor> getAvailableDialogues(String language) {
		List<FileDescriptor> filteredAvailableDialogues =
				new ArrayList<>();
		Locale prefLocale;
		try {
			prefLocale = I18nUtils.languageTagToLocale(language);
		} catch (ParseException ex) {
            logger.error("{}: {}", String.format(
                    "Invalid language tag \"%s\", falling back to system locale",
                    language), ex.getMessage());
			prefLocale = Locale.getDefault();
		}
		for (Map<String, FileDescriptor> langMap :
				dialogueLanguageMap.values()) {
			List<String> keys = new ArrayList<>(langMap.keySet());
			I18nLanguageFinder i18nFinder = new I18nLanguageFinder(keys);
			i18nFinder.setUserLocale(prefLocale);
			String lang = i18nFinder.find();
			if (lang != null)
				filteredAvailableDialogues.add(langMap.get(lang));
			else if (!keys.isEmpty())
				filteredAvailableDialogues.add(langMap.get(keys.get(0)));
		}
		return filteredAvailableDialogues;
	}

	/**
	 * Returns the dialogue description for the specified dialogue ID and preferred language.
	 *
	 * <p>If no dialogue with the specified ID is found, then this method
	 * returns null.</p>
	 *
	 * @param dialogueId the dialogue ID
	 * @param language an ISO language tag or null
	 * @return the dialogue description or null
	 */
	public FileDescriptor getDialogueDescriptionFromId(
			String dialogueId, String language) {
		for (FileDescriptor dialogueDescription : this.getAvailableDialogues(language)) {
			if (dialogueDescription.getDialogueName().equals(dialogueId)) {
				return dialogueDescription;
			}
		}
		return null;
	}

	/**
	 * Retrieves the dialogue definition for the specified description, or throws
	 * a {@link ExecutionException} with {@link
	 * ExecutionException.Type#DIALOGUE_NOT_FOUND DIALOGUE_NOT_FOUND} if no such
	 * dialogue definition exists in this service manager.
	 * 
	 * @param dialogueDescription the sought dialogue description
	 * @return the {@link Dialogue} containing the DialogueBranch dialogue representation.
	 * @throws ExecutionException if the dialogue definition is not found
	 */
	public Dialogue getDialogueDefinition(
			FileDescriptor dialogueDescription) throws ExecutionException {
		return this.applicationManager.getDialogueDefinition(dialogueDescription,
				translationContext);
	}

	public DialogueState getDialogueState(String loggedDialogueId,
			int loggedInteractionIndex) throws ExecutionException, DatabaseException,
			IOException {
		ServerLoggedDialogue loggedDialogue =
				loggedDialogueStore.findLoggedDialogue(loggedDialogueId);
		if (loggedDialogue == null) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
					"Logged dialogue not found");
		}
		return getDialogueState(loggedDialogue, loggedInteractionIndex);
	}

	public DialogueState getDialogueState(ServerLoggedDialogue loggedDialogue,
										  int loggedInteractionIndex) throws ExecutionException {
		String dialogueName = loggedDialogue.getDialogueName();
		FileDescriptor dialogueDescription =
				getDialogueDescriptionFromId(dialogueName,
				loggedDialogue.getLanguage());
		if (dialogueDescription == null) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
					"Dialogue not found: " + dialogueName);
		}
		Dialogue dialogueDefinition = getDialogueDefinition(
				dialogueDescription);
		List<LoggedInteraction> interactions =
				loggedDialogue.getInteractionList();
		if (loggedInteractionIndex < 0 || loggedInteractionIndex >= interactions.size()) {
			throw new ExecutionException(ExecutionException.Type.INTERACTION_NOT_FOUND,
					String.format(
					"Interaction \"%s\" not found in logged dialogue \"%s\"",
					loggedInteractionIndex, loggedDialogue.getId()));
		}
		String nodeId = loggedDialogue.getInteractionList()
				.get(loggedInteractionIndex).getNodeId();
		Node node = dialogueDefinition.getNodeById(nodeId);
		if (node == null) {
			throw new ExecutionException(ExecutionException.Type.NODE_NOT_FOUND,
					String.format("Node \"%s\" not found in dialogue \"%s\"",
							nodeId, dialogueName));
		}
		ActiveDialogue activeDialogue = new ActiveDialogue(
				dialogueDescription, dialogueDefinition);
		activeDialogue.setVariableStore(variableStore);
		activeDialogue.setCurrentNode(node);
		return new DialogueState(dialogueDescription, dialogueDefinition,
				loggedDialogue, loggedInteractionIndex, activeDialogue);
	}

	/**
	 * Checks whether a given {@code sessionId} exists for this user, and returns {@code true} if it
	 * does, or {@code false} if not.
	 * @param sessionId the sessionId {@link String} for which to check.
	 * @return {@code true} if the sessionId is already in use, false otherwise.
	 */
	public boolean existsSessionId(String sessionId) throws DatabaseException {
		return loggedDialogueStore.existsSessionId(sessionId);
	}

	public List<ServerLoggedDialogue> getDialogueSessionLog(String sessionId)
			throws IOException, DatabaseException {
        logger.info("Getting dialogue log session data for user '{}' and sessionId '{}'.",
				dialogueBranchUser.getId(), sessionId);
		return loggedDialogueStore.readSession(sessionId);
	}

}

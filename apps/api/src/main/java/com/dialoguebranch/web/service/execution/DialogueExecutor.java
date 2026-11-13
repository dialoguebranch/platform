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
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.execution.ResolvedDialogue;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.model.execute.*;
import com.dialoguebranch.web.service.DateTimeUtils;
import com.dialoguebranch.web.service.exception.DatabaseException;
import com.dialoguebranch.web.service.storage.ServerLoggedDialogue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * A {@link DialogueExecutor} holds a set of functions for executing Dialogue Branch Dialogue for a
 * given {@link UserService}.
 *
 * @author Tessa Beinema
 * @author Harm op den Akker
 */
public class DialogueExecutor {

	private static final Logger logger = LoggerFactory.getLogger(DialogueExecutor.class);

	/** The {@link UserService} for which dialogues are being executed. */
	protected UserService userService;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link DialogueExecutor} for a given {@code userService}.
	 * @param userService the {@link UserService} for which dialogues can be executed.
	 */
	public DialogueExecutor(UserService userService) {
		this.userService = userService;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Starts the dialogue for the specified dialogue definition. If you specify a node ID, it will
	 * start at that node. Otherwise, it starts at the "Start" node.
	 *
	 * @param dialogue the dialogue, as found by {@link ApplicationManager#getDialogueResolver}.
	 * @param nodeId the node ID or {@code null}.
	 * @param sessionId the unique session identifier to be added to the logs.
	 * @param sessionStartTime the utc timestamp of when this session was started.
	 * @param projectSlug the unique slug of the project the dialogue belongs to.
	 * @return the start node or specified node.
	 * @throws DatabaseException if a database error occurs.
	 * @throws IOException if a communication error occurs.
	 * @throws ExecutionException if the request is invalid.
	 */
	public ExecuteNodeResult startDialogue(ResolvedDialogue dialogue, String nodeId,
										   String sessionId, long sessionStartTime,
										   String projectSlug)
			throws DatabaseException, IOException, ExecutionException {

		// The dialogues that replies lead into are found by the same resolver (#473), and the end
		// user's gender, $dlb.userGender, selects the translation at every step (#400).
		ActiveDialogue activeDialogue = new ActiveDialogue(
				userService.getApplicationManager().getDialogueResolver(projectSlug), dialogue,
				userService.getVariableStore(projectSlug));
		readExternalVariables(projectSlug, dialogue.dialogue());

		// The timestamp of this "start dialogue" trigger will be passed on and used for logging
		ZonedDateTime eventTime = DateTimeUtils.nowMs(
				userService.getDialogueBranchUser().getTimeZone());

		Node startNode;
		try {
			startNode = activeDialogue.startDialogue(nodeId,eventTime);
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}

		ServerLoggedDialogue serverLoggedDialogue = newLoggedDialogue(dialogue, eventTime,
				sessionId, sessionStartTime, projectSlug);
		updateLoggedDialogue(activeDialogue, serverLoggedDialogue, -1);
		userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
		return new ExecuteNodeResult(dialogue.dialogue(), startNode, serverLoggedDialogue,
				serverLoggedDialogue.getInteractionList().size() - 1);
	}

	/**
	 * Updates the variables the given dialogue uses from the External Variable Service, if one
	 * is enabled, as at the start of every dialogue.
	 */
	private void readExternalVariables(String projectSlug, Dialogue dialogue) {
		Set<String> variablesNeeded = dialogue.getVariablesNeeded();
		logger.info("Dialogue '" + dialogue.getDialogueName() +
				"' uses the following set of Dialogue Branch Variables: "+variablesNeeded);
		if(!variablesNeeded.isEmpty())
			userService.updateVariablesFromExternalService(projectSlug, variablesNeeded);
	}

	/** Creates the log of a dialogue that starts in the given session. */
	private ServerLoggedDialogue newLoggedDialogue(ResolvedDialogue dialogue,
			ZonedDateTime eventTime, String sessionId, long sessionStartTime,
			String projectSlug) {
		ServerLoggedDialogue serverLoggedDialogue = new ServerLoggedDialogue(
				userService.getDialogueBranchUser().getId(),
				eventTime, sessionId, sessionStartTime);
		serverLoggedDialogue.setProjectName(projectSlug);
		serverLoggedDialogue.setDialogueName(dialogue.dialogue().getDialogueName());
		serverLoggedDialogue.setLanguage(dialogue.pointer().getLanguage());
		Integer publishedVersionNumber =
				userService.getApplicationManager().getProjectVersion(projectSlug);
		if (publishedVersionNumber != null) {
			serverLoggedDialogue.setPublishedVersionNumber(publishedVersionNumber);
		}
		return serverLoggedDialogue;
	}

	/**
	 * Continues the dialogue after the user selected the specified reply. This method stores the
	 * reply as a user action in the database, and it performs any "set" actions associated with the
	 * reply. Then it determines the next node, if any.
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
	 * @param state a collection of objects defining the state of the currently ongoing dialogue.
	 * @param replyId the reply ID
	 * @return the next node or null
	 * @throws DatabaseException if a database error occurs
	 * @throws IOException if a communication error occurs
	 * @throws ExecutionException if the request is invalid
	 */
	public ExecuteNodeResult progressDialogue(DialogueState state, int replyId)
			throws DatabaseException, IOException, ExecutionException {

		// Define the event time that is passed along and used for logging
		ZonedDateTime progressDialogueEventTime =
				DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());

		ServerLoggedDialogue serverLoggedDialogue = (ServerLoggedDialogue)state.getLoggedDialogue();
		ActiveDialogue dialogue = state.getActiveDialogue();
		String userStatement = dialogue.getUserStatementFromReplyId(replyId);

		// Update the serverLoggedDialogue with this interaction
		serverLoggedDialogue.getInteractionList().add(new LoggedInteraction(
				System.currentTimeMillis(), MessageSource.USER, "USER",
				serverLoggedDialogue.getDialogueName(), dialogue.getCurrentNode().getTitle(),
				state.getLoggedInteractionIndex(),
				userStatement, replyId));

		int userActionIndex = serverLoggedDialogue.getInteractionList().size() - 1;

		// Entering another dialogue reads its variables from the External Variable Service, as
		// at a start; its log is made below, once its first node has run (#473).
		String projectSlug = serverLoggedDialogue.getProjectName();
		ResolvedDialogue[] entered = new ResolvedDialogue[1];
		dialogue.setDialogueSwitchListener((from, to) -> {
			readExternalVariables(projectSlug, to.dialogue());
			entered[0] = to;
		});

		Node nextNode;
		try {
			nextNode = dialogue.selectReply(replyId, progressDialogueEventTime);
		} catch (EvaluationException ex) {
			userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
			throw new RuntimeException("Expression evaluation error: " + ex.getMessage(), ex);
		}

		if (entered[0] == null) { // The dialogue continues within the same .dlb script
			updateLoggedDialogue(dialogue, serverLoggedDialogue, userActionIndex);
			userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
			if (nextNode == null)
				return null;
			return new ExecuteNodeResult(state.getDialogueDefinition(), nextNode,
					serverLoggedDialogue, serverLoggedDialogue.getInteractionList().size() - 1);
		}

		// The reply led into another .dlb script: its log completes, and a new one starts in the
		// same session, for the dialogue that was entered.
		serverLoggedDialogue.setCompleted(true);
		userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
		ServerLoggedDialogue enteredLog = newLoggedDialogue(entered[0],
				progressDialogueEventTime, serverLoggedDialogue.getSessionId(),
				serverLoggedDialogue.getSessionStartTime(), projectSlug);
		updateLoggedDialogue(dialogue, enteredLog, -1);
		userService.getLoggedDialogueStore().saveToSession(enteredLog);
		return new ExecuteNodeResult(entered[0].dialogue(), nextNode, enteredLog,
				enteredLog.getInteractionList().size() - 1);
	}

	/**
	 * Reverts the given dialogue to the previous agent interaction node, re-executing that node
	 * at the given event time and returning its result.
	 *
	 * @param state the current dialogue state from which to go back.
	 * @param eventTime the timestamp of the back-navigation event in the user's time zone.
	 * @return the {@link ExecuteNodeResult} for the previous agent node.
	 * @throws ExecutionException if the previous state cannot be determined.
	 */
	public ExecuteNodeResult backDialogue(DialogueState state, ZonedDateTime eventTime)
			throws ExecutionException {
		ServerLoggedDialogue serverLoggedDialogue = (ServerLoggedDialogue)state.getLoggedDialogue();
		List<LoggedInteraction> interactions = serverLoggedDialogue.getInteractionList();
		int prevIndex = findPreviousAgentInteractionIndex(interactions,
				state.getLoggedInteractionIndex());
		DialogueState backState = userService.getDialogueState(serverLoggedDialogue,
				prevIndex);
		return executeCurrentNode(backState, eventTime);
	}

	private int findPreviousAgentInteractionIndex(List<LoggedInteraction> interactions,
												  int start) {
		LoggedInteraction interaction = interactions.get(start);
		while (interaction.getPreviousIndex() != -1) {
			int index = interaction.getPreviousIndex();
			interaction = interactions.get(index);
			if (interaction.getMessageSource() == MessageSource.AGENT)
				return index;
		}
		return start;
	}

	/**
	 * Executes the current node of the given dialogue state at the given event time, resolving
	 * variables and returning the result.
	 *
	 * @param state the current dialogue state whose node should be executed.
	 * @param eventTime the timestamp of the event in the user's time zone.
	 * @return the {@link ExecuteNodeResult} for the current node.
	 */
	public ExecuteNodeResult executeCurrentNode(DialogueState state, ZonedDateTime eventTime) {
		ServerLoggedDialogue serverLoggedDialogue = (ServerLoggedDialogue)state.getLoggedDialogue();
		ActiveDialogue dialogue = state.getActiveDialogue();
		dialogue.setVariableStore(
				userService.getVariableStore(serverLoggedDialogue.getProjectName()));
		Node node = dialogue.getCurrentNode();
		try {
			node = dialogue.executeNode(node,eventTime);
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}
		return new ExecuteNodeResult(state.getDialogueDefinition(),
				node, serverLoggedDialogue, state.getLoggedInteractionIndex());
	}

	/**
	 * This method is called before the current node is returned from startDialogue() or
	 * progressDialogue(). The current node is null after a reply to End.
	 *
	 * <p>If there is a current node, this method adds a logged agent interaction for it.</p>
	 *
	 * <p>If the dialogue has finished ({@link ActiveDialogue#isFinished()}), it is marked as
	 * completed.</p>
	 *
	 * @param dialogue the dialogue, at its current node.
	 * @param serverLoggedDialogue the {@link ServerLoggedDialogue} to update.
	 * @param previousIndex the previous interaction index
	 */
	private void updateLoggedDialogue(ActiveDialogue dialogue,
									  ServerLoggedDialogue serverLoggedDialogue,
									  int previousIndex) {
		Node node = dialogue.getCurrentNode();
		ResolvedNodeBody body = node == null ? null : (ResolvedNodeBody) node.getBody();
		if (node != null) {
			StringBuilder agentStatement = new StringBuilder();
			for (NodeBody.Segment segment : body.getSegments()) {
				agentStatement.append(segment.toString());
			}
			String readableAgentStatement = agentStatement.toString();

			serverLoggedDialogue.getInteractionList().add(new LoggedInteraction(
				System.currentTimeMillis(),
				MessageSource.AGENT,
				node.getHeader().getSpeaker(),
				serverLoggedDialogue.getDialogueName(),
				node.getTitle(),
				previousIndex,
				readableAgentStatement,
				-1)
			);

		}
		if (dialogue.isFinished()) {
			serverLoggedDialogue.setCompleted(true);
		}

	}

}

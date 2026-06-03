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
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.model.*;
import com.dialoguebranch.model.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.web.service.storage.ServerLoggedDialogue;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.expressions.EvaluationException;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * A {@link DialogueExecutor} holds a set of functions for executing Dialogue Branch Dialogue for a
 * given {@link UserService}.
 * 
 * @author Tessa Beinema (University of Twente)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class DialogueExecutor {

	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());
	protected UserService userService;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of a {@link DialogueExecutor} for a given {@code userService}.
	 * @param userService the {@link UserService} for which dialogues can be executed.
	 */
	public DialogueExecutor(UserService userService) {
		this.userService = userService;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	/**
	 * Starts the dialogue for the specified dialogue definition. If you specify a node ID, it will
	 * start at that node. Otherwise, it starts at the "Start" node.
	 *
	 * @param dialogueDescription the dialogue description.
	 * @param dialogueDefinition the dialogue definition.
	 * @param nodeId the node ID or {@code null}.
	 * @param sessionId the unique session identifier to be added to the logs.
	 * @param sessionStartTime the utc timestamp of when this session was started.
	 * @return the start node or specified node.
	 * @throws DatabaseException if a database error occurs.
	 * @throws IOException if a communication error occurs.
	 * @throws ExecutionException if the request is invalid.
	 */
	public ExecuteNodeResult startDialogue(FileDescriptor dialogueDescription,
										   Dialogue dialogueDefinition, String nodeId,
										   String sessionId, long sessionStartTime)
			throws DatabaseException, IOException, ExecutionException {

		ActiveDialogue dialogue = new ActiveDialogue(dialogueDescription,
				dialogueDefinition);
		dialogue.setVariableStore(userService.getVariableStore());

		// Collects all the Dialogue Branch Variables needed to execute this file and update from an
		// external variable service (if enabled).
		Set<String> variablesNeeded = dialogueDefinition.getVariablesNeeded();
		logger.info("Dialogue '" + dialogue.getDialogueDefinition().getDialogueName() +
				"' uses the following set of DialogueBranch Variables: "+variablesNeeded);
		if(!variablesNeeded.isEmpty())
			userService.updateVariablesFromExternalService(variablesNeeded);

		// The timestamp of this "start dialogue" trigger will be passed on and used for logging
		ZonedDateTime eventTime = DateTimeUtils.nowMs(
				userService.getDialogueBranchUser().getTimeZone());

		Node startNode;
		try {
			startNode = dialogue.startDialogue(nodeId,eventTime);
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}

		ServerLoggedDialogue serverLoggedDialogue = new ServerLoggedDialogue(
				userService.getDialogueBranchUser().getId(),
				eventTime, sessionId, sessionStartTime);
		serverLoggedDialogue.setDialogueName(dialogueDefinition.getDialogueName());
		serverLoggedDialogue.setLanguage(dialogueDescription.getLanguage());
		updateLoggedDialogue(startNode, serverLoggedDialogue, -1);
		userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
		return new ExecuteNodeResult(dialogueDefinition, startNode, serverLoggedDialogue,
				serverLoggedDialogue.getInteractionList().size() - 1);
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

		// Find next dialogue node:
		NodePointer nodePointer;
		try {
			nodePointer = dialogue.processReplyAndGetNodePointer(replyId,progressDialogueEventTime);
		} catch (EvaluationException ex) {
			userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
			throw new RuntimeException("Expression evaluation error: " + ex.getMessage(), ex);
		}
		Dialogue dialogueDefinition = state.getDialogueDefinition();
		Node nextNode;
		if (nodePointer instanceof InternalNodePointer) {
			try {
				nextNode = dialogue.progressDialogue((InternalNodePointer)nodePointer,
								progressDialogueEventTime);
			} catch (EvaluationException e) {
				throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
			}
			updateLoggedDialogue(nextNode, serverLoggedDialogue, userActionIndex);
			userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
			if (nextNode == null)
				return null;
			return new ExecuteNodeResult(dialogueDefinition, nextNode, serverLoggedDialogue,
					serverLoggedDialogue.getInteractionList().size() - 1);

		} else { // The dialogue continues with a pointer to another .dlb script
			serverLoggedDialogue.setCompleted(true);
			userService.getLoggedDialogueStore().saveToSession(serverLoggedDialogue);
			String language = dialogue.getDialogueFileDescription().getLanguage();
			ExternalNodePointer externalNodePointer = (ExternalNodePointer)nodePointer;
			String dialogueId = externalNodePointer.getAbsoluteTargetDialogue();
			String nodeId = externalNodePointer.getTargetNodeId();

			FileDescriptor dialogueDescription =
					userService.getDialogueDescriptionFromId(dialogueId, language);
			if (dialogueDescription == null) {
				throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
						"Dialogue not found: " + dialogueId);
			}
			Dialogue newDialogue = userService.getDialogueDefinition(dialogueDescription);

			return this.startDialogue(dialogueDescription, newDialogue, nodeId,
					serverLoggedDialogue.getSessionId(), serverLoggedDialogue.getSessionStartTime());
		}
	}

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

	public ExecuteNodeResult executeCurrentNode(DialogueState state, ZonedDateTime eventTime) {
		ServerLoggedDialogue serverLoggedDialogue = (ServerLoggedDialogue)state.getLoggedDialogue();
		ActiveDialogue dialogue = state.getActiveDialogue();
		dialogue.setVariableStore(userService.getVariableStore());
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
	 * progressDialogue(). The node can be null as a result of progressDialogue() with an end reply.
	 * 
	 * <p>If the node is not null, this method adds a logged agent interaction for it.</p>
	 * 
	 * <p>If the node is null or it has no replies, the dialogue is marked as completed.</p>
	 * 
	 * @param node the current node or null
	 * @param serverLoggedDialogue the {@link ServerLoggedDialogue} to update.
	 * @param previousIndex the previous interaction index
	 */
	private void updateLoggedDialogue(Node node, ServerLoggedDialogue serverLoggedDialogue,
									  int previousIndex) {
		if (node != null) {
			StringBuilder agentStatement = new StringBuilder();
			for (NodeBody.Segment segment : node.getBody().getSegments()) {
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
		if (node == null || node.getBody().getReplies().isEmpty()) {
			serverLoggedDialogue.setCompleted(true);
		}

	}

}

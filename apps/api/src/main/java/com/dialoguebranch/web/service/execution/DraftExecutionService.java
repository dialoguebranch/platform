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
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.expressions.EvaluationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral, in-memory execution service for test-running a single draft dialogue's current
 * content without requiring it to be published first.
 *
 * <p>Sessions are driven directly through {@link ActiveDialogue} and held only in this service's
 * in-memory {@code sessions} map — they never touch
 * {@link com.dialoguebranch.web.service.storage.LoggedDialogueStore}, and are entirely separate
 * from the published-dialogue execution path in {@link DialogueExecutor}/{@link UserService}
 * used by {@code /dialogue/*}.</p>
 *
 * <p>A draft test session reads and writes the user's real {@link VariableStore}. A snapshot is
 * taken when the session starts so that {@link #revertVariables(DraftTestSession)} can restore
 * exactly the values that existed beforehand.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class DraftExecutionService {

	private final DraftDialogueService draftDialogueService;
	private final Map<String, DraftTestSession> sessions = new ConcurrentHashMap<>();

	public DraftExecutionService(DraftDialogueService draftDialogueService) {
		this.draftDialogueService = draftDialogueService;
	}

	/**
	 * The result of starting a draft test session: the new session's ID and the
	 * {@link ExecuteNodeResult} for its start node.
	 *
	 * @param sessionId        the ID of the newly created {@link DraftTestSession}.
	 * @param executeNodeResult the result for the dialogue's start node.
	 */
	public record StartResult(String sessionId, ExecuteNodeResult executeNodeResult) {
	}

	/**
	 * Returns the draft test session with the given {@code sessionId}.
	 *
	 * @param sessionId the session ID.
	 * @return the matching {@link DraftTestSession}.
	 * @throws NotFoundException if no session with that ID exists (e.g. it was never started, was
	 *                           already cancelled/reverted, or the service has restarted).
	 */
	public DraftTestSession getSession(String sessionId) throws NotFoundException {
		DraftTestSession session = sessions.get(sessionId);
		if (session == null) {
			throw new NotFoundException("Draft test session not found: " + sessionId);
		}
		return session;
	}

	/**
	 * Parses the current content of the given draft dialogue and starts an ephemeral test session
	 * for it, snapshotting the user's variable state beforehand (before the start node's own "set"
	 * commands, if any, are executed).
	 *
	 * @param userService the {@link UserService} of the user testing the draft.
	 * @param dialogue    the draft dialogue to test.
	 * @param language    the ISO language code to parse the draft content as.
	 * @return the new session's ID and the {@link ExecuteNodeResult} for its start node.
	 * @throws BadRequestException if the draft content does not currently parse.
	 * @throws ExecutionException  if the dialogue cannot be started.
	 */
	public StartResult startSession(UserService userService, DBDraftDialogue dialogue,
									String language) throws BadRequestException, ExecutionException {
		String scriptContent = draftDialogueService.reconstructScript(dialogue);

		Map<String, String> scriptContents = new LinkedHashMap<>();
		scriptContents.put(dialogue.getName(), scriptContent);

		ScriptLoader scriptLoader = new DatabasePublishedScriptLoader(
				language, scriptContents, Collections.emptyMap());

		ProjectParserResult parserResult;
		try {
			parserResult = new ProjectParser(scriptLoader).parse();
		} catch (IOException e) {
			throw new BadRequestException(
					"Failed to read draft dialogue content: " + e.getMessage());
		}

		if (!parserResult.getParseErrors().isEmpty()) {
			StringBuilder message = new StringBuilder("Draft dialogue '" + dialogue.getName() +
					"' does not currently parse:");
			parserResult.getParseErrors().forEach((path, errors) -> errors.forEach(err ->
					message.append(" ").append(err.getMessage())));
			throw new BadRequestException(message.toString());
		}

		ExecutableProject project = (ExecutableProject) parserResult.getProject();
		ResourcePointer pointer =
				new ResourcePointer(language, dialogue.getName(), ResourceType.SCRIPT);
		Dialogue dialogueDefinition = project.getDialogues().get(pointer);
		if (dialogueDefinition == null) {
			throw new BadRequestException("Draft dialogue '" + dialogue.getName() +
					"' could not be parsed into a runnable dialogue.");
		}

		ActiveDialogue activeDialogue = new ActiveDialogue(pointer, dialogueDefinition);
		VariableStore variableStore = userService.getVariableStore();
		activeDialogue.setVariableStore(variableStore);

		// Snapshot before executing the start node, so any "set" commands it triggers are
		// included in what gets reverted.
		Variable[] snapshot = variableStore.getVariables();

		ZonedDateTime eventTime =
				DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());
		Node startNode;
		try {
			startNode = activeDialogue.startDialogue(eventTime);
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}

		DraftTestSession session = new DraftTestSession(
				userService.getDialogueBranchUser().getId(), activeDialogue, dialogueDefinition,
				snapshot);
		sessions.put(session.getId(), session);

		ExecuteNodeResult result = new ExecuteNodeResult(dialogueDefinition, startNode, null, 0);
		return new StartResult(session.getId(), result);
	}

	/**
	 * Progresses the given draft test session after the user selected the specified reply.
	 *
	 * @param session   the draft test session to progress.
	 * @param replyId   the reply ID.
	 * @param variables optional variables submitted alongside the reply (e.g. from input
	 *                  segments), or {@code null}.
	 * @param eventTime the timestamp of this progress event, in the user's time zone.
	 * @return the {@link ExecuteNodeResult} for the next node, or {@code null} if the dialogue has
	 *         completed.
	 * @throws BadRequestException if the reply points to another dialogue — following
	 *                             cross-dialogue links is not supported in draft test mode.
	 * @throws ExecutionException  if the reply cannot be processed.
	 */
	public ExecuteNodeResult progressSession(DraftTestSession session, int replyId,
											 Map<String, ?> variables, ZonedDateTime eventTime)
			throws BadRequestException, ExecutionException {
		ActiveDialogue activeDialogue = session.getActiveDialogue();

		if (variables != null && !variables.isEmpty()) {
			activeDialogue.getVariableStore().addAll(variables, true, eventTime,
					VariableUpdatedSource.INPUT_REPLY);
		}

		NodePointer nodePointer;
		try {
			nodePointer = activeDialogue.processReplyAndGetNodePointer(replyId, eventTime);
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}

		if (!(nodePointer instanceof InternalNodePointer internalNodePointer)) {
			throw new BadRequestException("This reply points to another dialogue, which isn't " +
					"supported in draft test mode.");
		}

		Node nextNode;
		try {
			nextNode = activeDialogue.progressDialogue(internalNodePointer, eventTime);
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}

		if (nextNode == null) return null;
		return new ExecuteNodeResult(session.getDialogueDefinition(), nextNode, null, 0);
	}

	/**
	 * Discards the given draft test session without reverting any variable changes made during
	 * it — matching how {@code /dialogue/cancel} behaves for real sessions.
	 *
	 * @param session the draft test session to discard.
	 */
	public void cancelSession(DraftTestSession session) {
		sessions.remove(session.getId());
	}

	/**
	 * Restores the user's variable store to the state it was in when the given draft test session
	 * started — resetting every variable present in the snapshot to its snapshotted value, and
	 * deleting any variable that was newly created since — then discards the session.
	 *
	 * @param session the draft test session whose variable changes should be reverted.
	 * @param userService the {@link UserService} of the user testing the draft.
	 */
	public void revertVariables(DraftTestSession session, UserService userService) {
		VariableStore variableStore = userService.getVariableStore();
		ZonedDateTime eventTime =
				DateTimeUtils.nowMs(userService.getDialogueBranchUser().getTimeZone());

		Set<String> snapshotNames = new HashSet<>();
		for (Variable variable : session.getVariableSnapshot()) {
			snapshotNames.add(variable.getName());
			variableStore.setValue(variable.getName(), variable.getValue(), true, eventTime,
					VariableUpdatedSource.WEB_SERVICE);
		}

		for (String name : new HashSet<>(variableStore.getVariableNames())) {
			if (!snapshotNames.contains(name)) {
				variableStore.removeByName(name, true, eventTime, VariableUpdatedSource.WEB_SERVICE);
			}
		}

		sessions.remove(session.getId());
	}

}

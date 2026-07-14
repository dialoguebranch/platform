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
import com.dialoguebranch.web.service.exception.ErrorCode;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.exception.ProjectParseHttpError;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBProject;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.expressions.EvaluationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
	 * Parses the current content of the given draft dialogue's whole project (so that any node
	 * pointers to sibling dialogues resolve) and starts an ephemeral test session for the given
	 * dialogue specifically, snapshotting the user's variable state beforehand (before the start
	 * node's own "set" commands, if any, are executed).
	 *
	 * @param userService the {@link UserService} of the user testing the draft.
	 * @param project     the project {@code dialogue} belongs to (passed explicitly rather than
	 *                    read via {@code dialogue.getProject()}, which is a lazy relation that may
	 *                    no longer have an active Hibernate session attached by this point).
	 * @param dialogue    the draft dialogue to test.
	 * @param language    the ISO language code to parse the draft content as.
	 * @param startNodeId the node ID to start the test from, or {@code null} to start from the
	 *                    dialogue's default "Start" node.
	 * @return the new session's ID and the {@link ExecuteNodeResult} for its start node.
	 * @throws BadRequestException if the draft content does not currently parse.
	 * @throws ExecutionException  if the dialogue cannot be started, e.g. {@code startNodeId} does
	 *                             not exist in the dialogue.
	 */
	public StartResult startSession(UserService userService, DBProject project,
									DBDraftDialogue dialogue, String language, String startNodeId)
			throws BadRequestException, ExecutionException {
		try {
			project.validateLanguageCode(language);
		} catch (UnknownLanguageCodeException e) {
			throw new BadRequestException(ErrorCode.UNKNOWN_LANGUAGE_CODE, e.getMessage());
		}

		// The dialogue under test may contain node pointers to sibling dialogues in the same
		// project — parsing needs all of them available, not just this one, otherwise those
		// pointers are reported as pointing to an "unknown dialogue". Every dialogue in a project
		// always has a draft (seeded/published dialogues get their drafts created up front — see
		// ProjectSeedService and PublishService), so simply including every draft dialogue's
		// current content (not just the one being tested) is enough for cross-references to
		// resolve.
		// scriptContents holds every dialogue's own (source-language) script; translationContents
		// holds every dialogue's translations, keyed by language — both are needed so `language`
		// below can resolve to either kind, exactly as a real project's content is structured.
		Map<String, String> scriptContents = new LinkedHashMap<>();
		Map<String, Map<String, String>> translationContents = new LinkedHashMap<>();
		for (DBDraftDialogue projectDialogue : draftDialogueService.listDialogues(project)) {
			scriptContents.put(projectDialogue.getName(),
					draftDialogueService.reconstructScript(projectDialogue));
			for (DBDraftTranslation translation :
					draftDialogueService.listTranslations(projectDialogue)) {
				translationContents.computeIfAbsent(translation.getLanguage(),
								(k) -> new LinkedHashMap<>())
						.put(projectDialogue.getName(), translation.getContent());
			}
		}

		// The project's actual source language — NOT `language` (the language being requested for
		// this test), which may instead name one of the project's translation languages.
		String sourceLanguage = project.getSourceLanguageCode() != null
				? project.getSourceLanguageCode() : "";
		ScriptLoader scriptLoader = new DatabasePublishedScriptLoader(
				sourceLanguage, scriptContents, translationContents);

		ProjectParserResult parserResult;
		try {
			parserResult = new ProjectParser(scriptLoader).parse();
		} catch (IOException e) {
			throw new BadRequestException(
					"Failed to read draft dialogue content: " + e.getMessage());
		}

		if (!parserResult.getParseErrors().isEmpty()) {
			Map<String, List<String>> errors = new LinkedHashMap<>();
			parserResult.getParseErrors().forEach((path, exceptions) -> errors.put(path,
					exceptions.stream().map(Throwable::getMessage).toList()));
			throw new BadRequestException(new ProjectParseHttpError("The current project '" +
					project.getSlug() + "' contains errors, preventing execution of dialogues.",
					errors));
		}

		ExecutableProject executableProject = (ExecutableProject) parserResult.getProject();
		// `language` may be the project's source language (the dialogue's own script, stored under
		// ResourceType.SCRIPT) or one of its translation languages (stored separately under
		// ResourceType.TRANSLATION, per ProjectParser.createTranslatedDialogues) — try both, since
		// the caller doesn't distinguish which kind of language it asked for.
		ResourcePointer pointer =
				new ResourcePointer(language, dialogue.getName(), ResourceType.SCRIPT);
		Dialogue dialogueDefinition = executableProject.getDialogues().get(pointer);
		if (dialogueDefinition == null) {
			pointer = new ResourcePointer(language, dialogue.getName(), ResourceType.TRANSLATION);
			dialogueDefinition = executableProject.getDialogues().get(pointer);
		}
		if (dialogueDefinition == null) {
			throw new BadRequestException("Draft dialogue '" + dialogue.getName() +
					"' could not be parsed into a runnable dialogue in language '" + language + "'.");
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
			startNode = activeDialogue.startDialogue(startNodeId, eventTime);
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

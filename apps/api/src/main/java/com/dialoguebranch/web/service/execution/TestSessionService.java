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
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.web.service.DateTimeUtils;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.controller.ControllerFunctions;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ForbiddenException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.exception.ProjectParseHttpError;
import com.dialoguebranch.web.service.project.DraftDialogueService;
import com.dialoguebranch.web.service.storage.ExternalVariableServiceClient;
import com.dialoguebranch.web.service.storage.VariableStoreDatabaseStorageHandler;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Ephemeral, in-memory execution service for test-running a dialogue's current draft content
 * without requiring it to be published first.
 *
 * <p>Sessions are driven directly through {@link ActiveDialogue} and held only in this service's
 * in-memory {@code sessions} map — they never touch
 * {@link com.dialoguebranch.web.service.storage.LoggedDialogueStore}, and are entirely separate
 * from the published-dialogue execution path in {@link DialogueExecutor}/{@link UserService}
 * used by {@code /dialogue/*}.</p>
 *
 * <p>A test session never reads or writes the user's real variables (#319). It runs against a
 * <em>copy</em>, kept in memory and seeded from the user's stored variables when first needed:
 * the copy is never saved, never recorded in the variable history, and never sent to the
 * External Variable Service. All test sessions of the same tester, for the same user (themselves
 * or a delegate) and project, share one copy, so what a test in one Studio tab sets is seen by a
 * test in another, as in real play.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class TestSessionService {

	/**
	 * Identifies one copy of the variables: the tester's, for one user (the tester or someone
	 * they act for, in the tester's own realm) and project.
	 *
	 * @param testerIssuer the tester's token issuer.
	 * @param testerSubject the tester's OIDC subject.
	 * @param userSubject the subject of the user whose variables were copied.
	 * @param projectSlug the project the variables belong to.
	 */
	public record CopyKey(String testerIssuer, String testerSubject, String userSubject,
			String projectSlug) {

		/**
		 * Returns the key of the given tester's copy, for the given user and project.
		 *
		 * @param tester the tester.
		 * @param user the user whose variables are copied.
		 * @param projectSlug the project.
		 * @return the key.
		 */
		public static CopyKey of(DialogueBranchUserId tester, DialogueBranchUserId user,
				String projectSlug) {
			return new CopyKey(tester.issuer(), tester.subject(), user.subject(), projectSlug);
		}
	}

	/** One copy of the variables, with when it was last used (see {@link #removeIdle}). */
	private static final class Copy {

		private final VariableStore store;
		private volatile long lastUsed = System.currentTimeMillis();

		private Copy(VariableStore store) {
			this.store = store;
		}

		private VariableStore use() {
			lastUsed = System.currentTimeMillis();
			return store;
		}
	}

	/**
	 * How many test sessions and copies of the variables an idle sweep removed.
	 *
	 * @param sessions the number of test sessions removed.
	 * @param copies the number of copies of the variables removed.
	 */
	public record Evicted(int sessions, int copies) {
	}

	/** How often the idle sweep runs, regardless of the configured idle timeout. */
	private static final long SWEEP_INTERVAL_MILLIS = 5 * 60 * 1000;

	private static final Logger logger = LoggerFactory.getLogger(TestSessionService.class);

	private final DraftDialogueService draftDialogueService;
	private final VariableStoreDatabaseStorageHandler storageHandler;
	private final DlbProperties dlbProperties;
	private final Map<String, TestSession> sessions = new ConcurrentHashMap<>();
	private final Map<CopyKey, Copy> copies = new ConcurrentHashMap<>();

	/**
	 * Creates a new {@link TestSessionService}.
	 *
	 * @param draftDialogueService service used to read a project's draft dialogues and their
	 *                             content when starting a test session.
	 * @param storageHandler handler used to read a user's stored variables, to seed a copy.
	 * @param dlbProperties the configuration, for the test sessions' idle timeout.
	 */
	public TestSessionService(DraftDialogueService draftDialogueService,
			VariableStoreDatabaseStorageHandler storageHandler, DlbProperties dlbProperties) {
		this.draftDialogueService = draftDialogueService;
		this.storageHandler = storageHandler;
		this.dlbProperties = dlbProperties;
	}

	/**
	 * The result of starting a test session: the new session's ID and the
	 * {@link ExecuteNodeResult} for its start node.
	 *
	 * @param sessionId        the ID of the newly created {@link TestSession}.
	 * @param executeNodeResult the result for the dialogue's start node.
	 */
	public record StartResult(String sessionId, ExecuteNodeResult executeNodeResult) {
	}

	// -------------------- Copies of the variables -------------------- //

	/**
	 * Returns the tester's copy of the variables of the given user in the given project, seeding
	 * it from the user's stored variables if it doesn't exist yet. Nothing is created or changed
	 * for the user: a tester testing as themselves without any stored variables starts from an
	 * empty copy, and an unknown delegated user is reported as such.
	 *
	 * @param tester the authenticated user running the test.
	 * @param user the user whose variables to copy: the tester, or someone they act for.
	 * @param delegated whether {@code user} is someone other than the tester.
	 * @param project the project the variables belong to.
	 * @param timeZone the time zone of the copy's user, used only when the copy is created.
	 * @return the copy.
	 * @throws NotFoundException if {@code user} is a delegated user this service doesn't know.
	 * @throws ParseException if a stored value cannot be parsed.
	 */
	public VariableStore getCopy(DialogueBranchUserId tester, DialogueBranchUserId user,
			boolean delegated, DBProject project, ZoneId timeZone)
			throws NotFoundException, ParseException {
		CopyKey key = CopyKey.of(tester, user, project.getSlug());
		Copy copy = copies.get(key);
		if (copy != null) return copy.use();
		Variable[] seed = readStoredVariables(user, delegated, project);
		Copy created = new Copy(new VariableStore(new User(user.subject(), timeZone), seed));
		Copy existing = copies.putIfAbsent(key, created);
		return (existing != null ? existing : created).use();
	}

	/**
	 * Makes the tester's copy of the given user's variables in the given project identical to the
	 * user's stored variables again, in place, so test sessions running against it continue with
	 * the reset values. Does nothing if there is no copy yet: the next use seeds a fresh one.
	 *
	 * @param tester the authenticated user running the test.
	 * @param user the user whose variables were copied.
	 * @param delegated whether {@code user} is someone other than the tester.
	 * @param project the project the variables belong to.
	 * @throws NotFoundException if {@code user} is a delegated user this service doesn't know.
	 * @throws ParseException if a stored value cannot be parsed.
	 */
	public void resetCopy(DialogueBranchUserId tester, DialogueBranchUserId user,
			boolean delegated, DBProject project) throws NotFoundException, ParseException {
		Variable[] seed = readStoredVariables(user, delegated, project);
		Copy entry = copies.get(CopyKey.of(tester, user, project.getSlug()));
		if (entry == null) return;
		VariableStore copy = entry.use();
		ZonedDateTime now = DateTimeUtils.nowMs(copy.getUser().getTimeZone());
		copy.getModifiableMap(false, now).clear();
		for (Variable variable : seed) {
			copy.setValue(variable.getName(), variable.getValue(), false,
					storedTime(variable, now), variable.getUpdatedSource());
		}
	}

	/** The user's stored variables in the project, read without creating anything. */
	private Variable[] readStoredVariables(DialogueBranchUserId user, boolean delegated,
			DBProject project) throws NotFoundException, ParseException {
		Optional<List<Variable>> stored = storageHandler.readExisting(user, project);
		if (stored.isEmpty() && delegated) throw ControllerFunctions.unknownDelegateUser(user);
		return stored.orElse(List.of()).toArray(new Variable[0]);
	}

	/** When a stored variable was last updated, in the time zone it was updated in. */
	private static ZonedDateTime storedTime(Variable variable, ZonedDateTime fallback) {
		if (variable.getUpdatedTime() == null) return fallback;
		ZoneId zone = variable.getUpdatedTimeZone() != null
				? ZoneId.of(variable.getUpdatedTimeZone()) : fallback.getZone();
		return Instant.ofEpochMilli(variable.getUpdatedTime()).atZone(zone);
	}

	// -------------------- Sessions -------------------- //

	/**
	 * Returns the test session with the given {@code sessionId}, if it belongs to the given
	 * tester.
	 *
	 * @param sessionId the session ID.
	 * @param tester the authenticated user making the request.
	 * @return the matching {@link TestSession}.
	 * @throws NotFoundException if no session with that ID exists (e.g. it was never started, was
	 *                           already cancelled, or the service has restarted).
	 * @throws ForbiddenException if the session belongs to another tester.
	 */
	public TestSession getSession(String sessionId, DialogueBranchUserId tester)
			throws NotFoundException, ForbiddenException {
		TestSession session = sessions.get(sessionId);
		if (session == null) {
			throw new NotFoundException("Test session not found: " + sessionId);
		}
		if (!session.getTester().issuer().equals(tester.issuer())
				|| !session.getTester().subject().equals(tester.subject())) {
			throw new ForbiddenException();
		}
		session.recordActivity();
		Copy copy = copies.get(session.getCopyKey());
		if (copy != null) copy.use();
		return session;
	}

	/**
	 * Parses the current content of the given draft dialogue's whole project (so that any node
	 * pointers to sibling dialogues resolve) and starts an ephemeral test session for the given
	 * dialogue specifically, running against the tester's copy of the given user's variables.
	 *
	 * @param tester      the authenticated user running the test.
	 * @param user        the user whose variables the test starts from: the tester, or someone
	 *                    they act for.
	 * @param delegated   whether {@code user} is someone other than the tester.
	 * @param project     the project {@code dialogue} belongs to (passed explicitly rather than
	 *                    read via {@code dialogue.getProject()}, which is a lazy relation that may
	 *                    no longer have an active Hibernate session attached by this point).
	 * @param dialogue    the draft dialogue to test.
	 * @param language    the language code to parse the draft content as; falls back to the
	 *                    project's source language if this dialogue has no content in exactly
	 *                    this language (matching {@code UserService.findExactLanguageMatch}'s
	 *                    behavior for published dialogues).
	 * @param startNodeId an optional node title to start at, instead of the default start node.
	 * @param timeZone    the tester's time zone.
	 * @return the new session's ID and the result for its start node.
	 * @throws BadRequestException if the project's draft content has errors, or the dialogue
	 *                             can't be parsed into a runnable dialogue.
	 * @throws NotFoundException if {@code user} is a delegated user this service doesn't know.
	 * @throws ParseException if a stored variable value cannot be parsed.
	 * @throws ExecutionException if the dialogue can't be started.
	 */
	public StartResult startSession(DialogueBranchUserId tester, DialogueBranchUserId user,
			boolean delegated, DBProject project, DBDraftDialogue dialogue, String language,
			String startNodeId, ZoneId timeZone)
			throws BadRequestException, NotFoundException, ParseException, ExecutionException {
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
				// A language pending deletion is being removed from the project — don't offer it
				// for draft testing either, even though it isn't gone from the database yet.
				if (translation.getLanguage().getIsDeleted()) continue;
				translationContents.computeIfAbsent(
								translation.getLanguage().getTranslationLanguageCode(),
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
		ResolvedDialogue resolved = resolveDialogue(executableProject, dialogue.getName(), language,
				sourceLanguage);
		if (resolved == null) {
			throw new BadRequestException("Draft dialogue '" + dialogue.getName() +
					"' could not be parsed into a runnable dialogue.");
		}

		VariableStore copy = getCopy(tester, user, delegated, project, timeZone);
		CopyKey copyKey = CopyKey.of(tester, user, project.getSlug());
		readFromExternalService(copyKey, copy, resolved.definition());
		ActiveDialogue activeDialogue = new ActiveDialogue(resolved.pointer(), resolved.definition(),
				copy);

		Node startNode;
		try {
			startNode = activeDialogue.startDialogue(startNodeId, DateTimeUtils.nowMs(timeZone));
		} catch (EvaluationException e) {
			throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
		}

		TestSession session = new TestSession(tester, copyKey,
				activeDialogue, resolved.definition(), executableProject, sourceLanguage);
		ExecuteNodeResult result = new ExecuteNodeResult(resolved.definition(), startNode, null, 0);
		// A dialogue that ends at its start node needs no session to continue it.
		if (!hasEnded(result)) sessions.put(session.getId(), session);

		return new StartResult(session.getId(), result);
	}

	private record ResolvedDialogue(ResourcePointer pointer, Dialogue definition) {
	}

	/**
	 * Resolves {@code dialogueName} to a {@link Dialogue} within the given already-parsed {@code
	 * executableProject}, preferring {@code language} but falling back to {@code sourceLanguage}
	 * if this dialogue has no content in exactly that language — matching {@code
	 * UserService.findExactLanguageMatch}'s behavior for published dialogues. {@code language}
	 * may itself be the project's source language (the dialogue's own script, stored under
	 * {@link ResourceType#SCRIPT}) or one of its translation languages (stored separately under
	 * {@link ResourceType#TRANSLATION}, per {@code ProjectParser.createTranslatedDialogues}) —
	 * both are tried, since the caller doesn't distinguish which kind of language it asked for.
	 *
	 * <p>{@code sourceLanguage} must be passed in by the caller (resolved from the authoritative
	 * {@code DBProject} record) rather than read from {@code executableProject.getMetaData()} —
	 * these dialogues are parsed from in-memory content maps via {@code
	 * DatabasePublishedScriptLoader}, not an actual {@code dlb-project.xml}, so the parsed
	 * project's own metadata is not a reliable source for it.</p>
	 *
	 * @param executableProject the already-parsed project to resolve against.
	 * @param dialogueName      the dialogue to resolve.
	 * @param language          the preferred language code.
	 * @param sourceLanguage    the project's source language code, to fall back to.
	 * @return the resolved pointer and dialogue, or {@code null} if {@code dialogueName} has no
	 * content in {@code language} or {@code sourceLanguage}.
	 */
	private ResolvedDialogue resolveDialogue(ExecutableProject executableProject,
			String dialogueName, String language, String sourceLanguage) {
		ResourcePointer pointer = new ResourcePointer(language, dialogueName, ResourceType.SCRIPT);
		Dialogue definition = executableProject.getDialogues().get(pointer);
		if (definition == null) {
			pointer = new ResourcePointer(language, dialogueName, ResourceType.TRANSLATION);
			definition = executableProject.getDialogues().get(pointer);
		}
		if (definition == null) {
			// No content in the requested language (an undeclared code, or a declared translation
			// language this dialogue doesn't have content for yet) — fall back to the source
			// language, rather than failing the request.
			pointer = new ResourcePointer(sourceLanguage, dialogueName, ResourceType.SCRIPT);
			definition = executableProject.getDialogues().get(pointer);
		}
		if (definition == null) return null;
		return new ResolvedDialogue(pointer, definition);
	}

	/**
	 * Progresses the given draft test session after the user selected the specified reply. If the
	 * reply points to a sibling dialogue rather than a node within the one currently being
	 * tested, switches the session to that dialogue and starts it at the linked node — see
	 * {@link TestSession#switchToDialogue}. The sibling is resolved against the project
	 * content already parsed when this session started (see {@link
	 * #resolveDialogue(ExecutableProject, String, String, String)}), preferring whatever language the
	 * dialogue currently being tested actually resolved to (which may itself already be a
	 * source-language fallback), not necessarily what was originally requested when the session
	 * started.
	 *
	 * @param session   the draft test session to progress.
	 * @param replyId   the reply ID.
	 * @param variables optional variables submitted alongside the reply (e.g. from input
	 *                  segments), or {@code null}.
	 * @param eventTime the timestamp of this progress event, in the user's time zone.
	 * @return the {@link ExecuteNodeResult} for the next node, or {@code null} if the dialogue has
	 *         completed. A dialogue that has ended (also at a node without replies) ends the
	 *         session.
	 * @throws NotFoundException  if the reply's target dialogue has no content in this project's
	 *                            draft dialogues (e.g. it was renamed or deleted since this
	 *                            session started).
	 * @throws ExecutionException if the reply cannot be processed.
	 */
	public ExecuteNodeResult progressSession(TestSession session, int replyId,
											 Map<String, ?> variables, ZonedDateTime eventTime)
			throws NotFoundException, ExecutionException {
		// One request at a time per session (#215): ActiveDialogue isn't thread-safe. The session's
		// copy of the variables has no listeners (#319), so the only I/O in here is the read from
		// the External Variable Service when a reply starts another dialogue, which holds up only
		// this session's own requests.
		synchronized (session) {
			ExecuteNodeResult result = advance(session, replyId, variables, eventTime);
			// A dialogue that has ended can't be progressed any more: the session is done.
			if (hasEnded(result)) sessions.remove(session.getId());
			return result;
		}
	}

	/**
	 * Asks the External Variable Service, if one is configured, for updated values of the
	 * variables the given dialogue uses, as real playback does at every dialogue start (#411),
	 * and writes the answers into the copy only. The request says it comes from a test session,
	 * and sends the copy's values; nothing a test changes is ever sent to the service.
	 */
	private void readFromExternalService(CopyKey copyKey, VariableStore copy, Dialogue dialogue) {
		if (!dlbProperties.getExternalVariableService().isEnabled()) return;
		if (dialogue.getVariablesNeeded().isEmpty()) return;
		List<Variable> current = new ArrayList<>();
		for (String name : dialogue.getVariablesNeeded()) {
			Variable variable = copy.getVariable(name);
			current.add(variable != null ? variable : Variable.unknown(name));
		}
		Variable[] updates = new ExternalVariableServiceClient(dlbProperties).retrieveUpdates(
				copyKey.testerIssuer(), copyKey.userSubject(), copyKey.projectSlug(),
				copy.getUser().getTimeZone().toString(), current, true);
		ExternalVariableServiceClient.applyUpdates(copy, updates);
	}

	/**
	 * Whether a dialogue has ended with the given step: it reached {@code End}, or a node without
	 * replies, which can't be progressed any further (the same rule clients use).
	 */
	private static boolean hasEnded(ExecuteNodeResult result) {
		return result == null || result.node().getBody().getReplies().isEmpty();
	}

	private ExecuteNodeResult advance(TestSession session, int replyId,
			Map<String, ?> variables, ZonedDateTime eventTime)
			throws NotFoundException, ExecutionException {
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

		if (nodePointer instanceof ExternalNodePointer externalNodePointer) {
			String targetDialogueName = externalNodePointer.getAbsoluteTargetDialogue();
			String targetNodeId = externalNodePointer.getTargetNodeId();
			String language = activeDialogue.getDialogueFileDescription().getLanguage();

			ResolvedDialogue resolved = resolveDialogue(session.getExecutableProject(),
					targetDialogueName, language, session.getSourceLanguage());
			if (resolved == null) {
				throw new NotFoundException("Dialogue '" + targetDialogueName + "' not found " +
						"in this project's draft content.");
			}

			readFromExternalService(session.getCopyKey(), activeDialogue.getVariableStore(),
					resolved.definition());
			ActiveDialogue newActiveDialogue = new ActiveDialogue(resolved.pointer(),
					resolved.definition(), activeDialogue.getVariableStore());

			Node startNode;
			try {
				startNode = newActiveDialogue.startDialogue(targetNodeId, eventTime);
			} catch (EvaluationException e) {
				throw new RuntimeException("Expression evaluation error: " + e.getMessage(), e);
			}

			session.switchToDialogue(newActiveDialogue, resolved.definition());
			return new ExecuteNodeResult(resolved.definition(), startNode, null, 0);
		}

		InternalNodePointer internalNodePointer = (InternalNodePointer) nodePointer;
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
	 * Ends a test session. Its copy of the variables stays, for the tester's other sessions.
	 *
	 * @param session the session to end.
	 */
	public void cancelSession(TestSession session) {
		sessions.remove(session.getId());
	}

	// -------------------- Idle eviction (#218) -------------------- //

	/**
	 * Removes the test sessions and copies of the variables that have been idle for longer than
	 * the configured timeout ({@code dlb.test-session.idle-timeout-minutes}). Without this, a
	 * session that is never cancelled — a closed browser tab, a crashed client — would stay in
	 * memory, with its parsed project, until the service restarts. Runs on a fixed interval,
	 * independent of the timeout itself.
	 */
	@Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
	public void evictIdle() {
		int idleTimeoutMinutes = dlbProperties.getTestSession().getIdleTimeoutMinutes();
		Evicted evicted = removeIdle(TimeUnit.MINUTES.toMillis(idleTimeoutMinutes),
				System.currentTimeMillis());
		if (evicted.sessions() > 0 || evicted.copies() > 0) {
			logger.info("Evicted {} idle test session(s) and {} unused copy(ies) of the " +
					"variables (idle timeout: {} minute(s)).", evicted.sessions(),
					evicted.copies(), idleTimeoutMinutes);
		}
	}

	/**
	 * Removes the test sessions idle for longer than {@code idleTimeoutMillis}, and then the
	 * copies of the variables that are idle as long and no remaining session uses.
	 *
	 * @param idleTimeoutMillis how long a session or copy may be idle.
	 * @param now the current time, in epoch milliseconds.
	 * @return how many sessions and copies were removed.
	 */
	public Evicted removeIdle(long idleTimeoutMillis, long now) {
		int sessionsBefore = sessions.size();
		sessions.values().removeIf(
				session -> now - session.getLastActivityTime() > idleTimeoutMillis);
		Set<CopyKey> inUse = new HashSet<>();
		for (TestSession session : sessions.values()) inUse.add(session.getCopyKey());
		int copiesBefore = copies.size();
		copies.entrySet().removeIf(entry -> !inUse.contains(entry.getKey())
				&& now - entry.getValue().lastUsed > idleTimeoutMillis);
		return new Evicted(sessionsBefore - sessions.size(), copiesBefore - copies.size());
	}

}

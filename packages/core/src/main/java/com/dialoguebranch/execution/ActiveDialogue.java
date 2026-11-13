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

package com.dialoguebranch.execution;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.ReservedVariables;
import com.dialoguebranch.model.execute.*;
import com.dialoguebranch.model.execute.command.Command;
import com.dialoguebranch.model.execute.command.InputCommand;
import com.dialoguebranch.model.execute.command.SetCommand;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.DialogueMessageFactory;
import com.dialoguebranch.model.execute.protocol.ReplyMessage;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * An {@link ActiveDialogue} is a wrapper around a {@link Dialogue}, which contains a static
 * definition of a dialogue (referred to as the {@code dialogueDefinition}). The
 * {@link ActiveDialogue} also contains utility functions to keep track of the state during
 * "execution" of the dialogue. When a reply leads into another dialogue, it switches to that
 * dialogue itself, found by its {@link DialogueResolver} (see {@link #selectReply}), so one
 * {@link ActiveDialogue} runs a conversation across dialogues (#473).
 *
 * <p>It can keep a log of the conversation ({@link #setLoggedDialogue}): the agent's statements
 * and the user's replies, across dialogues, as the Web Service records them. With a log it can go
 * {@link #back()} a step, be {@link #restore restored} from the log later, and give the log's
 * identifier and the step's index in its {@link #getCurrentMessage() message} (#483). The
 * application decides where the log lives: it is not stored by this class.</p>
 *
 * <p><b>Not thread-safe.</b> {@link #getCurrentNode()}/{@link #setCurrentNode(Node)} and the
 * dialogue-progression methods below mutate {@link #currentNode} with no synchronization — two
 * threads driving the same {@link ActiveDialogue} instance concurrently can race on it. This is a
 * deliberate choice, not an oversight (see #204): most owners of an {@link ActiveDialogue} are
 * single-threaded by construction (a CLI session, a test, or — in the bundled web service — a
 * fresh instance reconstructed per request from the persisted dialogue log, never shared across
 * requests). Baking locking into this class would cost every one of those owners overhead for a
 * guarantee only one kind of owner actually needs. The one case that does need it is a long-lived
 * session an owner deliberately keeps and reuses across multiple separate calls (e.g. the bundled
 * web service's draft-dialogue test sessions, which persist one {@link ActiveDialogue} across a
 * whole test run) — that owner is responsible for serializing its own access to this instance,
 * and should do so without holding its lock across any downstream call that can block (a
 * {@link VariableStoreOnChangeListener} doing I/O, for instance) — see #205 for the equivalent
 * problem already fixed inside {@link VariableStore} itself.
 *
 * @author Harm op den Akker
 * @author Tessa Beinema
 */
public class ActiveDialogue {

	/**
	 * Metadata describing the file from which the dialogue definition was loaded. Changes when
	 * a reply leads into another dialogue.
	 */
	private ResourcePointer dialogueFileDescription;

	/** Finds the dialogue a reply leads into. */
	private final DialogueResolver resolver;

	/** Hears when a reply leads into another dialogue, or {@code null}. */
	private @Nullable DialogueSwitchListener switchListener;

	/** The static dialogue definition containing all nodes and their content. */
	private Dialogue dialogueDefinition;

	/**
	 * The dialogue as translated for each gender of the end user (#400), or {@code null} when
	 * the translation doesn't change with it (see {@link ResolvedDialogue#userGenderTranslations()}).
	 * Before every step, the value of {@code $dlb.userGender} in the variable store selects the
	 * definition used for it.
	 */
	private @Nullable Function<TranslationContext.Gender, Dialogue> userGenderTranslations;

	/**
	 * Whether the dialogue has been started (or put at a node with {@link #setCurrentNode}), so
	 * that a {@code null} {@link #currentNode} means it has ended.
	 */
	private boolean started;

	/** The node currently being presented to the user, or {@code null} if not yet started. */
	private @Nullable Node currentNode;

	/** The variable store used to read and write Dialogue Branch variables during execution. */
	// Set in the constructor; setVariableStore() may re-point it (e.g. when a restored dialogue is
	// attached to a fresh user session).
	private VariableStore variableStore;

	/** The log the conversation is recorded into, or {@code null} if none is kept (#483). */
	private @Nullable LoggedDialogue loggedDialogue;

	/** Creates the log of the dialogue a reply leads into; set whenever a log is kept. */
	private @Nullable LoggedDialogueFactory logFactory;

	/** The index in the log of the current step's interaction, or -1 if it has none yet. */
	private int loggedInteractionIndex = -1;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an {@link ActiveDialogue} for the given dialogue, which follows replies into other
	 * dialogues by finding them with the given {@link DialogueResolver} (#473). It keeps the same
	 * {@link VariableStore} throughout.
	 *
	 * @param resolver finds the dialogues that replies lead into, such as the {@link
	 *                 com.dialoguebranch.model.execute.ExecutableProject} the dialogue is part of.
	 * @param dialogue the dialogue to run.
	 * @param variableStore the {@link VariableStore} used to read and write Dialogue Branch
	 *                      variables while the dialogue runs.
	 */
	public ActiveDialogue(DialogueResolver resolver, ResolvedDialogue dialogue,
						  VariableStore variableStore) {
		this.resolver = resolver;
		this.dialogueFileDescription = dialogue.pointer();
		this.dialogueDefinition = dialogue.dialogue();
		this.userGenderTranslations = dialogue.userGenderTranslations();
		this.variableStore = variableStore;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Sets the listener that hears when a reply leads into another dialogue, just before the
	 * first node there is executed.
	 *
	 * @param switchListener the listener, or {@code null} for none.
	 */
	public void setDialogueSwitchListener(@Nullable DialogueSwitchListener switchListener) {
		this.switchListener = switchListener;
	}

	/** Switches to the definition for the end user's current gender, if this dialogue follows it. */
	private void followUserGender() {
		if (userGenderTranslations == null)
			return;
		Dialogue definition = userGenderTranslations.apply(TranslationContext.Gender.ofUserGender(
				variableStore.getValue(ReservedVariables.USER_GENDER)));
		if (definition != null)
			dialogueDefinition = definition;
	}

	/**
	 * Returns the {@link ResourcePointer} of the dialogue file corresponding to this
	 * {@link ActiveDialogue} containing metadata for the file.
	 * @return the dialogue file description as a {@link ResourcePointer}
	 */
	public ResourcePointer getDialogueFileDescription() {
		return dialogueFileDescription;
	}

	/**
	 * Returns the {@link Dialogue} containing the definition of the dialogue being run through
	 * this {@link ActiveDialogue} object.
	 * @return the dialogue definition as a {@link Dialogue}
	 */
	public Dialogue getDialogueDefinition() {
		return dialogueDefinition;
	}

	/**
	 * Returns the "current node" (the current step in the active dialogue) as a {@link Node}.
	 * @return the current step in the active dialogue as a {@link Node}
	 */
	public @Nullable Node getCurrentNode() {
		return currentNode;
	}

	/**
	 * Sets in which node the current active dialogue is.
	 * @param currentNode the {@link Node} currently being executed.
	 */
	public void setCurrentNode(Node currentNode) {
		this.currentNode = currentNode;
		this.started = true;
	}

	/**
	 * Returns whether the dialogue has finished: a reply led to its {@code End}, or it is at a
	 * node without replies, so no reply can be selected any more. A dialogue that hasn't been
	 * started hasn't finished.
	 *
	 * <p>A node without replies still has a statement to show: {@link #getCurrentMessage()}
	 * returns it. Only after a reply to {@code End} is there nothing left to show.</p>
	 *
	 * @return {@code true} if the dialogue has finished.
	 * @throws IllegalStateException if the current node was put there by {@link #restore} or
	 *                               {@link #setCurrentNode} and hasn't been executed since: which
	 *                               of its replies there are depends on its {@code <<if>>}s.
	 */
	public boolean isFinished() {
		if (!started)
			return false;
		Node node = currentNode;
		if (node == null)
			return true;
		if (!(node.getBody() instanceof ResolvedNodeBody body)) {
			throw new IllegalStateException("The current node \"" + node.getTitle()
					+ "\" hasn't been executed: whether it has replies isn't known yet");
		}
		return body.getReplies().isEmpty();
	}

	/**
	 * Returns the current step as a {@link DialogueMessage}, ready to show: the speaker, the
	 * statement with its variables filled in and its actions and input fields, and the replies to
	 * choose from. This is the same message the Web Service sends for a step. When a log is
	 * kept ({@link #setLoggedDialogue}), its {@code loggedDialogueId} and {@code
	 * loggedInteractionIndex} say where the step is in it; without one they are {@code null} and 0
	 * (which has no meaning).
	 *
	 * @return the current step, or {@code null} if the dialogue hasn't started, or a reply led to
	 *         its {@code End}.
	 * @throws IllegalStateException if the current node was set with {@link #setCurrentNode} and
	 *                               hasn't been executed since, so its variables aren't filled in.
	 */
	public @Nullable DialogueMessage getCurrentMessage() {
		if (currentNode == null)
			return null;
		if (!(currentNode.getBody() instanceof ResolvedNodeBody)) {
			throw new IllegalStateException("The current node \"" + currentNode.getTitle()
					+ "\" hasn't been executed: there is no step to show yet");
		}
		return DialogueMessageFactory.generateDialogueMessage(
				loggedInteractionIndex < 0
						? new ExecuteNodeResult(dialogueDefinition, currentNode, null, 0)
						: new ExecuteNodeResult(dialogueDefinition, currentNode, loggedDialogue,
								loggedInteractionIndex));
	}

	/**
	 * Returns the {@link VariableStore} associated with this {@link ActiveDialogue}.
	 * @return the {@link VariableStore} associated with this {@link ActiveDialogue}.
	 */
	public VariableStore getVariableStore() {
		return variableStore;
	}

	/**
	 * Re-points the {@link VariableStore} used by this {@link ActiveDialogue}. The store is
	 * normally supplied to the constructor; this is for the case where a dialogue
	 * restored from a log ({@link #restore}) is attached to a fresh user session and its variable
	 * store must be swapped for the live one.
	 *
	 * @param variableStore the {@link VariableStore} to use from now on.
	 */
	public void setVariableStore(VariableStore variableStore) {
		this.variableStore = variableStore;
	}

	/**
	 * Sets the log this conversation is recorded into, or none (#483). From then on, every agent
	 * statement is added to it as its node is executed, and every reply as it is selected; it is
	 * marked completed when the dialogue finishes. A reply that leads into another dialogue
	 * completes this log, and the conversation goes on in the log the {@code factory} makes for
	 * that dialogue. Set it before {@link #startDialogue}, or on a dialogue made with {@link
	 * #restore}. The application stores the logs itself: {@link #getLoggedDialogue()} gives the
	 * current one, and the one that was completed is the log that was passed in.
	 *
	 * @param loggedDialogue the log to record into, or {@code null} to keep none.
	 * @param factory makes the log of a dialogue the conversation enters; required with a log.
	 * @throws IllegalArgumentException if a log is given without a factory.
	 * @throws IllegalStateException if a log is given and the dialogue has already started: its
	 *                               steps so far wouldn't be in the log, and going back would
	 *                               stop at the first step that is.
	 */
	public void setLoggedDialogue(@Nullable LoggedDialogue loggedDialogue,
			@Nullable LoggedDialogueFactory factory) {
		if (loggedDialogue != null && factory == null)
			throw new IllegalArgumentException("A log needs a factory for the logs of the "
					+ "dialogues a reply leads into");
		if (loggedDialogue != null && started)
			throw new IllegalStateException("A log must be set before the dialogue starts");
		this.loggedDialogue = loggedDialogue;
		this.logFactory = loggedDialogue == null ? null : factory;
		this.loggedInteractionIndex = -1;
	}

	/**
	 * Keeps a log in memory for this conversation, for the dialogue it is in now (#483). A
	 * shorthand for {@link #setLoggedDialogue} with an {@link InMemoryLoggedDialogue}, with a new
	 * session ID that the logs of the dialogues the conversation enters share. Each of those logs
	 * points back to the one before it ({@link InMemoryLoggedDialogue#getPrevious()}), so the
	 * whole conversation can be read from {@link #getLoggedDialogue()}. It starts now, in the time
	 * zone of the {@link VariableStore}'s user. Call it before the dialogue starts.
	 *
	 * @return the log, to read the transcript from.
	 * @throws IllegalStateException if the dialogue has already started.
	 */
	public InMemoryLoggedDialogue keepInMemoryLog() {
		return keepInMemoryLog(now());
	}

	/**
	 * Keeps a log in memory for this conversation, as {@link #keepInMemoryLog()} does, with the
	 * given start time.
	 *
	 * @param startTime when the dialogue starts, in the user's time zone.
	 * @return the log, to read the transcript from.
	 * @throws IllegalStateException if the dialogue has already started.
	 */
	public InMemoryLoggedDialogue keepInMemoryLog(ZonedDateTime startTime) {
		InMemoryLoggedDialogue log = new InMemoryLoggedDialogue(variableStore.getUser().getId(),
				new ResolvedDialogue(dialogueFileDescription, dialogueDefinition), startTime,
				UUID.randomUUID().toString().toLowerCase().replaceAll("-", ""));
		setLoggedDialogue(log, InMemoryLoggedDialogue.factory());
		return log;
	}

	/**
	 * Returns the log the conversation is recorded into: the log of the dialogue it is in now,
	 * which changes when a reply leads into another dialogue.
	 *
	 * @return the log, or {@code null} if none is kept.
	 */
	public @Nullable LoggedDialogue getLoggedDialogue() {
		return loggedDialogue;
	}

	/**
	 * Returns the index in the log's interaction list of the current step: the agent's statement
	 * that {@link #getCurrentMessage()} gives.
	 *
	 * @return the index, or -1 if there is no log or the step isn't in it yet.
	 */
	public int getLoggedInteractionIndex() {
		return loggedInteractionIndex;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Starts this {@link ActiveDialogue} at its "Start" node, now (in the time zone of the
	 * {@link VariableStore}'s user). See {@link #startDialogue(String, ZonedDateTime)}.
	 *
	 * @return the start {@link Node}, executed.
	 * @throws ExecutionException if the dialogue has no start node.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 */
	public Node startDialogue() throws ExecutionException, EvaluationException {
		return startDialogue((String) null, now());
	}

	/**
	 * Starts this {@link ActiveDialogue} at the node with the given ID, or at its "Start" node if
	 * it is {@code null}, now (in the time zone of the {@link VariableStore}'s user). See {@link
	 * #startDialogue(String, ZonedDateTime)}.
	 *
	 * @param startNodeId the ID of the node to start at, or {@code null} for the "Start" node.
	 * @return the start {@link Node}, executed.
	 * @throws ExecutionException if there is no such node.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 */
	public Node startDialogue(@Nullable String startNodeId)
			throws ExecutionException, EvaluationException {
		return startDialogue(startNodeId, now());
	}

	/**
	 * "Starts" this {@link ActiveDialogue}, returning the start node and updating its internal
	 * state.
	 *
	 * @param eventTime the timestamp (in the time zone of the user) of the event that triggered
	 *                  this start of the Dialogue Branch dialogue.
	 * @return the initial {@link Node}.
	 * @throws ExecutionException if the request is invalid
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Node startDialogue(ZonedDateTime eventTime) throws ExecutionException,
			EvaluationException {
		return startDialogue((String) null, eventTime);
	}

	/**
	 * "Starts" this {@link ActiveDialogue} at the {@link Node} represented by the provided
	 * {@code startNodeId}, or at the "Start" node of the dialogue if the given {@code startNodeId}
	 * is {@code null}, returning that node and updating the dialogue's internal state. If you set
	 * the {@code startNodeId} to null, it will return the start node.
	 *
	 * <p>If executed successfully, this method also sets {@code this.currentNode} and thus updates
	 * the value of {@link #getCurrentNode()}.</p>
	 *
	 * @param startNodeId the node ID of the node to start from, or {@code null} to start from the
	 *                    default "Start" node.
	 * @param eventTime the timestamp (in the time zone of the user) that triggered this start of
	 *                  execution of the dialogue
	 * @return the {@link Node} object representing the given "starting node" after it has been
	 * 		   executed by the Dialogue Branch parser (e.g. after control statements have been
	 * 		   resolved)
	 * @throws ExecutionException if the request is invalid
	 * @throws EvaluationException if an expression cannot be evaluated during execution of the node
	 */
	public Node startDialogue(@Nullable String startNodeId, ZonedDateTime eventTime)
			throws ExecutionException, EvaluationException {
		followUserGender();
		Node nextNode;
		if (startNodeId == null) {
			nextNode = dialogueDefinition.getStartNode();
			if (nextNode == null) {
				throw new ExecutionException(ExecutionException.Type.NODE_NOT_FOUND,
						String.format("Dialogue \"%s\" has no start node",
								dialogueDefinition.getDialogueName()));
			}
		} else {
			nextNode = dialogueDefinition.getNodeById(startNodeId);
			if (nextNode == null) {
				throw new ExecutionException(ExecutionException.Type.NODE_NOT_FOUND,
						String.format("Node \"%s\" not found in dialogue \"%s\"",
								startNodeId, dialogueDefinition.getDialogueName()));
			}
		}
		this.currentNode = executeNode(nextNode,eventTime);
		this.started = true;
		recordAgentStep(-1, eventTime);
		return currentNode;
	}

	/**
	 * Selects the reply with the given {@code replyId} in the current node, now (in the time zone
	 * of the {@link VariableStore}'s user), and returns the node it leads to. See {@link
	 * #selectReply(int, ZonedDateTime)}.
	 *
	 * @param replyId the ID of the selected reply.
	 * @return the next {@link Node}, or {@code null} if the reply ends the dialogue.
	 * @throws ExecutionException if the dialogue or node the reply leads to can't be found, or
	 *                            if the {@link DialogueSwitchListener} stops the switch.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 */
	public @Nullable Node selectReply(int replyId)
			throws ExecutionException, EvaluationException {
		return selectReply(replyId, now());
	}

	/**
	 * Selects the given reply of the current step, as {@link #getCurrentMessage()} gave it, now
	 * (in the time zone of the {@link VariableStore}'s user), and returns the node it leads to.
	 * See {@link #selectReply(int, ZonedDateTime)}.
	 *
	 * @param reply the selected reply.
	 * @return the next {@link Node}, or {@code null} if the reply ends the dialogue.
	 * @throws ExecutionException if the dialogue or node the reply leads to can't be found, or
	 *                            if the {@link DialogueSwitchListener} stops the switch.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 */
	public @Nullable Node selectReply(ReplyMessage reply)
			throws ExecutionException, EvaluationException {
		return selectReply(reply.getReplyId(), now());
	}

	/** Returns the current time in the time zone of the {@link VariableStore}'s user. */
	private ZonedDateTime now() {
		return ZonedDateTime.now(variableStore.getUser().getTimeZone());
	}

	/**
	 * Selects the reply with the given {@code replyId} in the current node, and returns the node
	 * it leads to, executed. Performs the reply's "set" commands first. If the reply leads into
	 * another dialogue, this {@link ActiveDialogue} switches to that dialogue, found by its
	 * {@link DialogueResolver} in the language of the current dialogue (or the source language,
	 * if it isn't available in that one), tells its {@link DialogueSwitchListener}, and starts
	 * it at the linked node. The {@link VariableStore} stays the same.
	 *
	 * @param replyId the ID of the selected reply.
	 * @param eventTime the time (in the user's time zone) of the event that triggered this
	 *                  selection.
	 * @return the next {@link Node}, or {@code null} if the reply ends the dialogue.
	 * @throws ExecutionException if the current node has no reply with that ID ({@code
	 *                            REPLY_NOT_FOUND}), the dialogue has finished ({@code
	 *                            DIALOGUE_COMPLETED}), its log has been cancelled ({@code
	 *                            DIALOGUE_CANCELLED}) or completed ({@code DIALOGUE_COMPLETED}),
	 *                            the dialogue or node the reply leads to can't be found, or the
	 *                            {@link DialogueSwitchListener} stops the switch. Nothing has
	 *                            changed then, except when a switch fails.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 * @throws IllegalStateException if the dialogue hasn't started.
	 */
	public @Nullable Node selectReply(int replyId, ZonedDateTime eventTime)
			throws ExecutionException, EvaluationException {
		checkReplyCanBeSelected(replyId);
		int userInteractionIndex = recordUserReply(replyId, eventTime);
		NodePointer nodePointer = processReply(replyId, eventTime);
		if (nodePointer instanceof ExternalNodePointer externalPointer) {
			ResolvedDialogue target = resolver.resolve(
					externalPointer.getAbsoluteTargetDialogue(),
					dialogueFileDescription.getLanguage());
			if (target.dialogue().getNodeById(externalPointer.getTargetNodeId()) == null) {
				throw new ExecutionException(ExecutionException.Type.NODE_NOT_FOUND,
						String.format("Node \"%s\" not found in dialogue \"%s\"",
								externalPointer.getTargetNodeId(),
								target.dialogue().getDialogueName()));
			}
			if (switchListener != null)
				switchListener.onDialogueSwitch(dialogueFileDescription, target);
			LoggedDialogue previousLog = loggedDialogue;
			int previousLogIndex = loggedInteractionIndex;
			ResourcePointer previousPointer = dialogueFileDescription;
			Dialogue previousDefinition = dialogueDefinition;
			@Nullable Function<TranslationContext.Gender, Dialogue> previousGenders =
					userGenderTranslations;
			Node previousNode = currentNode;
			if (previousLog != null) {
				LoggedDialogue enteredLog = Objects.requireNonNull(logFactory).create(previousLog,
						target, eventTime);
				if (enteredLog == previousLog) {
					throw new IllegalStateException("The LoggedDialogueFactory returned the log "
							+ "of the dialogue that is left; it must make a new one");
				}
				loggedDialogue = enteredLog;
				loggedInteractionIndex = -1;
			}
			this.dialogueFileDescription = target.pointer();
			this.dialogueDefinition = target.dialogue();
			this.userGenderTranslations = target.userGenderTranslations();
			Node entered;
			try {
				entered = startDialogue(externalPointer.getTargetNodeId(), eventTime);
			} catch (ExecutionException | EvaluationException | RuntimeException e) {
				// The dialogue stays where it was, and the reply stays in the log of the dialogue
				// it was given in, not completed.
				dialogueFileDescription = previousPointer;
				dialogueDefinition = previousDefinition;
				userGenderTranslations = previousGenders;
				currentNode = previousNode;
				loggedDialogue = previousLog;
				loggedInteractionIndex = previousLogIndex;
				throw e;
			}
			if (previousLog != null)
				previousLog.setCompleted(true);
			return entered;
		}
		Node next = followInternalPointer((InternalNodePointer) nodePointer, eventTime);
		recordAgentStep(userInteractionIndex, eventTime);
		return next;
	}

	/**
	 * Checks that the reply with the given ID can be selected now: the dialogue has started and
	 * not finished, its log (if one is kept) is still open, and the current node has the reply.
	 */
	private void checkReplyCanBeSelected(int replyId) throws ExecutionException {
		if (!started)
			throw new IllegalStateException("The dialogue hasn't started: there is no reply to select");
		LoggedDialogue log = loggedDialogue;
		if (log != null && log.isCancelled()) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_CANCELLED,
					"Dialogue '" + log.getId() + "' has been cancelled and cannot be progressed.");
		}
		Node current = currentNode;
		boolean executedWithoutReplies = current != null
				&& current.getBody() instanceof ResolvedNodeBody body && body.getReplies().isEmpty();
		if (current == null || executedWithoutReplies || (log != null && log.isCompleted())) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_COMPLETED,
					String.format("Dialogue \"%s\" has finished: there is no reply to select",
							dialogueDefinition.getDialogueName()));
		}
		NodeContent body = Objects.requireNonNull(current.getBody(), "Current node has no body");
		if (body.findReplyById(replyId) == null) {
			throw new ExecutionException(ExecutionException.Type.REPLY_NOT_FOUND,
					String.format("Reply with ID %s not found in dialogue \"%s\", node \"%s\"",
					replyId, dialogueDefinition.getDialogueName(), current.getTitle()));
		}
	}

	/**
	 * Adds the reply that is selected to the log, if one is kept, as the Web Service does: with the
	 * statement the user gave (including what they typed into input fields, which is already
	 * stored), before the reply's commands run.
	 *
	 * @return the index of the new interaction, or -1 if no log is kept.
	 */
	private int recordUserReply(int replyId, ZonedDateTime eventTime) throws ExecutionException {
		if (loggedDialogue == null)
			return -1;
		String statement = getUserStatementFromReplyId(replyId);
		List<LoggedInteraction> interactions = loggedDialogue.getInteractionList();
		interactions.add(new LoggedInteraction(eventTime.toInstant().toEpochMilli(),
				MessageSource.USER, "USER", loggedDialogue.getDialogueName(),
				Objects.requireNonNull(Objects.requireNonNull(currentNode).getTitle()),
				loggedInteractionIndex, statement, replyId));
		loggedInteractionIndex = interactions.size() - 1;
		return loggedInteractionIndex;
	}

	/**
	 * Adds the agent's statement of the current node to the log, if one is kept and there is a
	 * node (there is none after a reply to End), and completes the log when the dialogue has
	 * finished.
	 */
	private void recordAgentStep(int previousIndex, ZonedDateTime eventTime) {
		if (loggedDialogue == null)
			return;
		Node node = currentNode;
		if (node != null) {
			StringBuilder statement = new StringBuilder();
			for (NodeBody.Segment segment : ((ResolvedNodeBody) Objects.requireNonNull(
					node.getBody())).getSegments()) {
				statement.append(segment.toString());
			}
			List<LoggedInteraction> interactions = loggedDialogue.getInteractionList();
			NodeHeader header = Objects.requireNonNull(node.getHeader(), "Node has no header");
			interactions.add(new LoggedInteraction(eventTime.toInstant().toEpochMilli(),
					MessageSource.AGENT, Objects.requireNonNullElse(header.getSpeaker(), ""),
					loggedDialogue.getDialogueName(),
					Objects.requireNonNull(node.getTitle()), previousIndex,
					statement.toString(), -1));
			loggedInteractionIndex = interactions.size() - 1;
		}
		if (isFinished())
			loggedDialogue.setCompleted(true);
	}

	/**
	 * Retrieves the pointer to the next node based on the provided {@code replyId}. This might be a
	 * pointer to the end node. This method also performs any "set" actions associated with the
	 * reply.
	 *
	 * @param replyId the reply ID
	 * @param eventTime the time (in the user's timezone) of the event that triggered this
	 *                  processing of the reply.
	 * @return The {@link NodePointer} pointing to the next Dialogue Branch Node
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	private NodePointer processReply(int replyId, ZonedDateTime eventTime)
			throws EvaluationException {
		Node current = Objects.requireNonNull(currentNode,
				"Dialogue has not been started");
		NodeContent currentBody = Objects.requireNonNull(
				current.getBody(), "Current node has no body");
		Reply selectedReply = Objects.requireNonNull(currentBody.findReplyById(replyId),
				"No reply with id " + replyId + " in the current node");
		Map<String,Object> variableMap =
				variableStore.getModifiableMap(true, eventTime,
					VariableUpdatedSource.DLB_SCRIPT);
		for (Command command : selectedReply.getCommands()) {
			if (command instanceof SetCommand setCommand) {
				setCommand.getExpression().evaluate(variableMap);
			}
		}
		return selectedReply.getNodePointer();
	}

	/**
	 * Takes the next node pointer from the selected reply and determines the next node. The pointer
	 * might point to the end note, which means that there is no next node. If there is no next
	 * node, or the next node has no reply options, then the dialogue is considered finished.
	 *
	 * <p>If there is a next node, then it returns the executed version of that next
	 * {@link Node}.</p>
	 *
	 * @param nodePointer the next node pointer from the selected reply
	 * @param eventTime the timestamp (in the time zone of the user) of the event that triggered the
	 *                  progressing of the dialogue
	 * @return the next {@link Node} that follows on the selected reply or {@code null}
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	private @Nullable Node followInternalPointer(InternalNodePointer nodePointer,
			ZonedDateTime eventTime)
			throws EvaluationException {
		followUserGender();
		Node nextNode = null;
		if (!nodePointer.getTargetNodeId().equalsIgnoreCase(DialogueBranchConstants.DLB_NODE_END_ID))
			nextNode = dialogueDefinition.getNodeById(nodePointer.getTargetNodeId());
		this.currentNode = nextNode;
		if (nextNode != null) this.currentNode = executeNode(nextNode, eventTime);
		return this.currentNode;
	}

	/**
	 * Stores a set of variable values that were captured directly from the user's reply — the
	 * values entered into the {@code <<input ...>>} fields of the reply option they selected — into
	 * the {@link VariableStore}, tagged with {@link VariableUpdatedSource#INPUT_REPLY}. Call this
	 * before {@link #selectReply} so the next node's {@code if}/{@code set}/text can already
	 * see the entered values. The {@code INPUT_REPLY} source is what tells these apart from
	 * script-set ({@link VariableUpdatedSource#DLB_SCRIPT}), external-service, or web-service-set
	 * values later on.
	 *
	 * <p>This is a convenience for callers that drive an {@link ActiveDialogue} directly. The
	 * bundled web service does the equivalent itself, straight against its own
	 * {@link VariableStore} handle, so it does not go through this method.</p>
	 *
	 * @param variables the reply-input variable values, keyed by variable name
	 * @param eventTime the time (in the time zone of the user) of the event that triggered this
	 *                  change in Dialogue Branch Variables
	 */
	public void storeReplyInput(Map<String,?> variables, ZonedDateTime eventTime) {
		variableStore.addAll(variables,true,eventTime,
				VariableUpdatedSource.INPUT_REPLY);
	}

	/**
	 * Returns the statement corresponding to a given {@code replyId}. In any given state of the
	 * conversation, the user's client may return a replyId corresponding to a specific reply option
	 * in the current node. This method retrieves the corresponding statement to that reply, which
	 * may be the statement as defined in the {@link Dialogue}, or it may be a constant defining
	 * that this was an "Auto Forward" reply without a specified statement (see
	 * {@link DialogueBranchConstants#DLB_REPLY_STATEMENT_AUTOFORWARD}).
	 *
	 * @param replyId the reply id as provided e.g. by a client application.
	 * @return the statement {@link String} corresponding to the reply identified by {@code replyId}
	 * @throws ExecutionException if no reply with the specified {@code replyId} is found
	 */
	public String getUserStatementFromReplyId(int replyId) throws ExecutionException {
		Node current = Objects.requireNonNull(currentNode,
				"Dialogue has not been started");
		NodeContent currentBody = Objects.requireNonNull(
				current.getBody(), "Current node has no body");
		Reply selectedReply = currentBody.findReplyById(replyId);
		if (selectedReply == null) {
			throw new ExecutionException(ExecutionException.Type.REPLY_NOT_FOUND,
					String.format("Reply with ID %s not found in dialogue \"%s\", node \"%s\"",
					replyId, dialogueDefinition.getDialogueName(), current.getTitle()));
		}
		if (selectedReply.isAutoForward())
			return DialogueBranchConstants.DLB_REPLY_STATEMENT_AUTOFORWARD;
		StringBuilder result = new StringBuilder();
		List<NodeBody.Segment> segments = Objects.requireNonNull(
				selectedReply.getStatement()).getSegments();
		for (NodeBody.Segment segment : segments) {
			if (segment instanceof NodeBody.TextSegment textSegment) {
				result.append(textSegment.getText().evaluate(null));
			} else {
				NodeBody.CommandSegment cmdSegment =
						(NodeBody.CommandSegment)segment;
				// a reply statement can only contain an "input" command
				InputCommand command =
						(InputCommand)cmdSegment.getCommand();
				result.append(command.getStatementLog(variableStore));
			}
		}
		return result.toString();
	}

	/**
	 * Executes the agent statement and reply statements in the specified node. It executes "if",
	 * "random" and "set" commands and resolves variables. Any resulting body content that should be
	 * sent to the client, is added to the (agent or reply) statement body in the resulting node.
	 * This content can be text or client commands, with all variables resolved.
	 *
	 * @param node a node to execute
	 * @param eventTime the time stamp (in the time zone of the user) of the event that triggered
	 *                  the execution of this Dialogue Branch Node
	 * @return the executed {@link Node}.
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Node executeNode(Node node, ZonedDateTime eventTime) throws EvaluationException {
		NodeHeader header = Objects.requireNonNull(node.getHeader(), "Node has no header");
		NodeBody body = (NodeBody) Objects.requireNonNull(node.getBody(), "Node has no body");
		Map<String,Object> variables =
				variableStore.getModifiableMap(true, eventTime,
						VariableUpdatedSource.DLB_SCRIPT);
		ResolvedNodeBody processedBody = body.execute(variables, true);
		return new Node(header, processedBody);
	}

	/**
	 * Executes the agent statement and reply statements in the specified node. It executes "if" and
	 * "random" commands and resolves variables. Any resulting body content that should be sent to
	 * the client, is added to the (agent or reply) statement body in the resulting node. This
	 * content can be text or client commands, with all variables resolved.
	 *
	 * <p>This method does not change the dialogue state and does not change
	 * any variables. Any "set" commands have no effect.</p>
	 *
	 * @param node a node to execute.
	 * @param eventTime the timestamp (in the time zone of the user) of the event that triggered the
	 *                  execution of the Dialogue Branch Node
	 * @return the executed {@link Node}
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Node executeNodeStateless(Node node, ZonedDateTime eventTime)
			throws EvaluationException {
		NodeHeader header = Objects.requireNonNull(node.getHeader(), "Node has no header");
		NodeBody body = (NodeBody) Objects.requireNonNull(node.getBody(), "Node has no body");
		Map<String,Object> variables = new LinkedHashMap<>(
				variableStore.getModifiableMap(false,eventTime));
		ResolvedNodeBody processedBody = body.execute(variables, true);
		return new Node(header, processedBody);
	}


	// ------------------------------------------------------------------ //
	// -------------------- Going back, resuming, cancelling ------------- //
	// ------------------------------------------------------------------ //

	/**
	 * Goes back to the agent's previous statement in the current dialogue, now (in the time zone of
	 * the {@link VariableStore}'s user). See {@link #back(ZonedDateTime)}.
	 *
	 * @return the previous agent step's node, executed again.
	 * @throws ExecutionException if the log is completed or cancelled, or its steps can't be found.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 * @throws IllegalStateException if no log is kept.
	 */
	public Node back() throws ExecutionException, EvaluationException {
		return back(now());
	}

	/**
	 * Goes back to the agent's previous statement in the current dialogue (#483), as the Web
	 * Service does for {@code /dialogue/back}: the node of that step is executed again, so its
	 * commands run again, and it becomes the current step. Nothing is added to the log, so a
	 * reply to it later continues from that step. With no earlier step, the current one is
	 * executed again. It stays within the current dialogue: it doesn't go back across a link.
	 *
	 * @param eventTime the time of the event that triggered this, in the user's time zone.
	 * @return the previous agent step's node, executed again.
	 * @throws ExecutionException if the log is completed or cancelled, or its steps can't be found.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 * @throws IllegalStateException if no log is kept.
	 */
	public Node back(ZonedDateTime eventTime) throws ExecutionException, EvaluationException {
		LoggedDialogue log = loggedDialogue;
		if (log == null)
			throw new IllegalStateException("No log is kept, so there is nothing to go back in");
		if (log.isCancelled()) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_CANCELLED,
					"Dialogue '" + log.getId() + "' has been cancelled and cannot be progressed.");
		}
		if (log.isCompleted()) {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_COMPLETED,
					"Dialogue '" + log.getId() + "' has already completed and cannot be progressed.");
		}
		List<LoggedInteraction> interactions = log.getInteractionList();
		checkInteractionIndex(log, loggedInteractionIndex);
		moveToInteraction(findPreviousAgentInteractionIndex(interactions, loggedInteractionIndex));
		return executeCurrentNode(eventTime);
	}

	/**
	 * Rebuilds an {@link ActiveDialogue} from a log, at the step with the given index (#483), so a
	 * conversation can be resumed later, as the Web Service does on every request. The dialogue is
	 * found by the log's dialogue name and language. The current node is the one of that
	 * interaction, not executed yet, so a reply can be selected right away; to show it again, use
	 * {@link #executeCurrentNode}. Only the position is restored: the variables are those of the
	 * {@code variableStore} that is passed in. The log is kept, and goes on being recorded into.
	 *
	 * @param resolver finds the dialogue of the log, and the ones replies lead into.
	 * @param loggedDialogue the log to resume.
	 * @param interactionIndex the index in its interaction list of the step to resume at: an agent
	 *                         step, as {@link #getLoggedInteractionIndex()} gave it.
	 * @param variableStore the variables of the user.
	 * @param factory makes the log of a dialogue the conversation enters.
	 * @return the dialogue, at that step.
	 * @throws ExecutionException if the dialogue, the interaction or its node can't be found.
	 */
	public static ActiveDialogue restore(DialogueResolver resolver, LoggedDialogue loggedDialogue,
			int interactionIndex, VariableStore variableStore, LoggedDialogueFactory factory)
			throws ExecutionException {
		ResolvedDialogue resolved = resolver.resolve(loggedDialogue.getDialogueName(),
				loggedDialogue.getLanguage());
		checkInteractionIndex(loggedDialogue, interactionIndex);
		ActiveDialogue dialogue = new ActiveDialogue(resolver, resolved, variableStore);
		dialogue.setLoggedDialogue(loggedDialogue, factory);
		dialogue.moveToInteraction(interactionIndex);
		return dialogue;
	}

	/**
	 * Executes the current node, which was put there by {@link #restore} or {@link
	 * #setCurrentNode} and hasn't been executed yet, and makes the result the current step, so that
	 * {@link #getCurrentMessage()} gives it. Its commands run, so this is not for a node that has
	 * been shown before unless that is meant (as with {@link #back}).
	 *
	 * @param eventTime the time of the event that triggered this, in the user's time zone.
	 * @return the executed node.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 * @throws IllegalStateException if the dialogue has no current node, or it was executed already.
	 */
	public Node executeCurrentNode(ZonedDateTime eventTime) throws EvaluationException {
		Node node = Objects.requireNonNull(currentNode, "Dialogue has not been started");
		if (!(node.getBody() instanceof NodeBody)) {
			throw new IllegalStateException("The current node \"" + node.getTitle()
					+ "\" has already been executed");
		}
		currentNode = executeNode(node, eventTime);
		return currentNode;
	}

	/**
	 * Marks the log of this conversation as cancelled, as {@code /dialogue/cancel} does (#483). It
	 * does nothing if no log is kept. The log can't be progressed or gone back in after that.
	 */
	public void cancel() {
		if (loggedDialogue != null)
			loggedDialogue.setCancelled(true);
	}

	/** Puts the dialogue at the node of the interaction with the given index in the log. */
	private void moveToInteraction(int index) throws ExecutionException {
		LoggedInteraction interaction = Objects.requireNonNull(loggedDialogue)
				.getInteractionList().get(index);
		followUserGender();
		String nodeId = interaction.getNodeId();
		Node node = nodeId == null ? null : dialogueDefinition.getNodeById(nodeId);
		if (node == null) {
			throw new ExecutionException(ExecutionException.Type.NODE_NOT_FOUND,
					String.format("Node \"%s\" not found in dialogue \"%s\"", nodeId,
							dialogueDefinition.getDialogueName()));
		}
		this.currentNode = node;
		this.started = true;
		this.loggedInteractionIndex = index;
	}

	private static void checkInteractionIndex(LoggedDialogue log, int index)
			throws ExecutionException {
		if (index < 0 || index >= log.getInteractionList().size()) {
			throw new ExecutionException(ExecutionException.Type.INTERACTION_NOT_FOUND,
					String.format("Interaction \"%s\" not found in logged dialogue \"%s\"",
							index, log.getId()));
		}
	}

	/**
	 * Finds the agent step before the one at {@code start}, following the interactions' links to
	 * the interaction they came from; {@code start} itself if there is none.
	 */
	private static int findPreviousAgentInteractionIndex(List<LoggedInteraction> interactions,
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

}

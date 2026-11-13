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
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * An {@link ActiveDialogue} is a wrapper around a {@link Dialogue}, which contains a static
 * definition of a dialogue (referred to as the {@code dialogueDefinition}). The
 * {@link ActiveDialogue} also contains utility functions to keep track of the state during
 * "execution" of the dialogue. When a reply leads into another dialogue, it switches to that
 * dialogue itself, found by its {@link DialogueResolver} (see {@link #selectReply}), so one
 * {@link ActiveDialogue} runs a conversation across dialogues (#473).
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
	// Set in the constructor; setVariableStore() may re-point it (e.g. when a persisted
	// DialogueState is re-attached to a fresh user session).
	private VariableStore variableStore;

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
	 */
	public boolean isFinished() {
		if (!started)
			return false;
		return currentNode == null || Objects.requireNonNull(currentNode.getBody(),
				"The current node has no body").getReplies().isEmpty();
	}

	/**
	 * Returns the current step as a {@link DialogueMessage}, ready to show: the speaker, the
	 * statement with its variables filled in and its actions and input fields, and the replies to
	 * choose from. This is the same message the Web Service sends for a step. Its {@code
	 * loggedDialogueId} is {@code null} and its {@code loggedInteractionIndex} is 0 (and has no
	 * meaning), because an {@link ActiveDialogue} keeps no log of its own.
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
				new ExecuteNodeResult(dialogueDefinition, currentNode, null, 0));
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
	 * normally supplied to the constructor; this is for the case where a persisted
	 * {@link com.dialoguebranch.model.execute.DialogueState} is re-attached to a fresh user
	 * session and its variable store must be swapped for the live one.
	 *
	 * @param variableStore the {@link VariableStore} to use from now on.
	 */
	public void setVariableStore(VariableStore variableStore) {
		this.variableStore = variableStore;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

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
		return startDialogue(null, eventTime);
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
		return currentNode;
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
	 * @throws ExecutionException if the dialogue or node the reply leads to can't be found, or
	 *                            if the {@link DialogueSwitchListener} stops the switch.
	 * @throws EvaluationException if an expression cannot be evaluated.
	 */
	public @Nullable Node selectReply(int replyId, ZonedDateTime eventTime)
			throws ExecutionException, EvaluationException {
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
			this.dialogueFileDescription = target.pointer();
			this.dialogueDefinition = target.dialogue();
			this.userGenderTranslations = target.userGenderTranslations();
			return startDialogue(externalPointer.getTargetNodeId(), eventTime);
		}
		return followInternalPointer((InternalNodePointer) nodePointer, eventTime);
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

}

/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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
import com.dialoguebranch.model.*;
import com.dialoguebranch.model.command.Command;
import com.dialoguebranch.model.command.InputCommand;
import com.dialoguebranch.model.command.SetCommand;
import com.dialoguebranch.model.nodepointer.NodePointer;
import com.dialoguebranch.model.nodepointer.InternalNodePointer;
import nl.rrd.utils.expressions.EvaluationException;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link ActiveDialogue} is a wrapper around a {@link Dialogue}, which contains a static
 * definition of a dialogue (referred to as the {@code dialogueDefinition}). The
 * {@link ActiveDialogue} also contains utility functions to keep track of the state during
 * "execution" of the dialogue.
 * 
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Tessa Beinema (Roessingh Research and Development)
 */
public class ActiveDialogue {

	private final FileDescriptor dialogueFileDescription;
	private final Dialogue dialogueDefinition;
	private Node currentNode;
	private VariableStore variableStore;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of an {@link ActiveDialogue} with a given
	 * {@link FileDescriptor} and {@link Dialogue}.
	 *
	 * @param dialogueFileDescription the {@link FileDescriptor} containing metadata
	 *                                of the dialogue file used in this {@link ActiveDialogue}.
	 * @param dialogueDefinition the dialogue definition
	 */
	public ActiveDialogue(FileDescriptor dialogueFileDescription,
						  Dialogue dialogueDefinition) {
		this.dialogueFileDescription = dialogueFileDescription;
		this.dialogueDefinition = dialogueDefinition;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the {@link FileDescriptor} of the dialogue file corresponding to this
	 * {@link ActiveDialogue} containing metadata for the file.
	 * @return the dialogue file description as a {@link FileDescriptor}
	 */
	public FileDescriptor getDialogueFileDescription() {
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
	public Node getCurrentNode() {
		return currentNode;
	}

	/**
	 * Sets in which node the current active dialogue is.
	 * @param currentNode the {@link Node} currently being executed.
	 */
	public void setCurrentNode(Node currentNode) {
		this.currentNode = currentNode;
	}

	/**
	 * Returns the {@link VariableStore} associated with this {@link ActiveDialogue}.
	 * @return the {@link VariableStore} associated with this {@link ActiveDialogue}.
	 */
	public VariableStore getVariableStore() {
		return variableStore;
	}

	/**
	 * Sets the {@link VariableStore} used to store/retrieve parameters for this
	 * {@link ActiveDialogue}.
	 * @param variableStore the {@link VariableStore} used to store/retrieve parameters for
	 *                         this {@link ActiveDialogue}.
	 */
	public void setVariableStore(VariableStore variableStore) {
		this.variableStore = variableStore;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------
	
	/**
	 * "Starts" this {@link ActiveDialogue}, returning the start node and updating its internal
	 * state.
	 *
	 * @param eventTime the timestamp (in the time zone of the user) of the event that triggered
	 *                  this start of the DialogueBranch dialogue.
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
	 * 		   executed by the DialogueBranch parser (e.g. after control statements have been
	 * 		   resolved)
	 * @throws ExecutionException if the request is invalid
	 * @throws EvaluationException if an expression cannot be evaluated during execution of the node
	 */
	public Node startDialogue(String startNodeId, ZonedDateTime eventTime)
			throws ExecutionException, EvaluationException {
		Node nextNode;
		if (startNodeId == null) {
			nextNode = dialogueDefinition.getStartNode();
		} else {
			nextNode = dialogueDefinition.getNodeById(startNodeId);
			if (nextNode == null) {
				throw new ExecutionException(ExecutionException.Type.NODE_NOT_FOUND,
						String.format("Node \"%s\" not found in dialogue \"%s\"",
								startNodeId, dialogueDefinition.getDialogueName()));
			}
		}
		this.currentNode = executeNode(nextNode,eventTime);
		return currentNode;
	}
	
	/**
	 * Retrieves the pointer to the next node based on the provided {@code replyId}. This might be a
	 * pointer to the end node. This method also performs any "set" actions associated with the
	 * reply.
	 * 
	 * @param replyId the reply ID
	 * @param eventTime the time (in the user's timezone) of the event that triggered this
	 *                  processing of the reply.
	 * @return The {@link NodePointer} pointing to the next DialogueBranch Node
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public NodePointer processReplyAndGetNodePointer(int replyId, ZonedDateTime eventTime)
			throws EvaluationException {
		Reply selectedReply = currentNode.getBody().findReplyById(replyId);
		Map<String,Object> variableMap =
				variableStore.getModifiableMap(true, eventTime,
					VariableStoreChange.Source.DLB_SCRIPT);
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
	public Node progressDialogue(InternalNodePointer nodePointer, ZonedDateTime eventTime)
			throws EvaluationException {
		Node nextNode = null;
		if (!nodePointer.getTargetNodeId().equalsIgnoreCase(Constants.DLB_NODE_END_ID))
			nextNode = dialogueDefinition.getNodeById(nodePointer.getTargetNodeId());
		this.currentNode = nextNode;
		if (nextNode != null) this.currentNode = executeNode(nextNode, eventTime);
		return this.currentNode;
	}

	/**
	 * Stores the specified variables in the variable store.
	 *
	 * @param eventTime the time (in the time zone of the user) of the event that triggered this
	 *                  change in DialogueBranch Variables.
	 * @param variables the variables
	 * // TODO: It's not exactly clear how this method is supposed to be used. The assumption is now
	 *                  that this method stores a set of variables that are the direct result from
	 *                  a user input reply and therefore the chosen INPUT_REPLY source is used.
	 */
	public void storeReplyInput(Map<String,?> variables, ZonedDateTime eventTime) {
		variableStore.addAll(variables,true,eventTime,
				VariableStoreChange.Source.INPUT_REPLY);
	}

	/**
	 * Returns the statement corresponding to a given {@code replyId}. In any given state of the
	 * conversation, the user's client may return a replyId corresponding to a specific reply option
	 * in the current node. This method retrieves the corresponding statement to that reply, which
	 * may be the statement as defined in the {@link Dialogue}, or it may be a constant defining
	 * that this was an "Auto Forward" reply without a specified statement (see
	 * {@link Constants#DLB_REPLY_STATEMENT_AUTOFORWARD}).
	 *
	 * @param replyId the reply id as provided e.g. by a client application.
	 * @return the statement {@link String} corresponding to the reply identified by {@code replyId}
	 * @throws ExecutionException if no reply with the specified {@code replyId} is found
	 */
	public String getUserStatementFromReplyId(int replyId) throws ExecutionException {
		Reply selectedReply = currentNode.getBody().findReplyById(replyId);
		if (selectedReply == null) {
			throw new ExecutionException(ExecutionException.Type.REPLY_NOT_FOUND,
					String.format("Reply with ID %s not found in dialogue \"%s\", node \"%s\"",
					replyId, dialogueDefinition.getDialogueName(), currentNode.getTitle()));
		}
		if (selectedReply.getStatement() == null)
			return Constants.DLB_REPLY_STATEMENT_AUTOFORWARD;
		StringBuilder result = new StringBuilder();
		List<NodeBody.Segment> segments = selectedReply.getStatement()
				.getSegments();
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
	 *                  the execution of this DialogueBranch Node
	 * @return the executed {@link Node}.
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Node executeNode(Node node, ZonedDateTime eventTime) throws EvaluationException {
		Node processedNode = new Node();
		processedNode.setHeader(node.getHeader());
		NodeBody processedBody = new NodeBody();
		Map<String,Object> variables =
				variableStore.getModifiableMap(true, eventTime,
						VariableStoreChange.Source.DLB_SCRIPT);
		node.getBody().execute(variables, true, processedBody);
		processedNode.setBody(processedBody);
		return processedNode;
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
	 *                  execution of the DialogueBranch Node
	 * @return the executed {@link Node}
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Node executeNodeStateless(Node node, ZonedDateTime eventTime)
			throws EvaluationException {
		Node processedNode = new Node();
		processedNode.setHeader(node.getHeader());
		NodeBody processedBody = new NodeBody();
		Map<String,Object> variables = new LinkedHashMap<>(
				variableStore.getModifiableMap(false,eventTime));
		node.getBody().execute(variables, true, processedBody);
		processedNode.setBody(processedBody);
		return processedNode;
	}

}

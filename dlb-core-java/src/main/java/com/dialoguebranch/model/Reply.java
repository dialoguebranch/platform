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

package com.dialoguebranch.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dialoguebranch.model.command.Command;
import com.dialoguebranch.model.command.InputCommand;
import com.dialoguebranch.model.command.SetCommand;
import com.dialoguebranch.model.nodepointer.NodePointer;
import nl.rrd.utils.expressions.EvaluationException;
import com.dialoguebranch.model.command.ActionCommand;

/**
 * TODO: It may be nice to make Reply Abstract with different implementing subclasses, e.g.
 *       "AutoForwardReply" and "NormalReply".
 * A reply option within a {@link NodeBody}. A reply always has a pointer to the next node when the
 * reply is chosen. This might be a pointer to the end node. The reply usually has a statement that
 * is shown in the UI, but a node may have at most one reply without a statement, which is known as
 * an auto-forward reply.
 * 
 * <p>The statement may contain a {@link InputCommand} (see {@link NodeBody}).</p>
 * 
 * <p>The reply may also have commands that should be performed when the reply is chosen. This can
 * be:</p>
 * 
 * <ul>
 *   <li>{@link ActionCommand}</li>
 *   <li>{@link SetCommand}</li>
 * </ul>
 * 
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class Reply {
	private int replyId;
	private NodeBody statement = null;
	private NodePointer nodePointer;
	private List<Command> commands = new ArrayList<>();

	/**
	 * Constructs a new reply.
	 *
	 * @param replyId the reply ID
	 * @param statement the statement or null (auto-forward reply)
	 * @param nodePointer the next node when the reply is chosen
	 */
	public Reply(int replyId, NodeBody statement, NodePointer nodePointer) {
		this.replyId = replyId;
		this.statement = statement;
		this.nodePointer = nodePointer;
	}

	/**
	 * Constructs an auto-forward reply without a statement.
	 *
	 * @param replyId the reply ID
	 * @param nodePointer the next node when the reply is chosen
	 */
	public Reply(int replyId, NodePointer nodePointer) {
		this.replyId = replyId;
		this.nodePointer = nodePointer;
	}

	public Reply(Reply other) {
		this.replyId = other.replyId;
		if (other.statement != null)
			this.statement = new NodeBody(other.statement);
		this.nodePointer = other.nodePointer.clone();
		for (Command cmd : other.commands) {
			this.commands.add(cmd.clone());
		}
	}

	/**
	 * Returns the reply ID. The ID is unique within a node.
	 * 
	 * @return the reply ID
	 */
	public int getReplyId() {
		return replyId;
	}

	/**
	 * Sets the reply ID. The ID is unique within a node.
	 * 
	 * @param replyId the reply ID
	 */
	public void setReplyId(int replyId) {
		this.replyId = replyId;
	}

	/**
	 * Returns the statement. If this reply is an auto-forward reply, then this
	 * method returns null.
	 * 
	 * @return the statement or null
	 */
	public NodeBody getStatement() {
		return statement;
	}

	/**
	 * Sets the statement. If this reply is an auto-forward reply, then the
	 * statement can be null.
	 * 
	 * @param statement the statement or null
	 */
	public void setStatement(NodeBody statement) {
		this.statement = statement;
	}

	/**
	 * Returns the next node when this reply is chosen. This might be the end
	 * node.
	 * 
	 * @return the next node when this reply is chosen
	 */
	public NodePointer getNodePointer() {
		return nodePointer;
	}

	/**
	 * Sets the next node when this reply is chosen.
	 * 
	 * @param nodePointer the next node when this reply is chosen
	 */
	public void setNodePointer(NodePointer nodePointer) {
		this.nodePointer = nodePointer;
	}

	/**
	 * Returns the commands that should be executed when this reply is chosen.
	 * 
	 * @return the commands that should be executed when this reply is chosen
	 */
	public List<Command> getCommands() {
		return commands;
	}

	/**
	 * Sets the commands that should be executed when this reply is chosen.
	 * 
	 * @param commands the commands that should be executed when this reply is
	 * chosen
	 */
	public void setCommands(List<Command> commands) {
		this.commands = commands;
	}
	
	/**
	 * Adds a command that should be executed when this reply is chosen.
	 * 
	 * @param command the command that should be executed when this reply is
	 * chosen
	 */
	public void addCommand(Command command) {
		commands.add(command);
	}
	
	/**
	 * Retrieves all variable names that are read in this reply and adds them to
	 * the specified set.
	 * 
	 * @param varNames the set to which the variable names are added
	 */
	public void getReadVariableNames(Set<String> varNames) {
		if (statement != null)
			statement.getReadVariableNames(varNames);
		for (Command command : commands) {
			command.getReadVariableNames(varNames);
		}
	}
	
	/**
	 * Retrieves all variable names that are written in this reply and adds them
	 * to the specified set.
	 * 
	 * @param varNames the set to which the variable names are added
	 */
	public void getWriteVariableNames(Set<String> varNames) {
		if (statement != null)
			statement.getWriteVariableNames(varNames);
		for (Command command : commands) {
			command.getWriteVariableNames(varNames);
		}
	}
	
	/**
	 * Executes the statement in this reply with respect to the specified
	 * variable map. It executes commands and resolves variables, so that only
	 * content that should be sent to the client, remains in the resulting
	 * reply statement. This content can be text or client commands, with all
	 * variables resolved.
	 * 
	 * @param variables the variable map
	 * @return the processed reply
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Reply execute(Map<String,Object> variables)
			throws EvaluationException {
		if (statement == null)
			return this;
		NodeBody processedStatement = new NodeBody();
		statement.execute(variables, false, processedStatement);
		Reply result = new Reply(replyId, processedStatement,
				nodePointer);
		for (Command command : commands) {
			if (command instanceof ActionCommand actionCmd) {
				result.addCommand(actionCmd.executeReplyCommand(variables));
			} else {
				result.addCommand(command);
			}
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder("[[");
		if (statement != null)
			result.append(statement).append("|");
		result.append(nodePointer.toString());
		if (!commands.isEmpty()) {
			result.append("|");
			for (Command command : commands) {
				result.append(command.toString());
			}
		}
		result.append("]]");
		return result.toString();
	}
}

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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.model.execute.command.ActionCommand;
import com.dialoguebranch.model.execute.command.Command;
import com.dialoguebranch.model.execute.command.InputCommand;
import com.dialoguebranch.model.execute.command.SetCommand;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
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
 * <p>Immutable: built via {@link Builder}, since both parsing ({@code ReplyParser}) and execution
 * ({@link #execute}) accumulate its {@code commands} list incrementally. {@link #getStatement()}
 * is typed against {@link NodeContent} rather than concretely against {@link NodeBody}, since a
 * resolved {@link Reply} (the output of {@link #execute}) holds a {@link ResolvedNodeBody}
 * there instead — unlike {@link NodeBody}, {@link Reply} itself doesn't get a
 * script/resolved type split, since its {@code commands} list doesn't structurally narrow the
 * way a body's segments do (a non-{@link ActionCommand} like {@link SetCommand} is deliberately
 * carried over unresolved by {@link #execute}, since it only actually runs once the reply is
 * chosen).</p>
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class Reply {
	private final int replyId;
	private final @Nullable NodeContent statement;
	private final NodePointer nodePointer;
	private final List<Command> commands;

	private Reply(int replyId, @Nullable NodeContent statement, NodePointer nodePointer,
			List<Command> commands) {
		this.replyId = replyId;
		this.statement = statement;
		this.nodePointer = nodePointer;
		this.commands = commands;
	}

	/**
	 * Constructs a new reply with no commands.
	 *
	 * @param replyId the reply ID
	 * @param statement the statement or null (auto-forward reply)
	 * @param nodePointer the next node when the reply is chosen
	 */
	public Reply(int replyId, @Nullable NodeContent statement, NodePointer nodePointer) {
		this(replyId, statement, nodePointer, new ArrayList<>());
	}

	/**
	 * Constructs an auto-forward reply without a statement or commands.
	 *
	 * @param replyId the reply ID
	 * @param nodePointer the next node when the reply is chosen
	 */
	public Reply(int replyId, NodePointer nodePointer) {
		this(replyId, null, nodePointer, new ArrayList<>());
	}

	/**
	 * Creates a deep copy of the given {@link Reply}, cloning its statement body (if it's a
	 * {@link NodeBody} — a resolved {@link ResolvedNodeBody} statement is copied by reference,
	 * since resolved content is never mutated or re-executed), node pointer, and all commands.
	 *
	 * @param other the {@link Reply} to copy.
	 */
	public Reply(Reply other) {
		this.replyId = other.replyId;
		this.statement = other.statement instanceof NodeBody body ? new NodeBody(body)
				: other.statement;
		this.nodePointer = other.nodePointer.clone();
		List<Command> commands = new ArrayList<>();
		for (Command cmd : other.commands) {
			commands.add(cmd.clone());
		}
		this.commands = commands;
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
	 * Returns whether this is an auto-forward reply, i.e. a reply without a statement. A node may
	 * have at most one such reply.
	 *
	 * @return true if this reply has no statement, false otherwise
	 */
	public boolean isAutoForward() {
		return statement == null;
	}

	/**
	 * Returns the statement. If this reply is an auto-forward reply, then this method returns
	 * null. Holds a {@link NodeBody} before this reply has been through {@link #execute}, a
	 * {@link ResolvedNodeBody} after.
	 *
	 * @return the statement or null
	 */
	public @Nullable NodeContent getStatement() {
		return statement;
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
	 * Returns the commands that should be executed when this reply is chosen, as an unmodifiable
	 * list.
	 *
	 * @return the commands that should be executed when this reply is chosen
	 */
	public List<Command> getCommands() {
		return Collections.unmodifiableList(commands);
	}

	/**
	 * Retrieves all variable names that are read in this reply and adds them to the specified
	 * set. Only meaningful before {@link #execute} — a resolved reply's statement has no
	 * unresolved variable references left to report.
	 *
	 * @param varNames the set to which the variable names are added
	 */
	public void getReadVariableNames(Set<String> varNames) {
		if (statement instanceof NodeBody body)
			body.getReadVariableNames(varNames);
		for (Command command : commands) {
			command.getReadVariableNames(varNames);
		}
	}

	/**
	 * Retrieves all variable names that are written in this reply and adds them to the specified
	 * set. Only meaningful before {@link #execute} — see {@link #getReadVariableNames}.
	 *
	 * @param varNames the set to which the variable names are added
	 */
	public void getWriteVariableNames(Set<String> varNames) {
		if (statement instanceof NodeBody body)
			body.getWriteVariableNames(varNames);
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
	 * <p>Must only be called on a not-yet-executed {@link Reply} — {@link #getStatement()} is
	 * guaranteed to be a {@link NodeBody} (never a {@link ResolvedNodeBody}) on any {@link Reply}
	 * this hasn't already been called on.</p>
	 *
	 * @param variables the variable map
	 * @return the processed reply
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public Reply execute(Map<String,Object> variables)
			throws EvaluationException {
		if (statement == null)
			return this;
		ResolvedNodeBody processedStatement =
				((NodeBody) statement).execute(variables, false);
		Reply.Builder builder = new Reply.Builder(replyId, processedStatement, nodePointer);
		for (Command command : commands) {
			if (command instanceof ActionCommand actionCmd) {
				builder.addCommand(actionCmd.executeReplyCommand(variables));
			} else {
				builder.addCommand(command);
			}
		}
		return builder.build();
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

	/**
	 * Accumulates commands incrementally, then produces an immutable {@link Reply} via
	 * {@link #build}. Used by {@code ReplyParser} at parse time and by {@link #execute} at
	 * execution time — the {@code replyId}/{@code statement}/{@code nodePointer} are always known
	 * upfront in both cases, only the command list is built up incrementally.
	 */
	public static class Builder {
		private final int replyId;
		private final @Nullable NodeContent statement;
		private final NodePointer nodePointer;
		private final List<Command> commands = new ArrayList<>();

		/**
		 * Creates a {@link Builder} for a reply with the given {@code replyId}, {@code statement}
		 * (or {@code null} for an auto-forward reply), and {@code nodePointer}.
		 *
		 * @param replyId the reply ID.
		 * @param statement the statement, or {@code null} for an auto-forward reply.
		 * @param nodePointer the next node when the reply is chosen.
		 */
		public Builder(int replyId, @Nullable NodeContent statement, NodePointer nodePointer) {
			this.replyId = replyId;
			this.statement = statement;
			this.nodePointer = nodePointer;
		}

		/**
		 * Appends a command that should be executed when this reply is chosen.
		 *
		 * @param command the command to add.
		 */
		public void addCommand(Command command) {
			commands.add(command);
		}

		/**
		 * Builds the immutable {@link Reply}.
		 *
		 * @return the built {@link Reply}.
		 */
		public Reply build() {
			return new Reply(replyId, statement, nodePointer, new ArrayList<>(commands));
		}
	}
}

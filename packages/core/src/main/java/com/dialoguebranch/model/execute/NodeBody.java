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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.model.execute.command.*;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import nl.rrd.utils.expressions.EvaluationException;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;

import java.util.*;

/**
 * A node body can occur in three different contexts inside a {@link Node}.
 * 
 * <ul>
 *   <li>Directly in the node. In this case it specifies the agent statement with possible commands
 *   and user replies.</li>
 *   <li>As part of a clause in a {@link IfCommand} or {@link RandomCommand}. The content is
 *   the same as directly in the node. The only difference is that it is performed
 *   conditionally.</li>
 *   <li>As part of a {@link Reply}. In this case it specifies the user statement with possible
 *   commands, but no replies. Note that the UI shows these statements as options immediately along
 *   with the agent statement. This {@link NodeBody} does not contain commands that are to be
 *   performed when the reply is chosen. Such commands are specified separately in a
 *   {@link Reply}.</li>
 * </ul>
 * 
 * <p>The body contains a statement as a list of segments where each segment is one of:</p>
 * 
 * <ul>
 *   <li>{@link NodeBody.TextSegment TextSegment}: a {@link VariableString} with text and
 *   variables</li>
 *   <li>{@link NodeBody.CommandSegment CommandSegment}: a command (see below)</li>
 * </ul>
 * 
 * <p>The segments are always normalized so that subsequent text segments are
 * automatically merged into one.</p>
 * 
 * <p>The type of commands depend on the context. Directly in the node or in a
 * {@link IfCommand} or {@link RandomCommand}, it can be:</p>
 * 
 * <ul>
 *   <li>{@link ActionCommand}: Actions to perform along with the agent's text statement.</li>
 *   <li>{@link IfCommand}: Contains clauses, each with a {@link NodeBody} specifying
 *   conditional statements, replies and commands.</li>
 *   <li>{@link RandomCommand}: Contains clauses, each with a {@link NodeBody} specifying
 *   statements, replies and commands.</li>
 *   <li>{@link SetCommand}: Sets a variable value.</li>
 * </ul>
 * 
 * <p>As part of a reply (remember the earlier remarks about commands in a
 * reply), it can be:</p>
 * 
 * <ul>
 *   <li>{@link InputCommand}: Allow user to provide input other than just clicking the reply
 *   option.</li>
 * </ul>
 * 
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class NodeBody {
	private List<Segment> segments = new ArrayList<>();
	private List<Reply> replies = new ArrayList<>();

	/**
	 * Creates an empty {@link NodeBody} with no segments and no replies.
	 */
	public NodeBody() {
	}

	/**
	 * Creates a deep copy of the given {@link NodeBody}, cloning all segments and replies.
	 *
	 * @param other the {@link NodeBody} to copy.
	 */
	public NodeBody(NodeBody other) {
		for (Segment segment : other.segments) {
			this.segments.add(segment.clone());
		}
		for (Reply reply : other.replies) {
			this.replies.add(new Reply(reply));
		}
	}

	/**
	 * Returns the segments as an unmodifiable list.
	 * 
	 * @return the segments as an unmodifiable list
	 */
	public List<Segment> getSegments() {
		return Collections.unmodifiableList(segments);
	}

	/**
	 * Appends the given {@link Segment} to this body. If the new segment and the current last
	 * segment are both {@link TextSegment}s, they are merged into a single segment to maintain
	 * the normalized invariant.
	 *
	 * @param segment the {@link Segment} to append.
	 */
	public void addSegment(Segment segment) {
		Segment lastSegment = null;
		if (!segments.isEmpty())
			lastSegment = segments.get(segments.size() - 1);
		if (lastSegment instanceof TextSegment &&
				segment instanceof TextSegment) {
			TextSegment lastTextSegment = (TextSegment)lastSegment;
			TextSegment textSegment = (TextSegment)segment;
			VariableString text = new VariableString();
			text.addSegments(lastTextSegment.text.getSegments());
			text.addSegments(textSegment.text.getSegments());
			TextSegment mergedSegment = new TextSegment(text);
			segments.remove(segments.size() - 1);
			segments.add(mergedSegment);
		} else {
			segments.add(segment);
		}
	}

	/**
	 * Removes all segments from this {@link NodeBody}.
	 */
	public void clearSegments() {
		segments.clear();
	}

	private void trimText() {
		if (!segments.isEmpty() && segments.get(0) instanceof TextSegment) {
			TextSegment segment = (TextSegment)segments.get(0);
			String text = segment.text.evaluate(null).replaceAll("^\\s+", "");
			segment.text = new VariableString(text);
		}
		if (!segments.isEmpty() && segments.get(segments.size() - 1)
				instanceof TextSegment) {
			TextSegment segment = (TextSegment)segments.get(
					segments.size() - 1);
			String text = segment.text.evaluate(null).replaceAll("\\s+$", "");
			segment.text = new VariableString(text);
		}
	}

	/**
	 * Returns the live list of {@link Reply} objects attached to this body.
	 *
	 * @return the list of replies.
	 */
	public List<Reply> getReplies() {
		return replies;
	}

	/**
	 * Searches this body and all nested command segments for a {@link Reply} with the given
	 * {@code replyId}.
	 *
	 * @param replyId the reply identifier to look up.
	 * @return the matching {@link Reply}, or {@code null} if not found.
	 */
	public Reply findReplyById(int replyId) {
		for (Reply reply : replies) {
			if (reply.getReplyId() == replyId)
				return reply;
		}
		for (Segment segment : segments) {
			Reply reply = segment.findReplyById(replyId);
			if (reply != null)
				return reply;
		}
		return null;
	}

	/**
	 * Appends the given {@link Reply} to this body's reply list.
	 *
	 * @param reply the {@link Reply} to add.
	 */
	public void addReply(Reply reply) {
		replies.add(reply);
	}

	/**
	 * Retrieves all variable names that are read in this body.
	 * 
	 * @return the variable names that are read in this body
	 */
	public List<String> getReadVariableNames() {
		Set<String> set = new HashSet<>();
		getReadVariableNames(set);
		List<String> result = new ArrayList<>(set);
		Collections.sort(result);
		return result;
	}
	
	/**
	 * Retrieves all variable names that are read in this body and adds them to
	 * the specified set.
	 * 
	 * @param varNames the set to which the variable names are added
	 */
	public void getReadVariableNames(Set<String> varNames) {
		for (Segment segment : segments) {
			segment.getReadVariableNames(varNames);
		}
		for (Reply reply : replies) {
			reply.getReadVariableNames(varNames);
		}
	}

	/**
	 * Retrieves all variable names that are written in this body.
	 * 
	 * @return the variable names that are written in this body
	 */
	public List<String> getWriteVariableNames() {
		Set<String> set = new HashSet<>();
		getWriteVariableNames(set);
		List<String> result = new ArrayList<>(set);
		Collections.sort(result);
		return result;
	}

	/**
	 * Retrieves all variable names that are written in this body and adds them
	 * to the specified set.
	 * 
	 * @param varNames the set to which the variable names are added
	 */
	public void getWriteVariableNames(Set<String> varNames) {
		for (Segment segment : segments) {
			segment.getWriteVariableNames(varNames);
		}
		for (Reply reply : replies) {
			reply.getWriteVariableNames(varNames);
		}
	}
	
	/**
	 * Returns a sorted list of all {@link NodePointer}s found in this body's command segments and
	 * reply list. Internal pointers are listed before external ones; within each group they are
	 * sorted alphabetically.
	 *
	 * @return a sorted list of node pointers.
	 */
	public List<NodePointer> getNodePointers() {
		Set<NodePointer> set = new HashSet<>();
		getNodePointers(set);
		List<NodePointer> result = new ArrayList<>(set);
		Collections.sort(result, this::compareNodePointers);
		return result;
	}
	
	private int compareNodePointers(NodePointer o1, NodePointer o2) {
		if (o1 instanceof InternalNodePointer) {
			if (o2 instanceof ExternalNodePointer)
				return -1;
			InternalNodePointer p1 = (InternalNodePointer)o1;
			InternalNodePointer p2 = (InternalNodePointer)o2;
			return p1.getTargetNodeId().compareTo(p2.getTargetNodeId());
		} else {
			if (o2 instanceof InternalNodePointer)
				return -1;
			ExternalNodePointer p1 = (ExternalNodePointer)o1;
			ExternalNodePointer p2 = (ExternalNodePointer)o2;
			int result = p1.getAbsoluteTargetDialogue().compareTo(p2.getAbsoluteTargetDialogue());
			if (result != 0)
				return result;
			return p1.getTargetNodeId().compareTo(p2.getTargetNodeId());
		}
	}
	
	/**
	 * Collects all {@link NodePointer}s from this body's command segments and replies into the
	 * given {@code pointers} set.
	 *
	 * @param pointers the set to which node pointers are added.
	 */
	public void getNodePointers(Set<NodePointer> pointers) {
		for (Segment segment : segments) {
			if (!(segment instanceof CommandSegment))
				continue;
			Command command = ((CommandSegment)segment).command;
			command.getNodePointers(pointers);
		}
		for (Reply reply : replies) {
			pointers.add(reply.getNodePointer());
		}
	}
	
	/**
	 * Executes the agent statement and reply statements in this body with
	 * respect to the specified variable map. It executes ("if" and "set")
	 * commands and resolves variables. Any resulting body content that should
	 * be sent to the client, is added to agent and reply statements in
	 * "processedBody". This content can be text or client commands, with all
	 * variables resolved.
	 * 
	 * <p>This method also normalizes whitespace in the text segments. It
	 * removes empty lines and makes sure that lines end with "\n". Within each
	 * line, it trims whitespace from the start and end, and it replaces any
	 * sequence of spaces and tabs with one space.</p>
	 * 
	 * <p>This method should only be called if all variables in the text
	 * segments have been resolved.</p>
	 *  
	 * @param variables the variable map
	 * @param trimText true if trailing new lines should be trimmed, false if
	 * they should be preserved. This should be set to true for the body that is
	 * directly in the node. If the body is in an "if" clause or in a reply, it
	 * should be set to false.
	 * @param processedBody the processed body
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public void execute(Map<String,Object> variables, boolean trimText,
			NodeBody processedBody) throws EvaluationException {
		for (Segment segment : segments) {
			if (segment instanceof TextSegment) {
				executeTextSegment((TextSegment)segment, variables,
						processedBody);
			} else {
				executeCommandSegment((CommandSegment)segment, variables,
						processedBody);
			}
		}
		for (Reply reply : replies) {
			processedBody.addReply(reply.execute(variables));
		}
		if (trimText)
			processedBody.trimText();
	}
	
	private void executeTextSegment(TextSegment segment,
			Map<String,Object> variables, NodeBody processedBody) {
		TextSegment processedText = new TextSegment(
				segment.text.execute(variables));
		processedBody.addSegment(processedText);
	}
	
	private void executeCommandSegment(CommandSegment segment,
			Map<String,Object> variables, NodeBody processedBody)
			throws EvaluationException {
		segment.command.executeBodyCommand(variables, processedBody);
	}

	/**
	 * Removes leading and trailing whitespace from this body's segment list.
	 */
	public void trimWhitespace() {
		trimWhitespace(segments);
	}

	/**
	 * Removes leading and trailing whitespace from the given list of {@link Segment}s.
	 *
	 * @param segments the segment list to trim.
	 */
	public static void trimWhitespace(List<NodeBody.Segment> segments) {
		removeLeadingWhitespace(segments);
		removeTrailingWhitespace(segments);
	}

	/**
	 * Removes leading whitespace from this body's segment list.
	 */
	public void removeLeadingWhitespace() {
		removeLeadingWhitespace(segments);
	}

	/**
	 * Removes leading whitespace {@link TextSegment}s (or the leading whitespace within the first
	 * text segment) from the given segment list.
	 *
	 * @param segments the segment list to trim.
	 */
	public static void removeLeadingWhitespace(List<NodeBody.Segment> segments) {
		while (!segments.isEmpty()) {
			Segment segment = segments.get(0);
			if (!(segment instanceof TextSegment))
				return;
			TextSegment textSegment = (TextSegment)segment;
			VariableString text = textSegment.getText();
			text.removeLeadingWhitespace();
			if (!text.getSegments().isEmpty())
				return;
			segments.remove(0);
		}
	}

	/**
	 * Removes trailing whitespace from this body's segment list.
	 */
	public void removeTrailingWhitespace() {
		removeTrailingWhitespace(segments);
	}

	/**
	 * Removes trailing whitespace {@link TextSegment}s (or the trailing whitespace within the last
	 * text segment) from the given segment list.
	 *
	 * @param segments the segment list to trim.
	 */
	public static void removeTrailingWhitespace(List<NodeBody.Segment> segments) {
		while (!segments.isEmpty()) {
			Segment segment = segments.get(segments.size() - 1);
			if (!(segment instanceof TextSegment))
				return;
			TextSegment textSegment = (TextSegment)segment;
			VariableString text = textSegment.getText();
			text.removeTrailingWhitespace();
			if (!text.getSegments().isEmpty())
				return;
			segments.remove(segments.size() - 1);
		}
	}
	
	@Override
	public String toString() {
		String newline = System.getProperty("line.separator");
		StringBuilder builder = new StringBuilder();
		for (Segment segment : segments) {
			builder.append(segment.toString());
		}
		for (Reply reply : replies) {
			builder.append(newline);
			builder.append(reply);
		}
		return builder.toString();
	}

	/**
	 * Abstract base for the two kinds of content elements that can appear in a {@link NodeBody}:
	 * {@link TextSegment} (plain text with optional variables) and {@link CommandSegment} (an
	 * embedded {@link Command}).
	 */
	public static abstract class Segment implements Cloneable {

		/**
		 * Creates a new {@link Segment}. Exists for use by concrete subclasses.
		 */
		public Segment() { }
		/**
		 * Tries to find a reply with the specified ID within this segment. If
		 * no such reply is found, this method returns null.
		 * 
		 * @param replyId the reply ID
		 * @return the reply or null
		 */
		public abstract Reply findReplyById(int replyId);

		/**
		 * Retrieves all variable names that are read in this segment and adds
		 * them to the specified set.
		 * 
		 * @param varNames the set to which the variable names are added
		 */
		public abstract void getReadVariableNames(Set<String> varNames);
		
		/**
		 * Retrieves all variable names that are written in this segment and
		 * adds them to the specified set.
		 * 
		 * @param varNames the set to which the variable names are added
		 */
		public abstract void getWriteVariableNames(Set<String> varNames);

		/**
		 * Returns a deep copy of this segment.
		 *
		 * @return a deep copy of this segment
		 */
		@Override
		public abstract Segment clone();
	}
	
	/**
	 * A {@link Segment} that holds a {@link VariableString} — plain text that may contain
	 * variable references.
	 */
	public static class TextSegment extends Segment {
		private VariableString text;

		/**
		 * Creates a {@link TextSegment} with the given {@link VariableString}.
		 *
		 * @param text the text content, possibly containing variable references.
		 */
		public TextSegment(VariableString text) {
			this.text = text;
		}

		/**
		 * Creates a deep copy of the given {@link TextSegment}.
		 *
		 * @param other the {@link TextSegment} to copy.
		 */
		public TextSegment(TextSegment other) {
			this.text = new VariableString(other.text);
		}

		/**
		 * Returns the {@link VariableString} held by this segment.
		 *
		 * @return the text content.
		 */
		public VariableString getText() {
			return text;
		}

		/**
		 * Sets the {@link VariableString} held by this segment.
		 *
		 * @param text the new text content.
		 */
		public void setText(VariableString text) {
			this.text = text;
		}
		
		@Override
		public Reply findReplyById(int replyId) {
			return null;
		}

		@Override
		public void getReadVariableNames(Set<String> varNames) {
			text.getReadVariableNames(varNames);
		}

		@Override
		public void getWriteVariableNames(Set<String> varNames) {
		}

		@Override
		public String toString() {
			return text.toString();
		}

		@Override
		public TextSegment clone() {
			return new TextSegment(this);
		}
	}
	
	/**
	 * A {@link Segment} that wraps a {@link Command} embedded in the node body (e.g. an
	 * {@code <<if>>}, {@code <<set>>}, or {@code <<action>>} command).
	 */
	public static class CommandSegment extends Segment {
		private Command command;

		/**
		 * Creates a {@link CommandSegment} wrapping the given {@link Command}.
		 *
		 * @param command the {@link Command} embedded in this segment.
		 */
		public CommandSegment(Command command) {
			this.command = command;
		}

		/**
		 * Creates a deep copy of the given {@link CommandSegment}, cloning its command.
		 *
		 * @param other the {@link CommandSegment} to copy.
		 */
		public CommandSegment(CommandSegment other) {
			this.command = other.command.clone();
		}

		/**
		 * Returns the {@link Command} wrapped by this segment.
		 *
		 * @return the embedded command.
		 */
		public Command getCommand() {
			return command;
		}
		
		@Override
		public Reply findReplyById(int replyId) {
			return command.findReplyById(replyId);
		}

		@Override
		public void getReadVariableNames(Set<String> varNames) {
			command.getReadVariableNames(varNames);
		}

		@Override
		public void getWriteVariableNames(Set<String> varNames) {
			command.getWriteVariableNames(varNames);
		}
		
		@Override
		public String toString() {
			return command.toString();
		}

		@Override
		public CommandSegment clone() {
			return new CommandSegment(this);
		}
	}
}

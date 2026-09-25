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

import com.dialoguebranch.model.execute.command.ActionCommand;
import com.dialoguebranch.model.execute.command.InputCommand;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of executing a {@link NodeBody} ({@link NodeBody#execute}) — what actually gets sent
 * to a client. Structurally narrower than {@link NodeBody}: tracing every {@code Command}'s
 * {@code executeBodyCommand} showed a {@code SetCommand}, {@code IfCommand}, or
 * {@code RandomCommand} never survives execution ({@code SetCommand} contributes nothing;
 * {@code IfCommand}/{@code RandomCommand} are replaced by whichever clause's own content was
 * chosen) — only an {@link ActionCommand} (a resolved copy) or an {@link InputCommand} (passed
 * through) can ever appear in a {@link NodeBody.CommandSegment} here.
 * {@link Builder#addSegment} enforces that at runtime.
 *
 * <p>Immutable: built via {@link Builder}, the same way {@link NodeBody} is.</p>
 *
 * @author Harm op den Akker
 */
public class ResolvedNodeBody implements NodeContent {

	private final List<NodeBody.Segment> segments;
	private final List<Reply> replies;

	private ResolvedNodeBody(List<NodeBody.Segment> segments, List<Reply> replies) {
		this.segments = segments;
		this.replies = replies;
	}

	/**
	 * Returns the segments as an unmodifiable list.
	 *
	 * @return the segments as an unmodifiable list.
	 */
	@Override
	public List<NodeBody.Segment> getSegments() {
		return Collections.unmodifiableList(segments);
	}

	/**
	 * Returns the replies as an unmodifiable list.
	 *
	 * @return the replies as an unmodifiable list.
	 */
	@Override
	public List<Reply> getReplies() {
		return Collections.unmodifiableList(replies);
	}

	/**
	 * Searches this body and all nested command segments for a {@link Reply} with the given
	 * {@code replyId}.
	 *
	 * @param replyId the reply identifier to look up.
	 * @return the matching {@link Reply}, or {@code null} if not found.
	 */
	@Override
	public @Nullable Reply findReplyById(int replyId) {
		for (Reply reply : replies) {
			if (reply.getReplyId() == replyId)
				return reply;
		}
		for (NodeBody.Segment segment : segments) {
			Reply reply = segment.findReplyById(replyId);
			if (reply != null)
				return reply;
		}
		return null;
	}

	@Override
	public String toString() {
		String newline = System.getProperty("line.separator");
		StringBuilder builder = new StringBuilder();
		for (NodeBody.Segment segment : segments) {
			builder.append(segment.toString());
		}
		for (Reply reply : replies) {
			builder.append(newline);
			builder.append(reply);
		}
		return builder.toString();
	}

	/**
	 * Accumulates segments and replies incrementally during execution, then produces an
	 * immutable {@link ResolvedNodeBody} via {@link #build}. Threaded through
	 * {@code Command.executeBodyCommand} so a nested {@code <<if>>}/{@code <<random>>} clause's
	 * resolved content merges into the same builder as its enclosing body, rather than needing a
	 * separate merge step afterward.
	 */
	public static class Builder {
		private final List<NodeBody.Segment> segments = new ArrayList<>();
		private final List<Reply> replies = new ArrayList<>();

		/**
		 * Creates an empty {@link Builder} with no segments and no replies.
		 */
		public Builder() {
		}

		/**
		 * Appends the given {@link NodeBody.Segment}. If it and the current last segment are both
		 * {@link NodeBody.TextSegment}s, they are merged into a single segment to maintain the
		 * normalized invariant every {@link ResolvedNodeBody} holds.
		 *
		 * @param segment the segment to append.
		 * @throws IllegalArgumentException if {@code segment} wraps a {@code Command} that can
		 * never legitimately appear in resolved output (anything other than an
		 * {@link ActionCommand} or {@link InputCommand}) — a parser or execution bug, not
		 * something a caller should ever be able to trigger with a valid {@code .dlb} script.
		 */
		public void addSegment(NodeBody.Segment segment) {
			if (segment instanceof NodeBody.CommandSegment commandSegment &&
					!(commandSegment.getCommand() instanceof ActionCommand) &&
					!(commandSegment.getCommand() instanceof InputCommand)) {
				throw new IllegalArgumentException(
						"Resolved node body cannot contain a " +
						commandSegment.getCommand().getClass().getSimpleName());
			}
			NodeBody.mergeAddSegment(segments, segment);
		}

		/**
		 * Appends the given {@link Reply}.
		 *
		 * @param reply the {@link Reply} to add.
		 */
		public void addReply(Reply reply) {
			replies.add(reply);
		}

		/**
		 * Trims leading whitespace from the first segment and trailing whitespace from the last
		 * segment, if they're {@link NodeBody.TextSegment}s — mirrors what {@code NodeBody}'s own
		 * segment list gets at parse time, applied here at execution time since a resolved body's
		 * first/last segment isn't necessarily the same one the unresolved body started/ended
		 * with (an {@code <<if>>}/{@code <<random>>} at either end may have resolved away).
		 */
		void trimText() {
			if (!segments.isEmpty() && segments.get(0) instanceof NodeBody.TextSegment first) {
				String text = first.getText().evaluate(null).replaceAll("^\\s+", "");
				segments.set(0, new NodeBody.TextSegment(new VariableString(text)));
			}
			if (!segments.isEmpty() && segments.get(segments.size() - 1)
					instanceof NodeBody.TextSegment last) {
				String text = last.getText().evaluate(null).replaceAll("\\s+$", "");
				segments.set(segments.size() - 1, new NodeBody.TextSegment(new VariableString(text)));
			}
		}

		/**
		 * Builds the immutable {@link ResolvedNodeBody}.
		 *
		 * @return the built {@link ResolvedNodeBody}.
		 */
		public ResolvedNodeBody build() {
			return new ResolvedNodeBody(new ArrayList<>(segments), new ArrayList<>(replies));
		}
	}
}

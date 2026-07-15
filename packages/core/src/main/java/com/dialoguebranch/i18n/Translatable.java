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

package com.dialoguebranch.i18n;

import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.command.InputCommand;

import java.util.List;

/**
 * This class models a translatable segment from a {@link NodeBody}. It basically consists of plain
 * text, variables and &lt;&lt;input&gt;&gt; commands.
 *
 * <p>The class contains {@link NodeBody.TextSegment TextSegment}s (with plain text and variables)
 * and {@link NodeBody.CommandSegment}s where the command is a {@link InputCommand}.</p>
 *
 * <p>Instances of this class can be obtained from {@link TranslatableExtractor} or {@link
 * TranslationParser}.</p>
 *
 * @param parent   the {@link NodeBody} that contains these segments; used by {@link Translator}
 *                 to locate and replace the segments in-place during translation.
 * @param segments the ordered list of {@link NodeBody.Segment}s that make up the translatable
 *                 content (text, variables, and optional {@code <<input>>} commands).
 *
 * @author Dennis Hofs
 */
public record Translatable(NodeBody parent, List<NodeBody.Segment> segments) {

	/**
	 * Constructs a new {@link Translatable}.
	 *
	 * @param parent   the parent (used in {@link Translator})
	 * @param segments the segments
	 */
	public Translatable {
	}

	/**
	 * Returns the parent (used in {@link Translator}).
	 *
	 * @return the parent (used in {@link Translator})
	 */
	@Override
	public NodeBody parent() {
		return parent;
	}

	/**
	 * Returns the translatable segments.
	 *
	 * @return the translatable segments
	 */
	@Override
	public List<NodeBody.Segment> segments() {
		return segments;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj.getClass() != getClass())
			return false;
		Translatable other = (Translatable) obj;
		return toString().equals(other.toString());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (NodeBody.Segment segment : segments) {
			builder.append(segment);
		}
		return builder.toString();
	}

	/**
	 * Returns a trimmed, export-friendly string representation of this {@link Translatable} by
	 * concatenating the trimmed {@link String} representation of each segment. Unlike
	 * {@link #toString()}, leading and trailing whitespace within each segment is removed, making
	 * the result suitable for use as a translation key in external tools such as POEditor.
	 *
	 * @return a whitespace-trimmed string representation of the translatable segments.
	 */
	public String toExportFriendlyString() {
		StringBuilder builder = new StringBuilder();
		for (NodeBody.Segment segment : segments) {
			String sourceString = segment.toString();
			builder.append(sourceString.trim());
		}
		return builder.toString();
	}

	/**
	 * Returns a whitespace-normalized string representation of this {@link Translatable}: the
	 * trimmed {@link #toString()} with every internal run of whitespace (including line breaks
	 * between source-script lines that belong to the same statement) collapsed to a single space.
	 * This is the canonical key {@link Translator} matches translations against — a translated
	 * dialogue's source text and the term keys stored in a translation file both go through this
	 * same normalization, so this must be used wherever a term key needs to line up with either
	 * side of that lookup (e.g. when listing a dialogue's translatable terms for editing).
	 *
	 * @return a whitespace-normalized string representation of the translatable segments.
	 */
	public String toNormalizedString() {
		String trimmed = toString().trim();
		if (trimmed.isEmpty())
			return trimmed;
		String[] words = trimmed.split("\\s+");
		return String.join(" ", words);
	}
}

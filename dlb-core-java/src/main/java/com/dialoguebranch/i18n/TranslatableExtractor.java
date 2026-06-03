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

package com.dialoguebranch.i18n;

import com.dialoguebranch.model.VariableString;
import com.dialoguebranch.model.command.Command;
import com.dialoguebranch.model.Node;
import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.Reply;
import com.dialoguebranch.model.command.IfCommand;
import com.dialoguebranch.model.command.InputCommand;
import com.dialoguebranch.model.command.RandomCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * This class can extract all translatable segments (plain text, variables and &lt;&lt;input&gt;&gt;
 * commands) from a {@link Node} or {@link NodeBody}. This includes translatables within "if" and
 * "random" commands and replies.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 */
public class TranslatableExtractor {

	public List<SourceTranslatable> extractFromNode(Node node) {
		return extractFromBody(node.getHeader().getSpeaker(),
				SourceTranslatable.USER, node.getBody());
	}

	public List<SourceTranslatable> extractFromBody(String speaker,
                                                    String addressee, NodeBody body) {
		List<SourceTranslatable> result = new ArrayList<>();
		List<NodeBody.Segment> current = new ArrayList<>();
		for (int i = 0; i < body.getSegments().size(); i++) {
			NodeBody.Segment segment = body.getSegments().get(i);
			if (segment instanceof NodeBody.TextSegment) {
				NodeBody.TextSegment textSegment =
						(NodeBody.TextSegment)segment;
				current.add(textSegment);
			} else if (segment instanceof NodeBody.CommandSegment) {
				NodeBody.CommandSegment cmdSegment =
						(NodeBody.CommandSegment)segment;
				Command cmd = cmdSegment.getCommand();
				if (cmd instanceof IfCommand) {
					IfCommand ifCmd = (IfCommand)cmd;
					finishCurrentTranslatableSegment(speaker, addressee, body,
							current, result);
					result.addAll(getTranslatableSegmentsFromIfCommand(speaker,
							addressee, ifCmd));
				} else if (cmd instanceof RandomCommand) {
					RandomCommand rndCmd = (RandomCommand)cmd;
					finishCurrentTranslatableSegment(speaker, addressee, body,
							current, result);
					result.addAll(getTranslatableSegmentsFromRandomCommand(
							speaker, addressee, rndCmd));
				} else if (cmd instanceof InputCommand) {
					current.add(segment);
				}
			}
		}
		finishCurrentTranslatableSegment(speaker, addressee, body, current,
				result);
		for (Reply reply : body.getReplies()) {
			if (reply.getStatement() != null) {
				result.addAll(extractFromBody(addressee, speaker,
						reply.getStatement()));
			}
		}
		return result;
	}

	private List<SourceTranslatable> getTranslatableSegmentsFromIfCommand(
			String speaker, String addressee, IfCommand ifCmd) {
		List<SourceTranslatable> result = new ArrayList<>();
		for (IfCommand.Clause clause : ifCmd.getIfClauses()) {
			result.addAll(extractFromBody(speaker, addressee,
					clause.getStatement()));
		}
		if (ifCmd.getElseClause() != null) {
			result.addAll(extractFromBody(speaker, addressee,
					ifCmd.getElseClause()));
		}
		return result;
	}

	private List<SourceTranslatable> getTranslatableSegmentsFromRandomCommand(
			String speaker, String addressee, RandomCommand rndCmd) {
		List<SourceTranslatable> result = new ArrayList<>();
		for (RandomCommand.Clause clause : rndCmd.getClauses()) {
			result.addAll(extractFromBody(speaker, addressee,
					clause.getStatement()));
		}
		return result;
	}

	private void finishCurrentTranslatableSegment(String speaker,
			String addressee, NodeBody parent,
			List<NodeBody.Segment> current,
			List<SourceTranslatable> translatables) {
		if (hasContent(current)) {
			List<NodeBody.Segment> segments = new ArrayList<>(current);
			SourceTranslatable translatable = new SourceTranslatable(
					speaker, addressee, new Translatable(parent, segments));
			translatables.add(translatable);
		}
		current.clear();
	}

	private boolean hasContent(List<NodeBody.Segment> segments) {
		for (NodeBody.Segment segment : segments) {
			if (segment instanceof NodeBody.TextSegment) {
				NodeBody.TextSegment textSegment =
						(NodeBody.TextSegment)segment;
				VariableString string = textSegment.getText();
				if (hasContent(string))
					return true;
			} else if (segment instanceof NodeBody.CommandSegment) {
				NodeBody.CommandSegment cmdSegment =
						(NodeBody.CommandSegment)segment;
				if (cmdSegment.getCommand() instanceof InputCommand)
					return true;
			}
		}
		return false;
	}

	private boolean hasContent(VariableString string) {
		return !string.getSegments().isEmpty() && string.hasContents();
	}

}

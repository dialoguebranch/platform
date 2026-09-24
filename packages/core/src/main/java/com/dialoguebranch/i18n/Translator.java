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

package com.dialoguebranch.i18n;

import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.NodeHeader;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.VariableString;
import com.dialoguebranch.model.execute.command.Command;
import com.dialoguebranch.model.execute.command.IfCommand;
import com.dialoguebranch.model.execute.command.InputCommand;
import com.dialoguebranch.model.execute.command.RandomCommand;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class can translate {@link Node}s given a translation map.
 * The translation map can be obtained from a translation file using the {@link
 * TranslationParser}.
 *
 * <p>Rebuilds every {@link NodeBody} it touches from scratch via {@link NodeBody.Builder} rather
 * than mutating the original in place — {@link NodeBody}/{@link Reply} are immutable, so there's
 * nothing to mutate. Translatable runs are identified and grouped exactly the way
 * {@link TranslatableExtractor} does (see {@link TranslatableExtractor#hasContent}), but matched
 * against a translation and rebuilt in the same recursive pass, rather than extracting a flat
 * list first and splicing each one back into a shared mutable tree second.</p>
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class Translator {
	private TranslationContext context;
	private Map<String,List<ContextTranslation>> exactTranslations;
	private Map<String,List<ContextTranslation>> normalizedTranslations;
	private Pattern preWhitespaceRegex;
	private Pattern postWhitespaceRegex;

	/**
	 * Constructs a new translator.
	 *
	 * @param context the translation context
	 * @param translations the translation map
	 */
	public Translator(TranslationContext context,
					  Map<Translatable,List<ContextTranslation>> translations) {
		this.context = context;
		this.exactTranslations = new LinkedHashMap<>();
		for (Translatable key : translations.keySet()) {
			this.exactTranslations.put(key.toString().trim(),
					translations.get(key));
		}
		this.normalizedTranslations = new LinkedHashMap<>();
		for (Translatable key : translations.keySet()) {
			this.normalizedTranslations.put(key.toNormalizedString(),
					translations.get(key));
		}
		preWhitespaceRegex = Pattern.compile("^\\s+");
		postWhitespaceRegex = Pattern.compile("\\s+$");
	}

	/**
	 * Translates the specified dialogue. This method creates a clone of the
	 * dialogue and then tries to fill in a translation for every translatable
	 * segment (plain text, variables and &lt;&lt;input&gt;&gt; commands).
	 *
	 * @param dialogue the dialogue
	 * @return the translated dialogue
	 */
	public Dialogue translate(Dialogue dialogue) {
		dialogue = new Dialogue(dialogue);
		for (Node node : dialogue.getNodes()) {
			NodeHeader header = Objects.requireNonNull(node.getHeader(), "Node has no header");
			NodeBody body = (NodeBody) Objects.requireNonNull(node.getBody(), "Node has no body");
			NodeBody translated = translateBody(header.getSpeaker(), SourceTranslatable.USER,
					body);
			dialogue.addNode(new Node(header, translated));
		}
		return dialogue;
	}

	/**
	 * Translates the specified node. This method creates a clone of the node
	 * and then tries to fill in a translation for every translatable segment
	 * (plain text, variables and &lt;&lt;input&gt;&gt; commands).
	 *
	 * @param node the node
	 * @return the translated node
	 */
	public Node translate(Node node) {
		node = new Node(node);
		NodeHeader header = Objects.requireNonNull(node.getHeader(), "Node has no header");
		NodeBody body = (NodeBody) Objects.requireNonNull(node.getBody(), "Node has no body");
		NodeBody translated = translateBody(header.getSpeaker(), SourceTranslatable.USER, body);
		return new Node(header, translated);
	}

	/**
	 * Rebuilds {@code body}, substituting a translation for every translatable run of segments
	 * found — recursing into {@code <<if>>}/{@code <<random>>} clause bodies (mutating their
	 * already-cloned {@code Clause.statement} in place, same as before this class was rewritten —
	 * only {@link NodeBody}/{@link Reply} themselves are immutable, {@code Clause} isn't) and
	 * rebuilding reply statements — and leaving everything else exactly as it was.
	 *
	 * @param speaker the name of the agent delivering the top-level statements in {@code body}.
	 * @param addressee the name of the agent being addressed at the top level.
	 * @param body the body to translate.
	 * @return the translated body.
	 */
	private NodeBody translateBody(@Nullable String speaker, @Nullable String addressee,
			NodeBody body) {
		NodeBody.Builder builder = new NodeBody.Builder();
		// "current" accumulates the text/input segments that make up the run currently being
		// matched against a translation; "interposed" buffers any other command segment (e.g.
		// "<<set>>") found in between, which doesn't itself get translated but must still appear
		// in the output — right after the run it interrupted resolves, same position it would
		// end up in were this a splice into a flat list rather than a rebuild.
		List<NodeBody.Segment> current = new ArrayList<>();
		List<NodeBody.Segment> interposed = new ArrayList<>();
		for (NodeBody.Segment segment : body.getSegments()) {
			if (segment instanceof NodeBody.TextSegment) {
				current.add(segment);
			} else {
				NodeBody.CommandSegment cmdSegment = (NodeBody.CommandSegment) segment;
				Command cmd = cmdSegment.getCommand();
				if (cmd instanceof IfCommand ifCmd) {
					flushRun(speaker, addressee, current, interposed, builder);
					translateIfCommand(speaker, addressee, ifCmd);
					builder.addSegment(segment);
				} else if (cmd instanceof RandomCommand rndCmd) {
					flushRun(speaker, addressee, current, interposed, builder);
					translateRandomCommand(speaker, addressee, rndCmd);
					builder.addSegment(segment);
				} else if (cmd instanceof InputCommand) {
					current.add(segment);
				} else {
					interposed.add(segment);
				}
			}
		}
		flushRun(speaker, addressee, current, interposed, builder);
		for (Reply reply : body.getReplies()) {
			builder.addReply(translateReply(addressee, speaker, reply));
		}
		return builder.build();
	}

	private void translateIfCommand(@Nullable String speaker, @Nullable String addressee,
			IfCommand ifCmd) {
		for (IfCommand.Clause clause : ifCmd.getIfClauses()) {
			clause.setStatement(translateBody(speaker, addressee, clause.getStatement()));
		}
		if (ifCmd.getElseClause() != null) {
			ifCmd.setElseClause(translateBody(speaker, addressee, ifCmd.getElseClause()));
		}
	}

	private void translateRandomCommand(@Nullable String speaker, @Nullable String addressee,
			RandomCommand rndCmd) {
		for (RandomCommand.Clause clause : rndCmd.getClauses()) {
			clause.setStatement(translateBody(speaker, addressee, clause.getStatement()));
		}
	}

	private Reply translateReply(@Nullable String speaker, @Nullable String addressee,
			Reply reply) {
		if (reply.isAutoForward())
			return reply;
		NodeBody statement = (NodeBody) Objects.requireNonNull(reply.getStatement());
		NodeBody translatedStatement = translateBody(speaker, addressee, statement);
		Reply.Builder builder = new Reply.Builder(reply.getReplyId(), translatedStatement,
				reply.getNodePointer());
		for (Command command : reply.getCommands()) {
			builder.addCommand(command);
		}
		return builder.build();
	}

	/**
	 * Resolves {@code current} (if it has any translatable content — see
	 * {@link TranslatableExtractor#hasContent}) against the translation map and appends the
	 * result to {@code builder}, followed by whatever was buffered in {@code interposed}. Both
	 * lists are cleared afterward, ready for the next run.
	 */
	private void flushRun(@Nullable String speaker, @Nullable String addressee,
			List<NodeBody.Segment> current, List<NodeBody.Segment> interposed,
			NodeBody.Builder builder) {
		List<NodeBody.Segment> resolved = TranslatableExtractor.hasContent(current)
				? resolveTranslation(speaker, addressee, current) : current;
		for (NodeBody.Segment segment : resolved) {
			builder.addSegment(segment);
		}
		for (NodeBody.Segment segment : interposed) {
			builder.addSegment(segment);
		}
		current.clear();
		interposed.clear();
	}

	/**
	 * Looks up a translation for {@code runSegments} and returns its replacement (leading/trailing
	 * whitespace preserved from the original), or {@code runSegments} itself unchanged if no
	 * translation is found.
	 */
	private List<NodeBody.Segment> resolveTranslation(@Nullable String speaker,
			@Nullable String addressee, List<NodeBody.Segment> runSegments) {
		Translatable translatable = new Translatable(new ArrayList<>(runSegments));
		String textPlain = translatable.toString();
		String preWhitespace = "";
		String postWhitespace = "";
		Matcher m = preWhitespaceRegex.matcher(textPlain);
		if (m.find())
			preWhitespace = m.group();
		m = postWhitespaceRegex.matcher(textPlain);
		if (m.find())
			postWhitespace = m.group();
		List<ContextTranslation> transList = exactTranslations.get(textPlain.trim());
		if (transList == null) {
			transList = normalizedTranslations.get(translatable.toNormalizedString());
		}
		if (transList == null)
			return runSegments;
		SourceTranslatable source = new SourceTranslatable(speaker, addressee, translatable);
		Translatable translation = findContextTranslation(source, transList);
		List<NodeBody.Segment> result = new ArrayList<>();
		if (!preWhitespace.isEmpty()) {
			result.add(new NodeBody.TextSegment(new VariableString(preWhitespace)));
		}
		result.addAll(translation.segments());
		if (!postWhitespace.isEmpty()) {
			result.add(new NodeBody.TextSegment(new VariableString(postWhitespace)));
		}
		return result;
	}

	private Translatable findContextTranslation(
			SourceTranslatable source,
			List<ContextTranslation> transList) {
		TranslationContext.Gender speakerGender = getGenderForSpeaker(
				source.speaker());
		TranslationContext.Gender addresseeGender = getGenderForSpeaker(
				source.addressee());
		List<ContextTranslation> prevFilter = transList;
		List<ContextTranslation> filtered = filterSpeaker(transList,
				source.speaker());
		if (filtered.isEmpty())
			filtered = prevFilter;
		prevFilter = filtered;
		filtered = filterGender(transList, speakerGender, addresseeGender);
		if (filtered.isEmpty())
			filtered = prevFilter;
		return filtered.get(0).translation();
	}

	private TranslationContext.Gender getGenderForSpeaker(@Nullable String speaker) {
		if (SourceTranslatable.USER.equals(speaker))
			return context.getUserGender();
		if (context.getAgentGenders().containsKey(speaker))
			return context.getAgentGenders().get(speaker);
		return context.getDefaultAgentGender();
	}

	private List<ContextTranslation> filterSpeaker(
			List<ContextTranslation> terms, @Nullable String speaker) {
		List<ContextTranslation> result = new ArrayList<>();
		if (speaker == null)
			return result; // no speaker context — caller falls back to the unfiltered list
		String speakerContext = getSpeakerContext(speaker);
		for (ContextTranslation term : terms) {
			if (term.context().contains(speakerContext))
				result.add(term);
		}
		return result;
	}

	private String getSpeakerContext(String speaker) {
		if (speaker.equals(SourceTranslatable.USER))
			return "_user";
		else
			return speaker;
	}

	private List<ContextTranslation> filterGender(
			List<ContextTranslation> terms,
			TranslationContext.Gender speakerGender,
			TranslationContext.Gender addresseeGender) {
		List<ContextTranslation> result = new ArrayList<>();
		if (speakerGender == null)
			speakerGender = TranslationContext.Gender.MALE;
		if (addresseeGender == null)
			addresseeGender = TranslationContext.Gender.MALE;
		for (ContextTranslation term : terms) {
			if (speakerGender == TranslationContext.Gender.MALE &&
					term.context().contains("female_speaker")) {
				continue;
			}
			if (addresseeGender == TranslationContext.Gender.MALE &&
					term.context().contains("female_addressee")) {
				continue;
			}
			if (speakerGender == TranslationContext.Gender.FEMALE &&
					term.context().contains("male_speaker")) {
				continue;
			}
			if (addresseeGender == TranslationContext.Gender.FEMALE &&
					term.context().contains("male_addressee")) {
				continue;
			}
			result.add(term);
		}
		return result;
	}
}

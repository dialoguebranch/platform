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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dialoguebranch.model.Dialogue;
import com.dialoguebranch.model.Node;
import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.VariableString;

/**
 * This class can translate {@link Node}s given a translation map.
 * The translation map can be obtained from a translation file using the {@link
 * TranslationParser}.
 *
 * @author Dennis Hofs (RRD)
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
			this.normalizedTranslations.put(getNormalizedText(key),
					translations.get(key));
		}
		preWhitespaceRegex = Pattern.compile("^\\s+");
		postWhitespaceRegex = Pattern.compile("\\s+$");
	}

	private String getNormalizedText(Translatable translatable) {
		String norm = translatable.toString().trim();
		if (norm.isEmpty())
			return norm;
		String[] words = norm.split("\\s+");
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			if (i > 0)
				result.append(" ");
			result.append(words[i]);
		}
		return result.toString();
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
			translateBody(node.getHeader().getSpeaker(),
					SourceTranslatable.USER, node.getBody());
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
		translateBody(node.getHeader().getSpeaker(),
				SourceTranslatable.USER, node.getBody());
		return node;
	}

	private void translateBody(String speaker, String addressee,
			NodeBody body) {
		TranslatableExtractor extractor = new TranslatableExtractor();
		List<SourceTranslatable> translatables = extractor.extractFromBody(
				speaker, addressee, body);
		for (SourceTranslatable translatable : translatables) {
			translateText(translatable);
		}
	}

	private void translateText(SourceTranslatable text) {
		String textPlain = text.translatable().toString();
		String preWhitespace = "";
		String postWhitespace = "";
		Matcher m = preWhitespaceRegex.matcher(textPlain);
		if (m.find())
			preWhitespace = m.group();
		m = postWhitespaceRegex.matcher(textPlain);
		if (m.find())
			postWhitespace = m.group();
		List<ContextTranslation> transList = exactTranslations.get(
				text.translatable().toString().trim());
		if (transList == null) {
			transList = normalizedTranslations.get(getNormalizedText(
					text.translatable()));
		}
		if (transList == null)
			return;
		Translatable translation = findContextTranslation(text, transList);
		NodeBody body = text.translatable().parent();
		List<NodeBody.Segment> bodySegments = new ArrayList<>(
				body.getSegments());
		List<NodeBody.Segment> textSegments = text.translatable()
				.segments();
		int insertIndex = body.getSegments().indexOf(textSegments.get(0));
		for (NodeBody.Segment segment : textSegments) {
			bodySegments.remove(segment);
		}
		if (preWhitespace.length() > 0) {
			bodySegments.add(insertIndex++, new NodeBody.TextSegment(
					new VariableString(preWhitespace)));
		}
		List<NodeBody.Segment> transSegments = translation.segments();
		for (NodeBody.Segment transSegment : transSegments) {
			bodySegments.add(insertIndex++, transSegment);
		}
		if (postWhitespace.length() > 0) {
			bodySegments.add(insertIndex, new NodeBody.TextSegment(
					new VariableString(postWhitespace)));
		}
		body.clearSegments();
		for (NodeBody.Segment segment : bodySegments) {
			body.addSegment(segment);
		}
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

	private TranslationContext.Gender getGenderForSpeaker(String speaker) {
		if (speaker.equals(SourceTranslatable.USER))
			return context.getUserGender();
		if (context.getAgentGenders().containsKey(speaker))
			return context.getAgentGenders().get(speaker);
		return context.getDefaultAgentGender();
	}

	private List<ContextTranslation> filterSpeaker(
            List<ContextTranslation> terms, String speaker) {
		List<ContextTranslation> result = new ArrayList<>();
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

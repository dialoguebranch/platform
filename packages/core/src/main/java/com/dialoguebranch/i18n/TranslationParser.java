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

import com.dialoguebranch.exception.LineNumberParseException;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.parser.BodyParser;
import com.dialoguebranch.execution.parser.BodyToken;
import com.dialoguebranch.execution.parser.BodyTokenizer;
import com.dialoguebranch.json.JsonMapper;
import com.dialoguebranch.model.execute.NodeBody;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class can parse a Dialogue Branch translation file. The file contains a JSON object of
 * speaker groups: each key is the name of a speaker, as a whole (#377), with {@link
 * com.dialoguebranch.model.common.DialogueBranchConstants#DLB_USER_SPEAKER} for the user's
 * replies, and each value is a JSON object with two types of key-value pairs:
 *
 * <ul>
 *   <li>Key and value are strings. The key is a translatable in one language, and the value is a
 *   translatable in another language. A translatable should be the string representation of a
 *   {@link Translatable}. That is a text that may include variables and &lt;&lt;input&gt;&gt;
 *   commands.</li>
 *   <li>The key is a string and the value is a JSON object: a context group. The key is a list of
 *   context names, separated by whitespace (for example {@code female_addressee}), and the value
 *   contains key-value pairs of these same two types. Context groups can be nested; an entry
 *   gets the contexts of every group it is in.</li>
 * </ul>
 *
 * <p>A translation at the top level, outside a speaker group, is a parse error: a translation
 * only applies to text of its own speaker, so it could never be used.</p>
 *
 * @author Dennis Hofs
 */
public class TranslationParser {

	/**
	 * Creates a new {@link TranslationParser}. All parse methods are static; this constructor
	 * exists only to satisfy JavaDoc tooling requirements.
	 */
	public TranslationParser() { }

	/**
	 * Parses a Dialogue Branch translation file from the given {@link URL} and returns a
	 * {@link TranslationParserResult} containing the parsed translations, any parse errors, and
	 * any warnings encountered during parsing.
	 *
	 * @param url the {@link URL} pointing to the translation JSON resource.
	 * @return a {@link TranslationParserResult} with the parse outcome.
	 * @throws IOException in case of an I/O error while opening or reading the resource.
	 */
	public static TranslationParserResult parse(URL url)
			throws IOException {
		try (InputStream input = url.openStream()) {
			return parse(input);
		}
	}

	/**
	 * Parses a Dialogue Branch translation file from the given {@link File} and returns a
	 * {@link TranslationParserResult} containing the parsed translations, any parse errors, and
	 * any warnings encountered during parsing.
	 *
	 * @param file the translation JSON {@link File} to parse.
	 * @return a {@link TranslationParserResult} with the parse outcome.
	 * @throws IOException in case of an I/O error while reading the file.
	 */
	public static TranslationParserResult parse(File file)
			throws IOException {
		try (InputStream input = new FileInputStream(file)) {
			return parse(input);
		}
	}

	/**
	 * Parses a Dialogue Branch translation file from the given {@link InputStream} (read as
	 * UTF-8) and returns a {@link TranslationParserResult} containing the parsed translations,
	 * any parse errors, and any warnings encountered during parsing.
	 *
	 * @param input the {@link InputStream} containing the translation JSON content.
	 * @return a {@link TranslationParserResult} with the parse outcome.
	 * @throws IOException in case of an I/O error while reading the stream.
	 */
	public static TranslationParserResult parse(InputStream input)
			throws IOException{
		return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
	}

	/**
	 * Parses a Dialogue Branch translation file from the given {@link Reader} and returns a
	 * {@link TranslationParserResult} containing the parsed translations, any parse errors, and
	 * any warnings encountered during parsing.
	 *
	 * <p>The reader is read to completion and its content is interpreted as a JSON object
	 * conforming to the POEditor key-value export format described in the class-level
	 * documentation.</p>
	 *
	 * @param reader the {@link Reader} providing the translation JSON content.
	 * @return a {@link TranslationParserResult} with the parse outcome.
	 * @throws IOException in case of an I/O error while reading from the reader.
	 */
	public static TranslationParserResult parse(Reader reader)
			throws IOException {
		TranslationParserResult result = new TranslationParserResult();
		Map<Translatable,List<ContextTranslation>> translations =
				new LinkedHashMap<>();
		StringWriter jsonWriter = new StringWriter();
		reader.transferTo(jsonWriter);
		String json = jsonWriter.toString();
		if (json.trim().isEmpty()) {
			result.getWarnings().add("Empty translation file");
			result.setTranslations(translations);
			return result;
		}
		Map<String,?> map;
		try {
			map = JsonMapper.parse(json, new TypeReference<Map<String, ?>>() {});
		} catch (ParseException ex) {
			result.getParseErrors().add(ex);
			return result;
		}
		parseSpeakerGroups(map, translations, result);
		if (result.getParseErrors().isEmpty())
			result.setTranslations(translations);
		return result;
	}

	private static void parseSpeakerGroups(Map<String,?> map,
			Map<Translatable,List<ContextTranslation>> translations,
			TranslationParserResult parseResult) {
		for (String key : map.keySet()) {
			Object value = map.get(key);
			String speaker = key.trim();
			if (value instanceof String) {
				parseResult.getParseErrors().add(new ParseException(String.format(
						"Translation \"%s\" has no speaker: put it inside the group of the " +
						"speaker it translates", key)));
			} else if (speaker.isEmpty()) {
				parseResult.getParseErrors().add(new ParseException(
						"Found a speaker group without a speaker name"));
			} else {
				Map<String,?> group = toMap(key, value, parseResult);
				if (group != null)
					parse(speaker, new LinkedHashSet<>(), group, translations, parseResult);
			}
		}
	}

	private static void parse(String speaker, Set<String> context, Map<String,?> map,
			Map<Translatable,List<ContextTranslation>> translations,
			TranslationParserResult parseResult) {
		for (String key : map.keySet()) {
			Object value = map.get(key);
			if (value instanceof String) {
				parseTranslatable(speaker, context, key, (String)value, translations,
						parseResult);
			} else {
				parseContextMap(speaker, context, key, value, translations, parseResult);
			}
		}
	}

	private static void parseTranslatable(String speaker, Set<String> context, String key,
			String value,
			Map<Translatable,List<ContextTranslation>> translations,
			TranslationParserResult parseResult) {
		boolean success = true;
		Translatable source = null;
		try {
			source = parseTranslationString(key);
		} catch (ParseException ex) {
			parseResult.getParseErrors().add(new ParseException(String.format(
					"Failed to parse translation key \"%s\"", key) + ": " +
					ex.getMessage(), ex));
			success = false;
		}
		if (source != null) {
			try {
				checkDuplicateTranslation(source, speaker, context, translations);
			} catch (ParseException ex) {
				parseResult.getParseErrors().add(ex);
				success = false;
			}
		}
		if (value.trim().isEmpty()) {
			parseResult.getWarnings().add(String.format(
					"Empty translation value for key \"%s\"", key));
			return;
		}
		Translatable transValue = null;
		try {
			transValue = parseTranslationString(value);
		} catch (ParseException ex) {
			parseResult.getParseErrors().add(new ParseException(String.format(
					"Failed to parse translation value for key \"%s\"", key) +
					": " + value + ": " + ex.getMessage(), ex));
			success = false;
		}
		if (success && transValue != null) {
			List<ContextTranslation> transList = translations.get(source);
			if (transList == null) {
				transList = new ArrayList<>();
				translations.put(source, transList);
			}
			transList.add(new ContextTranslation(speaker, context, transValue));
		}
	}

	private static void checkDuplicateTranslation(Translatable source, String speaker,
												  Set<String> context,
												  Map<Translatable,List<ContextTranslation>> translations)
			throws ParseException {
		if (!translations.containsKey(source))
			return;
		List<ContextTranslation> sourceTrans = translations.get(source);
		for (ContextTranslation trans : sourceTrans) {
			if (trans.speaker().equals(speaker) && trans.context().equals(context)) {
				throw new ParseException(String.format(
						"Found duplicate translation \"%s\" for speaker \"%s\" with context %s",
						source, speaker, context));
			}
		}
	}

	private static void parseContextMap(String speaker, Set<String> outerContext, String key,
			Object value, Map<Translatable,List<ContextTranslation>> translations,
			TranslationParserResult parseResult) {
		Set<String> context = new LinkedHashSet<>(outerContext);
		String contextListStr = key.trim();
		if (!contextListStr.isEmpty()) {
			String[] contextList = contextListStr.split("\\s+");
			Collections.addAll(context, contextList);
		}
		Map<String,?> map = toMap(key, value, parseResult);
		if (map != null)
			parse(speaker, context, map, translations, parseResult);
	}

	/**
	 * Returns the value of a group as a map, or {@code null} after adding a parse error if it
	 * isn't one.
	 */
	private static @Nullable Map<String,?> toMap(String key, Object value,
			TranslationParserResult parseResult) {
		try {
			Map<String,?> map = JsonMapper.convert(value, new TypeReference<Map<String, ?>>() {});
			if (map == null)
				throw new ParseException("not a group of translations");
			return map;
		} catch (ParseException ex) {
			parseResult.getParseErrors().add(new ParseException(
					"Failed to parse translation map after key \"" +
					key + "\": " + ex.getMessage(), ex));
			return null;
		}
	}

	private static Translatable parseTranslationString(String translation)
			throws ParseException {
		BodyTokenizer tokenizer = new BodyTokenizer();
		List<BodyToken> tokens;
		try {
			tokens = tokenizer.readBodyTokens(translation, 1);
		} catch (LineNumberParseException ex) {
			throw new ParseException(
					"Invalid translation string: " + translation +
					": " + ex.getError());
		}
		// A bare translation string has no node context; BodyParser rejects any reply it finds
		// (BodyParser.parseUntilCommandClause) rather than dereferencing the null NodeState.
		@SuppressWarnings("NullAway")
		BodyParser parser = new BodyParser(null);
		NodeBody body;
		try {
			body = parser.parse(tokens, Collections.singletonList("input"));
		} catch (LineNumberParseException ex) {
			throw new ParseException(
					"Invalid translation string: " + translation +
					": " + ex.getError());
		}
		TranslatableExtractor extractor = new TranslatableExtractor();
		List<SourceTranslatable> sourceTranslatables =
				extractor.extractFromBody(null, null, body);
		List<Translatable> translatables = new ArrayList<>();
		for (SourceTranslatable sourceTranslatable : sourceTranslatables) {
			translatables.add(sourceTranslatable.translatable());
		}
		if (translatables.size() == 0) {
			throw new ParseException(
					"Invalid translation string: " + translation +
					": No translatable text found");
		}
		if (translatables.size() != 1) {
			throw new ParseException(
					"Invalid translation string: " + translation +
					": Multiple translatable texts found");
		}
		return translatables.get(0);
	}

}

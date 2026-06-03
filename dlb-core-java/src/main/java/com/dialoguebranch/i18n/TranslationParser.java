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

import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.parser.BodyToken;
import com.dialoguebranch.parser.BodyTokenizer;
import com.fasterxml.jackson.core.type.TypeReference;
import nl.rrd.utils.exception.LineNumberParseException;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.io.FileUtils;
import nl.rrd.utils.json.JsonMapper;
import com.dialoguebranch.parser.BodyParser;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class can parse a DialogueBranch translation file. The file should contain a
 * JSON object with key-value pairs as exported by POEditor. There are two types
 * of key-value pairs:
 *
 * <ul>
 *   <li>Key and value are strings. The key is a translatable in one language, and the value is a
 *   translatable in another language. A translatable should be the string representation of a
 *   {@link Translatable}. That is a text that may include variables and &lt;&lt;input&gt;&gt;
 *   commands.</li>
 *   <li>The key is a string the value is a JSON object. In this case the key is a context string,
 *   and the value contains translatable key-value pairs.</li>
 * </ul>
 *
 * <p>This parser ignores context strings and returns a flat map of translatables. This means that
 * it does not support different translations of the same string with different contexts.</p>
 *
 * @author Dennis Hofs (RRD)
 */
public class TranslationParser {
	public static TranslationParserResult parse(URL url)
			throws IOException {
		try (InputStream input = url.openStream()) {
			return parse(input);
		}
	}

	public static TranslationParserResult parse(File file)
			throws IOException {
		try (InputStream input = new FileInputStream(file)) {
			return parse(input);
		}
	}

	public static TranslationParserResult parse(InputStream input)
			throws IOException{
		return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
	}

	public static TranslationParserResult parse(Reader reader)
			throws IOException {
		TranslationParserResult result = new TranslationParserResult();
		Map<Translatable,List<ContextTranslation>> translations =
				new LinkedHashMap<>();
		String json = FileUtils.readFileString(reader);
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
		parse(new LinkedHashSet<>(), map, translations, result);
		if (result.getParseErrors().isEmpty())
			result.setTranslations(translations);
		return result;
	}

	private static void parse(Set<String> context, Map<String,?> map,
			Map<Translatable,List<ContextTranslation>> translations,
			TranslationParserResult parseResult) {
		for (String key : map.keySet()) {
			Object value = map.get(key);
			if (value instanceof String) {
				parseTranslatable(context, key, (String)value, translations,
						parseResult);
			} else {
				parseContextMap(key, value, translations, parseResult);
			}
		}
	}

	private static void parseTranslatable(Set<String> context, String key,
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
				checkDuplicateTranslation(source, context, translations);
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
		if (success) {
			List<ContextTranslation> transList = translations.get(source);
			if (transList == null) {
				transList = new ArrayList<>();
				translations.put(source, transList);
			}
			transList.add(new ContextTranslation(context, transValue));
		}
	}

	private static void checkDuplicateTranslation(Translatable source,
												  Set<String> context,
												  Map<Translatable,List<ContextTranslation>> translations)
			throws ParseException {
		if (!translations.containsKey(source))
			return;
		List<ContextTranslation> sourceTrans = translations.get(source);
		for (ContextTranslation trans : sourceTrans) {
			if (trans.context().equals(context)) {
				throw new ParseException(String.format(
						"Found duplicate translation \"%s\" with context %s",
						source, context));
			}
		}
	}

	private static void parseContextMap(String key, Object value,
			Map<Translatable,List<ContextTranslation>> translations,
			TranslationParserResult parseResult) {
		String contextListStr = key.trim();
		Set<String> context = new LinkedHashSet<>();
		if (!contextListStr.isEmpty()) {
			String[] contextList = contextListStr.split("\\s+");
			Collections.addAll(context, contextList);
		}
		Map<String,?> map;
		try {
			map = JsonMapper.convert(value,
					new TypeReference<Map<String, ?>>() {});
		} catch (ParseException ex) {
			parseResult.getParseErrors().add(new ParseException(
					"Failed to parse translation map after context key \"" +
					key + "\": " + ex.getMessage(), ex));
			return;
		}
		parse(context, map, translations, parseResult);
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

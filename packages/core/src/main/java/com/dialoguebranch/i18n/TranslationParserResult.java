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

import nl.rrd.utils.exception.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of parsing a Dialogue Branch translation file via {@link TranslationParser}.
 *
 * <p>After a successful parse, {@link #getTranslations()} returns a map from each source
 * {@link Translatable} to the list of context-specific {@link ContextTranslation}s that provide
 * its translations. If fatal parse errors were encountered the translations map is {@code null}
 * and {@link #getParseErrors()} is non-empty. Non-fatal issues (e.g. empty values) are reported
 * as {@link #getWarnings() warnings} without preventing the translations from being returned.</p>
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class TranslationParserResult {

	private Map<Translatable,List<ContextTranslation>> translations = null;
	private List<ParseException> parseErrors = new ArrayList<>();
	private List<String> warnings = new ArrayList<>();

	/**
	 * Returns the parsed translation map, or {@code null} if fatal parse errors were encountered.
	 * The map keys are source-language {@link Translatable}s; each value is the ordered list of
	 * {@link ContextTranslation}s providing context-specific target translations.
	 *
	 * @return the translation map, or {@code null} if parsing failed.
	 */
	public Map<Translatable,List<ContextTranslation>> getTranslations() {
		return translations;
	}

	/**
	 * Sets the translation map produced by the parser.
	 *
	 * @param translations the translation map, or {@code null} to indicate a failed parse.
	 */
	public void setTranslations(
			Map<Translatable,List<ContextTranslation>> translations) {
		this.translations = translations;
	}

	/**
	 * Returns the list of {@link ParseException}s encountered during parsing. A non-empty list
	 * indicates fatal errors; the translations map will be {@code null} in that case.
	 *
	 * @return the list of parse errors (never {@code null}, may be empty).
	 */
	public List<ParseException> getParseErrors() {
		return parseErrors;
	}

	/**
	 * Sets the list of parse errors.
	 *
	 * @param parseErrors the list of {@link ParseException}s encountered during parsing.
	 */
	public void setParseErrors(List<ParseException> parseErrors) {
		this.parseErrors = parseErrors;
	}

	/**
	 * Returns the list of non-fatal warning messages produced during parsing (e.g. empty
	 * translation values or an empty file). Translations may still be returned even when warnings
	 * are present.
	 *
	 * @return the list of warning messages (never {@code null}, may be empty).
	 */
	public List<String> getWarnings() {
		return warnings;
	}

	/**
	 * Sets the list of warning messages.
	 *
	 * @param warnings the list of warning messages produced during parsing.
	 */
	public void setWarnings(List<String> warnings) {
		this.warnings = warnings;
	}

}

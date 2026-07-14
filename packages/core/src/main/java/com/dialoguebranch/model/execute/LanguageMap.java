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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link LanguageMap} describes a Dialogue Branch project's language configuration: exactly one
 * source {@link Language} (the language its {@code .dlb} scripts are written in) and zero or more
 * translation {@link Language}s (for which {@code .json} translation files may exist).
 *
 * @author Harm op den Akker
 */
public class LanguageMap {

	private Language sourceLanguage;
	private List<Language> translationLanguages;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link LanguageMap}, with no source language and no
	 * translation languages.
	 */
	public LanguageMap() {
		translationLanguages = new ArrayList<>();
	}

	/**
	 * Creates an instance of a {@link LanguageMap} with a given {@code sourceLanguage} and no
	 * translation languages.
	 *
	 * @param sourceLanguage the source {@link Language} of this {@link LanguageMap}.
	 */
	public LanguageMap(Language sourceLanguage) {
		this.sourceLanguage = sourceLanguage;
		this.translationLanguages = new ArrayList<>();
	}

	/**
	 * Creates an instance of a {@link LanguageMap} with a given {@code sourceLanguage} and list of
	 * {@code translationLanguages}. If the given list of translation languages is {@code null}, an
	 * empty list will be set instead.
	 *
	 * @param sourceLanguage the source {@link Language} of this {@link LanguageMap}.
	 * @param translationLanguages a list of translation {@link Language}s for this
	 *                              {@link LanguageMap}.
	 */
	public LanguageMap(Language sourceLanguage, List<Language> translationLanguages) {
		this.sourceLanguage = sourceLanguage;
		this.translationLanguages = Objects.requireNonNullElseGet(
				translationLanguages, ArrayList::new);
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the source {@link Language} of this {@link LanguageMap}, or {@code null} if none has
	 * been set.
	 *
	 * @return the source language of this {@link LanguageMap}.
	 */
	public Language getSourceLanguage() {
		return sourceLanguage;
	}

	/**
	 * Sets the source {@link Language} of this {@link LanguageMap}.
	 *
	 * @param sourceLanguage the source language of this {@link LanguageMap}.
	 */
	public void setSourceLanguage(Language sourceLanguage) {
		this.sourceLanguage = sourceLanguage;
	}

	/**
	 * Returns the {@link List} of translation {@link Language}s in this {@link LanguageMap}.
	 *
	 * @return the list of translation languages in this {@link LanguageMap}.
	 */
	public List<Language> getTranslationLanguages() {
		return translationLanguages;
	}

	/**
	 * Sets the {@link List} of translation {@link Language}s for this {@link LanguageMap}. If the
	 * given {@code translationLanguages} is {@code null}, the current list will be set to an empty
	 * list.
	 *
	 * @param translationLanguages the list of translation languages for this {@link LanguageMap}.
	 */
	public void setTranslationLanguages(List<Language> translationLanguages) {
		this.translationLanguages = Objects.requireNonNullElseGet(
				translationLanguages, ArrayList::new);
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Adds the given {@link Language} to the list of translation languages in this
	 * {@link LanguageMap}, unless {@code translationLanguage} is {@code null}, in which case this
	 * method does nothing.
	 *
	 * @param translationLanguage the translation {@link Language} to add to this
	 *                            {@link LanguageMap}.
	 */
	public void addTranslationLanguage(Language translationLanguage) {
		if (translationLanguage != null)
			translationLanguages.add(translationLanguage);
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder("LanguageMap: \n");
		if (sourceLanguage != null)
			result.append("[SourceLanguage:").append(sourceLanguage).append("]\n");
		for (Language language : translationLanguages) {
			result.append(language).append("\n");
		}
		return result.toString();
	}

}

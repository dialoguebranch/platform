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

package com.dialoguebranch.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link LanguageSet} is a mapping from a single source {@link Language} to a list of translation
 * {@link Language}s.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class LanguageSet {

	private Language sourceLanguage;
	private List<Language> translationLanguages;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of an empty {@link LanguageSet}.
	 */
	public LanguageSet() {
		this.translationLanguages = new ArrayList<>();
	}

	/**
	 * Creates an instance of a {@link LanguageSet} with a given defined {@code sourceLanguage}, but
	 * no translation language(s).
	 *
	 * @param sourceLanguage the source {@link Language} of this {@link LanguageSet}.
	 */
	public LanguageSet(Language sourceLanguage) {
		this.sourceLanguage = sourceLanguage;
		this.translationLanguages = new ArrayList<>();
	}

	/**
	 * Creates an instance of a {@link LanguageSet} with a given {@code sourceLanguage} and list of
	 * {@code translationLanguages}.
	 *
	 * @param sourceLanguage the source {@link Language} of this {@link LanguageSet}.
	 * @param translationLanguages a list of translation {@link Language}s mapped to the given
	 *                             {@code sourceLanguage}.
	 */
	public LanguageSet(Language sourceLanguage, List<Language> translationLanguages) {
		this.sourceLanguage = sourceLanguage;
		this.translationLanguages = Objects.requireNonNullElseGet(translationLanguages,ArrayList::new);
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the source language of this {@link LanguageMap}.
	 * @return the source language of this {@link LanguageMap}.
	 */
	public Language getSourceLanguage() {
		return sourceLanguage;
	}

	/**
	 * Sets the source language for this {@link LanguageMap}.
	 * @param sourceLanguage the source language for this {@link LanguageMap}.
	 */
	public void setSourceLanguage(Language sourceLanguage) {
		this.sourceLanguage = sourceLanguage;
	}

	/**
	 * Returns the {@link List} of translation {@link Language}s for this {@link LanguageSet}.
	 * @return the {@link List} of translation {@link Language}s for this {@link LanguageSet}.
	 */
	public List<Language> getTranslationLanguages() {
		return translationLanguages;
	}

	/**
	 * Sets the {@link List} of translation {@link Language}s for this {@link LanguageSet}.
	 * @param translationLanguages the {@link List} of translation {@link Language}s for this
	 *                             {@link LanguageSet}.
	 */
	public void setTranslationLanguages(List<Language> translationLanguages) {
		this.translationLanguages = translationLanguages;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	/**
	 * Add the given {@code translationLanguage} to the {@link List} of translation
	 * {@link Language}s for this {@link LanguageSet}.
	 * @param translationLanguage the translation {@link Language} to add.
	 */
	public void addTranslationLanguage(Language translationLanguage) {
		translationLanguages.add(translationLanguage);
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder("LanguageSet: \n");
		result.append("[SourceLanguage:").append(sourceLanguage.toString()).append("]\n");
		for(Language language : translationLanguages) {
			result.append(language.toString()).append("\n");
		}
		return result.toString();
	}

}

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

/**
 * A {@link TranslationTerm} models a piece of text and a description of its context that can is
 * used by the {@link POEditorTools} class for generating files that can be consumed by POEditor.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class TranslationTerm {

	/** The actual term (string) that needs to be translated. */
	public String term;

	/** A description of the context in which the term occurs. */
	public String context;

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of an empty {@link TranslationTerm}.
	 */
	public TranslationTerm() {}

	/**
	 * Creates an instance of a {@link TranslationTerm} with given {@code term} and {@code context}.
	 * @param term the actual text to translate.
	 * @param context a description of the context in which this text is found.
	 */
	public TranslationTerm(String term, String context) {
		this.term = term;
		this.context = context;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the actual term to be translated.
	 * @return the actual term to be translated.
	 */
	public String getTerm () {
		return this.term;
	}

	/**
	 * Sets the term to be translated.
	 * @param term the term to be translated.
	 */
	public void setTerm (String term) {
		this.term = term;
	}

	/**
	 * Returns the description of the context in which this the term occurs.
	 * @return the description of the context in which this the term occurs.
	 */
	public String getContext() {
		return this.context;
	}

	/**
	 * Sets the description of the context in which this the term occurs.
	 * @param context the description of the context in which this the term occurs.
	 */
	public void setContext(String context) {
		this.context = context;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	@Override
	public String toString() {
		return "Term: '" + this.term + "' (context: '" + this.context + "').";
	}

}

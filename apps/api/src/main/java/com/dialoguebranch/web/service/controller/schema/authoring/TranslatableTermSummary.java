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

package com.dialoguebranch.web.service.controller.schema.authoring;

/**
 * A single translatable term extracted from a draft dialogue's current content, as returned by the
 * {@code /list-translatable-terms} end-point. The {@code (speaker, term)} pair matches the key
 * structure of a translation file's content map (see {@code TranslationFile} in {@code
 * packages/core}), so it can be used directly to look up or store a translation for this term.
 *
 * @author Harm op den Akker
 */
public class TranslatableTermSummary {

	private final String speaker;
	private final String term;

	/**
	 * Creates a {@link TranslatableTermSummary}.
	 *
	 * @param speaker the name of the speaker delivering (or, for a reply, receiving) this term, or
	 *                {@code "_user"} when it belongs to the end-user.
	 * @param term    the whitespace-normalized source-language text of the term (see {@code
	 *                Translatable.toNormalizedString()} in {@code packages/core}).
	 */
	public TranslatableTermSummary(String speaker, String term) {
		this.speaker = speaker;
		this.term = term;
	}

	/**
	 * @return the name of the speaker this term belongs to, or {@code "_user"} for the end-user.
	 */
	public String getSpeaker() {
		return speaker;
	}

	/**
	 * @return the whitespace-normalized source-language text of the term (see {@code
	 *         Translatable.toNormalizedString()} in {@code packages/core}).
	 */
	public String getTerm() {
		return term;
	}

}

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

import java.util.UUID;

/**
 * One entry of the {@code updateLanguages} list in {@link UpdateProjectDraftPayload}: the new
 * name/code for an existing draft translation language, identified by id.
 *
 * @author Harm op den Akker
 */
public class UpdateDraftLanguagePayload {
	private UUID id;
	private String translationLanguageName;
	private String translationLanguageCode;

	/**
	 * @return the id of the draft translation language to update.
	 */
	public UUID getId() { return id; }

	/**
	 * @param id the id of the draft translation language to update.
	 */
	public void setId(UUID id) { this.id = id; }

	/**
	 * @return the new human-readable name for the translation language.
	 */
	public String getTranslationLanguageName() { return translationLanguageName; }

	/**
	 * @param translationLanguageName the new human-readable name for the translation language.
	 */
	public void setTranslationLanguageName(String translationLanguageName) { this.translationLanguageName = translationLanguageName; }

	/**
	 * @return the new language code (e.g. {@code "en"}) for the translation language.
	 */
	public String getTranslationLanguageCode() { return translationLanguageCode; }

	/**
	 * @param translationLanguageCode the new language code (e.g. {@code "en"}) for the
	 *                                 translation language.
	 */
	public void setTranslationLanguageCode(String translationLanguageCode) { this.translationLanguageCode = translationLanguageCode; }
}

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
 * Request payload for creating a new project.
 *
 * @author Harm op den Akker
 */
public class CreateProjectPayload {
	private String slug;
	private String displayName;
	private String description;
	private String defaultLanguageCode;
	private String defaultLanguageName;

	/**
	 * @return the unique slug identifying the project to create.
	 */
	public String getSlug() { return slug; }

	/**
	 * @param slug the unique slug identifying the project to create.
	 */
	public void setSlug(String slug) { this.slug = slug; }

	/**
	 * @return the human-readable display name of the project to create.
	 */
	public String getDisplayName() { return displayName; }

	/**
	 * @param displayName the human-readable display name of the project to create.
	 */
	public void setDisplayName(String displayName) { this.displayName = displayName; }

	/**
	 * @return the description of the project to create.
	 */
	public String getDescription() { return description; }

	/**
	 * @param description the description of the project to create.
	 */
	public void setDescription(String description) { this.description = description; }

	/**
	 * @return the language code (e.g. {@code "en"}) of the project's default (source) language.
	 */
	public String getDefaultLanguageCode() { return defaultLanguageCode; }

	/**
	 * @param defaultLanguageCode the language code (e.g. {@code "en"}) of the project's default
	 *                             (source) language.
	 */
	public void setDefaultLanguageCode(String defaultLanguageCode) { this.defaultLanguageCode = defaultLanguageCode; }

	/**
	 * @return the human-readable name of the project's default (source) language.
	 */
	public String getDefaultLanguageName() { return defaultLanguageName; }

	/**
	 * @param defaultLanguageName the human-readable name of the project's default (source)
	 *                             language.
	 */
	public void setDefaultLanguageName(String defaultLanguageName) { this.defaultLanguageName = defaultLanguageName; }
}

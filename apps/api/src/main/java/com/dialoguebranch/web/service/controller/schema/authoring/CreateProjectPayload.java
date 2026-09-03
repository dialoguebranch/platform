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

package com.dialoguebranch.web.service.controller.schema.authoring;

/**
 * Request payload for creating a new project.
 *
 * @author Harm op den Akker
 */
public class CreateProjectPayload {

	/** Creates a new, empty {@link CreateProjectPayload}. */
	public CreateProjectPayload() { }

	private String slug;
	private String displayName;
	private String description;
	private String sourceLanguageCode;
	private String sourceLanguageName;

	/**
	 * Returns the unique slug identifying the project to create.
	 *
	 * @return the unique slug identifying the project to create.
	 */
	public String getSlug() { return slug; }

	/**
	 * Sets the unique slug identifying the project to create.
	 *
	 * @param slug the unique slug identifying the project to create.
	 */
	public void setSlug(String slug) { this.slug = slug; }

	/**
	 * Returns the human-readable display name of the project to create.
	 *
	 * @return the human-readable display name of the project to create.
	 */
	public String getDisplayName() { return displayName; }

	/**
	 * Sets the human-readable display name of the project to create.
	 *
	 * @param displayName the human-readable display name of the project to create.
	 */
	public void setDisplayName(String displayName) { this.displayName = displayName; }

	/**
	 * Returns the description of the project to create.
	 *
	 * @return the description of the project to create.
	 */
	public String getDescription() { return description; }

	/**
	 * Sets the description of the project to create.
	 *
	 * @param description the description of the project to create.
	 */
	public void setDescription(String description) { this.description = description; }

	/**
	 * Returns the language code (e.g. {@code "en"}) of the project's source language.
	 *
	 * @return the language code (e.g. {@code "en"}) of the project's source language.
	 */
	public String getSourceLanguageCode() { return sourceLanguageCode; }

	/**
	 * Sets the language code (e.g. {@code "en"}) of the project's source language.
	 *
	 * @param sourceLanguageCode the language code (e.g. {@code "en"}) of the project's source
	 *                           language.
	 */
	public void setSourceLanguageCode(String sourceLanguageCode) { this.sourceLanguageCode = sourceLanguageCode; }

	/**
	 * Returns the human-readable name of the project's source language.
	 *
	 * @return the human-readable name of the project's source language.
	 */
	public String getSourceLanguageName() { return sourceLanguageName; }

	/**
	 * Sets the human-readable name of the project's source language.
	 *
	 * @param sourceLanguageName the human-readable name of the project's source language.
	 */
	public void setSourceLanguageName(String sourceLanguageName) { this.sourceLanguageName = sourceLanguageName; }
}

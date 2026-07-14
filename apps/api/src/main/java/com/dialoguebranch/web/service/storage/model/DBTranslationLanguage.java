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

package com.dialoguebranch.web.service.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * JPA entity representing a single translation language of a project in the
 * {@code project_translation_languages} table. A project's source language is a fixed property of
 * {@link DBProject} itself ({@link DBProject#getSourceLanguageCode()}); this entity only records
 * the zero-or-more additional languages that project's dialogues have (or will have) {@code .json}
 * translation files for.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "project_translation_languages",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_project_translation_languages",
			columnNames = { "project_id", "translation_language_code" }
		)
	}
)
public class DBTranslationLanguage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private DBProject project;

	@Column(name = "translation_language_name", nullable = false, length = 64)
	private String translationLanguageName;

	@Column(name = "translation_language_code", nullable = false, length = 16)
	private String translationLanguageCode;

	/**
	 * Creates an empty instance of {@link DBTranslationLanguage}.
	 */
	public DBTranslationLanguage() {
	}

	/**
	 * Returns the unique UUID identifier of this translation language.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this translation language.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProject} this translation language belongs to.
	 *
	 * @return the owning project.
	 */
	public DBProject getProject() {
		return project;
	}

	/**
	 * Sets the {@link DBProject} this translation language belongs to.
	 *
	 * @param project the owning project.
	 */
	public void setProject(DBProject project) {
		this.project = project;
	}

	/**
	 * Returns the human-readable name of this translation language (e.g. {@code "Dutch"}).
	 *
	 * @return the translation language name.
	 */
	public String getTranslationLanguageName() {
		return translationLanguageName;
	}

	/**
	 * Sets the human-readable name of this translation language.
	 *
	 * @param translationLanguageName the translation language name.
	 */
	public void setTranslationLanguageName(String translationLanguageName) {
		this.translationLanguageName = translationLanguageName;
	}

	/**
	 * Returns the code of this translation language (e.g. {@code "nl"}).
	 *
	 * @return the translation language code.
	 */
	public String getTranslationLanguageCode() {
		return translationLanguageCode;
	}

	/**
	 * Sets the code of this translation language.
	 *
	 * @param translationLanguageCode the translation language code.
	 */
	public void setTranslationLanguageCode(String translationLanguageCode) {
		this.translationLanguageCode = translationLanguageCode;
	}

}

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
 * JPA entity representing a single source-to-translation language mapping in the
 * {@code project_language_mappings} table. The full set of mappings for a project corresponds to
 * a {@link com.dialoguebranch.model.execute.LanguageMap}, where rows sharing the same
 * {@code sourceLanguage} form a single {@link com.dialoguebranch.model.execute.LanguageSet}.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "project_language_mappings",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_project_language_mappings",
			columnNames = { "project_id", "source_language_code", "translation_language_code" }
		)
	}
)
public class DBProjectLanguageMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private DBProject project;

	@Column(name = "source_language_name", nullable = false, length = 64)
	private String sourceLanguageName;

	@Column(name = "source_language_code", nullable = false, length = 16)
	private String sourceLanguageCode;

	@Column(name = "translation_language_name", nullable = false, length = 64)
	private String translationLanguageName;

	@Column(name = "translation_language_code", nullable = false, length = 16)
	private String translationLanguageCode;

	/**
	 * Creates an empty instance of {@link DBProjectLanguageMapping}.
	 */
	public DBProjectLanguageMapping() {
	}

	/**
	 * Returns the unique UUID identifier of this language mapping.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this language mapping.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProject} this language mapping belongs to.
	 *
	 * @return the owning project.
	 */
	public DBProject getProject() {
		return project;
	}

	/**
	 * Sets the {@link DBProject} this language mapping belongs to.
	 *
	 * @param project the owning project.
	 */
	public void setProject(DBProject project) {
		this.project = project;
	}

	/**
	 * Returns the human-readable name of the source language (e.g. {@code "English"}).
	 *
	 * @return the source language name.
	 */
	public String getSourceLanguageName() {
		return sourceLanguageName;
	}

	/**
	 * Sets the human-readable name of the source language.
	 *
	 * @param sourceLanguageName the source language name.
	 */
	public void setSourceLanguageName(String sourceLanguageName) {
		this.sourceLanguageName = sourceLanguageName;
	}

	/**
	 * Returns the code of the source language (e.g. {@code "en"}).
	 *
	 * @return the source language code.
	 */
	public String getSourceLanguageCode() {
		return sourceLanguageCode;
	}

	/**
	 * Sets the code of the source language.
	 *
	 * @param sourceLanguageCode the source language code.
	 */
	public void setSourceLanguageCode(String sourceLanguageCode) {
		this.sourceLanguageCode = sourceLanguageCode;
	}

	/**
	 * Returns the human-readable name of the translation language (e.g. {@code "Dutch"}).
	 *
	 * @return the translation language name.
	 */
	public String getTranslationLanguageName() {
		return translationLanguageName;
	}

	/**
	 * Sets the human-readable name of the translation language.
	 *
	 * @param translationLanguageName the translation language name.
	 */
	public void setTranslationLanguageName(String translationLanguageName) {
		this.translationLanguageName = translationLanguageName;
	}

	/**
	 * Returns the code of the translation language (e.g. {@code "nl"}).
	 *
	 * @return the translation language code.
	 */
	public String getTranslationLanguageCode() {
		return translationLanguageCode;
	}

	/**
	 * Sets the code of the translation language.
	 *
	 * @param translationLanguageCode the translation language code.
	 */
	public void setTranslationLanguageCode(String translationLanguageCode) {
		this.translationLanguageCode = translationLanguageCode;
	}

}

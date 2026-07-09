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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity representing a single language set in the {@code language_sets} table. A project
 * has one or more language sets; each has exactly one source language and zero or more
 * {@link DBLanguageSetTranslation}s. Exactly one of a project's language sets is designated as
 * its default (see {@link DBProject#getDefaultLanguageSet()}).
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "language_sets",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_language_sets",
			columnNames = { "project_id", "source_language_code" }
		)
	}
)
public class DBLanguageSet {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private DBProject project;

	@Column(name = "source_language_code", nullable = false, length = 16)
	private String sourceLanguageCode;

	@Column(name = "source_language_name", nullable = false, length = 64)
	private String sourceLanguageName;

	@OneToMany(mappedBy = "languageSet", fetch = FetchType.EAGER)
	private Set<DBLanguageSetTranslation> translations = new HashSet<>();

	/**
	 * Creates an empty instance of {@link DBLanguageSet}.
	 */
	public DBLanguageSet() {
	}

	/**
	 * Returns the unique UUID identifier of this language set.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this language set.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProject} this language set belongs to.
	 *
	 * @return the owning project.
	 */
	public DBProject getProject() {
		return project;
	}

	/**
	 * Sets the {@link DBProject} this language set belongs to.
	 *
	 * @param project the owning project.
	 */
	public void setProject(DBProject project) {
		this.project = project;
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
	 * Returns the set of {@link DBLanguageSetTranslation}s belonging to this language set. May be
	 * empty.
	 *
	 * @return the set of translations.
	 */
	public Set<DBLanguageSetTranslation> getTranslations() {
		return translations;
	}

	/**
	 * Sets the set of {@link DBLanguageSetTranslation}s belonging to this language set.
	 *
	 * @param translations the set of translations.
	 */
	public void setTranslations(Set<DBLanguageSetTranslation> translations) {
		this.translations = translations;
	}

}

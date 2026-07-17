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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a translation language in the editable draft registry of a project, in
 * the {@code draft_translation_languages} table. This is the working copy of a project's
 * translation languages — edited freely through the authoring API — that gets reconciled into the
 * immutable published registry ({@link DBTranslationLanguage}) whenever the project is published.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "draft_translation_languages",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_draft_translation_languages",
			columnNames = { "project_id", "translation_language_code" }
		)
	}
)
public class DBDraftTranslationLanguage {

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

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Whether this language has no published counterpart yet. */
	@Column(name = "is_new", nullable = false)
	private boolean isNew;

	/**
	 * Whether this language is pending deletion. A soft-delete: the row (and any draft
	 * translations in this language) are kept, and the deletion is revertible, until the project
	 * is next published, at which point its published counterpart (if any) is marked removed and
	 * this draft row is hard-deleted for real.
	 */
	@Column(name = "is_deleted", nullable = false)
	private boolean isDeleted;

	/**
	 * Creates an empty instance of {@link DBDraftTranslationLanguage}.
	 */
	public DBDraftTranslationLanguage() {
	}

	/**
	 * Returns the unique UUID identifier of this draft translation language.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this draft translation language.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProject} this draft translation language belongs to.
	 *
	 * @return the owning project.
	 */
	public DBProject getProject() {
		return project;
	}

	/**
	 * Sets the {@link DBProject} this draft translation language belongs to.
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

	/**
	 * Returns the timestamp at which this draft translation language was created.
	 *
	 * @return the creation timestamp.
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the timestamp at which this draft translation language was created.
	 *
	 * @param createdAt the creation timestamp.
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Returns the timestamp at which this draft translation language was last updated.
	 *
	 * @return the last-updated timestamp.
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Sets the timestamp at which this draft translation language was last updated.
	 *
	 * @param updatedAt the last-updated timestamp.
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns whether this language has no published counterpart yet.
	 *
	 * @return whether this language is new.
	 */
	public boolean getIsNew() {
		return isNew;
	}

	/**
	 * Sets whether this language has no published counterpart yet.
	 *
	 * @param isNew whether this language is new.
	 */
	public void setIsNew(boolean isNew) {
		this.isNew = isNew;
	}

	/**
	 * Returns whether this language is pending deletion.
	 *
	 * @return whether this language is pending deletion.
	 */
	public boolean getIsDeleted() {
		return isDeleted;
	}

	/**
	 * Sets whether this language is pending deletion.
	 *
	 * @param isDeleted whether this language is pending deletion.
	 */
	public void setIsDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

}

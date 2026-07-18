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
 * JPA entity representing a translation language as it existed within a single, immutable
 * {@link DBProjectVersion}, stored in the {@code published_translation_languages} table. Unlike
 * the draft translation-language registry ({@link DBDraftTranslationLanguage}), this is not a
 * single current-state table shared across every published version — each publish creates a fresh
 * row here for every currently-active draft language, exactly as {@link DBPublishedDialogue} does
 * for dialogues, giving every version an accurate historical snapshot of its own language list.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "published_translation_languages",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_published_translation_languages",
			columnNames = { "version_id", "translation_language_code" }
		)
	}
)
public class DBPublishedTranslationLanguage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "version_id", nullable = false)
	@JsonIgnore
	private DBProjectVersion version;

	@Column(name = "translation_language_name", nullable = false, length = 64)
	private String translationLanguageName;

	@Column(name = "translation_language_code", nullable = false, length = 16)
	private String translationLanguageCode;

	/**
	 * Creates an empty instance of {@link DBPublishedTranslationLanguage}.
	 */
	public DBPublishedTranslationLanguage() {
	}

	/**
	 * Returns the unique UUID identifier of this published translation language.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this published translation language.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProjectVersion} this published translation language belongs to.
	 *
	 * @return the owning project version.
	 */
	public DBProjectVersion getVersion() {
		return version;
	}

	/**
	 * Sets the {@link DBProjectVersion} this published translation language belongs to.
	 *
	 * @param version the owning project version.
	 */
	public void setVersion(DBProjectVersion version) {
		this.version = version;
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

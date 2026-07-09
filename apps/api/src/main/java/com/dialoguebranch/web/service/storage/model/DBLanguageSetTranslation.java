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
 * JPA entity representing a single translation language belonging to a {@link DBLanguageSet}, in
 * the {@code language_set_translations} table.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "language_set_translations",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_language_set_translations",
			columnNames = { "language_set_id", "translation_language_code" }
		)
	}
)
public class DBLanguageSetTranslation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "language_set_id", nullable = false)
	@JsonIgnore
	private DBLanguageSet languageSet;

	@Column(name = "translation_language_code", nullable = false, length = 16)
	private String translationLanguageCode;

	@Column(name = "translation_language_name", nullable = false, length = 64)
	private String translationLanguageName;

	/**
	 * Creates an empty instance of {@link DBLanguageSetTranslation}.
	 */
	public DBLanguageSetTranslation() {
	}

	/**
	 * Returns the unique UUID identifier of this translation.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this translation.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBLanguageSet} this translation belongs to.
	 *
	 * @return the owning language set.
	 */
	public DBLanguageSet getLanguageSet() {
		return languageSet;
	}

	/**
	 * Sets the {@link DBLanguageSet} this translation belongs to.
	 *
	 * @param languageSet the owning language set.
	 */
	public void setLanguageSet(DBLanguageSet languageSet) {
		this.languageSet = languageSet;
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

}

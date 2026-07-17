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
 * JPA entity representing an immutable published translation linked to a
 * {@link DBPublishedDialogue}, stored in the {@code published_translations} table. The
 * {@code content} field holds the raw JSON translation content as it was at publish time.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "published_translations",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_published_translations",
			columnNames = { "published_dialogue_id", "translation_language_id" }
		)
	}
)
public class DBPublishedTranslation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "published_dialogue_id", nullable = false)
	@JsonIgnore
	private DBPublishedDialogue publishedDialogue;

	// EAGER, unlike publishedDialogue above: this project runs with open-in-view disabled, and
	// callers routinely read getTranslationLanguage() outside an open Hibernate
	// session/transaction (e.g. ProjectLoaderService.loadProject) — LAZY would throw
	// LazyInitializationException there. DBTranslationLanguage is a small lookup row, so
	// Hibernate resolves this as a plain SQL join rather than a real N+1 cost.
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "translation_language_id", nullable = false)
	@JsonIgnore
	private DBTranslationLanguage translationLanguage;

	@Column(columnDefinition = "TEXT")
	private String content;

	/**
	 * Creates an empty instance of {@link DBPublishedTranslation}.
	 */
	public DBPublishedTranslation() {
	}

	/**
	 * Returns the unique UUID identifier of this published translation.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this published translation.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBPublishedDialogue} this translation belongs to.
	 *
	 * @return the owning published dialogue.
	 */
	public DBPublishedDialogue getPublishedDialogue() {
		return publishedDialogue;
	}

	/**
	 * Sets the {@link DBPublishedDialogue} this translation belongs to.
	 *
	 * @param publishedDialogue the owning published dialogue.
	 */
	public void setPublishedDialogue(DBPublishedDialogue publishedDialogue) {
		this.publishedDialogue = publishedDialogue;
	}

	/**
	 * Returns the {@link DBTranslationLanguage} this translation targets.
	 *
	 * @return the target language.
	 */
	public DBTranslationLanguage getTranslationLanguage() {
		return translationLanguage;
	}

	/**
	 * Sets the {@link DBTranslationLanguage} this translation targets.
	 *
	 * @param translationLanguage the target language.
	 */
	public void setTranslationLanguage(DBTranslationLanguage translationLanguage) {
		this.translationLanguage = translationLanguage;
	}

	/**
	 * Returns the raw JSON translation content as it was at publish time.
	 *
	 * @return the translation content as a JSON string.
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Sets the raw JSON translation content.
	 *
	 * @param content the translation content as a JSON string.
	 */
	public void setContent(String content) {
		this.content = content;
	}

}

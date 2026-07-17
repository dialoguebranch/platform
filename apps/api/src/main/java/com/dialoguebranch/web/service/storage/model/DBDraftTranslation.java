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
 * JPA entity representing a translation file linked to a draft dialogue, stored in the
 * {@code draft_translations} table. The {@code content} field holds the raw JSON translation
 * content equivalent to a {@code .json} translation file in the source project.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "draft_translations",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_draft_translations",
			columnNames = { "draft_dialogue_id", "draft_translation_language_id" }
		)
	}
)
public class DBDraftTranslation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "draft_dialogue_id", nullable = false)
	@JsonIgnore
	private DBDraftDialogue draftDialogue;

	// EAGER, unlike draftDialogue above: this project runs with open-in-view disabled, and
	// callers routinely read getLanguage() outside an open Hibernate session/transaction (e.g.
	// DraftExecutionService.startSession, PublishService.verify) — LAZY would throw
	// LazyInitializationException there. DBDraftTranslationLanguage is a small lookup row, so
	// Hibernate resolves this as a plain SQL join rather than a real N+1 cost.
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "draft_translation_language_id", nullable = false)
	@JsonIgnore
	private DBDraftTranslationLanguage language;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Creates an empty instance of {@link DBDraftTranslation}.
	 */
	public DBDraftTranslation() {
	}

	/**
	 * Returns the unique UUID identifier of this draft translation.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this draft translation.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBDraftDialogue} this translation belongs to.
	 *
	 * @return the owning draft dialogue.
	 */
	public DBDraftDialogue getDraftDialogue() {
		return draftDialogue;
	}

	/**
	 * Sets the {@link DBDraftDialogue} this translation belongs to.
	 *
	 * @param draftDialogue the owning draft dialogue.
	 */
	public void setDraftDialogue(DBDraftDialogue draftDialogue) {
		this.draftDialogue = draftDialogue;
	}

	/**
	 * Returns the {@link DBDraftTranslationLanguage} this translation targets.
	 *
	 * @return the target language.
	 */
	public DBDraftTranslationLanguage getLanguage() {
		return language;
	}

	/**
	 * Sets the {@link DBDraftTranslationLanguage} this translation targets.
	 *
	 * @param language the target language.
	 */
	public void setLanguage(DBDraftTranslationLanguage language) {
		this.language = language;
	}

	/**
	 * Returns the raw JSON translation content.
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

	/**
	 * Returns the timestamp at which this translation was created.
	 *
	 * @return the creation timestamp.
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the timestamp at which this translation was created.
	 *
	 * @param createdAt the creation timestamp.
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Returns the timestamp at which this translation was last updated.
	 *
	 * @return the last-updated timestamp.
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Sets the timestamp at which this translation was last updated.
	 *
	 * @param updatedAt the last-updated timestamp.
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}

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
 * JPA entity representing an immutable published dialogue within a {@link DBProjectVersion},
 * stored in the {@code published_dialogues} table. The full reconstructed {@code .dlb} script
 * content is stored in {@code content}, ready to be parsed directly by the execution engine.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "published_dialogues",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_published_dialogues",
			columnNames = { "version_id", "name" }
		)
	}
)
public class DBPublishedDialogue {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "version_id", nullable = false)
	@JsonIgnore
	private DBProjectVersion version;

	private String name;

	@Column(columnDefinition = "TEXT")
	private String content;

	@OneToMany(mappedBy = "publishedDialogue")
	@JsonIgnore
	private Set<DBPublishedTranslation> translations = new HashSet<>();

	/**
	 * Creates an empty instance of {@link DBPublishedDialogue}.
	 */
	public DBPublishedDialogue() {
	}

	/**
	 * Returns the unique UUID identifier of this published dialogue.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this published dialogue.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProjectVersion} this published dialogue belongs to.
	 *
	 * @return the owning project version.
	 */
	public DBProjectVersion getVersion() {
		return version;
	}

	/**
	 * Sets the {@link DBProjectVersion} this published dialogue belongs to.
	 *
	 * @param version the owning project version.
	 */
	public void setVersion(DBProjectVersion version) {
		this.version = version;
	}

	/**
	 * Returns the logical name of this dialogue (e.g. {@code "intro"}).
	 *
	 * @return the dialogue name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the logical name of this dialogue.
	 *
	 * @param name the dialogue name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the full reconstructed {@code .dlb} script content of this dialogue.
	 *
	 * @return the script content.
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Sets the full reconstructed {@code .dlb} script content of this dialogue.
	 *
	 * @param content the script content.
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * Returns the set of {@link DBPublishedTranslation}s for this published dialogue.
	 *
	 * @return the set of published translations.
	 */
	public Set<DBPublishedTranslation> getTranslations() {
		return translations;
	}

	/**
	 * Sets the set of {@link DBPublishedTranslation}s for this published dialogue.
	 *
	 * @param translations the set of published translations.
	 */
	public void setTranslations(Set<DBPublishedTranslation> translations) {
		this.translations = translations;
	}

}

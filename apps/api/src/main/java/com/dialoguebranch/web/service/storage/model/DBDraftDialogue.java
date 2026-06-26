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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity representing an editable dialogue in the {@code draft_dialogues} table. A
 * {@link DBDraftDialogue} belongs to a {@link DBProject} and contains a set of
 * {@link DBDraftNode}s and {@link DBDraftTranslation}s that make up the working copy of
 * the dialogue script.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "draft_dialogues",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_draft_dialogues", columnNames = { "project_id", "name" })
	}
)
public class DBDraftDialogue {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private DBProject project;

	private String name;

	@OneToMany(mappedBy = "draftDialogue")
	@JsonIgnore
	private Set<DBDraftNode> nodes = new HashSet<>();

	@OneToMany(mappedBy = "draftDialogue")
	@JsonIgnore
	private Set<DBDraftTranslation> translations = new HashSet<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Creates an empty instance of {@link DBDraftDialogue}.
	 */
	public DBDraftDialogue() {
	}

	/**
	 * Returns the unique UUID identifier of this draft dialogue.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this draft dialogue.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProject} this draft dialogue belongs to.
	 *
	 * @return the owning project.
	 */
	public DBProject getProject() {
		return project;
	}

	/**
	 * Sets the {@link DBProject} this draft dialogue belongs to.
	 *
	 * @param project the owning project.
	 */
	public void setProject(DBProject project) {
		this.project = project;
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
	 * Returns the set of {@link DBDraftNode}s belonging to this draft dialogue.
	 *
	 * @return the set of draft nodes.
	 */
	public Set<DBDraftNode> getNodes() {
		return nodes;
	}

	/**
	 * Sets the set of {@link DBDraftNode}s belonging to this draft dialogue.
	 *
	 * @param nodes the set of draft nodes.
	 */
	public void setNodes(Set<DBDraftNode> nodes) {
		this.nodes = nodes;
	}

	/**
	 * Returns the set of {@link DBDraftTranslation}s for this draft dialogue.
	 *
	 * @return the set of draft translations.
	 */
	public Set<DBDraftTranslation> getTranslations() {
		return translations;
	}

	/**
	 * Sets the set of {@link DBDraftTranslation}s for this draft dialogue.
	 *
	 * @param translations the set of draft translations.
	 */
	public void setTranslations(Set<DBDraftTranslation> translations) {
		this.translations = translations;
	}

	/**
	 * Returns the timestamp at which this draft dialogue was created.
	 *
	 * @return the creation timestamp.
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the timestamp at which this draft dialogue was created.
	 *
	 * @param createdAt the creation timestamp.
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Returns the timestamp at which this draft dialogue was last updated.
	 *
	 * @return the last-updated timestamp.
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Sets the timestamp at which this draft dialogue was last updated.
	 *
	 * @param updatedAt the last-updated timestamp.
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}

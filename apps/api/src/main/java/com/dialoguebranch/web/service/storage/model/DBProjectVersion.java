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
 * JPA entity representing an immutable published snapshot of a project, stored in the
 * {@code project_versions} table. Each publish operation creates a new {@link DBProjectVersion}
 * containing a full copy of all dialogues and translations valid at that point in time.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "project_versions",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_project_versions",
			columnNames = { "project_id", "version_number" }
		)
	}
)
public class DBProjectVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private DBProject project;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "published_by")
	@JsonIgnore
	private DBUser publishedBy;

	@OneToMany(mappedBy = "version")
	@JsonIgnore
	private Set<DBPublishedDialogue> publishedDialogues = new HashSet<>();

	/**
	 * Creates an empty instance of {@link DBProjectVersion}.
	 */
	public DBProjectVersion() {
	}

	/**
	 * Returns the unique UUID identifier of this project version.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this project version.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBProject} this version belongs to.
	 *
	 * @return the owning project.
	 */
	public DBProject getProject() {
		return project;
	}

	/**
	 * Sets the {@link DBProject} this version belongs to.
	 *
	 * @param project the owning project.
	 */
	public void setProject(DBProject project) {
		this.project = project;
	}

	/**
	 * Returns the sequential version number of this published snapshot.
	 *
	 * @return the version number.
	 */
	public int getVersionNumber() {
		return versionNumber;
	}

	/**
	 * Sets the sequential version number of this published snapshot.
	 *
	 * @param versionNumber the version number.
	 */
	public void setVersionNumber(int versionNumber) {
		this.versionNumber = versionNumber;
	}

	/**
	 * Returns the timestamp at which this version was published.
	 *
	 * @return the publish timestamp.
	 */
	public Instant getPublishedAt() {
		return publishedAt;
	}

	/**
	 * Sets the timestamp at which this version was published.
	 *
	 * @param publishedAt the publish timestamp.
	 */
	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	/**
	 * Returns the {@link DBUser} who published this version, or {@code null} if published by the
	 * system.
	 *
	 * @return the publishing user, or {@code null}.
	 */
	public DBUser getPublishedBy() {
		return publishedBy;
	}

	/**
	 * Sets the {@link DBUser} who published this version.
	 *
	 * @param publishedBy the publishing user.
	 */
	public void setPublishedBy(DBUser publishedBy) {
		this.publishedBy = publishedBy;
	}

	/**
	 * Returns the set of {@link DBPublishedDialogue}s included in this version.
	 *
	 * @return the set of published dialogues.
	 */
	public Set<DBPublishedDialogue> getPublishedDialogues() {
		return publishedDialogues;
	}

	/**
	 * Sets the set of {@link DBPublishedDialogue}s included in this version.
	 *
	 * @param publishedDialogues the set of published dialogues.
	 */
	public void setPublishedDialogues(Set<DBPublishedDialogue> publishedDialogues) {
		this.publishedDialogues = publishedDialogues;
	}

}

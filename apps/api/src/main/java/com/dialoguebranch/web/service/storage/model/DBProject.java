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
 * JPA entity representing a Dialogue Branch project in the {@code projects} database table. A
 * project is the top-level container for a collection of {@link DBDraftDialogue}s and their
 * published snapshots ({@link DBProjectVersion}s).
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "projects",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_projects_slug", columnNames = "slug")
	}
)
public class DBProject {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String slug;

	@Column(name = "display_name")
	private String displayName;

	private String description;

	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "latest_version_id")
	private DBProjectVersion latestVersion;

	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "default_language_set_id")
	private DBLanguageSet defaultLanguageSet;

	@OneToMany(mappedBy = "project", fetch = FetchType.EAGER)
	private Set<DBLanguageSet> languageSets = new HashSet<>();

	@OneToMany(mappedBy = "project", fetch = FetchType.EAGER)
	private Set<DBProjectLanguageMapping> languageMappings = new HashSet<>();

	@OneToMany(mappedBy = "project")
	@JsonIgnore
	private Set<DBDraftDialogue> draftDialogues = new HashSet<>();

	@OneToMany(mappedBy = "project")
	@JsonIgnore
	private Set<DBProjectVersion> versions = new HashSet<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Creates an empty instance of {@link DBProject}.
	 */
	public DBProject() {
	}

	/**
	 * Returns the unique UUID identifier of this project.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this project.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the unique slug of this project.
	 *
	 * @return the project slug.
	 */
	public String getSlug() {
		return slug;
	}

	/**
	 * Sets the unique slug of this project.
	 *
	 * @param slug the project slug.
	 */
	public void setSlug(String slug) {
		this.slug = slug;
	}

	/**
	 * Returns the human-readable display name of this project.
	 *
	 * @return the display name.
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Sets the human-readable display name of this project.
	 *
	 * @param displayName the display name.
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Returns the description of this project.
	 *
	 * @return the description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description of this project.
	 *
	 * @param description the description.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the latest published {@link DBProjectVersion} for this project, or {@code null} if
	 * this project has never been published.
	 *
	 * @return the latest project version, or {@code null}.
	 */
	public DBProjectVersion getLatestVersion() {
		return latestVersion;
	}

	/**
	 * Sets the latest published {@link DBProjectVersion} for this project.
	 *
	 * @param latestVersion the latest project version.
	 */
	public void setLatestVersion(DBProjectVersion latestVersion) {
		this.latestVersion = latestVersion;
	}

	/**
	 * Returns this project's default {@link DBLanguageSet}, or {@code null} if the project has
	 * not yet been assigned one (e.g. seed projects that predate the LanguageSet model). Every
	 * project created through the API is guaranteed to have exactly one.
	 *
	 * @return the default language set, or {@code null}.
	 */
	public DBLanguageSet getDefaultLanguageSet() {
		return defaultLanguageSet;
	}

	/**
	 * Sets this project's default {@link DBLanguageSet}.
	 *
	 * @param defaultLanguageSet the default language set.
	 */
	public void setDefaultLanguageSet(DBLanguageSet defaultLanguageSet) {
		this.defaultLanguageSet = defaultLanguageSet;
	}

	/**
	 * Returns the set of all {@link DBLanguageSet}s defined for this project.
	 *
	 * @return the set of language sets.
	 */
	public Set<DBLanguageSet> getLanguageSets() {
		return languageSets;
	}

	/**
	 * Sets the set of all {@link DBLanguageSet}s for this project.
	 *
	 * @param languageSets the set of language sets.
	 */
	public void setLanguageSets(Set<DBLanguageSet> languageSets) {
		this.languageSets = languageSets;
	}

	/**
	 * Returns the set of {@link DBProjectLanguageMapping}s defining the language map for this
	 * project.
	 *
	 * @return the set of language mappings.
	 */
	public Set<DBProjectLanguageMapping> getLanguageMappings() {
		return languageMappings;
	}

	/**
	 * Sets the set of {@link DBProjectLanguageMapping}s for this project.
	 *
	 * @param languageMappings the set of language mappings.
	 */
	public void setLanguageMappings(Set<DBProjectLanguageMapping> languageMappings) {
		this.languageMappings = languageMappings;
	}

	/**
	 * Returns the set of {@link DBDraftDialogue}s belonging to this project.
	 *
	 * @return the set of draft dialogues.
	 */
	public Set<DBDraftDialogue> getDraftDialogues() {
		return draftDialogues;
	}

	/**
	 * Sets the set of {@link DBDraftDialogue}s belonging to this project.
	 *
	 * @param draftDialogues the set of draft dialogues.
	 */
	public void setDraftDialogues(Set<DBDraftDialogue> draftDialogues) {
		this.draftDialogues = draftDialogues;
	}

	/**
	 * Returns the set of all published {@link DBProjectVersion}s for this project.
	 *
	 * @return the set of project versions.
	 */
	public Set<DBProjectVersion> getVersions() {
		return versions;
	}

	/**
	 * Sets the set of all published {@link DBProjectVersion}s for this project.
	 *
	 * @param versions the set of project versions.
	 */
	public void setVersions(Set<DBProjectVersion> versions) {
		this.versions = versions;
	}

	/**
	 * Returns the timestamp at which this project was created.
	 *
	 * @return the creation timestamp.
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the timestamp at which this project was created.
	 *
	 * @param createdAt the creation timestamp.
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Returns the timestamp at which this project was last updated.
	 *
	 * @return the last-updated timestamp.
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Sets the timestamp at which this project was last updated.
	 *
	 * @param updatedAt the last-updated timestamp.
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}

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

/**
 * JPA entity representing a single logged dialogue execution, stored in the
 * {@code logged_dialogues} database table. Each row corresponds to one
 * {@link com.dialoguebranch.web.service.storage.ServerLoggedDialogue} — one dialogue within a
 * (possibly multi-dialogue) user session — including which published version of the project it
 * was started against and its full interaction history (stored as a JSON blob).
 *
 * @author Harm op den Akker
 */
@Entity
@Table(name = "logged_dialogues")
public class DBLoggedDialogue {

	@Id
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	@JsonIgnore
	private DBUser user;

	@Column(name = "session_id", nullable = false)
	private String sessionId;

	@Column(name = "session_start_time", nullable = false)
	private long sessionStartTime;

	@Column(name = "local_time", nullable = false)
	private String localTime;

	@Column(name = "utc_time_ms", nullable = false)
	private long utcTime;

	@Column(nullable = false)
	private String timezone;

	@Column(name = "project_slug", nullable = false)
	private String projectSlug;

	@Column(name = "dialogue_name", nullable = false)
	private String dialogueName;

	@Column(nullable = false)
	private String language;

	@Column(name = "published_version_number", nullable = false)
	private int publishedVersionNumber;

	@Column(nullable = false)
	private boolean completed;

	@Column(nullable = false)
	private boolean cancelled;

	@Column(name = "latest_interaction_timestamp", nullable = false)
	private long latestInteractionTimestamp;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String interactions;

	/**
	 * Creates an empty instance of {@link DBLoggedDialogue}.
	 */
	public DBLoggedDialogue() {
	}

	/**
	 * Returns the unique identifier of this logged dialogue (a dash-free 32-character hex
	 * string, matching {@link com.dialoguebranch.web.service.storage.ServerLoggedDialogue#getId()}
	 * exactly, since this ID is part of the public API contract as {@code loggedDialogueId}).
	 *
	 * @return the logged dialogue identifier.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets the unique identifier of this logged dialogue.
	 *
	 * @param id the logged dialogue identifier.
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBUser} that this logged dialogue belongs to.
	 *
	 * @return the owning user.
	 */
	public DBUser getUser() {
		return user;
	}

	/**
	 * Sets the {@link DBUser} that this logged dialogue belongs to.
	 *
	 * @param user the owning user.
	 */
	public void setUser(DBUser user) {
		this.user = user;
	}

	/**
	 * Returns the identifier of the session that this logged dialogue is part of.
	 *
	 * @return the session identifier.
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * Sets the identifier of the session that this logged dialogue is part of.
	 *
	 * @param sessionId the session identifier.
	 */
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * Returns the UTC epoch time (milliseconds) at which the session started.
	 *
	 * @return the session start time in epoch milliseconds.
	 */
	public long getSessionStartTime() {
		return sessionStartTime;
	}

	/**
	 * Sets the UTC epoch time (milliseconds) at which the session started.
	 *
	 * @param sessionStartTime the session start time in epoch milliseconds.
	 */
	public void setSessionStartTime(long sessionStartTime) {
		this.sessionStartTime = sessionStartTime;
	}

	/**
	 * Returns the local time at which this dialogue started, as a human-readable string.
	 *
	 * @return the local start time string.
	 */
	public String getLocalTime() {
		return localTime;
	}

	/**
	 * Sets the local time at which this dialogue started.
	 *
	 * @param localTime the local start time string.
	 */
	public void setLocalTime(String localTime) {
		this.localTime = localTime;
	}

	/**
	 * Returns the UTC epoch time (milliseconds) at which this dialogue started.
	 *
	 * @return the UTC start time in epoch milliseconds.
	 */
	public long getUtcTime() {
		return utcTime;
	}

	/**
	 * Sets the UTC epoch time (milliseconds) at which this dialogue started.
	 *
	 * @param utcTime the UTC start time in epoch milliseconds.
	 */
	public void setUtcTime(long utcTime) {
		this.utcTime = utcTime;
	}

	/**
	 * Returns the IANA timezone identifier of the user's local time.
	 *
	 * @return the timezone identifier.
	 */
	public String getTimezone() {
		return timezone;
	}

	/**
	 * Sets the IANA timezone identifier of the user's local time.
	 *
	 * @param timezone the timezone identifier.
	 */
	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	/**
	 * Returns the slug of the project that this logged dialogue belongs to.
	 *
	 * @return the project slug.
	 */
	public String getProjectSlug() {
		return projectSlug;
	}

	/**
	 * Sets the slug of the project that this logged dialogue belongs to.
	 *
	 * @param projectSlug the project slug.
	 */
	public void setProjectSlug(String projectSlug) {
		this.projectSlug = projectSlug;
	}

	/**
	 * Returns the name of the dialogue that was executed.
	 *
	 * @return the dialogue name.
	 */
	public String getDialogueName() {
		return dialogueName;
	}

	/**
	 * Sets the name of the dialogue that was executed.
	 *
	 * @param dialogueName the dialogue name.
	 */
	public void setDialogueName(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	/**
	 * Returns the ISO language code in which the dialogue was conducted.
	 *
	 * @return the language code.
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * Sets the ISO language code in which the dialogue was conducted.
	 *
	 * @param language the language code.
	 */
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * Returns the version number of the project's published content that this dialogue was
	 * started against.
	 *
	 * @return the published version number.
	 */
	public int getPublishedVersionNumber() {
		return publishedVersionNumber;
	}

	/**
	 * Sets the version number of the project's published content that this dialogue was started
	 * against.
	 *
	 * @param publishedVersionNumber the published version number.
	 */
	public void setPublishedVersionNumber(int publishedVersionNumber) {
		this.publishedVersionNumber = publishedVersionNumber;
	}

	/**
	 * Returns whether this dialogue reached a terminal node and completed normally.
	 *
	 * @return {@code true} if completed, {@code false} otherwise.
	 */
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Sets whether this dialogue reached a terminal node and completed normally.
	 *
	 * @param completed {@code true} if completed.
	 */
	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	/**
	 * Returns whether this dialogue was cancelled before completion.
	 *
	 * @return {@code true} if cancelled, {@code false} otherwise.
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Sets whether this dialogue was cancelled before completion.
	 *
	 * @param cancelled {@code true} if cancelled.
	 */
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	/**
	 * Returns the timestamp (epoch milliseconds) of the most recent interaction in this
	 * dialogue, or of the dialogue's own start time if it has no interactions yet. Denormalized
	 * at write time so the "latest ongoing dialogue" lookup can be answered with an indexed query
	 * instead of scanning and parsing every logged dialogue.
	 *
	 * @return the latest interaction timestamp in epoch milliseconds.
	 */
	public long getLatestInteractionTimestamp() {
		return latestInteractionTimestamp;
	}

	/**
	 * Sets the timestamp (epoch milliseconds) of the most recent interaction in this dialogue.
	 *
	 * @param latestInteractionTimestamp the latest interaction timestamp in epoch milliseconds.
	 */
	public void setLatestInteractionTimestamp(long latestInteractionTimestamp) {
		this.latestInteractionTimestamp = latestInteractionTimestamp;
	}

	/**
	 * Returns the JSON-serialized list of
	 * {@link com.dialoguebranch.model.execute.LoggedInteraction}s that make up this dialogue's
	 * conversation history.
	 *
	 * @return the interactions as a JSON string.
	 */
	public String getInteractions() {
		return interactions;
	}

	/**
	 * Sets the JSON-serialized list of
	 * {@link com.dialoguebranch.model.execute.LoggedInteraction}s that make up this dialogue's
	 * conversation history.
	 *
	 * @param interactions the interactions as a JSON string.
	 */
	public void setInteractions(String interactions) {
		this.interactions = interactions;
	}

}

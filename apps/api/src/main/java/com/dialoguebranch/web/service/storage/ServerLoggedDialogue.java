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

package com.dialoguebranch.web.service.storage;

import com.dialoguebranch.model.execute.LoggedDialogue;
import com.dialoguebranch.model.execute.LoggedInteraction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single logged dialogue execution for a specific user, stored as part of the
 * Dialogue Branch Web Service's dialogue logging mechanism. Each instance captures the start time,
 * session information, dialogue name, language, and the list of {@link LoggedInteraction}s that
 * make up the dialogue.
 *
 * @author Harm op den Akker
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class ServerLoggedDialogue implements LoggedDialogue {

	private String id;
	private String sessionId;
	private long sessionStartTime;
	private String user;
	private String localTime;
	private long utcTime;
	private String timezone;
	private String projectName;
	private String dialogueName;
	private String language;
	private boolean completed;
	private boolean cancelled;
	private List<LoggedInteraction> interactionList = new ArrayList<>();

	/**
	 * Creates an empty instance of {@link ServerLoggedDialogue}.
	 */
	public ServerLoggedDialogue() {
	}

	/**
	 * Constructs a new instance at the specified time. It should define the
	 * local time and location-based time zone (not an offset).
	 *
	 * @param user the identifier of the user for which the dialogue is being logged.
	 * @param dialogueStartTime the time that this dialogue started in the time zone of the user.
	 * @param sessionId an optional externally provided id to be added to the logs (or
	 *                    {@code null}).
	 * @param sessionStartTime the UTC epoch-millisecond timestamp for when the session started.
	 */
	public ServerLoggedDialogue(String user, ZonedDateTime dialogueStartTime, String sessionId,
								long sessionStartTime) {
		this.user = user;
		this.utcTime = dialogueStartTime.toInstant().toEpochMilli();
		this.timezone = dialogueStartTime.getZone().toString();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
		this.localTime = dialogueStartTime.format(formatter);
		this.sessionId = sessionId;
		this.sessionStartTime = sessionStartTime;
		this.id = UUID.randomUUID().toString().toLowerCase().replaceAll("-", "");
	}

	/**
	 * Returns the unique identifier of this logged dialogue session.
	 * @return the session identifier.
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * Sets the unique identifier of this logged dialogue session.
	 * @param id the session identifier.
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the optional custom logging identifier, or {@code null} if none is set.
	 * @return the optional custom logging identifier, or {@code null} if none is set.
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * Sets an optional custom logging identifier that may be used to cross-reference Dialogue
	 * Branch Web Service dialogue logs for a session with logs from an external system.
	 * @param sessionId an optional custom logging identifier.
	 */
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * Returns the UTC timestamp (milliseconds since Jan 1st 1970) at which this dialogue session
	 * started.
	 *
	 * @return the session start time in milliseconds UTC.
	 */
	public long getSessionStartTime() {
		return sessionStartTime;
	}

	/**
	 * Sets the UTC timestamp (milliseconds since Jan 1st 1970) at which this dialogue session
	 * started.
	 *
	 * @param sessionStartTime the session start time in milliseconds UTC.
	 */
	public void setSessionStartTime(long sessionStartTime) {
		this.sessionStartTime = sessionStartTime;
	}

	/**
	 * Returns the identifier of the user who participated in this dialogue session.
	 * @return the user identifier.
	 */
	@Override
	public String getUser() {
		return user;
	}

	/**
	 * Sets the identifier of the user who participated in this dialogue session.
	 * @param user the user identifier.
	 */
	@Override
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Returns the local time at which this dialogue session started, as a human-readable string.
	 * @return the local start time string.
	 */
	@Override
	public String getLocalTime() {
		return localTime;
	}

	/**
	 * Sets the local time at which this dialogue session started.
	 * @param localTime the local start time string.
	 */
	@Override
	public void setLocalTime(String localTime) {
		this.localTime = localTime;
	}

	/**
	 * Returns the UTC epoch time (in milliseconds) at which this dialogue session started.
	 * @return the UTC start time in milliseconds.
	 */
	@Override
	public long getUtcTime() {
		return utcTime;
	}

	/**
	 * Sets the UTC epoch time (in milliseconds) at which this dialogue session started.
	 * @param utcTime the UTC start time in milliseconds.
	 */
	@Override
	public void setUtcTime(long utcTime) {
		this.utcTime = utcTime;
	}

	/**
	 * Returns the timezone identifier (e.g. {@code "Europe/Amsterdam"}) of the user's local time.
	 * @return the timezone identifier.
	 */
	@Override
	public String getTimezone() {
		return timezone;
	}

	/**
	 * Sets the timezone identifier of the user's local time.
	 * @param timezone the timezone identifier.
	 */
	@Override
	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	/**
	 * Returns the name of the project that the logged dialogue belongs to.
	 * @return the project name.
	 */
	public String getProjectName() {
		return projectName;
	}

	/**
	 * Sets the name of the project that the logged dialogue belongs to.
	 * @param projectName the project name.
	 */
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	/**
	 * Returns the name of the dialogue that was executed in this session.
	 * @return the dialogue name.
	 */
	@Override
	public String getDialogueName() {
		return dialogueName;
	}

	/**
	 * Sets the name of the dialogue that was executed in this session.
	 * @param dialogueName the dialogue name.
	 */
	@Override
	public void setDialogueName(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	/**
	 * Returns the ISO language code of the language in which the dialogue was conducted.
	 * @return the language code.
	 */
	@Override
	public String getLanguage() {
		return language;
	}

	/**
	 * Sets the ISO language code of the language in which the dialogue was conducted.
	 * @param language the language code.
	 */
	@Override
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * Returns {@code true} if the dialogue session reached a terminal node and completed
	 * normally.
	 * @return {@code true} if completed, {@code false} otherwise.
	 */
	@Override
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Sets whether this dialogue session completed normally.
	 * @param completed {@code true} if the session completed normally.
	 */
	@Override
	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	/**
	 * Returns {@code true} if the dialogue session was cancelled before completion.
	 * @return {@code true} if cancelled, {@code false} otherwise.
	 */
	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Sets whether this dialogue session was cancelled before completion.
	 * @param cancelled {@code true} if the session was cancelled.
	 */
	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	/**
	 * Returns the ordered list of {@link LoggedInteraction}s that make up the conversation
	 * history of this session.
	 * @return the list of logged interactions.
	 */
	@Override
	public List<LoggedInteraction> getInteractionList() {
		return interactionList;
	}

	/**
	 * Sets the ordered list of {@link LoggedInteraction}s for this session.
	 * @param interactionList the list of logged interactions.
	 */
	@Override
	public void setInteractionList(List<LoggedInteraction> interactionList) {
		this.interactionList = interactionList;
	}

	/**
	 * Returns the timestamp (milliseconds since Jan 1st 1970 UTC) of the latest step in this
	 * {@link ServerLoggedDialogue}.
	 * @return the timestamp of the latest step in this {@link ServerLoggedDialogue}.
	 */
	@JsonIgnore
	public long getLatestInteractionTimestamp() {
		if(interactionList.isEmpty()) return this.getUtcTime();
		else {
			return interactionList.get(interactionList.size()-1).getTimestamp();
		}
	}

	/**
	 * @return a string representation of this logged dialogue for debugging/logging purposes.
	 */
	@Override
	public String toString() {
		return "ServerLoggedDialogue[" +
			"id='" + id + '\'' +
			", sessionId='" + sessionId + '\'' +
			", user='" + user + '\'' +
			", dialogueName='" + dialogueName + '\'' +
			']';
	}
}

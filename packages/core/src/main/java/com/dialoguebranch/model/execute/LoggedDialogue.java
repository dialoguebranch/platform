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

package com.dialoguebranch.model.execute;

import java.util.List;

/**
 * Defines the contract for a persisted record of a single Dialogue Branch dialogue session.
 * Implementations are responsible for storing session metadata and the ordered list of
 * {@link LoggedInteraction}s that make up the conversation history.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public interface LoggedDialogue {

	/**
	 * Returns the unique identifier of this logged dialogue session.
	 * @return the session identifier.
	 */
	String getId();

	/**
	 * Sets the unique identifier of this logged dialogue session.
	 * @param id the session identifier.
	 */
	void setId(String id);

	/**
	 * Returns the identifier of the user who participated in this dialogue session.
	 * @return the user identifier.
	 */
	String getUser();

	/**
	 * Sets the identifier of the user who participated in this dialogue session.
	 * @param user the user identifier.
	 */
	void setUser(String user);

	/**
	 * Returns the local time at which this dialogue session started, as a human-readable string.
	 * @return the local start time string.
	 */
	String getLocalTime();

	/**
	 * Sets the local time at which this dialogue session started.
	 * @param localTime the local start time string.
	 */
	void setLocalTime(String localTime);

	/**
	 * Returns the UTC epoch time (in milliseconds) at which this dialogue session started.
	 * @return the UTC start time in milliseconds.
	 */
	long getUtcTime();

	/**
	 * Sets the UTC epoch time (in milliseconds) at which this dialogue session started.
	 * @param utcTime the UTC start time in milliseconds.
	 */
	void setUtcTime(long utcTime);

	/**
	 * Returns the timezone identifier (e.g. {@code "Europe/Amsterdam"}) of the user's local time.
	 * @return the timezone identifier.
	 */
	String getTimezone();

	/**
	 * Sets the timezone identifier of the user's local time.
	 * @param timezone the timezone identifier.
	 */
	void setTimezone(String timezone);

	/**
	 * Returns the name of the dialogue that was executed in this session.
	 * @return the dialogue name.
	 */
	String getDialogueName();

	/**
	 * Sets the name of the dialogue that was executed in this session.
	 * @param dialogueName the dialogue name.
	 */
	void setDialogueName(String dialogueName);

	/**
	 * Returns the ISO language code of the language in which the dialogue was conducted.
	 * @return the language code.
	 */
	String getLanguage();

	/**
	 * Sets the ISO language code of the language in which the dialogue was conducted.
	 * @param language the language code.
	 */
	void setLanguage(String language);

	/**
	 * Returns {@code true} if the dialogue session reached a terminal node and completed normally.
	 * @return {@code true} if completed, {@code false} otherwise.
	 */
	boolean isCompleted();

	/**
	 * Sets whether this dialogue session completed normally.
	 * @param completed {@code true} if the session completed normally.
	 */
	void setCompleted(boolean completed);

	/**
	 * Returns {@code true} if the dialogue session was cancelled before completion.
	 * @return {@code true} if cancelled, {@code false} otherwise.
	 */
	boolean isCancelled();

	/**
	 * Sets whether this dialogue session was cancelled before completion.
	 * @param cancelled {@code true} if the session was cancelled.
	 */
	void setCancelled(boolean cancelled);

	/**
	 * Returns the ordered list of {@link LoggedInteraction}s that make up the conversation history
	 * of this session.
	 * @return the list of logged interactions.
	 */
	List<LoggedInteraction> getInteractionList();

	/**
	 * Sets the ordered list of {@link LoggedInteraction}s for this session.
	 * @param interactionList the list of logged interactions.
	 */
	void setInteractionList(List<LoggedInteraction> interactionList);
}

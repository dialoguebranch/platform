/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import java.util.ArrayList;
import java.util.List;

/**
 * A plain in-memory {@link LoggedDialogue} for tests. The concrete implementations live in
 * {@code apps/api}; core tests that need a session log use this instead.
 */
public class TestLoggedDialogue implements LoggedDialogue {

	private String id = "log-1";
	private String user = "test-user";
	private String localTime = "2026-06-01T12:00:00";
	private long utcTime = 0L;
	private String timezone = "Europe/Lisbon";
	private String dialogueName = "flow";
	private String language = "en";
	private boolean completed = false;
	private boolean cancelled = false;
	private List<LoggedInteraction> interactionList = new ArrayList<>();

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getUser() {
		return user;
	}

	@Override
	public void setUser(String user) {
		this.user = user;
	}

	@Override
	public String getLocalTime() {
		return localTime;
	}

	@Override
	public void setLocalTime(String localTime) {
		this.localTime = localTime;
	}

	@Override
	public long getUtcTime() {
		return utcTime;
	}

	@Override
	public void setUtcTime(long utcTime) {
		this.utcTime = utcTime;
	}

	@Override
	public String getTimezone() {
		return timezone;
	}

	@Override
	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	@Override
	public String getDialogueName() {
		return dialogueName;
	}

	@Override
	public void setDialogueName(String dialogueName) {
		this.dialogueName = dialogueName;
	}

	@Override
	public String getLanguage() {
		return language;
	}

	@Override
	public void setLanguage(String language) {
		this.language = language;
	}

	@Override
	public boolean isCompleted() {
		return completed;
	}

	@Override
	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	@Override
	public List<LoggedInteraction> getInteractionList() {
		return interactionList;
	}

	@Override
	public void setInteractionList(List<LoggedInteraction> interactionList) {
		this.interactionList = interactionList;
	}
}

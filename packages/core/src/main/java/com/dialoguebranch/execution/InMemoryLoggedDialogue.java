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


package com.dialoguebranch.execution;

import com.dialoguebranch.model.execute.LoggedDialogue;
import com.dialoguebranch.model.execute.LoggedInteraction;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A {@link LoggedDialogue} that is only kept in memory, for an application that wants a transcript
 * of a conversation, or to go back in it, without storing it anywhere (#483). A conversation that
 * runs across dialogues has one log per dialogue, all with the same session ({@link #getSessionId()}),
 * made by {@link #factory()}.
 */
public class InMemoryLoggedDialogue implements LoggedDialogue {

	private static final DateTimeFormatter LOCAL_TIME_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

	private String id = UUID.randomUUID().toString().toLowerCase().replaceAll("-", "");
	private String user;
	private String localTime;
	private long utcTime;
	private String timezone;
	private String dialogueName;
	private String language;
	private boolean completed;
	private boolean cancelled;
	private List<LoggedInteraction> interactionList = new ArrayList<>();
	private final @Nullable String sessionId;

	/**
	 * Creates the log of a dialogue that starts at the given time.
	 *
	 * @param user the ID of the user the dialogue is for.
	 * @param dialogue the dialogue, which gives the log its name and language.
	 * @param startTime when the dialogue starts, in the user's time zone.
	 * @param sessionId an identifier shared by the logs of one conversation, or {@code null}.
	 */
	public InMemoryLoggedDialogue(String user, ResolvedDialogue dialogue, ZonedDateTime startTime,
			@Nullable String sessionId) {
		this.user = user;
		this.utcTime = startTime.toInstant().toEpochMilli();
		this.timezone = startTime.getZone().toString();
		this.localTime = startTime.format(LOCAL_TIME_FORMAT);
		this.dialogueName = Objects.requireNonNull(dialogue.dialogue().getDialogueName(),
				"The dialogue has no name");
		this.language = Objects.requireNonNull(dialogue.pointer().getLanguage(),
				"The dialogue has no language");
		this.sessionId = sessionId;
	}

	/**
	 * Returns a factory of {@link InMemoryLoggedDialogue}s, which carry on the user and the session
	 * of the log before them.
	 *
	 * @return the factory, for {@link ActiveDialogue#setLoggedDialogue}.
	 */
	public static LoggedDialogueFactory factory() {
		return (previous, entered, eventTime) -> new InMemoryLoggedDialogue(previous.getUser(),
				entered, eventTime,
				previous instanceof InMemoryLoggedDialogue inMemory ? inMemory.sessionId : null);
	}

	/**
	 * Returns the identifier that the logs of one conversation share.
	 *
	 * @return the session identifier, or {@code null} if there is none.
	 */
	public @Nullable String getSessionId() { return sessionId; }

	@Override public String getId() { return id; }
	@Override public void setId(String id) { this.id = id; }
	@Override public String getUser() { return user; }
	@Override public void setUser(String user) { this.user = user; }
	@Override public String getLocalTime() { return localTime; }
	@Override public void setLocalTime(String localTime) { this.localTime = localTime; }
	@Override public long getUtcTime() { return utcTime; }
	@Override public void setUtcTime(long utcTime) { this.utcTime = utcTime; }
	@Override public String getTimezone() { return timezone; }
	@Override public void setTimezone(String timezone) { this.timezone = timezone; }
	@Override public String getDialogueName() { return dialogueName; }
	@Override public void setDialogueName(String dialogueName) { this.dialogueName = dialogueName; }
	@Override public String getLanguage() { return language; }
	@Override public void setLanguage(String language) { this.language = language; }
	@Override public boolean isCompleted() { return completed; }
	@Override public void setCompleted(boolean completed) { this.completed = completed; }
	@Override public boolean isCancelled() { return cancelled; }
	@Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
	@Override public List<LoggedInteraction> getInteractionList() { return interactionList; }
	@Override public void setInteractionList(List<LoggedInteraction> interactionList) {
		this.interactionList = interactionList;
	}
}

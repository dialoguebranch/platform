/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

import java.time.ZoneId;

/**
 * A {@link User} represents the person that is interacting with a Dialogue Branch dialogue. The
 * {@link User} has an identifier and a time zone in which he currently resides that can be used to
 * log events (dialogue history and the stored update times for Dialogue Branch variables).
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class User {

	/** The identifier or 'username' of this User */
	private String id;

	/** The time zone in which this user currently resides - for logging timestamps */
	private ZoneId timeZone;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link User} in the system's default time zone.
	 *
	 * @param id the username or identifier of the {@link User}.
	 */
	public User(String id) {
		this.id = id;
		this.timeZone = ZoneId.systemDefault();
	}

	/**
	 * Creates an instance of a {@link User} with a given {@code id} and {@code timeZone}.
	 *
	 * @param id the username or identifier of the {@link User}
	 * @param timeZone the timezone (as {@link ZoneId}) in which the user currently resides.
	 */
	public User(String id, ZoneId timeZone) {
		this.id = id;
		this.timeZone = timeZone;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the identifier of this {@link User} as a {@link String}.
	 *
	 * @return the identifier of this {@link User} as a {@link String}.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets the identifier of this {@link User} as a {@link String}.
	 *
	 * @param id the identifier of this {@link User} as a {@link String}.
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the latest known time zone for this user as one of the IANA Codes defined in {@link
	 * java.util.TimeZone#getAvailableIDs()}.
	 *
	 * @return the latest known time zone for this user as one of the IANA Codes defined in
	 *         {@link java.util.TimeZone#getAvailableIDs()}.
	 */
	public ZoneId getTimeZone() {
		return timeZone;
	}

	/**
	 * Sets the latest known time zone for this user as one of the IANA Codes defined in {@link
	 * java.util.TimeZone#getAvailableIDs()}.
	 *
	 * @param timeZone the latest known time zone for this user as one of the IANA Codes defined in
	 *        {@link java.util.TimeZone#getAvailableIDs()}.
	 */
	public void setTimeZone(ZoneId timeZone) {
		this.timeZone = timeZone;
	}

}

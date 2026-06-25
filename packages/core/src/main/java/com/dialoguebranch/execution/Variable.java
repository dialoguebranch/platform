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

package com.dialoguebranch.execution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * A {@link Variable} models a Dialogue Branch variable at any specific moment in time, e.g. as part
 * of a {@link VariableStore}, using easily serializable parameters (i.e. avoiding Time-related
 * objects).
 *
 * <p>Instances of this class are immutable: all fields are {@code final} and no setters are
 * provided. This is intentional — a {@link Variable} represents a snapshot of state, not a mutable
 * container. When a variable's value changes, the {@link VariableStore} constructs a new
 * {@link Variable} instance and replaces the old one, rather than mutating an existing object.
 * This makes instances safe to share across threads without synchronization.</p>
 *
 * @author Harm op den Akker
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Variable {

	/** The name or 'identifier' of the variable */
	private final String name;

	/** The value of the variable */
	private final Object value;

	/** The last time this variable was updated (epoch time in milliseconds). */
	private final Long updatedTime;

	/** The time zone in which this variable was last updated (as IANA string). */
	private final String updatedTimeZone;

	/** The source of the last update to this variable. */
	private final VariableUpdatedSource updatedSource;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a Variable with a given {@code name}, and {@code value}, as well as
	 * the time at which it was last updated in epoch time ({@code updatedTime}), the timeZone
	 * in which this update took place as IANA code (e.g. "Europe/Lisbon"), and the source of the
	 * update.
	 *
	 * @param name the name (or 'identifier') of the variable.
	 * @param value the value of the variable.
	 * @param updatedTime the timestamp of when this {@link Variable} was last updated (as epoch
	 *                    time in milliseconds)
	 * @param updatedTimeZone the timezone corresponding to the {@code updatedTime} in which this
	 *                        variable was updated (as IANA Code, e.g. "Europe/Lisbon")
	 * @param updatedSource the {@link VariableUpdatedSource} indicating what caused this update.
	 */
	public Variable(
			@JsonProperty("name")
			String name,
			@JsonProperty("value")
			Object value,
			@JsonProperty("updatedTime")
			Long updatedTime,
			@JsonProperty("updatedTimeZone")
			String updatedTimeZone,
			@JsonProperty("updatedSource")
			VariableUpdatedSource updatedSource) {
		this.name = name;
		this.value = value;
		this.updatedTime = updatedTime;
		this.updatedTimeZone = updatedTimeZone;
		this.updatedSource = updatedSource != null ? updatedSource : VariableUpdatedSource.UNKNOWN;
	}

	/**
	 * Creates an instance of a Variable with a given {@code name}, and {@code value}, as well as
	 * the time at which it was last updated as a {@link ZonedDateTime} object, and the source of
	 * the update. From {@code lastUpdated}, the epoch milliseconds and timezone are extracted.
	 *
	 * @param name the name (or 'identifier') of the variable
	 * @param value the value of the variable.
	 * @param lastUpdated the last updated time for this variable in the timezone of the user.
	 * @param updatedSource the {@link VariableUpdatedSource} indicating what caused this update.
	 */
	@JsonIgnore
	public Variable(String name, Object value, ZonedDateTime lastUpdated,
					VariableUpdatedSource updatedSource) {
		this.name = name;
		this.value = value;
		this.updatedTime = lastUpdated.toInstant().toEpochMilli();
		this.updatedTimeZone = lastUpdated.getZone().toString();
		this.updatedSource = updatedSource;
	}

	// ----------------------------------------------------------- //
	// -------------------- Getters & Setters -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Returns the name of this {@link Variable} as a String.
	 *
	 * @return the name of this {@link Variable} as a String.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the value of this {@link Variable} as an {@link Object}.
	 *
	 * @return the value of this {@link Variable} as an {@link Object}.
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * Returns the timestamp of when this {@link Variable} was last updated (as epoch time in
	 * milliseconds).
	 *
	 * @return the UTC timestamp of when this {@link Variable} was last updated (as epoch time
	 *         in milliseconds).
	 */
	public Long getUpdatedTime() {
		return updatedTime;
	}

	/**
	 * Returns the time zone in which this {@link Variable} was last updated (as IANA Code
	 * {@code String}, e.g. "Europe/Lisbon").
	 *
	 * @return the time zone in which this {@link Variable} was last updated (as IANA Code String,
	 *         e.g. "Europe/Lisbon").
	 */
	public String getUpdatedTimeZone() {
		return updatedTimeZone;
	}

	/**
	 * Returns the {@link VariableUpdatedSource} indicating what caused the last update to this
	 * {@link Variable}.
	 *
	 * @return the source of the last update.
	 */
	public VariableUpdatedSource getUpdatedSource() {
		return updatedSource;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Returns a {@link ZonedDateTime} object representing the date/time that this {@link Variable}
	 * was last updated in the timezone of the user, or in the current system default time zone if
	 * no specific time zone is known.
	 *
	 * @return the last updated time for this variable in the timezone of the user.
	 */
	@JsonIgnore
	public ZonedDateTime getZonedUpdatedTime() {
		ZoneId timeZone;

		if (this.getUpdatedTimeZone() == null || this.getUpdatedTimeZone().isEmpty()) {
			timeZone = ZoneId.systemDefault();
		} else {
			timeZone = ZoneId.of(this.getUpdatedTimeZone());
		}

		if(this.getUpdatedTime() == null) {
			return ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), timeZone);
		} else {
			return ZonedDateTime.ofInstant(Instant.ofEpochMilli(this.getUpdatedTime()), timeZone);
		}
	}

	/**
	 * Returns a human-readable representation of this {@link Variable}, listing its name, value,
	 * last-updated epoch timestamp, and last-updated time zone.
	 *
	 * @return a string representation of this {@link Variable}
	 */
	@Override
	public String toString() {
		return "Variable{" +
				"name='" + name + '\'' +
				", value=" + value +
				", updatedTime=" + updatedTime +
				", updatedTimeZone='" + updatedTimeZone + '\'' +
				", updatedSource=" + updatedSource +
				'}';
	}

}

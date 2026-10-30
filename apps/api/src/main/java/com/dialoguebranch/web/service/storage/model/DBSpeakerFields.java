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

package com.dialoguebranch.web.service.storage.model;

import com.dialoguebranch.model.common.Speaker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/**
 * What a project's speaker (#393, #394) stores, whether in the draft ({@link DBDraftSpeaker}) or
 * in a published version ({@link DBPublishedSpeaker}): its name, gender, whether it is the
 * default, and its position in the project's list. In JSON a speaker is
 * {@code {"name", "gender", "default"}}, with {@code gender} one of {@code male},
 * {@code female} or {@code undefined}.
 *
 * @author Harm op den Akker
 */
@MappedSuperclass
public abstract class DBSpeakerFields {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@JsonIgnore
	private UUID id;

	/** Creates the fields of an empty speaker row, for a subclass. */
	protected DBSpeakerFields() {
	}

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Column(name = "gender", nullable = false, length = 16)
	private String gender = Speaker.Gender.UNDEFINED.value();

	@Column(name = "is_default", nullable = false)
	private boolean isDefault;

	@Column(name = "position", nullable = false)
	@JsonIgnore
	private int position;

	/**
	 * Returns the row's identifier.
	 * @return the identifier.
	 */
	public UUID getId() { return id; }

	/**
	 * Returns the speaker's name.
	 * @return the name.
	 */
	public String getName() { return name; }

	/**
	 * Sets the speaker's name.
	 * @param name the name.
	 */
	public void setName(String name) { this.name = name; }

	/**
	 * Returns the speaker's gender: {@code male}, {@code female} or {@code undefined}.
	 * @return the gender.
	 */
	public String getGender() { return gender; }

	/**
	 * Sets the speaker's gender.
	 * @param gender {@code male}, {@code female} or {@code undefined}.
	 */
	public void setGender(String gender) { this.gender = gender; }

	/**
	 * Returns whether this is the project's default speaker.
	 * @return {@code true} for the default.
	 */
	@JsonProperty("default")
	public boolean isDefault() { return isDefault; }

	/**
	 * Sets whether this is the project's default speaker.
	 * @param isDefault {@code true} for the default.
	 */
	public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

	/**
	 * Returns the speaker's position in the project's list, from 0.
	 * @return the position.
	 */
	public int getPosition() { return position; }

	/**
	 * Sets the speaker's position in the project's list.
	 * @param position the position, from 0.
	 */
	public void setPosition(int position) { this.position = position; }

	/**
	 * Copies the name, gender and default of the given speaker into this row.
	 *
	 * @param speaker the speaker.
	 * @param position its position in the project's list.
	 */
	public void copyFrom(Speaker speaker, int position) {
		setName(speaker.name());
		setGender(speaker.gender().value());
		setDefault(speaker.isDefault());
		setPosition(position);
	}

	/**
	 * Returns this row as core's {@link Speaker}.
	 *
	 * @return the speaker.
	 */
	public Speaker toSpeaker() {
		Speaker.Gender parsed = Speaker.Gender.fromValue(gender);
		return new Speaker(name, parsed != null ? parsed : Speaker.Gender.UNDEFINED, isDefault);
	}
}

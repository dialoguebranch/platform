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
package com.dialoguebranch.web.service.controller.schema.authoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A speaker in the {@code speakers} list of {@code POST /project/update-draft} (#394):
 * {@code {"name", "gender", "default"}}.
 *
 * @author Harm op den Akker
 */
public class SpeakerPayload {

	@Schema(description = "The speaker's name, as used in a node's speaker header",
			example = "Martin McOwl")
	private String name;

	@Schema(description = "male, female or undefined (the default)", example = "male")
	private String gender;

	@Schema(description = "Whether new nodes get this speaker; at most one speaker can be the "
			+ "default")
	private boolean isDefault;

	/** Creates an empty payload. */
	public SpeakerPayload() { }

	/**
	 * Creates a payload.
	 *
	 * @param name the speaker's name.
	 * @param gender male, female or undefined, or {@code null} for undefined.
	 * @param isDefault whether this is the default speaker.
	 */
	public SpeakerPayload(String name, String gender, boolean isDefault) {
		this.name = name;
		this.gender = gender;
		this.isDefault = isDefault;
	}

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
	 * Returns the speaker's gender.
	 * @return male, female or undefined, or {@code null} for undefined.
	 */
	public String getGender() { return gender; }

	/**
	 * Sets the speaker's gender.
	 * @param gender male, female or undefined, or {@code null} for undefined.
	 */
	public void setGender(String gender) { this.gender = gender; }

	/**
	 * Returns whether this is the default speaker.
	 * @return {@code true} for the default.
	 */
	@JsonProperty("default")
	public boolean isDefault() { return isDefault; }

	/**
	 * Sets whether this is the default speaker.
	 * @param isDefault {@code true} for the default.
	 */
	@JsonProperty("default")
	public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}

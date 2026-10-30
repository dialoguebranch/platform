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
package com.dialoguebranch.model.common;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A speaker of a Dialogue Branch project, as defined in its metadata (#393): a name a node's
 * {@code speaker} header can use, with a gender for translations into languages where a text
 * changes with it, and whether new nodes get this speaker by default.
 *
 * @param name      the speaker's name, as used in a node's {@code speaker} header.
 * @param gender    the speaker's gender; {@link Gender#UNDEFINED} when not given.
 * @param isDefault whether this is the project's default speaker. This only helps editors, who
 *                  give it to a new node; it has no meaning when a dialogue runs.
 * @author Harm op den Akker
 */
public record Speaker(String name, Gender gender, boolean isDefault) {

	/** The gender of a speaker, as written in project metadata. */
	public enum Gender {
		/** Masculine. */
		MALE("male"),
		/** Feminine. */
		FEMALE("female"),
		/** Not given: translations choose as if no gender were known. */
		UNDEFINED("undefined");

		private final String value;

		Gender(String value) {
			this.value = value;
		}

		/**
		 * Returns how this gender is written in project metadata.
		 *
		 * @return {@code male}, {@code female} or {@code undefined}.
		 */
		public String value() {
			return value;
		}

		/**
		 * Returns the gender written as {@code value}, or {@code null} if it is none of them.
		 *
		 * @param value {@code male}, {@code female} or {@code undefined}, in any case.
		 * @return the gender, or {@code null}.
		 */
		public static @Nullable Gender fromValue(String value) {
			for (Gender gender : values()) {
				if (gender.value.equals(value.toLowerCase(Locale.ROOT))) return gender;
			}
			return null;
		}
	}

	/**
	 * Creates a speaker.
	 *
	 * @param name      the speaker's name.
	 * @param gender    the speaker's gender, or {@code null} for {@link Gender#UNDEFINED}.
	 * @param isDefault whether this is the project's default speaker.
	 */
	public Speaker {
		Objects.requireNonNull(name, "A speaker needs a name");
		if (gender == null) gender = Gender.UNDEFINED;
	}

	/**
	 * Checks a project's speakers against the rules every project's speakers follow, wherever they
	 * come from: each has a name that isn't empty, doesn't start or end with spaces, and isn't
	 * the reserved {@link DialogueBranchConstants#DLB_USER_SPEAKER}; no two have the same name;
	 * and at most one is the default.
	 *
	 * @param speakers the speakers to check.
	 * @throws IllegalArgumentException if one of the rules is broken, saying which.
	 */
	public static void validate(List<Speaker> speakers) {
		Set<String> names = new HashSet<>();
		int defaults = 0;
		for (Speaker speaker : speakers) {
			String name = speaker.name();
			if (name.isBlank()) {
				throw new IllegalArgumentException("A speaker's name can't be empty.");
			}
			if (!name.equals(name.strip())) {
				throw new IllegalArgumentException("The speaker name '" + name + "' starts or "
						+ "ends with spaces.");
			}
			if (name.equals(DialogueBranchConstants.DLB_USER_SPEAKER)) {
				throw new IllegalArgumentException("The speaker name '" + name + "' is reserved "
						+ "for the user's replies.");
			}
			if (!names.add(name)) {
				throw new IllegalArgumentException("The speaker '" + name + "' is defined more "
						+ "than once.");
			}
			if (speaker.isDefault()) defaults++;
		}
		if (defaults > 1) {
			throw new IllegalArgumentException("Only one speaker can be the default, but "
					+ defaults + " are.");
		}
	}
}

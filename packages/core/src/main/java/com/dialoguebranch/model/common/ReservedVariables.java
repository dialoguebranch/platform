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

import java.util.Set;

/**
 * The reserved variables: names starting with {@code dlb.} have a predefined meaning (#400).
 * Setting a {@code $dlb.} variable that isn't defined here is refused, and a defined one is
 * checked against its allowed values. The checks run when a script is parsed (a {@code <<set>>}
 * with a literal value, or an input command storing into such a variable) and in the
 * {@link com.dialoguebranch.execution.VariableStore} (every other way a value is set).
 *
 * <p>Defined so far:</p>
 * <ul>
 *   <li>{@code $dlb.userGender}: the end user's gender, {@code male}, {@code female} or
 *   {@code undefined}; empty ({@code null}) means {@code undefined}. Translations into languages
 *   where a text changes with the gender of the one addressed use it.</li>
 * </ul>
 *
 * @author Harm op den Akker
 */
public final class ReservedVariables {

	/** The prefix of every reserved variable name. */
	public static final String PREFIX = "dlb.";

	/** The end user's gender: {@code male}, {@code female} or {@code undefined}. */
	public static final String USER_GENDER = "dlb.userGender";

	private static final Set<String> GENDERS = Set.of("male", "female", "undefined");

	/** Utility class — no instances. */
	private ReservedVariables() {
	}

	/**
	 * Returns whether a variable name is in the reserved {@code dlb.} namespace.
	 *
	 * @param name the variable name, without {@code $}.
	 * @return {@code true} if it starts with {@code dlb.}.
	 */
	public static boolean isReserved(String name) {
		return name.startsWith(PREFIX);
	}

	/**
	 * Checks that a variable name may be set: any name outside the {@code dlb.} namespace, or a
	 * defined reserved variable.
	 *
	 * @param name the variable name, without {@code $}.
	 * @return {@code null} if it may be set, or the reason it can't.
	 */
	public static @Nullable String checkName(String name) {
		if (!isReserved(name) || USER_GENDER.equals(name))
			return null;
		return "\"$" + name + "\" can't be set: names starting with \"" + PREFIX
				+ "\" are reserved for variables with a predefined meaning, and this one isn't"
				+ " defined.";
	}

	/**
	 * Checks that a value may be stored in a variable: the name must pass {@link #checkName}, and
	 * a defined reserved variable only takes its allowed values.
	 *
	 * @param name  the variable name, without {@code $}.
	 * @param value the value.
	 * @return {@code null} if it may be stored, or the reason it can't.
	 */
	public static @Nullable String check(String name, @Nullable Object value) {
		String problem = checkName(name);
		if (problem != null || !USER_GENDER.equals(name))
			return problem;
		if (value == null || (value instanceof String text && GENDERS.contains(text)))
			return null;
		return "\"$" + name + "\" can only be \"male\", \"female\", \"undefined\" or empty, not "
				+ (value instanceof String ? "\"" + value + "\"" : String.valueOf(value)) + ".";
	}
}

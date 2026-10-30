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

package com.dialoguebranch.web.varservice;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds this service's values for the {@code example.*} variables, which it reports as supported
 * and which dialogues may change: per user (issuer and subject) and project, in memory only, so
 * they are lost when the service restarts.
 *
 * <p>A variable without a value yet starts with a random value of its declared type. A change a
 * dialogue makes (through {@code notify-updated}) is kept and handed out again, until the
 * Web Service reports that the user's variables were cleared.</p>
 *
 * @author Harm op den Akker
 */
@Component
public class ExampleVariables {

	/** The example variables, none of which is read-only. */
	public static final Set<String> NAMES = Set.of("example.number", "example.flag",
			"example.choice", "example.noHints");

	private final Map<String, Map<String, Object>> valuesByUser = new ConcurrentHashMap<>();

	/**
	 * Returns whether the given name is one of the example variables.
	 *
	 * @param name a variable name.
	 * @return {@code true} for one of {@link #NAMES}.
	 */
	public boolean isExample(String name) {
		return NAMES.contains(name);
	}

	/**
	 * Returns this service's value of an example variable for a user in a project, starting it
	 * with a random value if it has none yet. For a test session, a new random value isn't kept,
	 * since a test session's values are discarded afterwards.
	 *
	 * @param issuer the user's token issuer.
	 * @param subject the user's subject.
	 * @param projectSlug the project's slug.
	 * @param name one of {@link #NAMES}.
	 * @param testSession whether the request comes from a test session.
	 * @return the value.
	 */
	public Object get(String issuer, String subject, String projectSlug, String name,
			boolean testSession) {
		Map<String, Object> values = valuesFor(issuer, subject, projectSlug);
		Object value = values.get(name);
		if (value == null) {
			value = randomValue(name);
			if (!testSession) values.put(name, value);
		}
		return value;
	}

	/**
	 * Keeps a dialogue's change to an example variable. A {@code null} value removes it, so it
	 * starts again with a random value.
	 *
	 * @param issuer the user's token issuer.
	 * @param subject the user's subject.
	 * @param projectSlug the project's slug.
	 * @param name one of {@link #NAMES}.
	 * @param value the new value, or {@code null}.
	 */
	public void set(String issuer, String subject, String projectSlug, String name,
			Object value) {
		Map<String, Object> values = valuesFor(issuer, subject, projectSlug);
		if (value == null) values.remove(name);
		else values.put(name, value);
	}

	/**
	 * Forgets all example values of a user in a project.
	 *
	 * @param issuer the user's token issuer.
	 * @param subject the user's subject.
	 * @param projectSlug the project's slug.
	 */
	public void clear(String issuer, String subject, String projectSlug) {
		valuesByUser.remove(key(issuer, subject, projectSlug));
	}

	/**
	 * Returns whether this service keeps a value of an example variable for a user in a project.
	 *
	 * @param issuer the user's token issuer.
	 * @param subject the user's subject.
	 * @param projectSlug the project's slug.
	 * @param name one of {@link #NAMES}.
	 * @return {@code true} if a value is kept.
	 */
	boolean hasValue(String issuer, String subject, String projectSlug, String name) {
		Map<String, Object> values = valuesByUser.get(key(issuer, subject, projectSlug));
		return values != null && values.containsKey(name);
	}

	private Map<String, Object> valuesFor(String issuer, String subject, String projectSlug) {
		return valuesByUser.computeIfAbsent(key(issuer, subject, projectSlug),
				k -> new ConcurrentHashMap<>());
	}

	private static String key(String issuer, String subject, String projectSlug) {
		return issuer + "\n" + subject + "\n" + projectSlug;
	}

	/**
	 * Returns a random value of the type an example variable's entry in
	 * {@code /variables/supported} declares: a number from 0 to 99 for {@code example.number}, a
	 * boolean for {@code example.flag}, one of its allowed values for {@code example.choice}, and
	 * a short string for {@code example.noHints}.
	 */
	private static Object randomValue(String name) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return switch (name) {
			case "example.number" -> random.nextInt(100);
			case "example.flag" -> random.nextBoolean();
			case "example.choice" -> List.of("a", "b", "c").get(random.nextInt(3));
			default -> "example-" + random.nextInt(1000);
		};
	}
}

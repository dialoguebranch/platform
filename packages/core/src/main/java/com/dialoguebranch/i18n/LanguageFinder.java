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

package com.dialoguebranch.i18n;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Picks the best available language for a project from a set of language codes, given the caller's
 * preferred {@link Locale}.
 *
 * <p>Matching follows the RFC 4647 "lookup" algorithm (via {@link Locale#lookupTag}) against a
 * priority list of: the preferred locale, then {@code en-GB}, {@code en-US} and {@code en} as the
 * English fallback. A language code matches a range when it equals the range or extends it with a
 * further subtag, so a request for {@code en} is satisfied by a project that only ships
 * {@code en-US}.</p>
 *
 * <p>The available codes and the returned value are BCP 47 language tags (e.g. {@code en},
 * {@code nl-NL}); the returned string is the matching entry from {@code availableLanguages}
 * verbatim, or {@code null} when nothing matches.</p>
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public final class LanguageFinder {

	private LanguageFinder() {
	}

	/**
	 * Returns the entry of {@code availableLanguages} that best matches {@code preferredLocale},
	 * falling back to British, American and then generic English. Returns {@code null} if none of
	 * the available languages match; callers then typically fall back to an arbitrary available
	 * language.
	 *
	 * @param availableLanguages the language codes a project provides (BCP 47 tags)
	 * @param preferredLocale the caller's preferred locale
	 * @return the best matching entry from {@code availableLanguages}, or {@code null}
	 */
	public static @Nullable String find(Collection<String> availableLanguages,
			Locale preferredLocale) {
		List<Locale.LanguageRange> priorityList = new ArrayList<>();
		priorityList.add(new Locale.LanguageRange(preferredLocale.toLanguageTag(), 1.0));
		priorityList.add(new Locale.LanguageRange("en-GB", 0.75));
		priorityList.add(new Locale.LanguageRange("en-US", 0.5));
		priorityList.add(new Locale.LanguageRange("en", 0.25));
		return Locale.lookupTag(priorityList, availableLanguages);
	}

}

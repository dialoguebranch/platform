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

package com.dialoguebranch.i18n;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link TranslationParser}: a translation string may contain text, variables and
 * {@code <<input>>} commands; anything else is reported as a parse error, not a crash (#379).
 */
public class TranslationParserTest {

	private static TranslationParserResult parse(String json) throws IOException {
		return TranslationParser.parse(new StringReader(json));
	}

	private static void assertParseError(String json, String expected) throws IOException {
		TranslationParserResult result = parse(json);
		List<String> messages = result.getParseErrors().stream()
				.map(Throwable::getMessage).toList();
		assertTrue("expected a parse error containing '" + expected + "', got " + messages,
				messages.stream().anyMatch(m -> m.contains(expected)));
	}

	@Test
	public void parsesTextVariablesAndInputCommands() throws IOException {
		TranslationParserResult result = parse("{\"Martin\": {" +
				"\"Hello $name.\": \"Hallo $name.\"," +
				"\"I am <<input type=\\\"text\\\" value=\\\"$age\\\">> years old.\": " +
				"\"Ik ben <<input type=\\\"text\\\" value=\\\"$age\\\">> jaar oud.\"}}");
		assertEquals(List.of(), result.getParseErrors());
		Map<Translatable, List<ContextTranslation>> translations = result.getTranslations();
		assertNotNull(translations);
		assertEquals(2, translations.size());
	}

	@Test
	public void aCommandInAKeyIsAParseError() throws IOException {
		assertParseError("{\"Martin\": {\"Hello <<if $x>>.\": \"Hallo.\"}}",
				"Failed to parse translation key");
		assertParseError("{\"Martin\": {\"Hello <<if $x>>.\": \"Hallo.\"}}",
				"Unexpected command: if");
	}

	@Test
	public void aCommandInAValueIsAParseError() throws IOException {
		assertParseError("{\"Martin\": {\"Hello.\": \"Hallo <<set $x = 1>>.\"}}",
				"Failed to parse translation value");
		assertParseError("{\"Martin\": {\"Hello.\": \"Hallo <<set $x = 1>>.\"}}",
				"Unexpected command: set");
	}

	@Test
	public void anUnknownCommandIsAParseError() throws IOException {
		assertParseError("{\"Martin\": {\"Hello.\": \"Hallo <<foo>>.\"}}",
				"Unexpected command: foo");
	}

	@Test
	public void aReplyIsAParseError() throws IOException {
		assertParseError("{\"Martin\": {\"Hello [[Next|Start]].\": \"Hallo.\"}}",
				"Unexpected start of reply");
	}

	@Test
	public void theOtherTranslationsStillParse() throws IOException {
		TranslationParserResult result = parse("{\"Martin\": {" +
				"\"Hello.\": \"Hallo <<if $x>>.\", \"Bye.\": \"Dag.\"}}");
		assertEquals(1, result.getParseErrors().size());
	}
}

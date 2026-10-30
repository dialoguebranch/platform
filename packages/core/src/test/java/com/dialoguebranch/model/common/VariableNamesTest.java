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

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.expression.ExpressionParser;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.i18n.TranslationParser;
import com.dialoguebranch.i18n.TranslationParserResult;
import com.dialoguebranch.i18n.Translator;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.ResolvedNodeBody;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Dots in variable names (#432): the rule itself, and that body text, {@code <<set>>},
 * {@code <<if>>} and expressions all read a dotted name the same way.
 */
public class VariableNamesTest {

	@Test
	public void validNames() {
		assertTrue(VariableNames.isValid("brightness"));
		assertTrue(VariableNames.isValid("_internal"));
		assertTrue(VariableNames.isValid("user.settings.brightness"));
		assertTrue(VariableNames.isValid("steps.today2"));
		assertTrue(VariableNames.isValid("a._b"));
	}

	@Test
	public void invalidNames() {
		assertFalse(VariableNames.isValid(null));
		assertFalse(VariableNames.isValid(""));
		assertFalse(VariableNames.isValid("2fast"));
		assertFalse(VariableNames.isValid(".user"));
		assertFalse(VariableNames.isValid("user."));
		assertFalse(VariableNames.isValid("user..settings"));
		assertFalse(VariableNames.isValid("value.5"));
		assertFalse(VariableNames.isValid("user-name"));
	}

	@Test
	public void aDotIsPartOfTheNameOnlyWhenALetterOrUnderscoreFollows() {
		assertEquals("user.settings.brightness", nameAt("$user.settings.brightness is set"));
		assertEquals("readingTime", nameAt("$readingTime."));
		assertEquals("readingTime", nameAt("$readingTime. Then"));
		assertEquals("value", nameAt("$value.5"));
		assertEquals("a", nameAt("$a..b"));
		assertEquals("a._b", nameAt("$a._b"));
		assertEquals("", nameAt("$5"));
	}

	private static String nameAt(String text) {
		return text.substring(1, VariableNames.end(text, 1));
	}

	@Test
	public void bodyTextReadsADottedName() throws Exception {
		ResolvedNodeBody body = execute("""
				Brightness $user.settings.brightness, taken at $readingTime.
				""", new HashMap<>(Map.of("user.settings.brightness", 7, "readingTime", "noon")));

		assertEquals("Brightness 7, taken at noon.", body.getSegments().get(0).toString());
	}

	@Test
	public void setAndIfUseTheDottedNameAsOneVariable() throws Exception {
		Map<String,Object> variables = new LinkedHashMap<>();
		ResolvedNodeBody body = execute("""
				<<set $user.settings.screenHalf = "left">>
				<<if $user.settings.screenHalf == "left">>Left half.<<else>>Whole screen.<<endif>>
				""", variables);

		assertEquals("left", variables.get("user.settings.screenHalf"));
		assertFalse(variables.containsKey("user"));
		assertEquals("Left half.", body.getSegments().get(0).toString().trim());
	}

	@Test
	public void anExpressionReadsADottedNameAsOneVariable() throws Exception {
		Map<String,Object> variables = new HashMap<>();
		variables.put("steps.today", 4200);
		assertEquals(4200, evaluate("$steps.today", variables));
		assertEquals(Set.of("steps.today"), variableNames("$steps.today + 1"));
	}

	@Test
	public void aDotAfterAVariableNoLongerReadsAMapField() throws Exception {
		Map<String,Object> variables = new HashMap<>();
		variables.put("m", Map.of("key", "value"));
		variables.put("m.key", "dotted");
		// The expression engine can hold a map in a variable (undocumented); $m.key is the
		// variable named "m.key", never the field of $m.
		assertEquals("dotted", evaluate("$m.key", variables));
		variables.remove("m.key");
		assertNull(evaluate("$m.key", variables));
	}

	@Test
	public void aTranslationKeepsADottedName() throws Exception {
		String dlb = "title: Start\nspeaker: Robot\n---\nSteps today: $steps.today.\n===\n";
		Dialogue dialogue;
		try (DialogueBranchParser parser =
				new DialogueBranchParser("dotted", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertEquals(List.of(), result.getParseErrors());
			dialogue = result.getDialogue();
		}
		TranslationParserResult translations = TranslationParser.parse(new StringReader(
				"{\"Robot\": {\"Steps today: $steps.today.\": \"Stappen vandaag: $steps.today.\"}}"));
		assertEquals(List.of(), translations.getParseErrors());
		Dialogue translated = new Translator(new TranslationContext(),
				translations.getTranslations()).translate(dialogue);

		NodeBody body = (NodeBody) translated.getNodeById("Start").getBody();
		ResolvedNodeBody resolved = body.execute(new HashMap<>(Map.of("steps.today", 4200)), true);
		assertEquals("Stappen vandaag: 4200.", resolved.getSegments().get(0).toString().trim());
	}

	@Test
	public void aDotFollowedByANumberIsNotPartOfTheName() throws Exception {
		assertEquals(Set.of("value"), variableNames("$value.5"));
	}

	private static Object evaluate(String expression, Map<String,Object> variables)
			throws Exception {
		ExpressionParser parser = scriptExpressionParser(expression);
		try {
			return parser.readExpression().evaluate(variables).getValue();
		} finally {
			parser.close();
		}
	}

	private static Set<String> variableNames(String expression) throws Exception {
		ExpressionParser parser = scriptExpressionParser(expression);
		try {
			return parser.readExpression().getVariableNames();
		} finally {
			parser.close();
		}
	}

	/** An expression parser configured as for a script: {@code $} variables, no plain ones. */
	private static ExpressionParser scriptExpressionParser(String expression) {
		ExpressionParser parser = new ExpressionParser(expression);
		parser.getConfig().setAllowDollarVariables(true);
		parser.getConfig().setAllowPlainVariables(false);
		return parser;
	}

	private static ResolvedNodeBody execute(String body, Map<String,Object> variables)
			throws Exception {
		NodeBody nodeBody = (NodeBody) parseStartNode(body).getBody();
		return nodeBody.execute(variables, true);
	}

	private static Node parseStartNode(String body) throws IOException {
		String dlb = "title: Start\nspeaker: Robot\n---\n" + body + "===\n";
		try (DialogueBranchParser parser =
				new DialogueBranchParser("dotted", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue().getNodeById("Start");
		}
	}
}

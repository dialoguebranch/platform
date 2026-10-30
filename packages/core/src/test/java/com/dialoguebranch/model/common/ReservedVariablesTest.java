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

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.expression.Expression;
import com.dialoguebranch.expression.ExpressionParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests the reserved {@code $dlb.} variables (#400): an undefined one can't be set, and
 * {@code $dlb.userGender} only takes {@code male}, {@code female}, {@code undefined} or empty.
 */
public class ReservedVariablesTest {

	private static List<ParseException> parse(String body) throws IOException {
		String script = "title: Start\nspeaker: Narrator\n---\n" + body + "\n===\n";
		try (DialogueBranchParser parser =
					 new DialogueBranchParser("main", new StringReader(script))) {
			ParserResult result = parser.readDialogue();
			return result.getParseErrors();
		}
	}

	@Test
	public void checkAllowsOrdinaryAndDefinedReservedVariables() {
		assertNull(ReservedVariables.check("dlbx", 1));
		assertNull(ReservedVariables.check("app.userGender", "anything"));
		assertNull(ReservedVariables.check("dlb.userGender", "female"));
		assertNull(ReservedVariables.check("dlb.userGender", "undefined"));
		assertNull(ReservedVariables.check("dlb.userGender", null));
		assertNotNull(ReservedVariables.check("dlb.userGender", "f"));
		assertNotNull(ReservedVariables.check("dlb.userGender", 1));
		assertNotNull(ReservedVariables.check("dlb.somethingElse", 1));
	}

	@Test
	public void aScriptCantSetAnUndefinedReservedVariableOrADisallowedLiteral() throws IOException {
		assertFalse(String.valueOf(parse("<<set $dlb.foo = 1>>Hi.")), parse("<<set $dlb.foo = 1>>Hi.").isEmpty());
		assertFalse(String.valueOf(parse("<<set $dlb.userGender = \"f\">>Hi.")), parse("<<set $dlb.userGender = \"f\">>Hi.").isEmpty());
		assertTrue(String.valueOf(parse("<<set $dlb.userGender = \"female\">>Hi.")), parse("<<set $dlb.userGender = \"female\">>Hi.").isEmpty());
		assertTrue(String.valueOf(parse("<<set $userGender = \"f\">>Hi.")), parse("<<set $userGender = \"f\">>Hi.").isEmpty());
	}

	@Test
	public void aComputedDisallowedValueIsRefusedWhenItIsSet() throws Exception {
		ExpressionParser parser = new ExpressionParser("$dlb.userGender = $answer");
		parser.getConfig().setAllowDollarVariables(true);
		Expression expr = parser.readExpression();
		Map<String, Object> variables = new HashMap<>();
		variables.put("answer", "f");
		assertThrows(EvaluationException.class, () -> expr.evaluate(variables));
		variables.put("answer", "male");
		expr.evaluate(variables);
		assertEquals("male", variables.get("dlb.userGender"));
	}

	@Test
	public void theVariableStoreRefusesDisallowedValues() {
		VariableStore store = new VariableStore(new User("test"));
		ZonedDateTime now = ZonedDateTime.now();
		assertThrows(IllegalArgumentException.class,
				() -> store.setValue("dlb.userGender", "f", false, now));
		assertThrows(IllegalArgumentException.class,
				() -> store.addAll(Map.of("ok", 1, "dlb.foo", 2), false, now));
		assertNull("Nothing of a refused batch is stored", store.getValue("ok"));
		store.setValue("dlb.userGender", "female", false, now);
		assertEquals("female", store.getValue("dlb.userGender"));
	}
}

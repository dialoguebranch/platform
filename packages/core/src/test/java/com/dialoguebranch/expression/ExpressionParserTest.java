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

package com.dialoguebranch.expression;

import com.dialoguebranch.exception.LineNumberParseException;
import com.dialoguebranch.exception.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for the expression engine vendored from {@code rrd-utils} in #102: it still parses
 * and evaluates the arithmetic, comparison, boolean, variable, assignment and string-interpolation
 * forms the {@code .dlb} {@code <<if>>} / {@code <<set>>} sublanguage relies on, and still reports
 * a syntax error as a {@link LineNumberParseException}.
 */
public class ExpressionParserTest {

	private static Object evaluate(String expression, Map<String, Object> variables)
			throws ParseException, IOException, EvaluationException {
		ExpressionParser parser = new ExpressionParser(expression);
		try {
			return parser.readExpression().evaluate(variables).getValue();
		} finally {
			parser.close();
		}
	}

	private static Object evaluate(String expression)
			throws ParseException, IOException, EvaluationException {
		return evaluate(expression, new HashMap<>());
	}

	private static long evaluateNumber(String expression) throws Exception {
		return ((Number) evaluate(expression)).longValue();
	}

	@Test
	public void arithmeticWithPrecedence() throws Exception {
		assertEquals(14L, evaluateNumber("2 + 3 * 4"));
	}

	@Test
	public void comparisonAndBooleanOperators() throws Exception {
		assertEquals(true, evaluate("3 > 2 && 1 <= 1"));
		assertEquals(false, evaluate("3 > 2 && 2 > 3"));
	}

	@Test
	public void variablesAreReadFromTheMap() throws Exception {
		Map<String, Object> vars = new HashMap<>();
		vars.put("age", 21);
		assertEquals(true, evaluate("age >= 18", vars));
	}

	@Test
	public void assignmentMutatesTheVariableMap() throws Exception {
		Map<String, Object> vars = new HashMap<>();
		evaluate("score = 5 + 5", vars);
		assertEquals(10L, ((Number) vars.get("score")).longValue());
	}

	@Test
	public void stringInterpolationExpandsEmbeddedExpressions() throws Exception {
		Map<String, Object> vars = new HashMap<>();
		vars.put("name", "Alice");
		Value value = new StringExpression("Hi ${name}, ${1 + 1} new messages")
				.evaluate(vars);
		assertEquals("Hi Alice, 2 new messages", value.getValue());
	}

	@Test
	public void aSyntaxErrorIsALineNumberParseException() {
		LineNumberParseException ex = assertThrows(LineNumberParseException.class,
				() -> evaluate("("));
		assertTrue(ex.getLineNum() >= 1);
	}
}

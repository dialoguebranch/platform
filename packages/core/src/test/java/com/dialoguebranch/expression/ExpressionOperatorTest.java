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
import com.dialoguebranch.expression.types.BinaryExpression;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
/**
 * Evaluates every {@code .dlb} expression operator through {@link ExpressionParser}, exercising
 * the {@code evaluate()} of each {@code com.dialoguebranch.expression.types} AST node plus the
 * parser's own edge and error paths. Part of the #90 area-C test hardening (#156).
 */
public class ExpressionOperatorTest {

	private static Object eval(String expr) throws Exception {
		return eval(expr, new HashMap<>());
	}

	private static Object eval(String expr, Map<String, Object> vars) throws Exception {
		ExpressionParser parser = new ExpressionParser(expr);
		try {
			return parser.readExpression().evaluate(vars).getValue();
		} finally {
			parser.close();
		}
	}

	private static long num(String expr) throws Exception {
		return num(expr, new HashMap<>());
	}

	private static long num(String expr, Map<String, Object> vars) throws Exception {
		return ((Number) eval(expr, vars)).longValue();
	}

	/** Normalises a list of numbers to {@code List<Long>} so element boxing type doesn't matter. */
	private static List<Long> longs(Object listValue) {
		List<Long> result = new ArrayList<>();
		for (Object element : (List<?>) listValue)
			result.add(((Number) element).longValue());
		return result;
	}

	private static void evalThrows(String expr) {
		assertThrows(EvaluationException.class, () -> eval(expr));
	}

	// ---------------------------------------------------------------- //
	// -------------------- arithmetic -------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void addition() throws Exception {
		assertEquals(5L, num("2 + 3"));
		assertEquals("ab", eval("\"a\" + \"b\""));                    // string concat
		assertEquals(List.of(1L, 2L), longs(eval("[1] + [2]")));      // list merge
		Map<?, ?> merged = (Map<?, ?>) eval("{\"a\": 1} + {\"b\": 2}");
		assertEquals(Set.of("a", "b"), merged.keySet());              // map merge
	}

	@Test
	public void subtraction() throws Exception {
		assertEquals(3L, num("5 - 2"));
		assertEquals(1.5, eval("2.5 - 1"));
		assertEquals(List.of(1L, 3L), longs(eval("[1, 2, 3] - 2")));  // remove element from list
		assertEquals(List.of(1L), longs(eval("[1, 2, 3] - [2, 3]"))); // subtract a list
		Map<?, ?> less = (Map<?, ?>) eval("{\"a\": 1, \"b\": 2} - \"a\"");
		assertEquals(Set.of("b"), less.keySet());                     // remove a key
	}

	@Test
	public void multiplicationAndDivision() throws Exception {
		assertEquals(12L, num("3 * 4"));
		assertEquals(5.0, eval("2.5 * 2"));
		assertEquals(3L, num("6 / 2"));       // exact -> int
		assertEquals(3.5, eval("7 / 2"));     // inexact -> double
	}

	@Test
	public void unaryMinusAndPrecedence() throws Exception {
		assertEquals(-5L, num("-5"));
		assertEquals(1L, num("2 + -1"));
		assertEquals(20L, num("(2 + 3) * 4"));  // GroupExpression
		assertEquals(14L, num("2 + 3 * 4"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- boolean / logical ------------------ //
	// ---------------------------------------------------------------- //

	@Test
	public void logicalNot() throws Exception {
		assertEquals(false, eval("!true"));
		assertEquals(true, eval("!0"));       // 0 is falsy
		assertEquals(true, eval("!\"\""));
	}

	@Test
	public void logicalOrShortCircuits() throws Exception {
		assertEquals(true, eval("false || true"));
		assertEquals(false, eval("false || false"));
		// the right operand would divide by zero, but || must not evaluate it
		assertEquals(true, eval("true || (1 / 0)"));
	}

	@Test
	public void logicalAnd() throws Exception {
		assertEquals(false, eval("false && true"));
		assertEquals(true, eval("true && 1 == 1"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- equality --------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void looseEqualityCoerces() throws Exception {
		assertEquals(true, eval("1 == \"1\""));
		assertEquals(true, eval("1 != 2"));
		assertEquals(true, eval("null == null"));
		assertEquals(false, eval("null == 0"));
	}

	@Test
	public void strictEqualityChecksType() throws Exception {
		assertEquals(true, eval("1 === 1"));
		assertEquals(false, eval("1 === \"1\""));
		assertEquals(true, eval("1 !== \"1\""));
	}

	// ---------------------------------------------------------------- //
	// -------------------- comparison ------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void relationalOperators() throws Exception {
		assertEquals(true, eval("1 < 2"));
		assertEquals(true, eval("2 <= 2"));
		assertEquals(true, eval("3 > 2"));
		assertEquals(true, eval("2 >= 2"));
		assertEquals(true, eval("\"a\" < \"b\""));  // string comparison
		evalThrows("1 < true");                       // boolean is not orderable
	}

	// ---------------------------------------------------------------- //
	// -------------------- in --------------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void inOperator() throws Exception {
		assertEquals(true, eval("2 in [1, 2, 3]"));
		assertEquals(false, eval("5 in [1, 2, 3]"));
		assertEquals(true, eval("\"ell\" in \"hello\""));
		evalThrows("1 in 5");                          // right side not a string/list
	}

	// ---------------------------------------------------------------- //
	// -------------------- list / map literals + access ------ //
	// ---------------------------------------------------------------- //

	@Test
	public void listLiteralAndIndexing() throws Exception {
		assertEquals(List.of(10L, 20L, 30L), longs(eval("[10, 20, 30]")));
		assertEquals(20L, num("[10, 20, 30][1]"));
		assertEquals("e", eval("\"hello\"[1]"));
		assertEquals(9L, num("{\"k\": 9}[\"k\"]"));    // map index
		evalThrows("\"hello\"[\"x\"]");                // string index must be numeric
		evalThrows("[1, 2, 3][1.5]");                  // list index must be an integer
		evalThrows("true[0]");                         // parent must be string/list/map
	}

	@Test
	public void objectLiteralDotAndIndexAccess() throws Exception {
		Map<?, ?> obj = (Map<?, ?>) eval("{\"name\": \"Ann\", \"age\": 30}");
		assertEquals("Ann", obj.get("name"));

		assertEquals("Ann", eval("{\"name\": \"Ann\"}.name"));
		assertNull(eval("{\"a\": 1}.b"));              // missing key -> null
		assertEquals(9L, num("{\"k\": 9}[\"k\"]"));
		evalThrows("5 . x");                            // dot parent must be a map
	}

	// ---------------------------------------------------------------- //
	// -------------------- variables / assignment ------------ //
	// ---------------------------------------------------------------- //

	@Test
	public void assignmentStoresIntoTheVariableMap() throws Exception {
		Map<String, Object> vars = new HashMap<>();
		eval("items = [1, 2, 3]", vars);
		assertEquals(List.of(1L, 2L, 3L), longs(vars.get("items")));
		assertEquals(2L, num("items[1]", vars));
	}

	@Test
	public void anUnknownVariableEvaluatesToNull() throws Exception {
		assertNull(eval("whoKnows"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- parser edges ---------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void endOfInputYieldsANullExpression() throws Exception {
		ExpressionParser parser = new ExpressionParser("   ");
		try {
			assertNull(parser.readExpression());
		} finally {
			parser.close();
		}
	}

	@Test
	public void aSyntaxErrorReportsALineAndColumn() {
		LineNumberParseException ex = assertThrows(LineNumberParseException.class,
				() -> eval("@"));
		assertEquals(1, ex.getLineNum());
		assertEquals(1, ex.getColNum());
	}

	@Test
	public void anUnterminatedGroupIsASyntaxError() {
		assertThrows(LineNumberParseException.class, () -> eval("(1 + 2"));
	}

	// ---------------------------------------------------------------- //
	// -------------------- tree methods ---------------------- //
	// ---------------------------------------------------------------- //

	private static Expression parse(String expr) throws Exception {
		ExpressionParser parser = new ExpressionParser(expr);
		try {
			return parser.readExpression();
		} finally {
			parser.close();
		}
	}

	@Test
	public void toCodeReparsesToTheSameValue() throws Exception {
		for (String expr : List.of(
				"1 + 2 * 3", "(1 + 2) * 3", "!true || false", "\"a\" + \"b\"",
				"[1, 2, 3] - 2", "{\"k\": 9}.k", "2 in [1, 2]", "5 - 3", "10 / 4")) {
			Object first = eval(expr);
			String code = parse(expr).toCode();
			Object second = eval(code);
			assertEquals("round trip of " + expr + " via " + code, String.valueOf(first),
					String.valueOf(second));
		}
	}

	@Test
	public void getVariableNamesWalksEveryOperandType() throws Exception {
		assertEquals(Set.of("a", "b", "c"), varNames("a + b * c"));
		assertEquals(Set.of("obj"), varNames("obj.field"));
		assertEquals(Set.of("list", "i"), varNames("list[i]"));
		assertEquals(Set.of("flag"), varNames("!flag"));
		assertEquals(Set.of("x"), varNames("(x)"));
		assertEquals(Set.of("v"), varNames("{\"k\": v}"));
		assertEquals(Set.of("target", "value"), varNames("target = value + 1"));
	}

	private static Set<String> varNames(String expr) throws Exception {
		Set<String> names = new HashSet<>();
		parse(expr).getVariableNames().forEach(names::add);
		return names;
	}

	@Test
	public void binaryExpressionExposesItsOperandsAndChildren() throws Exception {
		BinaryExpression sum = (BinaryExpression) parse("a + b");
		assertEquals(2, sum.getChildren().size());
		assertEquals(sum.getOperand1(), sum.getChildren().get(0));
		assertEquals(sum.getOperand2(), sum.getChildren().get(1));
		assertTrue(sum.getDescendants().containsAll(sum.getChildren()));
	}

	@Test
	public void substituteChildRewritesTheTreeForEveryNodeShape() throws Exception {
		for (String expr : List.of(
				"a + b", "!a", "(a)", "a = b", "[a, b]", "{\"k\": a}", "a.b", "a[b]")) {
			Expression node = parse(expr);
			Expression replacement = parse("999");
			node.substituteChild(0, replacement);
			assertEquals("substituteChild(0) on " + expr,
					replacement, node.getChildren().get(0));
		}
	}

	@Test
	public void toStringIsNonEmptyForEveryNodeShape() throws Exception {
		for (String expr : List.of(
				"a + b", "a - b", "a * b", "a / b", "!a", "a && b", "a || b",
				"a == b", "a != b", "a === b", "a !== b", "a < b", "a <= b",
				"a > b", "a >= b", "a in b", "(a)", "a = b", "[a, b]",
				"{\"k\": v}", "a.b", "a[b]")) {
			assertTrue(expr, !parse(expr).toString().isEmpty());
		}
	}

	@Test
	public void readingTwoExpressionsFromOneParser() throws Exception {
		ExpressionParser parser = new ExpressionParser("1 + 1\n2 * 3");
		try {
			assertEquals(2L, ((Number) parser.readExpression().evaluate(new HashMap<>())
					.getValue()).longValue());
			assertEquals(6L, ((Number) parser.readExpression().evaluate(new HashMap<>())
					.getValue()).longValue());
			assertNull(parser.readExpression());
		} finally {
			parser.close();
		}
	}
}

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

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Value} — the type-coercion, boolean-evaluation and equality core the
 * whole expression engine leans on. Part of the #90 area-C test hardening (#156).
 */
public class ValueTest {

	// ---------------------------------------------------------------- //
	// -------------------- type predicates ---------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void typePredicatesAndTypeString() {
		assertTrue(new Value(null).isNull());
		assertEquals("null", new Value(null).getTypeString());
		assertTrue(new Value("x").isString());
		assertEquals("string", new Value("x").getTypeString());
		assertTrue(new Value(3).isNumber());
		assertTrue(new Value(3).isIntNumber());
		assertFalse(new Value(3.5).isIntNumber());
		assertEquals("number", new Value(3).getTypeString());
		assertTrue(new Value(true).isBoolean());
		assertEquals("boolean", new Value(true).getTypeString());
		assertTrue(new Value(List.of(1, 2)).isList());
		assertEquals("list", new Value(List.of(1, 2)).getTypeString());
		assertTrue(new Value(Map.of("a", 1)).isMap());
		assertEquals("map", new Value(Map.of("a", 1)).getTypeString());
	}

	@Test
	public void isNumericString() {
		assertTrue(new Value("42").isNumericString());
		assertTrue(new Value("-7").isNumericString());
		assertTrue(new Value("3.14").isNumericString());
		assertFalse(new Value("3px").isNumericString());
		assertFalse(new Value(3).isNumericString()); // not a string
	}

	// ---------------------------------------------------------------- //
	// -------------------- asNumber ----------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void asNumberConverts() throws Exception {
		assertEquals(0, new Value(null).asNumber());
		assertEquals(42, new Value("42").asNumber());
		assertEquals(3.14, new Value("3.14").asNumber());
		assertEquals(1, new Value(true).asNumber());
		assertEquals(0, new Value(false).asNumber());
		assertEquals(5, new Value(5).asNumber());
	}

	@Test
	public void asNumberRejectsNonNumericValues() {
		assertThrows(EvaluationException.class, () -> new Value("abc").asNumber());
		assertThrows(EvaluationException.class, () -> new Value(List.of(1)).asNumber());
		assertThrows(EvaluationException.class, () -> new Value(Map.of("a", 1)).asNumber());
	}

	// ---------------------------------------------------------------- //
	// -------------------- asBoolean --------------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void asBooleanEvaluatesTruthiness() {
		assertFalse(new Value(null).asBoolean());
		assertTrue(new Value(true).asBoolean());
		assertFalse(new Value(false).asBoolean());
		assertFalse(new Value("").asBoolean());
		assertTrue(new Value("x").asBoolean());
		assertFalse(new Value(0).asBoolean());
		assertTrue(new Value(3).asBoolean());
		assertFalse(new Value(0.0).asBoolean());
		assertTrue(new Value(2.5).asBoolean());
		assertFalse(new Value(List.of()).asBoolean());
		assertTrue(new Value(List.of(1)).asBoolean());
		assertFalse(new Value(Map.of()).asBoolean());
		assertTrue(new Value(Map.of("a", 1)).asBoolean());
	}

	// ---------------------------------------------------------------- //
	// -------------------- isEqual (loose) --------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void isEqualCoercesAcrossTypes() {
		assertTrue(new Value(1).isEqual(new Value("1")));       // number vs numeric string
		assertTrue(new Value("1").isEqual(new Value(1)));
		assertFalse(new Value(1).isEqual(new Value("x")));      // non-numeric string
		assertTrue(new Value(2).isEqual(new Value(2L)));        // int vs long
		assertTrue(new Value("ab").isEqual(new Value("ab")));   // string vs string
	}

	@Test
	public void isEqualNullAndBooleanRules() {
		assertTrue(new Value(null).isEqual(new Value(null)));
		assertFalse(new Value(null).isEqual(new Value(0)));     // 0 is not null
		assertTrue(new Value(true).isEqual(new Value(true)));
		assertFalse(new Value(true).isEqual(new Value(1)));     // one is boolean, other is not
	}

	@Test
	public void isEqualListsAndMaps() {
		assertTrue(new Value(List.of(1, 2)).isEqual(new Value(List.of(1, 2))));
		assertFalse(new Value(List.of(1, 2)).isEqual(new Value(List.of(1, 3))));
		assertTrue(new Value(5).isEqual(new Value(List.of(5))));   // scalar promoted to 1-list
		assertTrue(new Value(Map.of("a", 1)).isEqual(new Value(Map.of("a", "1"))));
		assertFalse(new Value(Map.of("a", 1)).isEqual(new Value(Map.of("b", 1))));
		assertFalse(new Value(Map.of("a", 1)).isEqual(new Value("a")));
	}

	// ---------------------------------------------------------------- //
	// -------------------- isStrictEqual ----------------------- //
	// ---------------------------------------------------------------- //

	@Test
	public void isStrictEqualRequiresTheSameType() {
		assertTrue(new Value(1).isStrictEqual(new Value(1)));
		assertFalse(new Value(1).isStrictEqual(new Value("1")));
		assertTrue(new Value("a").isStrictEqual(new Value("a")));
		assertTrue(new Value(List.of(1)).isStrictEqual(new Value(List.of(1))));
		assertFalse(new Value(List.of(1)).isStrictEqual(new Value(List.of("1"))));
		assertTrue(new Value(Map.of("a", 1)).isStrictEqual(new Value(Map.of("a", 1))));
		assertTrue(new Value(null).isStrictEqual(new Value(null)));
		assertFalse(new Value(null).isStrictEqual(new Value(false)));
	}

	// ---------------------------------------------------------------- //
	// -------------------- toString / number helpers ---------- //
	// ---------------------------------------------------------------- //

	@Test
	public void toStringRendersEachType() {
		assertEquals("null", new Value(null).toString());
		assertEquals("hello", new Value("hello").toString());
		assertEquals("7", new Value(7).toString());
		assertEquals("[1,2]", new Value(List.of(1, 2)).toString());
		assertEquals("{\"a\":1}", new Value(Map.of("a", 1)).toString());
	}

	@Test
	public void normalizeNumberAndIsIntNumber() {
		assertEquals(5, Value.normalizeNumber(5L));                    // long in int range -> Integer
		assertEquals(5_000_000_000L, Value.normalizeNumber(5_000_000_000L)); // stays Long
		assertEquals(2.5, Value.normalizeNumber(2.5f));               // float -> Double

		assertTrue(Value.isIntNumber(1));
		assertTrue(Value.isIntNumber(1L));
		assertTrue(Value.isIntNumber((short) 1));
		assertFalse(Value.isIntNumber(1.0));
		assertFalse(Value.isIntNumber(1.0f));
	}
}

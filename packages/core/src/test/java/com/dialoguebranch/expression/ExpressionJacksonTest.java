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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Exercises the Jackson (de)serializers attached to {@link Value}, {@link Expression} and
 * {@link StringExpression} — the {@code @JsonSerialize} / {@code @JsonDeserialize} inner classes
 * used when an {@code <<if>>} / {@code <<set>>} expression crosses the wire. Part of the #90
 * area-D test hardening (#157).
 */
public class ExpressionJacksonTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void valueSerializesToItsRawContentAndBack() throws Exception {
		assertEquals("42", mapper.writeValueAsString(new Value(42)));
		assertEquals("\"hi\"", mapper.writeValueAsString(new Value("hi")));
		assertEquals("null", mapper.writeValueAsString(new Value(null)));

		assertEquals(42, mapper.readValue("42", Value.class).getValue());
		assertEquals("hi", mapper.readValue("\"hi\"", Value.class).getValue());
		assertEquals(List.of(1, 2), mapper.readValue("[1,2]", Value.class).getValue());
		// a bare JSON null deserializes to a null reference, not a Value wrapping null
		assertNull(mapper.readValue("null", Value.class));
	}

	@Test
	public void expressionSerializesAsItsCodeAndReparses() throws Exception {
		ExpressionParser parser = new ExpressionParser("1 + 2 * 3");
		Expression expr;
		try {
			expr = parser.readExpression();
		} finally {
			parser.close();
		}

		String json = mapper.writeValueAsString(expr);
		assertEquals("\"1 + 2 * 3\"", json);

		Expression restored = mapper.readValue(json, Expression.class);
		assertEquals(7L, ((Number) restored.evaluate(new HashMap<>()).getValue()).longValue());
	}

	@Test
	public void stringExpressionRoundTripsThroughJson() throws Exception {
		StringExpression original = new StringExpression("Hi ${name}, ${1 + 1} left");

		String json = mapper.writeValueAsString(original);
		StringExpression restored = mapper.readValue(json, StringExpression.class);

		Map<String, Object> vars = new HashMap<>();
		vars.put("name", "Ada");
		assertEquals("Hi Ada, 2 left", restored.evaluate(vars).getValue());
	}
}

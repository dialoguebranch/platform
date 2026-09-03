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

package com.dialoguebranch.model.execute.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link NullableResponse} wraps a value-or-null so a Spring endpoint always returns valid JSON.
 * Part of the #90 area-D test hardening (#157).
 */
public class NullableResponseTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void defaultConstructorHoldsNull() {
		assertNull(new NullableResponse<String>().getValue());
	}

	@Test
	public void valueConstructorAndSetter() {
		NullableResponse<String> response = new NullableResponse<>("hello");
		assertEquals("hello", response.getValue());
		response.setValue("world");
		assertEquals("world", response.getValue());
		response.setValue(null);
		assertNull(response.getValue());
	}

	@Test
	public void toStringMentionsTheValue() {
		assertTrue(new NullableResponse<>("x").toString().contains("x"));
		assertTrue(new NullableResponse<String>(null).toString().contains("null"));
	}

	@Test
	public void serializesToAValueWrapper() throws Exception {
		assertEquals("{\"value\":\"hi\"}",
				mapper.writeValueAsString(new NullableResponse<>("hi")));
		assertEquals("{\"value\":null}",
				mapper.writeValueAsString(new NullableResponse<String>(null)));
	}
}

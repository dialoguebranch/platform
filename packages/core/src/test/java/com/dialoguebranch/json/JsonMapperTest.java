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

package com.dialoguebranch.json;

import com.dialoguebranch.exception.ParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link JsonMapper} is a thin Jackson wrapper used across the engine; this covers a successful
 * parse/convert/generate plus each of its error-mapping branches. Part of the #90 area-E test
 * hardening (#158).
 */
public class JsonMapperTest {

	@Test
	public void parseAndGenerateHappyPath() throws Exception {
		Map<String, Object> parsed = JsonMapper.parse("{\"a\":1,\"b\":\"x\"}",
				new TypeReference<>() {});
		assertEquals(1, parsed.get("a"));
		assertEquals("x", parsed.get("b"));

		assertEquals("[1,2,3]", JsonMapper.generate(List.of(1, 2, 3)));
	}

	@Test
	public void parseWrapsMalformedJsonInAParseException() {
		ParseException ex = assertThrows(ParseException.class,
				() -> JsonMapper.parse("not json", Map.class));
		assertTrue(ex.getMessage(), ex.getMessage().contains("parse JSON"));
	}

	@Test
	public void parseWrapsAShapeMismatchInAParseException() {
		ParseException ex = assertThrows(ParseException.class,
				() -> JsonMapper.parse("\"text\"", Integer.class));
		assertTrue(ex.getMessage(), ex.getMessage().contains("map JSON"));
	}

	@Test
	public void convertHappyPathAndIncompatibleShape() throws Exception {
		Map<String, Object> converted =
				JsonMapper.convert(Map.of("k", 2), new TypeReference<Map<String, Object>>() {});
		assertEquals(2, converted.get("k"));

		assertThrows(ParseException.class,
				() -> JsonMapper.convert(List.of(1, 2), Map.class));
	}

	@Test
	public void generateWrapsAnUnserialisableObjectInARuntimeException() {
		assertThrows(RuntimeException.class, () -> JsonMapper.generate(new Object()));
	}
}

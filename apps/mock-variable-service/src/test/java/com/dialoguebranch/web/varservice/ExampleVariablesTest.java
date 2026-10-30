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

package com.dialoguebranch.web.varservice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleVariablesTest {

	private final ExampleVariables examples = new ExampleVariables();

	@Test
	void aValueStartsRandomOfItsTypeAndIsKept() {
		Object number = examples.get("iss", "sub", "p", "example.number", false);
		assertInstanceOf(Integer.class, number);
		assertEquals(number, examples.get("iss", "sub", "p", "example.number", false));
		assertInstanceOf(Boolean.class, examples.get("iss", "sub", "p", "example.flag", false));
		assertTrue(List.of("a", "b", "c").contains(
				examples.get("iss", "sub", "p", "example.choice", false)));
		assertInstanceOf(String.class, examples.get("iss", "sub", "p", "example.noHints", false));
	}

	@Test
	void aDialoguesChangeIsKeptPerUserAndProject() {
		examples.set("iss", "sub", "p", "example.noHints", "changed");

		assertEquals("changed", examples.get("iss", "sub", "p", "example.noHints", false));
		assertTrue(examples.get("iss", "other", "p", "example.noHints", false).toString()
				.startsWith("example-"));
		assertTrue(examples.get("iss", "sub", "q", "example.noHints", false).toString()
				.startsWith("example-"));
	}

	@Test
	void clearingStartsTheValuesAgain() {
		examples.set("iss", "sub", "p", "example.noHints", "changed");
		examples.clear("iss", "sub", "p");

		assertTrue(examples.get("iss", "sub", "p", "example.noHints", false).toString()
				.startsWith("example-"));
	}

	@Test
	void aTestSessionDoesntKeepANewValue() {
		examples.get("iss", "sub", "p", "example.noHints", true);
		assertFalse(examples.hasValue("iss", "sub", "p", "example.noHints"));

		examples.get("iss", "sub", "p", "example.noHints", false);
		assertTrue(examples.hasValue("iss", "sub", "p", "example.noHints"));
	}
}

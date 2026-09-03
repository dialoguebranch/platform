/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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

package com.dialoguebranch.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CurrentIterator}, the cursor-style iterator wrapper vendored from
 * {@code rrd-utils} in #102.
 */
public class CurrentIteratorTest {

	@Test
	public void iteratesAndExposesTheCurrentElementRepeatedly() {
		CurrentIterator<String> it = new CurrentIterator<>(List.of("a", "b", "c").iterator());

		assertNull("positioned before the first element", it.getCurrent());
		assertTrue(it.moveNext());
		assertEquals("a", it.getCurrent());
		assertEquals("a", it.getCurrent());
		assertTrue(it.moveNext());
		assertEquals("b", it.getCurrent());
		assertTrue(it.moveNext());
		assertEquals("c", it.getCurrent());
		assertFalse(it.moveNext());
		assertNull("positioned after the last element", it.getCurrent());
	}

	@Test
	public void removeMoveNextDropsTheCurrentElementFromTheBackingList() {
		List<String> backing = new ArrayList<>(List.of("a", "b", "c"));
		CurrentIterator<String> it = new CurrentIterator<>(backing.iterator());

		it.moveNext();
		assertTrue(it.removeMoveNext());
		assertEquals("b", it.getCurrent());
		assertEquals(List.of("b", "c"), backing);
	}
}

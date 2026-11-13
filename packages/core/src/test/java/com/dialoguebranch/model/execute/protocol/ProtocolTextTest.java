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
 * Portions of this file are vendored from the rrd-utils library
 * (https://github.com/RoessinghResearch/rrd-utils), used under the MIT License.
 *
 * Copyright (c) 2022 Roessingh Research and Development
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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link DialogueStatement#getText()}, {@link ReplyMessage#getText()} and {@link
 * ReplyMessage#isAutoForward()} (#485). The statements are those of {@link
 * DialogueMessageJsonTest#fullMessage()}; client-js's {@code protocol-fromjson.spec.js} checks
 * that its {@code fullStatement()} gives the same results for them.
 */
public class ProtocolTextTest {

	private final DialogueMessage message = DialogueMessageJsonTest.fullMessage();
	private final List<ReplyMessage> replies = message.getReplies();

	@Test
	public void aStatementsTextLeavesOutItsActions() {
		assertEquals("Hello, Sam.", message.getStatement().getText());
	}

	@Test
	public void aRepliesTextLeavesOutItsInputFieldsAndKeepsTheSpacesAsTheyAre() {
		assertEquals("I am  years old.", replies.get(0).getText());
		assertEquals("Bye.", replies.get(2).getText());
	}

	@Test
	public void aReplyWithoutAStatementIsAnAutoForwardWithoutText() {
		ReplyMessage autoForward = replies.get(1);
		assertTrue(autoForward.isAutoForward());
		assertEquals("", autoForward.getText());
		assertFalse(replies.get(0).isAutoForward());
		assertFalse(replies.get(2).isAutoForward());
	}

	@Test
	public void aStatementWithoutTextHasEmptyText() {
		assertEquals("", new DialogueStatement().getText());

		DialogueStatement onlyAnAction = new DialogueStatement();
		onlyAnAction.getSegments().add(message.getStatement().getSegments().get(1));
		assertEquals("", onlyAnAction.getText());
	}

	@Test
	public void theTextIsNotTrimmed() {
		DialogueStatement statement = new DialogueStatement();
		statement.addTextSegment("  Hi  ");
		assertEquals("  Hi  ", statement.getText());
	}
}

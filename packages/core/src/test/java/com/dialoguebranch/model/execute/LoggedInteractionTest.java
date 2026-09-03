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

package com.dialoguebranch.model.execute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies both {@link LoggedInteraction} constructors, the accessor pairs, and a Jackson
 * round-trip (the type is persisted and rehydrated by {@code apps/api}). Part of the #90
 * area-D test hardening (#157).
 */
public class LoggedInteractionTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void agentStatementConstructor() {
		LoggedInteraction interaction = new LoggedInteraction(
				1000L, MessageSource.AGENT, "Robot", "flow", "Start", -1, "Hello.");

		assertEquals(1000L, interaction.getTimestamp());
		assertEquals(MessageSource.AGENT, interaction.getMessageSource());
		assertEquals("Robot", interaction.getSourceName());
		assertEquals("flow", interaction.getDialogueId());
		assertEquals("Start", interaction.getNodeId());
		assertEquals(-1, interaction.getPreviousIndex());
		assertEquals("Hello.", interaction.getStatement());
		assertEquals(0, interaction.getReplyId());
	}

	@Test
	public void userReplyConstructor() {
		LoggedInteraction interaction = new LoggedInteraction(
				2000L, MessageSource.USER, "user-1", "flow", "Start", 0, "Yes.", 7);

		assertEquals(MessageSource.USER, interaction.getMessageSource());
		assertEquals(0, interaction.getPreviousIndex());
		assertEquals(7, interaction.getReplyId());
	}

	@Test
	public void everyFieldIsSettable() {
		LoggedInteraction interaction = new LoggedInteraction();
		interaction.setTimestamp(42L);
		interaction.setMessageSource(MessageSource.AGENT);
		interaction.setSourceName("A");
		interaction.setDialogueId("d");
		interaction.setNodeId("n");
		interaction.setPreviousIndex(3);
		interaction.setStatement("s");
		interaction.setReplyId(9);

		assertEquals(42L, interaction.getTimestamp());
		assertEquals(MessageSource.AGENT, interaction.getMessageSource());
		assertEquals("A", interaction.getSourceName());
		assertEquals("d", interaction.getDialogueId());
		assertEquals("n", interaction.getNodeId());
		assertEquals(3, interaction.getPreviousIndex());
		assertEquals("s", interaction.getStatement());
		assertEquals(9, interaction.getReplyId());
	}

	@Test
	public void jacksonRoundTrip() throws Exception {
		LoggedInteraction original = new LoggedInteraction(
				3000L, MessageSource.USER, "user-1", "flow", "Middle", 2, "Option A.", 4);

		String json = mapper.writeValueAsString(original);
		LoggedInteraction restored = mapper.readValue(json, LoggedInteraction.class);

		assertEquals(original.getTimestamp(), restored.getTimestamp());
		assertEquals(original.getMessageSource(), restored.getMessageSource());
		assertEquals(original.getSourceName(), restored.getSourceName());
		assertEquals(original.getDialogueId(), restored.getDialogueId());
		assertEquals(original.getNodeId(), restored.getNodeId());
		assertEquals(original.getPreviousIndex(), restored.getPreviousIndex());
		assertEquals(original.getStatement(), restored.getStatement());
		assertEquals(original.getReplyId(), restored.getReplyId());
	}

	@Test
	public void unknownJsonPropertiesAreIgnored() throws Exception {
		LoggedInteraction restored = mapper.readValue(
				"{\"timestamp\":1,\"statement\":\"hi\",\"somethingNew\":true}",
				LoggedInteraction.class);
		assertEquals(1L, restored.getTimestamp());
		assertEquals("hi", restored.getStatement());
	}

	@Test
	public void messageSourceHasUserAndAgent() {
		assertEquals(MessageSource.USER, MessageSource.valueOf("USER"));
		assertEquals(MessageSource.AGENT, MessageSource.valueOf("AGENT"));
		assertEquals(2, MessageSource.values().length);
		assertTrue(mapperReadsEnum());
	}

	private boolean mapperReadsEnum() {
		try {
			return mapper.readValue("\"AGENT\"", MessageSource.class) == MessageSource.AGENT;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

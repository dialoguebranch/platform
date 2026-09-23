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

package com.dialoguebranch.model.execute.command;

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.protocol.DialogueAction;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers {@link ActionCommand}'s {@code type} — now an {@link ActionType} rather than a plain
 * {@link String} — end to end: parsing every valid wire value, rejecting an invalid one the same
 * way it always did, round-tripping through {@code toString()}, and converting correctly to the
 * lowercase wire value {@link DialogueAction} sends to clients.
 */
public class ActionCommandTest {

	private static Dialogue parse(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("actions", new StringReader(dlb))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			return result.getDialogue();
		}
	}

	private static List<ParseException> parseErrors(String dlb) throws IOException {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("bad", new StringReader(dlb))) {
			return parser.readDialogue().getParseErrors();
		}
	}

	private static ActionCommand actionCommand(Dialogue dialogue) {
		for (NodeBody.Segment segment : dialogue.getNodeById("Start").getBody().getSegments()) {
			if (segment instanceof NodeBody.CommandSegment cmd
					&& cmd.getCommand() instanceof ActionCommand action) {
				return action;
			}
		}
		throw new AssertionError("no action command found");
	}

	private static String fixtureWithType(String type) {
		return """
			title: Start
			tags:
			speaker: Robot
			---
			<<action type="%s" value="thing" text="label">>
			Hello.
			===
			""".formatted(type);
	}

	@Test
	public void everyValidWireValueParsesToItsActionType() throws Exception {
		assertEquals(ActionType.IMAGE, actionCommand(parse(fixtureWithType("image"))).getType());
		assertEquals(ActionType.VIDEO, actionCommand(parse(fixtureWithType("video"))).getType());
		assertEquals(ActionType.LINK, actionCommand(parse(fixtureWithType("link"))).getType());
		assertEquals(ActionType.GENERIC, actionCommand(parse(fixtureWithType("generic"))).getType());
	}

	@Test
	public void anInvalidTypeIsStillAParseError() throws Exception {
		List<ParseException> errors = parseErrors(fixtureWithType("bogus"));
		assertEquals(1, errors.size());
		assertTrue(errors.get(0).getMessage(), errors.get(0).getMessage().contains("bogus"));
	}

	@Test
	public void actionTypeFromWireValueRejectsAnUnknownValue() {
		assertNull(ActionType.fromWireValue("bogus"));
	}

	@Test
	public void toStringRoundTripsTheLowercaseWireValue() throws Exception {
		ActionCommand action = actionCommand(parse(fixtureWithType("link")));
		assertEquals("<<action type=\"link\" value=\"thing\" text=\"label\">>", action.toString());
	}

	@Test
	public void dialogueActionConvertsToTheLowercaseWireValue() throws Exception {
		ActionCommand action = actionCommand(parse(fixtureWithType("video")));
		DialogueAction dialogueAction = new DialogueAction(action.executeReplyCommand(null));
		assertEquals("video", dialogueAction.getType());
	}
}

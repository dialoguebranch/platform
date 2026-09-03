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

import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.ExecuteNodeResult;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.TestLoggedDialogue;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that {@link DialogueMessageFactory} turns an executed node into the wire-shape
 * {@link DialogueMessage} the {@code /dialogue/*} endpoints return — resolved statement text,
 * per-reply statements, the ends-dialogue flag, reply actions and input segments, and the
 * logged-dialogue linkage. Part of the #90 area-D test hardening (#157).
 */
public class DialogueMessageFactoryTest {

	private static final ZonedDateTime NOW =
			ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneId.of("Europe/Lisbon"));

	private static final String FIXTURE = """
		title: Start
		tags:
		speaker: Robot
		---
		<<set $who = "world">>
		Hello $who.
		<<action type="link" value="https://agent.example">>
		[[Plain reply.|Next]]
		[[Quit.|End]]
		[[Visit site.|Next|<<action type="link" value="https://example.com">>]]
		[[Name it <<input type="text" value="$name">>|Next]]
		===
		title: Next
		tags:
		speaker: Robot
		---
		Bye.
		===
		title: End
		tags:
		speaker:
		---

		===
		""";

	private Dialogue dialogue;
	private Node executedStart;

	@Before
	public void setUp() throws Exception {
		try (DialogueBranchParser parser =
				new DialogueBranchParser("flow", new StringReader(FIXTURE))) {
			ParserResult result = parser.readDialogue();
			assertTrue("fixture should parse cleanly: " + result.getParseErrors(),
					result.getParseErrors().isEmpty());
			dialogue = result.getDialogue();
		}
		ActiveDialogue active = new ActiveDialogue(
				new ResourcePointer("en", "flow", ResourceType.SCRIPT), dialogue,
				new VariableStore(new User("u")));
		executedStart = active.executeNode(dialogue.getNodeById("Start"), NOW);
	}

	private DialogueMessage message(TestLoggedDialogue log, int interactionIndex) {
		return DialogueMessageFactory.generateDialogueMessage(
				new ExecuteNodeResult(dialogue, executedStart, log, interactionIndex));
	}

	@Test
	public void carriesDialogueNodeAndSpeaker() {
		DialogueMessage msg = message(null, 0);
		assertEquals("flow", msg.getDialogue());
		assertEquals("Start", msg.getNode());
		assertEquals("Robot", msg.getSpeaker());
	}

	@Test
	public void resolvesTheAgentStatementText() {
		DialogueStatement statement = message(null, 0).getStatement();
		DialogueStatement.TextSegment first =
				(DialogueStatement.TextSegment) statement.getSegments().get(0);
		assertTrue(first.getText(), first.getText().contains("Hello world."));
	}

	@Test
	public void aBodyActionCommandBecomesAnActionSegment() {
		DialogueStatement statement = message(null, 0).getStatement();
		DialogueStatement.ActionSegment action = statement.getSegments().stream()
				.filter(s -> s instanceof DialogueStatement.ActionSegment)
				.map(s -> (DialogueStatement.ActionSegment) s)
				.findFirst()
				.orElseThrow(() -> new AssertionError("no ActionSegment in the statement"));
		assertEquals("link", action.getAction().getType());
	}

	@Test
	public void mapsEveryReply() {
		DialogueMessage msg = message(null, 0);
		assertEquals(4, msg.getReplies().size());

		ReplyMessage plain = msg.getReplies().get(0);
		assertFalse(plain.isEndsDialogue());
		DialogueStatement.TextSegment plainText =
				(DialogueStatement.TextSegment) plain.getStatement().getSegments().get(0);
		assertEquals("Plain reply.", plainText.getText());

		assertTrue(msg.getReplies().get(1).isEndsDialogue()); // "Quit.|End"

		ReplyMessage withAction = msg.getReplies().get(2);
		assertEquals(1, withAction.getActions().size());
		assertEquals("link", withAction.getActions().get(0).getType());

		ReplyMessage withInput = msg.getReplies().get(3);
		DialogueStatement.InputSegment input =
				(DialogueStatement.InputSegment) withInput.getStatement().getSegments().get(1);
		assertEquals("text", input.getInputType());
	}

	@Test
	public void withoutASessionLogThereIsNoLoggedDialogueLinkage() {
		DialogueMessage msg = message(null, 0);
		assertNull(msg.getLoggedDialogueId());
		assertEquals(0, msg.getLoggedInteractionIndex());
	}

	@Test
	public void withASessionLogTheLinkageIsCarriedThrough() {
		DialogueMessage msg = message(new TestLoggedDialogue(), 3);
		assertEquals("log-1", msg.getLoggedDialogueId());
		assertEquals(3, msg.getLoggedInteractionIndex());
	}
}

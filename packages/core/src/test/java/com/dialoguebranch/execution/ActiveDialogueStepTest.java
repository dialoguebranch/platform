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

package com.dialoguebranch.execution;

import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.DialogueStatement;
import com.dialoguebranch.model.execute.protocol.ReplyMessage;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests {@link ActiveDialogue#isFinished()} and {@link ActiveDialogue#getCurrentMessage()}: the
 * one rule for when a dialogue has ended, and the current step in the shape the Web Service
 * sends (#473).
 */
public class ActiveDialogueStepTest {

	private static final ZonedDateTime NOW = ZonedDateTime.now();

	private static ActiveDialogue main() throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.mainAndOther();
		return new ActiveDialogue(project, project.resolve("main", null),
				new VariableStore(new User("test")));
	}

	private static String text(DialogueStatement statement) {
		StringBuilder text = new StringBuilder();
		for (DialogueStatement.Segment segment : statement.getSegments()) {
			if (segment instanceof DialogueStatement.TextSegment textSegment)
				text.append(textSegment.getText());
		}
		return text.toString().trim();
	}

	private static ReplyMessage reply(DialogueMessage step, String text) {
		for (ReplyMessage reply : step.getReplies()) {
			if (reply.getStatement() != null && text(reply.getStatement()).equals(text))
				return reply;
		}
		throw new AssertionError("No reply \"" + text + "\"");
	}

	private static void select(ActiveDialogue dialogue, String text) throws Exception {
		dialogue.selectReply(reply(dialogue.getCurrentMessage(), text).getReplyId(), NOW);
	}

	@Test
	public void aDialogueThatHasntStartedHasNoStepAndHasntFinished() throws Exception {
		ActiveDialogue dialogue = main();
		assertFalse(dialogue.isFinished());
		assertNull(dialogue.getCurrentMessage());
	}

	@Test
	public void theCurrentStepIsReadyToShow() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.startDialogue(NOW);

		DialogueMessage step = dialogue.getCurrentMessage();

		assertFalse(dialogue.isFinished());
		assertNotNull(step);
		assertEquals("main", step.getDialogue());
		assertEquals("Start", step.getNode());
		assertEquals("Robin", step.getSpeaker());
		assertEquals("Hello.", text(step.getStatement()));
		assertEquals(List.of("On.", "Skip ahead.", "Stay.", "Set and go.", "Bye."),
				step.getReplies().stream().map(r -> text(r.getStatement())).toList());
		// An ActiveDialogue keeps no log
		assertNull(step.getLoggedDialogueId());
		assertEquals(0, step.getLoggedInteractionIndex());
	}

	@Test
	public void theStepsReplyIdsSelectThoseReplies() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.startDialogue(NOW);
		int stay = reply(dialogue.getCurrentMessage(), "Stay.").getReplyId();

		Node here = dialogue.selectReply(stay, NOW);

		assertEquals("Here", here.getTitle());
		assertEquals("Here", dialogue.getCurrentMessage().getNode());
	}

	@Test
	public void aNodeWithoutRepliesIsFinishedButStillShown() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.startDialogue(NOW);
		select(dialogue, "Stay.");

		assertTrue(dialogue.isFinished());
		DialogueMessage step = dialogue.getCurrentMessage();
		assertNotNull(step);
		assertEquals("Still here.", text(step.getStatement()));
		assertTrue(step.getReplies().isEmpty());
	}

	@Test
	public void aReplyToEndFinishesWithNothingLeftToShow() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.startDialogue(NOW);
		select(dialogue, "Bye.");

		assertTrue(dialogue.isFinished());
		assertNull(dialogue.getCurrentMessage());
	}

	@Test
	public void afterASwitchTheStepIsInTheOtherDialogue() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.startDialogue(NOW);
		select(dialogue, "On.");

		DialogueMessage step = dialogue.getCurrentMessage();
		assertEquals("other", step.getDialogue());
		assertEquals("Start", step.getNode());
		assertEquals("Mood is happy.", text(step.getStatement()));
		assertFalse(dialogue.isFinished());

		select(dialogue, "Next.");
		assertTrue(dialogue.isFinished());
		assertEquals("Welcome.", text(dialogue.getCurrentMessage().getStatement()));
	}

	@Test
	public void puttingTheDialogueAtANodeStartsIt() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.setCurrentNode(dialogue.getDialogueDefinition().getNodeById("Here"));
		assertTrue(dialogue.isFinished());

		ActiveDialogue other = main();
		other.setCurrentNode(other.getDialogueDefinition().getNodeById("Start"));
		assertFalse(other.isFinished());
	}

	@Test
	public void aNodeThatHasntBeenExecutedHasNoStepToShow() throws Exception {
		ActiveDialogue dialogue = main();
		dialogue.setCurrentNode(dialogue.getDialogueDefinition().getNodeById("Start"));

		IllegalStateException e = assertThrows(IllegalStateException.class,
				dialogue::getCurrentMessage);
		assertTrue(e.getMessage(), e.getMessage().contains("\"Start\" hasn't been executed"));

		// Executing it, as the Web Service does after putting a dialogue at a node, gives one
		dialogue.setCurrentNode(dialogue.executeNode(dialogue.getCurrentNode(), NOW));
		assertEquals("Hello.", text(dialogue.getCurrentMessage().getStatement()));
	}

	/** The loop on the Core Java documentation page. */
	@Test
	public void theDocumentedLoopShowsEveryStep() throws Exception {
		for (List<String> choices : List.of(List.of("On.", "Next."), List.of("Bye."),
				List.of("Stay."))) {
			ActiveDialogue dialogue = main();
			dialogue.startDialogue(NOW);
			List<String> shown = new ArrayList<>();
			List<String> toChoose = new ArrayList<>(choices);

			DialogueMessage step = dialogue.getCurrentMessage();
			while (step != null) {
				shown.add(step.getStatement().getText().trim());
				if (dialogue.isFinished()) break;
				dialogue.selectReply(reply(step, toChoose.remove(0)));
				step = dialogue.getCurrentMessage();
			}

			assertTrue(toChoose.isEmpty());
			switch (choices.get(0)) {
				case "On." -> assertEquals(List.of("Hello.", "Mood is happy.", "Welcome."), shown);
				case "Bye." -> assertEquals(List.of("Hello."), shown);
				default -> assertEquals(List.of("Hello.", "Still here."), shown);
			}
		}
	}
}

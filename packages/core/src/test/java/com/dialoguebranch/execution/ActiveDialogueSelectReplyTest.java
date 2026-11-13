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


package com.dialoguebranch.execution;


import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Reply;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests the errors of {@link ActiveDialogue#selectReply}: a reply that isn't in the current node,
 * a dialogue that has finished, and a log that has been cancelled or completed.
 */
public class ActiveDialogueSelectReplyTest {

	private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 13, 10, 0, 0, 0,
			ZoneId.of("UTC"));

	/** Start sets a variable when its reply is selected, and ends. */
	private static final String SHORT = "title: Start\nspeaker: Robin\n---\nHello.\n"
			+ "[[Bye.|End|<<set $said = 1>>]]\n===\n"
			+ "title: End\nspeaker: Robin\n---\n===\n";

	private static final String LAST = "title: Start\nspeaker: Robin\n---\nThe end.\n===\n";

	private static ActiveDialogue start(String script, boolean keepLog) throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.project(Map.of("en/d.dlb", script));
		VariableStore store = new VariableStore(new User("test"));
		store.setValue("said", 0, false, NOW);
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("d", null), store);
		if (keepLog)
			dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);
		return dialogue;
	}

	private static int bye(ActiveDialogue dialogue) {
		Reply reply = dialogue.getCurrentNode().getBody().getReplies().get(0);
		return reply.getReplyId();
	}

	private static ExecutionException.Type selectFails(ActiveDialogue dialogue, int replyId) {
		try {
			dialogue.selectReply(replyId, NOW);
		} catch (ExecutionException e) {
			return e.getType();
		} catch (Exception e) {
			throw new AssertionError("expected an ExecutionException, got " + e, e);
		}
		throw new AssertionError("expected an ExecutionException");
	}

	@Test
	public void aReplyThatIsNotInTheNodeIsNotFoundAndChangesNothing() throws Exception {
		for (boolean keepLog : new boolean[] {false, true}) {
			ActiveDialogue dialogue = start(SHORT, keepLog);
			assertEquals(ExecutionException.Type.REPLY_NOT_FOUND, selectFails(dialogue, 999));
			assertEquals(0, dialogue.getVariableStore().getValue("said"));
			assertEquals("Start", dialogue.getCurrentNode().getTitle());
			if (keepLog)
				assertEquals(1, dialogue.getLoggedDialogue().getInteractionList().size());
		}
	}

	@Test
	public void aReplyAfterTheDialogueHasEndedIsRefused() throws Exception {
		for (boolean keepLog : new boolean[] {false, true}) {
			ActiveDialogue dialogue = start(SHORT, keepLog);
			int bye = bye(dialogue);
			dialogue.selectReply(bye, NOW);
			assertTrue(dialogue.isFinished());
			assertEquals(ExecutionException.Type.DIALOGUE_COMPLETED, selectFails(dialogue, bye));
		}
	}

	@Test
	public void aReplyAtANodeWithoutRepliesIsRefused() throws Exception {
		for (boolean keepLog : new boolean[] {false, true}) {
			ActiveDialogue dialogue = start(LAST, keepLog);
			assertEquals(ExecutionException.Type.DIALOGUE_COMPLETED, selectFails(dialogue, 0));
		}
	}

	@Test
	public void aReplyInACancelledDialogueIsRefusedAndNotLogged() throws Exception {
		ActiveDialogue dialogue = start(SHORT, true);
		dialogue.cancel();
		assertEquals(ExecutionException.Type.DIALOGUE_CANCELLED,
				selectFails(dialogue, bye(dialogue)));
		assertEquals(0, dialogue.getVariableStore().getValue("said"));
		assertEquals(1, dialogue.getLoggedDialogue().getInteractionList().size());
	}

	@Test
	public void aReplyBeforeTheDialogueHasStartedIsRefused() throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.project(Map.of("en/d.dlb", SHORT));
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("d", null),
				new VariableStore(new User("test")));
		try {
			dialogue.selectReply(0, NOW);
			fail("expected an IllegalStateException");
		} catch (IllegalStateException expected) {
			// nothing to reply to yet
		}
	}

}

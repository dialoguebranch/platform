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
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.model.common.ReservedVariables;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.LoggedDialogue;
import com.dialoguebranch.model.execute.LoggedInteraction;
import com.dialoguebranch.model.execute.MessageSource;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests the conversation log an {@link ActiveDialogue} keeps, going back in it, resuming from it
 * and cancelling (#483).
 */
public class ActiveDialogueLogTest {

	private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 13, 10, 0, 0, 0,
			ZoneId.of("UTC"));

	/** A chain Start, Two, Three that ends; Two counts how often it is executed. */
	private static final String CHAIN = "title: Start\nspeaker: Robin\n---\nHello.\n"
			+ "[[Go on.|Two]]\n===\n"
			+ "title: Two\nspeaker: Robin\n---\n<<set $count = $count + 1>>Two, $count.\n"
			+ "[[Go on.|Three]]\n[[Stop.|End]]\n===\n"
			+ "title: Three\nspeaker: Robin\n---\nThree.\n[[Fin.|End]]\n===\n"
			+ "title: End\nspeaker: Robin\n---\n===\n";

	private static final String LAST = "title: Start\nspeaker: Robin\n---\nThe end.\n===\n";

	private static ExecutableProject chain() throws Exception {
		return ActiveDialogueLinkTest.project(Map.of("en/chain.dlb", CHAIN));
	}

	private static VariableStore variables() {
		VariableStore store = new VariableStore(new User("test"));
		store.setValue("count", 0, false, NOW);
		return store;
	}

	/** A chain dialogue that keeps a log in memory, started. */
	private static ActiveDialogue startChain(ExecutableProject project, VariableStore store)
			throws Exception {
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("chain", null), store);
		dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);
		return dialogue;
	}

	private static int replyId(ActiveDialogue dialogue, String text) {
		for (Reply reply : dialogue.getCurrentNode().getBody().getReplies()) {
			if (reply.getStatement() != null
					&& reply.getStatement().toString().trim().equals(text))
				return reply.getReplyId();
		}
		throw new AssertionError("No reply \"" + text + "\"");
	}

	private static void reply(ActiveDialogue dialogue, String text) throws Exception {
		dialogue.selectReply(replyId(dialogue, text), NOW);
	}

	private static List<LoggedInteraction> interactions(ActiveDialogue dialogue) {
		return dialogue.getLoggedDialogue().getInteractionList();
	}

	@Test
	public void noLogIsKeptUnlessAskedFor() throws Exception {
		ExecutableProject project = chain();
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("chain", null),
				variables());
		dialogue.startDialogue(NOW);

		assertNull(dialogue.getLoggedDialogue());
		DialogueMessage message = dialogue.getCurrentMessage();
		assertNull(message.getLoggedDialogueId());
		assertEquals(0, message.getLoggedInteractionIndex());
		dialogue.cancel();
	}

	@Test
	public void aLogNeedsAFactory() throws Exception {
		ExecutableProject project = chain();
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("chain", null),
				variables());
		try {
			dialogue.setLoggedDialogue(new InMemoryLoggedDialogue("test",
					project.resolve("chain", null), NOW, null), null);
			fail("expected an IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// a log without a factory is refused
		}
	}

	@Test
	public void startingRecordsTheAgentStep() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());

		LoggedDialogue log = dialogue.getLoggedDialogue();
		assertEquals("chain", log.getDialogueName());
		assertEquals("en", log.getLanguage());
		assertEquals("test", log.getUser());
		assertEquals(1, log.getInteractionList().size());
		LoggedInteraction agent = log.getInteractionList().get(0);
		assertEquals(MessageSource.AGENT, agent.getMessageSource());
		assertEquals("Robin", agent.getSourceName());
		assertEquals("chain", agent.getDialogueId());
		assertEquals("Start", agent.getNodeId());
		assertEquals(-1, agent.getPreviousIndex());
		assertEquals("Hello.\n", agent.getStatement().replaceFirst("(?s)\\n*$", "\n"));
		assertEquals(NOW.toInstant().toEpochMilli(), agent.getTimestamp());
		assertFalse(log.isCompleted());
		assertEquals(0, dialogue.getLoggedInteractionIndex());
	}

	@Test
	public void theMessageSaysWhereTheStepIsInTheLog() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());
		reply(dialogue, "Go on.");

		DialogueMessage message = dialogue.getCurrentMessage();
		assertEquals(dialogue.getLoggedDialogue().getId(), message.getLoggedDialogueId());
		assertEquals(2, message.getLoggedInteractionIndex());
	}

	@Test
	public void aReplyIsRecordedWithItsStatementAndLinksTheSteps() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());
		int replyId = replyId(dialogue, "Go on.");
		dialogue.selectReply(replyId, NOW);

		List<LoggedInteraction> list = interactions(dialogue);
		assertEquals(3, list.size());
		LoggedInteraction user = list.get(1);
		assertEquals(MessageSource.USER, user.getMessageSource());
		assertEquals("USER", user.getSourceName());
		assertEquals("Start", user.getNodeId());
		assertEquals("Go on.", user.getStatement());
		assertEquals(replyId, user.getReplyId());
		assertEquals(0, user.getPreviousIndex());
		LoggedInteraction agent = list.get(2);
		assertEquals(MessageSource.AGENT, agent.getMessageSource());
		assertEquals("Two", agent.getNodeId());
		assertEquals(1, agent.getPreviousIndex());
		assertEquals(2, dialogue.getLoggedInteractionIndex());
		assertFalse(dialogue.getLoggedDialogue().isCompleted());
	}

	@Test
	public void theLogCompletesWhenTheDialogueEndsWithAReplyToEnd() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());
		reply(dialogue, "Go on.");
		reply(dialogue, "Stop.");

		assertTrue(dialogue.getLoggedDialogue().isCompleted());
		List<LoggedInteraction> list = interactions(dialogue);
		assertEquals(4, list.size());
		assertEquals(MessageSource.USER, list.get(3).getMessageSource());
	}

	@Test
	public void theLogCompletesAtANodeWithoutReplies() throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.project(Map.of("en/last.dlb", LAST));
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("last", null),
				variables());
		dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);

		assertTrue(dialogue.getLoggedDialogue().isCompleted());
		assertEquals(1, interactions(dialogue).size());
	}

	@Test
	public void aLinkIntoAnotherDialogueCompletesTheLogAndStartsANewOneInTheSameSession()
			throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.mainAndOther();
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("main", null),
				new VariableStore(new User("test")));
		InMemoryLoggedDialogue first = new InMemoryLoggedDialogue("test",
				project.resolve("main", null), NOW, "session-1");
		dialogue.setLoggedDialogue(first, InMemoryLoggedDialogue.factory());
		dialogue.startDialogue(NOW);

		reply(dialogue, "On.");

		assertTrue(first.isCompleted());
		assertEquals(2, first.getInteractionList().size()); // the agent's step and the reply
		InMemoryLoggedDialogue second = (InMemoryLoggedDialogue) dialogue.getLoggedDialogue();
		assertNotSame(first, second);
		assertNotEquals(first.getId(), second.getId());
		assertEquals("session-1", second.getSessionId());
		assertEquals("other", second.getDialogueName());
		assertEquals("test", second.getUser());
		assertFalse(second.isCompleted());
		assertEquals(1, second.getInteractionList().size());
		assertEquals(-1, second.getInteractionList().get(0).getPreviousIndex());
		assertEquals("other", second.getInteractionList().get(0).getDialogueId());
		assertEquals(second.getId(), dialogue.getCurrentMessage().getLoggedDialogueId());
		assertEquals(0, dialogue.getCurrentMessage().getLoggedInteractionIndex());
	}

	@Test
	public void aLinkBackIntoTheStartOfTheSameDialogueStartsANewLogToo() throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.mainAndOther();
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("other", null),
				new VariableStore(new User("test")));
		LoggedDialogue first = dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);
		reply(dialogue, "Back.");
		LoggedDialogue second = dialogue.getLoggedDialogue();
		assertEquals("main", second.getDialogueName());
		reply(dialogue, "On.");

		LoggedDialogue third = dialogue.getLoggedDialogue();
		assertTrue(second.isCompleted());
		assertEquals("other", third.getDialogueName());
		assertNotSame(first, third);
	}

	@Test
	public void goingBackShowsThePreviousAgentStepAgainWithoutLoggingAnything() throws Exception {
		VariableStore store = variables();
		ActiveDialogue dialogue = startChain(chain(), store);
		reply(dialogue, "Go on.");
		reply(dialogue, "Go on.");
		assertEquals("Three", dialogue.getCurrentNode().getTitle());
		int logged = interactions(dialogue).size();

		assertEquals("Two", dialogue.back(NOW).getTitle());

		assertEquals("Two", dialogue.getCurrentNode().getTitle());
		assertEquals(logged, interactions(dialogue).size());
		assertEquals(2, dialogue.getLoggedInteractionIndex());
		assertEquals(2, dialogue.getCurrentMessage().getLoggedInteractionIndex());
		// Two ran once when it was first reached, and runs again now: its <<set>> counts both.
		assertEquals(2, store.getValue("count"));
	}

	@Test
	public void aReplyAfterGoingBackContinuesFromThatStep() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());
		reply(dialogue, "Go on.");
		reply(dialogue, "Go on.");
		dialogue.back(NOW);

		reply(dialogue, "Go on.");

		List<LoggedInteraction> list = interactions(dialogue);
		assertEquals(7, list.size());
		assertEquals(MessageSource.USER, list.get(5).getMessageSource());
		assertEquals(2, list.get(5).getPreviousIndex());
		assertEquals(5, list.get(6).getPreviousIndex());
		assertEquals("Three", list.get(6).getNodeId());
	}

	@Test
	public void goingBackAtTheFirstStepShowsItAgain() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());

		assertEquals("Start", dialogue.back(NOW).getTitle());
		assertEquals(0, dialogue.getLoggedInteractionIndex());
	}

	@Test
	public void goingBackNeedsALogThatIsStillOpen() throws Exception {
		ExecutableProject project = chain();
		ActiveDialogue withoutLog = new ActiveDialogue(project, project.resolve("chain", null),
				variables());
		withoutLog.startDialogue(NOW);
		try {
			withoutLog.back(NOW);
			fail("expected an IllegalStateException");
		} catch (IllegalStateException expected) {
			// nothing to go back in
		}

		ActiveDialogue completed = startChain(project, variables());
		reply(completed, "Go on.");
		reply(completed, "Stop.");
		assertEquals(ExecutionException.Type.DIALOGUE_COMPLETED, backFails(completed));

		ActiveDialogue cancelled = startChain(project, variables());
		cancelled.cancel();
		assertTrue(cancelled.getLoggedDialogue().isCancelled());
		assertEquals(ExecutionException.Type.DIALOGUE_CANCELLED, backFails(cancelled));
	}

	private static ExecutionException.Type backFails(ActiveDialogue dialogue) throws Exception {
		try {
			dialogue.back(NOW);
		} catch (ExecutionException e) {
			return e.getType();
		}
		throw new AssertionError("expected an ExecutionException");
	}

	@Test
	public void aConversationIsResumedFromItsLogAtAStep() throws Exception {
		ExecutableProject project = chain();
		ActiveDialogue before = startChain(project, variables());
		reply(before, "Go on.");
		LoggedDialogue log = before.getLoggedDialogue();
		int index = before.getLoggedInteractionIndex();
		int logged = log.getInteractionList().size();

		ActiveDialogue resumed = ActiveDialogue.restore(project, log, index, variables(),
				InMemoryLoggedDialogue.factory());

		assertSame(log, resumed.getLoggedDialogue());
		assertEquals(index, resumed.getLoggedInteractionIndex());
		assertEquals("Two", resumed.getCurrentNode().getTitle());
		try {
			resumed.getCurrentMessage();
			fail("the node hasn't been executed yet");
		} catch (IllegalStateException expected) {
			// not executed
		}
		resumed.executeCurrentNode(NOW);
		assertEquals(log.getId(), resumed.getCurrentMessage().getLoggedDialogueId());
		assertEquals(index, resumed.getCurrentMessage().getLoggedInteractionIndex());

		reply(resumed, "Go on.");
		assertEquals(logged + 2, log.getInteractionList().size());
		assertEquals("Three", log.getInteractionList().get(logged + 1).getNodeId());
		assertEquals(index, log.getInteractionList().get(logged).getPreviousIndex());
	}

	@Test
	public void aStepThatIsNotInTheLogCantBeResumed() throws Exception {
		ExecutableProject project = chain();
		LoggedDialogue log = startChain(project, variables()).getLoggedDialogue();

		for (int index : new int[] { -1, 1, 99 }) {
			try {
				ActiveDialogue.restore(project, log, index, variables(),
						InMemoryLoggedDialogue.factory());
				fail("expected an ExecutionException for index " + index);
			} catch (ExecutionException e) {
				assertEquals(ExecutionException.Type.INTERACTION_NOT_FOUND, e.getType());
			}
		}
	}

	@Test
	public void aLogOfAnUnknownDialogueCantBeResumed() throws Exception {
		ExecutableProject project = chain();
		LoggedDialogue log = startChain(project, variables()).getLoggedDialogue();
		log.setDialogueName("gone");

		try {
			ActiveDialogue.restore(project, log, 0, variables(),
					InMemoryLoggedDialogue.factory());
			fail("expected an ExecutionException");
		} catch (ExecutionException e) {
			assertEquals(ExecutionException.Type.DIALOGUE_NOT_FOUND, e.getType());
		}
	}

	@Test
	public void executingAStepThatWasAlreadyExecutedIsRefused() throws Exception {
		ActiveDialogue dialogue = startChain(chain(), variables());
		try {
			dialogue.executeCurrentNode(NOW);
			fail("expected an IllegalStateException");
		} catch (IllegalStateException expected) {
			// already executed
		}
	}

	@Test
	public void theUsersReplyIsLoggedWithWhatTheyTypedBeforeItsCommandsRun() throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.project(Map.of("en/ask.dlb",
				"title: Start\nspeaker: Robin\n---\nWho are you?\n"
				+ "[[I am <<input type=\"text\" value=\"$name\">>|Next|<<set $name = \"changed\">>]]\n"
				+ "===\ntitle: Next\nspeaker: Robin\n---\nHi $name.\n===\n"));
		VariableStore store = new VariableStore(new User("test"));
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("ask", null), store);
		dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);

		dialogue.storeReplyInput(Map.of("name", "Ann"), NOW);
		dialogue.selectReply(dialogue.getCurrentNode().getBody().getReplies().get(0).getReplyId(),
				NOW);

		String logged = interactions(dialogue).get(1).getStatement();
		assertTrue(logged, logged.contains("Ann"));
		assertFalse(logged, logged.contains("changed"));
		assertEquals("changed", store.getValue("name"));
	}

	@Test
	public void aDialogueThatFailsToStartAfterALinkLeavesTheReplyInTheLogItWasGivenIn()
			throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.project(Map.of(
				"en/main.dlb", "title: Start\nspeaker: Robin\n---\nHello.\n[[On.|bad.Start]]\n===\n",
				"en/bad.dlb", "title: Start\nspeaker: Robin\n---\n<<if $missing.x > 1>>Hi.<<endif>>\n"
						+ "[[Next.|Start]]\n===\n"));
		ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("main", null),
				new VariableStore(new User("test")));
		LoggedDialogue first = dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);

		try {
			reply(dialogue, "On.");
			fail("the dialogue that is entered can't start");
		} catch (Exception expected) {
			// the first step of "bad" fails
		}

		assertSame(first, dialogue.getLoggedDialogue());
		assertFalse(first.isCompleted());
		assertEquals(2, first.getInteractionList().size());
		assertEquals(1, dialogue.getLoggedInteractionIndex());
		// The dialogue itself stays in "main" too, at its start step
		assertEquals("main", dialogue.getDialogueFileDescription().getDialogueName());
		assertEquals("main", dialogue.getDialogueDefinition().getDialogueName());
		assertEquals("Start", dialogue.getCurrentNode().getTitle());
		assertEquals("Start", dialogue.back(NOW).getTitle());
		assertEquals("main", dialogue.getCurrentMessage().getDialogue());
	}

	@Test
	public void goingBackAndShowingAStepAgainFollowTheUsersGenderTranslation() throws Exception {
		ExecutableProject base = chain();
		ExecutableProject female = ActiveDialogueLinkTest.project(Map.of("en/chain.dlb",
				CHAIN.replace("Two, $count.", "Two for her, $count.")));
		ResolvedDialogue baseResolved = base.resolve("chain", null);
		Dialogue femaleDialogue = female.resolve("chain", null).dialogue();
		ResolvedDialogue withGender = new ResolvedDialogue(baseResolved.pointer(),
				baseResolved.dialogue(),
				gender -> gender == TranslationContext.Gender.FEMALE ? femaleDialogue : null);
		DialogueResolver resolver = (name, language) -> withGender;
		VariableStore store = variables();
		store.setValue(ReservedVariables.USER_GENDER, "female", false, NOW);
		ActiveDialogue dialogue = new ActiveDialogue(resolver, withGender, store);
		LoggedDialogue log = dialogue.keepInMemoryLog(NOW);
		dialogue.startDialogue(NOW);
		reply(dialogue, "Go on.");
		assertTrue(dialogue.getCurrentNode().getBody().toString().contains("for her"));
		int index = dialogue.getLoggedInteractionIndex();

		ActiveDialogue resumed = ActiveDialogue.restore(resolver, log, index, store,
				InMemoryLoggedDialogue.factory());
		Node shown = resumed.executeCurrentNode(NOW);
		assertTrue(shown.getBody().toString(), shown.getBody().toString().contains("for her"));

		reply(dialogue, "Go on.");
		Node back = dialogue.back(NOW);
		assertTrue(back.getBody().toString(), back.getBody().toString().contains("for her"));
	}
}

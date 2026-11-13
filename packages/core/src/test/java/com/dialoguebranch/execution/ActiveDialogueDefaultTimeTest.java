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
import com.dialoguebranch.model.execute.protocol.ReplyMessage;
import org.junit.Test;

import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the overloads of {@link ActiveDialogue#startDialogue} and {@link
 * ActiveDialogue#selectReply} without a time (#485): they use now, in the time zone of the
 * {@link VariableStore}'s user, which is stored with every variable change a step makes.
 */
public class ActiveDialogueDefaultTimeTest {

	/** Not the time zone of the machine running the tests, whatever that is. */
	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
	private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

	private static ActiveDialogue main(ZoneId timeZone) throws Exception {
		ExecutableProject project = ActiveDialogueLinkTest.mainAndOther();
		return new ActiveDialogue(project, project.resolve("main", null),
				new VariableStore(new User("u", timeZone)));
	}

	/** Checks that $mood was last changed just now, in the given time zone. */
	private static void changedNow(ActiveDialogue dialogue, ZoneId timeZone, long before) {
		Variable mood = dialogue.getVariableStore().getVariable("mood");
		assertEquals(timeZone.getId(), mood.getUpdatedTimeZone());
		assertTrue(mood.getUpdatedTime() >= before);
		assertTrue(mood.getUpdatedTime() <= System.currentTimeMillis());
	}

	private static ReplyMessage reply(ActiveDialogue dialogue, String text) {
		return dialogue.getCurrentMessage().getReplies().stream()
				.filter(r -> r.getText().equals(text)).findFirst().orElseThrow();
	}

	@Test
	public void startingWithoutATimeStartsNowInTheUsersTimeZone() throws Exception {
		for (ZoneId timeZone : new ZoneId[] { TOKYO, LISBON }) {
			ActiveDialogue dialogue = main(timeZone);
			long before = System.currentTimeMillis();

			Node start = dialogue.startDialogue();

			assertEquals("Start", start.getTitle());
			// The start node sets $mood
			changedNow(dialogue, timeZone, before);
		}
	}

	@Test
	public void startingAtANodeWithoutATime() throws Exception {
		ActiveDialogue dialogue = main(TOKYO);
		assertEquals("Here", dialogue.startDialogue("Here").getTitle());
		assertEquals("Start", main(TOKYO).startDialogue((String) null).getTitle());
	}

	@Test
	public void selectingAReplyByIdWithoutATimeUsesNowInTheUsersTimeZone() throws Exception {
		ActiveDialogue dialogue = main(TOKYO);
		dialogue.startDialogue();
		long before = System.currentTimeMillis();

		Node other = dialogue.selectReply(reply(dialogue, "Set and go.").getReplyId());

		assertEquals("other", dialogue.getDialogueFileDescription().getDialogueName());
		assertEquals("Start", other.getTitle());
		assertEquals("curious", dialogue.getVariableStore().getValue("mood"));
		changedNow(dialogue, TOKYO, before);
	}

	@Test
	public void selectingTheReplyOfAStep() throws Exception {
		ActiveDialogue dialogue = main(LISBON);
		dialogue.startDialogue();
		long before = System.currentTimeMillis();

		Node other = dialogue.selectReply(reply(dialogue, "Set and go."));

		assertEquals("Start", other.getTitle());
		assertEquals("Mood is curious.", dialogue.getCurrentMessage().getStatement().getText()
				.trim());
		changedNow(dialogue, LISBON, before);

		dialogue.selectReply(reply(dialogue, "Next."));
		assertTrue(dialogue.isFinished());
	}
}

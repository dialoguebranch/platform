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

package com.dialoguebranch.model.execute;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.protocol.DialogueMessage;
import com.dialoguebranch.model.execute.protocol.ReplyMessage;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests starting a dialogue of a project with {@link ExecutableProject#startDialogue} (#473):
 * loading a project and playing one of its dialogues takes a few lines.
 */
public class ExecutableProjectStartTest {

	private static ExecutableProject project;

	@BeforeClass
	public static void loadProject() throws Exception {
		project = ExecutableProject.load(Path.of(ExecutableProjectStartTest.class.getClassLoader()
				.getResource("project-test/dlb-project.xml").toURI()));
	}

	private static String text(Node node) {
		return String.valueOf(node.getBody());
	}

	@Test
	public void aDialogueStartsInAFewLines() throws Exception {
		ExecutableProject project = ExecutableProject.load(Path.of(
				ExecutableProjectStartTest.class.getClassLoader()
						.getResource("project-test/dlb-project.xml").toURI()));
		ActiveDialogue dialogue = project.startDialogue("basic", new User("sam"));

		Node start = dialogue.getCurrentNode();
		assertNotNull(start);
		assertEquals("Start", start.getTitle());
		assertTrue(text(start), text(start).startsWith("Hi, my name is Martin McOwl"));
		assertEquals(2, start.getBody().getReplies().size());
		assertEquals(new ResourcePointer("en", "basic", ResourceType.SCRIPT),
				dialogue.getDialogueFileDescription());
		assertEquals("sam", dialogue.getVariableStore().getUser().getId());
	}

	/** The example on the Core Java documentation page, played to its end. */
	@Test
	public void theDocumentedExampleRuns() throws Exception {
		ExecutableProject project = ExecutableProject.load(Path.of(
				ExecutableProjectStartTest.class.getClassLoader()
						.getResource("project-test/dlb-project.xml").toURI()));
		ActiveDialogue dialogue = project.startDialogue("menu", new User("sam"));

		// The documented loop, with the println noted, "chooseReply" taking the first reply,
		// then the last (the first ones of the example project go round in circles), and a cap
		// in case it never ends
		List<String> shown = new ArrayList<>();
		DialogueMessage step = dialogue.getCurrentMessage();
		while (step != null && shown.size() < 50) {
			shown.add(step.getSpeaker() + ": " + step.getStatement().getText());
			if (dialogue.isFinished()) break;
			List<ReplyMessage> replies = step.getReplies();
			dialogue.selectReply(shown.size() == 1 ? replies.get(0)
					: replies.get(replies.size() - 1));
			step = dialogue.getCurrentMessage();
		}

		// The first reply of "menu" leads into "basic", whose last reply goes to End
		assertEquals(2, shown.size());
		assertTrue(shown.get(0), shown.get(0).startsWith("Martin McOwl: This is the Default"));
		assertTrue(shown.get(1), shown.get(1).startsWith("Martin McOwl: Hi, my name is"));
		assertTrue(dialogue.isFinished());
		assertNull(dialogue.getCurrentMessage());
	}

	@Test
	public void aDialogueStartsInTheGivenLanguage() throws Exception {
		ActiveDialogue dialogue = project.startDialogue("basic", "nl-NL",
				new VariableStore(new User("sam")));
		assertEquals("nl-NL", dialogue.getDialogueFileDescription().getLanguage());
		assertSame(project.getDialogue("basic", "nl-NL"), dialogue.getDialogueDefinition());
	}

	@Test
	public void anUnknownLanguageStartsTheSourceLanguage() throws Exception {
		ActiveDialogue dialogue = project.startDialogue("basic", "fr",
				new VariableStore(new User("sam")));
		assertEquals("en", dialogue.getDialogueFileDescription().getLanguage());
	}

	@Test
	public void aDialogueRunsWithTheGivenVariables() throws Exception {
		VariableStore variables = new VariableStore(new User("sam"));
		ActiveDialogue dialogue = project.startDialogue("variables", null, variables);
		assertSame(variables, dialogue.getVariableStore());
		// the start node of "variables" sets $subjectToday
		assertEquals("variables", variables.getValue("subjectToday"));
	}

	@Test
	public void theStartTimeIsUsedForWhatTheFirstNodeChanges() throws Exception {
		ZonedDateTime time = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneId.of("Europe/Lisbon"));
		VariableStore variables = new VariableStore(new User("sam"));
		project.startDialogue("variables", null, variables, time);
		Variable subject = variables.getVariable("subjectToday");
		assertEquals(Long.valueOf(time.toInstant().toEpochMilli()), subject.getUpdatedTime());
		assertEquals("Europe/Lisbon", subject.getUpdatedTimeZone());
	}

	@Test
	public void withoutATimeItStartsNowInTheUsersTimeZone() throws Exception {
		VariableStore variables = new VariableStore(new User("sam", ZoneId.of("Asia/Tokyo")));
		long before = System.currentTimeMillis();
		project.startDialogue("variables", null, variables);
		Variable subject = variables.getVariable("subjectToday");
		assertEquals("Asia/Tokyo", subject.getUpdatedTimeZone());
		assertTrue(subject.getUpdatedTime() >= before);
		assertTrue(subject.getUpdatedTime() <= System.currentTimeMillis());
	}

	@Test
	public void anUnknownDialogueFails() {
		ExecutionException e = assertThrows(ExecutionException.class,
				() -> project.startDialogue("nonexistent", new User("sam")));
		assertEquals(ExecutionException.Type.DIALOGUE_NOT_FOUND, e.getType());
	}

	@Test
	public void theDialogueFollowsRepliesIntoOtherDialoguesOfTheProject() throws Exception {
		ActiveDialogue dialogue = project.startDialogue("menu", new User("sam"));
		int lessonOne = dialogue.getCurrentNode().getBody().getReplies().get(0).getReplyId();

		Node basic = dialogue.selectReply(lessonOne, ZonedDateTime.now());

		assertEquals("basic", dialogue.getDialogueFileDescription().getDialogueName());
		assertTrue(text(basic), text(basic).startsWith("Hi, my name is Martin McOwl"));
	}
}

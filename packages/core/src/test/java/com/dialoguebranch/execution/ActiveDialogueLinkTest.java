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

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * Tests {@link ActiveDialogue#selectReply(int, ZonedDateTime)}: an {@link ActiveDialogue} follows
 * a reply into another dialogue itself, found by its {@link DialogueResolver} (#473).
 */
public class ActiveDialogueLinkTest {

	private static final ZonedDateTime NOW = ZonedDateTime.now();

	/** "main" sets $mood and links into "other", at its Start and at Second. */
	private static final String MAIN = "title: Start\nspeaker: Robin\n---\n"
			+ "<<set $mood = \"happy\">>Hello.\n"
			+ "[[On.|other.Start]]\n[[Skip ahead.|other.Second]]\n[[Stay.|Here]]\n"
			+ "[[Set and go.|other.Start|<<set $mood = \"curious\">>]]\n[[Bye.|End]]\n===\n"
			+ "title: Here\nspeaker: Robin\n---\nStill here.\n===\n"
			+ "title: End\nspeaker: Robin\n---\n===\n";

	private static final String OTHER = "title: Start\nspeaker: Robin\n---\n"
			+ "Mood is $mood.\n[[Next.|Second]]\n[[Back.|main.Start]]\n===\n"
			+ "title: Second\nspeaker: Robin\n---\nWelcome.\n===\n";

	/** Writes a project from the given files, keyed by "language/name.dlb" or ".json". */
	private static ExecutableProject project(Map<String, String> files) throws Exception {
		Map<ResourcePointer, String> content = new LinkedHashMap<>();
		files.forEach((path, text) -> {
			String language = path.substring(0, path.indexOf('/'));
			boolean script = path.endsWith(".dlb");
			String name = path.substring(path.indexOf('/') + 1, path.lastIndexOf('.'));
			content.put(new ResourcePointer(language, name,
					script ? ResourceType.SCRIPT : ResourceType.TRANSLATION), text);
		});
		ScriptLoader loader = new ScriptLoader() {
			@Override
			public List<ResourcePointer> listDialogueBranchFiles() {
				return new ArrayList<>(content.keySet());
			}

			@Override
			public Reader openFile(ResourcePointer file) {
				return new StringReader(content.get(file));
			}
		};
		ProjectParserResult result = new ProjectParser(loader).parse();
		assertTrue(String.valueOf(result.getParseErrors()), result.getParseErrors().isEmpty());
		return Objects.requireNonNull(result.getProject());
	}

	private static ExecutableProject mainAndOther() throws Exception {
		return project(Map.of("en/main.dlb", MAIN, "en/other.dlb", OTHER));
	}

	private static ActiveDialogue start(DialogueResolver resolver, String name,
			String language, VariableStore variables) throws Exception {
		ActiveDialogue dialogue = new ActiveDialogue(resolver,
				resolver.resolve(name, language), variables);
		dialogue.startDialogue(NOW);
		return dialogue;
	}

	private static ActiveDialogue start(ExecutableProject project, String name)
			throws Exception {
		return start(project, name, null, new VariableStore(new User("test")));
	}

	/** Selects the reply whose statement is {@code text}. */
	private static Node select(ActiveDialogue dialogue, String text) throws Exception {
		return dialogue.selectReply(replyId(dialogue, text), NOW);
	}

	private static int replyId(ActiveDialogue dialogue, String text) {
		for (Reply reply : dialogue.getCurrentNode().getBody().getReplies()) {
			if (reply.getStatement() != null
					&& reply.getStatement().toString().trim().equals(text))
				return reply.getReplyId();
		}
		throw new AssertionError("No reply \"" + text + "\" in "
				+ dialogue.getCurrentNode().getBody());
	}

	private static String text(Node node) {
		return String.valueOf(node.getBody());
	}

	@Test
	public void aReplyWithinTheDialogueStaysInIt() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		ResourcePointer main = dialogue.getDialogueFileDescription();

		Node here = select(dialogue, "Stay.");

		assertEquals("Here", here.getTitle());
		assertSame(here, dialogue.getCurrentNode());
		assertSame(main, dialogue.getDialogueFileDescription());
	}

	@Test
	public void aReplyIntoAnotherDialogueSwitchesToIt() throws Exception {
		ExecutableProject project = mainAndOther();
		VariableStore variables = new VariableStore(new User("test"));
		ActiveDialogue dialogue = start(project, "main", null, variables);

		Node other = select(dialogue, "On.");

		assertEquals("Start", other.getTitle());
		assertSame(other, dialogue.getCurrentNode());
		assertEquals("other", dialogue.getDialogueFileDescription().getDialogueName());
		assertEquals("en", dialogue.getDialogueFileDescription().getLanguage());
		assertSame(project.getDialogue("other"), dialogue.getDialogueDefinition());
		assertSame(variables, dialogue.getVariableStore());
		// $mood was set in "main"
		assertTrue(text(other), text(other).startsWith("Mood is happy."));

		// ...and the dialogue goes on in "other"
		Node second = select(dialogue, "Next.");
		assertEquals("Second", second.getTitle());
		assertEquals("other", dialogue.getDialogueFileDescription().getDialogueName());
	}

	@Test
	public void aRepliesSetCommandsRunBeforeTheSwitch() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		Node other = select(dialogue, "Set and go.");
		assertTrue(text(other), text(other).startsWith("Mood is curious."));
	}

	@Test
	public void aReplyIntoANodeOtherThanStartArrivesThere() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		Node second = select(dialogue, "Skip ahead.");
		assertEquals("Second", second.getTitle());
		assertEquals("other", dialogue.getDialogueFileDescription().getDialogueName());
	}

	@Test
	public void aDialogueCanBeSwitchedBackInto() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		select(dialogue, "On.");
		Node main = select(dialogue, "Back.");
		assertEquals("Start", main.getTitle());
		assertEquals("main", dialogue.getDialogueFileDescription().getDialogueName());
		select(dialogue, "On.");
		assertEquals("other", dialogue.getDialogueFileDescription().getDialogueName());
	}

	@Test
	public void aReplyToEndEndsTheDialogue() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		assertNull(select(dialogue, "Bye."));
		assertNull(dialogue.getCurrentNode());
	}

	@Test
	public void theDialogueSwitchedToIsInTheSameLanguage() throws Exception {
		ExecutableProject project = ExecutableProject.load(Path.of(ActiveDialogueLinkTest.class
				.getClassLoader().getResource("project-test/dlb-project.xml").toURI()));
		ActiveDialogue dialogue = start(project, "menu", "nl-NL",
				new VariableStore(new User("test")));
		select(dialogue, "Les: Dialoogstroom");
		assertEquals("basic", dialogue.getDialogueFileDescription().getDialogueName());
		assertEquals("nl-NL", dialogue.getDialogueFileDescription().getLanguage());
		assertSame(project.getDialogue("basic", "nl-NL"), dialogue.getDialogueDefinition());
	}

	@Test
	public void aDialogueWithoutTheTranslationIsInTheSourceLanguage() throws Exception {
		ExecutableProject project = project(Map.of("en/main.dlb", MAIN, "en/other.dlb", OTHER,
				"nl/main.json", "{\"_user\":{\"On.\":\"Verder.\"}}"));
		ActiveDialogue dialogue = start(project, "main", "nl",
				new VariableStore(new User("test")));
		assertEquals("nl", dialogue.getDialogueFileDescription().getLanguage());

		select(dialogue, "Verder.");

		assertEquals("other", dialogue.getDialogueFileDescription().getDialogueName());
		assertEquals("en", dialogue.getDialogueFileDescription().getLanguage());
	}

	@Test
	public void theDialogueSwitchedToFollowsTheUsersGender() throws Exception {
		ExecutableProject project = project(Map.of("en/main.dlb", MAIN, "en/other.dlb", OTHER,
				"nl/main.json", "{\"_user\":{\"On.\":\"Verder.\"}}",
				"nl/other.json", "{\"Robin\":{"
						+ "\"male_addressee\":{\"Welcome.\":\"Welkom, meneer.\"},"
						+ "\"female_addressee\":{\"Welcome.\":\"Welkom, mevrouw.\"},"
						+ "\"Welcome.\":\"Welkom.\"}}"));
		VariableStore variables = new VariableStore(new User("test"));
		variables.setValue("dlb.userGender", "female", false, NOW);
		ActiveDialogue dialogue = start(project, "main", "nl", variables);
		select(dialogue, "Verder.");
		Node second = select(dialogue, "Next.");
		assertTrue(text(second), text(second).startsWith("Welkom, mevrouw."));

		// The gender is read at every step, also after the switch
		dialogue = start(project, "main", "nl", variables);
		select(dialogue, "Verder.");
		variables.setValue("dlb.userGender", "male", false, NOW);
		second = select(dialogue, "Next.");
		assertTrue(text(second), text(second).startsWith("Welkom, meneer."));
	}

	@Test
	public void theListenerHearsTheSwitchBeforeTheFirstNodeRuns() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		List<String> heard = new ArrayList<>();
		dialogue.setDialogueSwitchListener((from, to) -> {
			heard.add(from.getDialogueName() + " -> " + to.pointer().getDialogueName());
			// as an outside service would: a value the new dialogue reads
			dialogue.getVariableStore().setValue("mood", "informed", false, NOW);
		});

		select(dialogue, "Stay.");
		assertEquals(List.of(), heard);

		ActiveDialogue again = start(mainAndOther(), "main");
		again.setDialogueSwitchListener((from, to) -> {
			heard.add(from.getDialogueName() + " -> " + to.pointer().getDialogueName());
			again.getVariableStore().setValue("mood", "informed", false, NOW);
		});
		Node other = select(again, "On.");
		assertEquals(List.of("main -> other"), heard);
		assertTrue(text(other), text(other).startsWith("Mood is informed."));
	}

	@Test
	public void aListenerCanStopTheSwitch() throws Exception {
		ActiveDialogue dialogue = start(mainAndOther(), "main");
		Node main = dialogue.getCurrentNode();
		dialogue.setDialogueSwitchListener((from, to) -> {
			throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND, "No.");
		});

		ExecutionException e = assertThrows(ExecutionException.class,
				() -> select(dialogue, "On."));

		assertEquals("No.", e.getMessage());
		assertEquals("main", dialogue.getDialogueFileDescription().getDialogueName());
		assertSame(main, dialogue.getCurrentNode());
	}

	@Test
	public void aDialogueTheResolverCantFindFailsAndStaysPut() throws Exception {
		ExecutableProject project = mainAndOther();
		DialogueResolver withoutOther = (name, language) -> {
			if (name.equals("other"))
				throw new ExecutionException(ExecutionException.Type.DIALOGUE_NOT_FOUND,
						"Dialogue not found: " + name);
			return project.resolve(name, language);
		};
		ActiveDialogue dialogue = start(withoutOther, "main", null,
				new VariableStore(new User("test")));
		Node main = dialogue.getCurrentNode();

		ExecutionException e = assertThrows(ExecutionException.class,
				() -> select(dialogue, "On."));

		assertEquals(ExecutionException.Type.DIALOGUE_NOT_FOUND, e.getType());
		assertEquals("main", dialogue.getDialogueFileDescription().getDialogueName());
		assertSame(main, dialogue.getCurrentNode());
	}

	@Test
	public void aMissingTargetNodeFailsBeforeTheListenerHearsOfIt() throws Exception {
		ExecutableProject project = mainAndOther();
		Dialogue emptyOther = new Dialogue("other");
		DialogueResolver resolver = (name, language) -> name.equals("other")
				? new ResolvedDialogue(new ResourcePointer("en", "other", ResourceType.SCRIPT),
						emptyOther)
				: project.resolve(name, language);
		ActiveDialogue dialogue = start(resolver, "main", null,
				new VariableStore(new User("test")));
		List<ResourcePointer> heard = new ArrayList<>();
		dialogue.setDialogueSwitchListener((from, to) -> heard.add(to.pointer()));

		ExecutionException e = assertThrows(ExecutionException.class,
				() -> select(dialogue, "On."));

		assertEquals(ExecutionException.Type.NODE_NOT_FOUND, e.getType());
		assertEquals("Node \"Start\" not found in dialogue \"other\"", e.getMessage());
		assertTrue(heard.isEmpty());
		assertEquals("main", dialogue.getDialogueFileDescription().getDialogueName());
	}

	@Test
	public void theResolverIsAskedInTheCurrentLanguage() throws Exception {
		ExecutableProject project = mainAndOther();
		List<String> asked = new ArrayList<>();
		DialogueResolver resolver = (name, language) -> {
			asked.add(name + "@" + language);
			return project.resolve(name, language);
		};
		ActiveDialogue dialogue = start(resolver, "main", "en",
				new VariableStore(new User("test")));
		select(dialogue, "On.");
		assertEquals(List.of("main@en", "other@en"), asked);
	}
}

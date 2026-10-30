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


package com.dialoguebranch.i18n;

import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * Tests translating for the end user's gender, {@code $dlb.userGender} (#400): it is read at
 * every step, and only a translation that uses it differs per gender.
 */
public class UserGenderTranslationTest {

	private static final String SCRIPT =
			"title: Start\nspeaker: Coach\n---\nWelcome.\n[[Go on.|Next]]\n===\n" +
			"title: Next\nspeaker: Coach\n---\nWelcome.\n===\n";

	private static final String DUTCH = "{\"Coach\": {" +
			"\"male_addressee\": {\"Welcome.\": \"Welkom, meneer.\"}," +
			"\"female_addressee\": {\"Welcome.\": \"Welkom, mevrouw.\"}," +
			"\"Welcome.\": \"Welkom.\"}}";

	private static final ResourcePointer NL = new ResourcePointer("nl", "main",
			ResourceType.TRANSLATION);

	private static ExecutableProject project(String translation) throws Exception {
		ScriptLoader loader = new ScriptLoader() {
			@Override
			public List<ResourcePointer> listDialogueBranchFiles() {
				return List.of(new ResourcePointer("en", "main", ResourceType.SCRIPT), NL);
			}

			@Override
			public Reader openFile(ResourcePointer file) {
				return new StringReader(file.getResourceType() == ResourceType.SCRIPT
						? SCRIPT : translation);
			}
		};
		ProjectParserResult result = new ProjectParser(loader).parse();
		assertTrue(String.valueOf(result.getParseErrors()), result.getParseErrors().isEmpty());
		return (ExecutableProject) Objects.requireNonNull(result.getProject());
	}

	private static String text(Node node) {
		return String.valueOf(node.getBody());
	}

	@Test
	public void theUsersGenderIsReadAtEveryStep() throws Exception {
		ExecutableProject project = project(DUTCH);
		VariableStore variables = new VariableStore(new User("test"));
		ActiveDialogue dialogue = new ActiveDialogue(NL,
				Objects.requireNonNull(project.getDialogues().get(NL)), variables);
		dialogue.setUserGenderTranslations((gender) ->
				project.getDialogueForUserGender(NL, gender));

		Node start = dialogue.startDialogue(ZonedDateTime.now());
		assertTrue(text(start), text(start).startsWith("Welkom."));

		variables.setValue("dlb.userGender", "female", false, ZonedDateTime.now());
		Node next = dialogue.progressDialogue(new InternalNodePointer("Start", "Next"),
				ZonedDateTime.now());
		assertTrue(text(next), text(Objects.requireNonNull(next)).startsWith("Welkom, mevrouw."));
	}

	@Test
	public void aTranslationThatDoesntUseTheUsersGenderIsShared() throws Exception {
		ExecutableProject project = project("{\"Coach\": {\"Welcome.\": \"Welkom.\"}}");
		Dialogue undefined = project.getDialogueForUserGender(NL, TranslationContext.Gender.UNDEFINED);
		assertSame(undefined, project.getDialogueForUserGender(NL, TranslationContext.Gender.MALE));
		assertSame(undefined, project.getDialogueForUserGender(NL, TranslationContext.Gender.FEMALE));
	}

	@Test
	public void aTranslationForAGenderIsMadeOnceAndKept() throws Exception {
		ExecutableProject project = project(DUTCH);
		Dialogue male = project.getDialogueForUserGender(NL, TranslationContext.Gender.MALE);
		assertSame(male, project.getDialogueForUserGender(NL, TranslationContext.Gender.MALE));
		assertNotSame(male, project.getDialogueForUserGender(NL, TranslationContext.Gender.FEMALE));
	}

	@Test
	public void theUserContextFollowsTheUsersGenderWhereverTheUserIs() throws Exception {
		// "Welcome." stands in for a sentence that only talks about the user.
		ExecutableProject project = project("{\"Coach\": {" +
				"\"female_user\": {\"Welcome.\": \"Kijk haar eens.\"}," +
				"\"male_user\": {\"Welcome.\": \"Kijk hem eens.\"}," +
				"\"Welcome.\": \"Kijk eens.\"}}");
		assertTrue(text(start(project, TranslationContext.Gender.FEMALE)).startsWith("Kijk haar eens."));
		assertTrue(text(start(project, TranslationContext.Gender.MALE)).startsWith("Kijk hem eens."));
		assertTrue(text(start(project, TranslationContext.Gender.UNDEFINED)).startsWith("Kijk eens."));
	}

	private static Node start(ExecutableProject project, TranslationContext.Gender gender) {
		Dialogue dialogue = Objects.requireNonNull(project.getDialogueForUserGender(NL, gender));
		return Objects.requireNonNull(dialogue.getStartNode());
	}
}

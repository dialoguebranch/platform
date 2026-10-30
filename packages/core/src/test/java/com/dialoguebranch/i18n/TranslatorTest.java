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

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link Translator}: a translation entry only applies to text of its own speaker (#377).
 */
public class TranslatorTest {

	/** Martin McOwl says "Hello." and the user replies "Next."; Anna says "Hello." too. */
	private static final String SCRIPT =
			"title: Start\nspeaker: Martin McOwl\n---\nHello.\n[[Next.|Anna]]\n===\n" +
			"title: Anna\nspeaker: Anna\n---\nHello.\n===\n";

	private static Dialogue translate(String json, TranslationContext context) throws IOException {
		ParserResult parsed;
		try (DialogueBranchParser parser =
					 new DialogueBranchParser("main", new StringReader(SCRIPT))) {
			parsed = parser.readDialogue();
		}
		assertEquals(List.of(), parsed.getParseErrors());
		TranslationParserResult translations = TranslationParser.parse(new StringReader(json));
		assertEquals(List.of(), translations.getParseErrors());
		return new Translator(context, translations.getTranslations()).translate(parsed.getDialogue());
	}

	private static Dialogue translate(String json) throws IOException {
		return translate(json, new TranslationContext());
	}

	private static String body(Dialogue dialogue, String title) {
		Node node = dialogue.getNodes().stream()
				.filter(n -> n.getTitle().equals(title)).findFirst().orElseThrow();
		return String.valueOf(node.getBody());
	}

	@Test
	public void theSameTextIsTranslatedPerSpeaker() throws IOException {
		Dialogue dialogue = translate("{\"Martin McOwl\": {\"Hello.\": \"Hallo (Martin).\"}," +
				"\"Anna\": {\"Hello.\": \"Hallo (Anna).\"}}");
		assertTrue(body(dialogue, "Start"), body(dialogue, "Start").startsWith("Hallo (Martin)."));
		assertTrue(body(dialogue, "Anna"), body(dialogue, "Anna").startsWith("Hallo (Anna)."));
	}

	@Test
	public void anEntryOfAnotherSpeakerDoesntApply() throws IOException {
		Dialogue dialogue = translate("{\"Martin McOwl\": {\"Hello.\": \"Hallo.\"}," +
				"\"Bob\": {\"Next.\": \"Volgende.\"}}");
		assertTrue(body(dialogue, "Start").startsWith("Hallo."));
		assertTrue(body(dialogue, "Start"), body(dialogue, "Start").contains("[[Next.|"));
		assertTrue(body(dialogue, "Anna"), body(dialogue, "Anna").startsWith("Hello."));
	}

	@Test
	public void theUsersRepliesAreTranslatedByTheUserGroup() throws IOException {
		Dialogue dialogue = translate("{\"_user\": {\"Next.\": \"Volgende.\"}}");
		assertTrue(body(dialogue, "Start"), body(dialogue, "Start").contains("[[Volgende.|"));
		assertTrue(body(dialogue, "Start"), body(dialogue, "Start").startsWith("Hello."));
	}

	@Test
	public void genderContextsChooseAmongTheSpeakersEntries() throws IOException {
		String json = "{\"Martin McOwl\": {\"female_addressee\": {\"Hello.\": \"Hallo (f).\"}," +
				"\"Hello.\": \"Hallo.\"}, \"Anna\": {\"Hello.\": \"Hoi (Anna).\"}}";
		Dialogue toWoman = translate(json, new TranslationContext(
				TranslationContext.Gender.FEMALE, Map.of()));
		assertTrue(body(toWoman, "Start"), body(toWoman, "Start").startsWith("Hallo (f)."));
		Dialogue toMan = translate(json, new TranslationContext(
				TranslationContext.Gender.MALE, Map.of()));
		assertTrue(body(toMan, "Start"), body(toMan, "Start").startsWith("Hallo."));
		assertFalse(body(toMan, "Start"), body(toMan, "Start").contains("Hoi"));
	}

	@Test
	public void aSpeakerOnlyGenderedTranslationStillAppliesToTheSpeaker() throws IOException {
		Dialogue dialogue = translate("{\"Martin McOwl\": {\"female_addressee\": " +
				"{\"Hello.\": \"Hallo (f).\"}}}", new TranslationContext(
				TranslationContext.Gender.MALE, Map.of()));
		assertTrue(body(dialogue, "Start"), body(dialogue, "Start").startsWith("Hallo (f)."));
		assertTrue(body(dialogue, "Anna"), body(dialogue, "Anna").startsWith("Hello."));
	}

	// -------------------- commands among the text (#437) -------------------- //

	/** Parses a one-node script spoken by Robot, translates it with {@code json} and returns it. */
	private static Node translatedStart(String body, String json) throws IOException {
		ParserResult parsed;
		try (DialogueBranchParser parser = new DialogueBranchParser("main",
				new StringReader("title: Start\nspeaker: Robot\n---\n" + body + "===\n"))) {
			parsed = parser.readDialogue();
		}
		assertEquals(List.of(), parsed.getParseErrors());
		TranslationParserResult translations = TranslationParser.parse(new StringReader(json));
		assertEquals(List.of(), translations.getParseErrors());
		return new Translator(new TranslationContext(), translations.getTranslations())
				.translate(parsed.getDialogue()).getNodeById("Start");
	}

	/** Runs a node with {@code $theme} set to "light" and returns what it says. */
	private static String say(Node node) throws Exception {
		Map<String,Object> variables = new HashMap<>(Map.of("theme", "light"));
		return ((NodeBody) node.getBody()).execute(variables, true).getSegments().stream()
				.map(Object::toString).collect(Collectors.joining()).trim();
	}

	@Test
	public void aSetBeforeTheTextStillRunsBeforeItsTranslation() throws Exception {
		Node node = translatedStart("<<set $theme = \"dark\">>\n\nYour theme is $theme.\n",
				"{\"Robot\": {\"Your theme is $theme.\": \"Uw thema is $theme.\"}}");
		assertEquals("Uw thema is dark.", say(node));
	}

	@Test
	public void aSetAfterTheTextStaysAfterItsTranslation() throws Exception {
		Node node = translatedStart("Your theme is $theme.\n<<set $theme = \"dark\">>\n",
				"{\"Robot\": {\"Your theme is $theme.\": \"Uw thema is $theme.\"}}");
		assertEquals("Uw thema is light.", say(node));
	}

	@Test
	public void aSetInsideTheTextFollowsItsTranslation() throws Exception {
		// A translation can't say where inside its own text the command belongs.
		Node node = translatedStart("It was $theme, <<set $theme = \"dark\">>now $theme.\n",
				"{\"Robot\": {\"It was $theme, now $theme.\": \"Het was $theme, nu $theme.\"}}");
		assertEquals("Het was light, nu light.", say(node));
	}

	@Test
	public void anUntranslatedRunKeepsItsSourceOrder() throws Exception {
		String body = "It was $theme, <<set $theme = \"dark\">>now $theme.\n";
		Node node = translatedStart(body, "{\"Robot\": {\"Something else.\": \"Iets anders.\"}}");
		assertEquals("It was light, now dark.", say(node));
	}
}

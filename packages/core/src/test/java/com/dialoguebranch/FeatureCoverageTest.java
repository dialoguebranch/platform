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

package com.dialoguebranch;

import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ProjectScriptLoader;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards that the bundled {@code project-test} project (mirrored from the Web Service's
 * {@code default-test} seed) keeps exercising every Dialogue Branch language feature: it must
 * parse without a single error or warning, every source dialogue must walk from {@code Start}
 * without throwing, every internal reply target must resolve, the translations must apply, and
 * the raw scripts must contain each language construct at least once. See issue #173.
 *
 * @author Harm op den Akker
 */
public class FeatureCoverageTest {

	private static ProjectParserResult parseResult;
	private static File projectDir;

	@BeforeClass
	public static void loadProject() throws Exception {
		URL xmlUrl = FeatureCoverageTest.class.getClassLoader()
				.getResource("project-test/dlb-project.xml");
		assertNotNull("project-test/dlb-project.xml not found on test classpath", xmlUrl);
		File projectFile = new File(xmlUrl.toURI());
		projectDir = projectFile.getParentFile();
		parseResult = new ProjectParser(new ProjectScriptLoader(projectFile)).parse();
	}

	// ----------------------------------------------------------------------------

	@Test
	public void projectParsesWithoutErrorsOrWarnings() {
		assertTrue("Unexpected parse errors: " + parseResult.getParseErrors(),
				parseResult.getParseErrors().isEmpty());
		assertTrue("Unexpected parse warnings: " + parseResult.getWarnings(),
				parseResult.getWarnings().isEmpty());
	}

	/**
	 * For every English source dialogue: checks that every internal reply points at a node that
	 * exists, then walks one path from {@code Start} — always taking the first not-yet-visited
	 * internal reply — with a shared variable store, so earlier {@code set} commands stay in
	 * effect. Executing each node on that path runs its {@code set} / {@code if} / {@code random}
	 * / {@code action} segments; none may throw. The lesson dialogues are linear, so this covers
	 * them fully; the branchy showcases are additionally navigated by {@link ProjectTest}.
	 */
	@Test
	public void everySourceDialogueWalksWithoutThrowing() throws Exception {
		ZonedDateTime now = ZonedDateTime.now();
		int dialogues = 0;
		for (Map.Entry<ResourcePointer, Dialogue> entry
				: parseResult.getProject().getDialogues().entrySet()) {
			if (!entry.getKey().getLanguage().equals("en"))
				continue;
			dialogues++;
			Dialogue dialogue = entry.getValue();
			String name = dialogue.getDialogueName();

			Set<String> titles = new LinkedHashSet<>();
			for (Node node : dialogue.getNodes())
				titles.add(node.getTitle().toLowerCase());
			for (Node node : dialogue.getNodes())
				for (Reply reply : node.getBody().getReplies())
					if (reply.getNodePointer() instanceof InternalNodePointer)
						assertTrue(name + ", node '" + node.getTitle() + "': reply points at "
										+ "unknown node '" + reply.getNodePointer().getTargetNodeId() + "'",
								titles.contains(reply.getNodePointer().getTargetNodeId().toLowerCase()));

			ActiveDialogue active = new ActiveDialogue(entry.getKey(), dialogue,
					new VariableStore(new User("coverage-test")));
			Node node;
			try {
				node = active.startDialogue(now);
			} catch (Exception e) {
				throw new AssertionError("Starting dialogue '" + name + "' threw", e);
			}
			Set<String> visited = new LinkedHashSet<>();
			visited.add(node.getTitle().toLowerCase());
			while (node != null) {
				Reply step = null;
				for (Reply reply : node.getBody().getReplies()) {
					if (!(reply.getNodePointer() instanceof InternalNodePointer))
						continue;
					String target = reply.getNodePointer().getTargetNodeId().toLowerCase();
					if (target.equals("end") || visited.contains(target))
						continue;
					step = reply;
					break;
				}
				if (step == null)
					break;
				visited.add(step.getNodePointer().getTargetNodeId().toLowerCase());
				try {
					NodePointer np = active.processReplyAndGetNodePointer(step.getReplyId(), now);
					node = (np instanceof InternalNodePointer ip)
							? active.progressDialogue(ip, now) : null;
				} catch (Exception e) {
					boolean inputReply = step.getStatement() != null
							&& step.getStatement().toString().contains("input");
					if (!inputReply)
						throw new AssertionError("Walking '" + name + "' from node '"
								+ node.getTitle() + "' threw", e);
					break;
				}
			}
		}
		assertEquals("expected 14 English source dialogues", 14, dialogues);
	}

	/**
	 * Scans the raw English {@code .dlb} sources and fails if any language construct is no longer
	 * demonstrated anywhere in the project.
	 */
	@Test
	public void rawSourcesExerciseEveryLanguageConstruct() throws Exception {
		String all = readAllEnglishSources();

		Map<String, String> required = new java.util.LinkedHashMap<>();
		// Statements & structure
		required.put("line comment", "// ");
		required.put("escaped dollar", "\\$");
		required.put("escaped backslash", "\\\\");
		required.put("passthrough markup", "<b>");
		required.put("colorId (canonical spelling)", "colorId:");
		required.put("colorID (legacy spelling)", "colorID:");
		// set / expressions
		required.put("set command", "<<set ");
		required.put("self-referential increment", "= $count + 1");
		required.put("string + number coercion", "1 + 2 + \"3\"");
		required.put("bracketed expression", "1 + (2 + \"3\")");
		// if
		required.put("if command", "<<if ");
		required.put("elseif clause", "<<elseif ");
		required.put("else clause", "<<else>>");
		required.put("not-equals operator", "!=");
		required.put("greater-than operator", "$count > 1");
		required.put("less-than operator", "$count < 5");
		required.put("greater-or-equal operator", ">= 2");
		required.put("less-or-equal operator", "<= 2");
		required.put("logical and", "&&");
		required.put("logical or", "||");
		// random
		required.put("random command", "<<random>>");
		required.put("weighted random", "<<random weight=");
		required.put("or clause", "<<or>>");
		// action
		required.put("link action", "type=\"link\"");
		required.put("image action", "type=\"image\"");
		required.put("video action", "type=\"video\"");
		required.put("generic action", "type=\"generic\"");
		required.put("action with extra params", "delay=\"2000\" page=\"42\"");
		required.put("reply-attached action", "|<<action ");
		// input
		required.put("text input", "type=\"text\"");
		required.put("longtext input", "type=\"longtext\"");
		required.put("email input", "type=\"email\"");
		required.put("numeric input", "type=\"numeric\"");
		required.put("time input", "type=\"time\"");
		required.put("set input", "type=\"set\"");
		required.put("input description param", "description=\"your first name\"");
		// replies
		required.put("auto-forward reply", "[[Explain2]]");
		required.put("reply-attached set", "|<<set ");
		// linking
		required.put("cross-dialogue link", ".Start]]");
		required.put("absolute path link", "|/bg1/");
		required.put("parent path link", "|../menu.Start");
		required.put("explicit current path link", "|./basic.Start");

		Set<String> missing = new TreeSet<>();
		for (Map.Entry<String, String> e : required.entrySet())
			if (!all.contains(e.getValue()))
				missing.add(e.getKey() + "  (looked for: " + e.getValue() + ")");
		if (!missing.isEmpty())
			fail("project-test no longer demonstrates:\n  " + String.join("\n  ", missing));
	}

	/**
	 * Confirms the translation JSON for the newer lesson dialogues actually applies (a mismatched
	 * key would silently leave the English text in place).
	 */
	@Test
	public void translationsApplyForTheLessonDialogues() throws Exception {
		assertStartText("nl-NL", "random", "Deze les gaat over de \"random\"-uitspraak");
		assertStartText("pt-PT", "random", "Esta lição é sobre a afirmação \"random\"");
		assertStartText("nl-NL", "actions", "Deze les gaat over de \"action\"-uitspraak");
		assertStartText("pt-PT", "inputs", "Esta lição é sobre respostas de entrada");
		assertStartText("nl-NL", "statements", "Deze les gaat over uitspraken");
	}

	// ----------------------------------------------------------------------------

	private void assertStartText(String language, String dialogueName, String expectedFragment)
			throws Exception {
		Dialogue dialogue = null;
		for (Map.Entry<ResourcePointer, Dialogue> entry
				: parseResult.getProject().getDialogues().entrySet()) {
			if (entry.getKey().getLanguage().equals(language)
					&& entry.getKey().getDialogueName().equals(dialogueName)) {
				dialogue = entry.getValue();
				break;
			}
		}
		assertNotNull(language + " '" + dialogueName + "' dialogue should be present", dialogue);
		ActiveDialogue active = new ActiveDialogue(
				new ResourcePointer(language, dialogueName, null), dialogue,
				new VariableStore(new User("coverage-test")));
		Node start = active.startDialogue(ZonedDateTime.now());
		StringBuilder text = new StringBuilder();
		start.getBody().getSegments().forEach(text::append);
		assertTrue(language + " '" + dialogueName + "' Start should be translated, got: " + text,
				text.toString().contains(expectedFragment));
	}

	private String readAllEnglishSources() throws Exception {
		File enDir = new File(projectDir, "en");
		StringBuilder sb = new StringBuilder();
		collectDlb(enDir, sb);
		return sb.toString();
	}

	private void collectDlb(File dir, StringBuilder sb) throws Exception {
		File[] children = dir.listFiles();
		if (children == null)
			return;
		for (File child : children) {
			if (child.isDirectory())
				collectDlb(child, sb);
			else if (child.getName().endsWith(".dlb"))
				sb.append(Files.readString(child.toPath(), StandardCharsets.UTF_8)).append('\n');
		}
	}
}

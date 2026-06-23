/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
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
import com.dialoguebranch.execution.parser.ProjectFileLoader;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration tests for loading and executing the {@code project-test} Dialogue Branch project,
 * which is included as a test resource from {@code examples/project-test}. Tests cover project
 * parsing, language map validation, and dialogue execution in English, Dutch, and Portuguese.
 *
 * @author Harm op den Akker
 */
public class ProjectTest {

    // ------------------------------------------------------------- //
    // -------------------- Constants / Fixtures -------------------- //
    // ------------------------------------------------------------- //

    private static final String LANG_EN    = "en";
    private static final String LANG_NL    = "nl-NL";
    private static final String LANG_PT    = "pt-PT";

    private static final int SOURCE_DIALOGUE_COUNT = 10;

    /** Shared parsed project loaded once for all tests in this class. */
    private static ProjectParserResult parseResult;

    // ----------------------------------------------------------- //
    // -------------------- Test Fixture Setup -------------------- //
    // ----------------------------------------------------------- //

    /**
     * Loads the {@code project-test} project once before any test in this class runs.
     */
    @BeforeClass
    public static void loadProject() throws Exception {
        URL xmlUrl = ProjectTest.class.getClassLoader()
                .getResource("project-test/dlb-project.xml");
        assertNotNull("project-test/dlb-project.xml not found on test classpath", xmlUrl);
        File projectFile = new File(xmlUrl.toURI());

        ProjectFileLoader loader = new ProjectFileLoader(projectFile);
        parseResult = new ProjectParser(loader).parse();
    }

    // --------------------------------------------------------------- //
    // -------------------- Helpers -------------------- //
    // --------------------------------------------------------------- //

    /**
     * Finds a {@link Dialogue} by language and name in the parsed project's dialogue map.
     */
    private Dialogue findDialogue(String language, String name) {
        for (Map.Entry<ResourcePointer, Dialogue> entry
                : parseResult.getProject().getDialogues().entrySet()) {
            ResourcePointer rp = entry.getKey();
            if (rp.getLanguage().equals(language) && rp.getDialogueName().equals(name))
                return entry.getValue();
        }
        return null;
    }

    /**
     * Creates an {@link ActiveDialogue} for the given language and dialogue name,
     * ready to execute (with an empty {@link VariableStore}).
     */
    private ActiveDialogue createActiveDialogue(String language, String name) {
        Dialogue dialogue = findDialogue(language, name);
        assertNotNull("Dialogue '" + name + "' (" + language + ") not found", dialogue);
        ResourcePointer rp = new ResourcePointer(language, name, null);
        ActiveDialogue ad = new ActiveDialogue(rp, dialogue);
        ad.setVariableStore(new VariableStore(new User("test-user")));
        return ad;
    }

    /**
     * Returns the plain text of the agent statement in {@code node} (segments only, no replies).
     */
    private String agentText(Node node) {
        StringBuilder sb = new StringBuilder();
        for (NodeBody.Segment segment : node.getBody().getSegments())
            sb.append(segment);
        return sb.toString().trim();
    }

    /**
     * Advances an {@link ActiveDialogue} by selecting the reply at the given zero-based
     * {@code replyIndex} and returns the next {@link Node}, or {@code null} if the dialogue ended.
     */
    private Node selectReply(ActiveDialogue ad, Node current, int replyIndex) throws Exception {
        List<Reply> replies = current.getBody().getReplies();
        int replyId = replies.get(replyIndex).getReplyId();
        NodePointer np = ad.processReplyAndGetNodePointer(replyId, ZonedDateTime.now());
        if (np instanceof InternalNodePointer ip)
            return ad.progressDialogue(ip, ZonedDateTime.now());
        return null;
    }

    // ----------------------------------------------------------------- //
    // -------------------- Tests: Project Parsing -------------------- //
    // ----------------------------------------------------------------- //

    /**
     * Verifies that the project-test project parses without any errors or warnings.
     */
    @Test
    public void testProjectParsesWithoutErrors() {
        assertTrue("Unexpected parse errors: " + parseResult.getParseErrors(),
                parseResult.getParseErrors().isEmpty());
    }

    /**
     * Verifies that the {@link ExecutableProject} is non-null after a successful parse.
     */
    @Test
    public void testProjectIsNotNull() {
        assertNotNull("ExecutableProject should not be null after parsing", parseResult.getProject());
    }

    /**
     * Verifies that exactly {@value #SOURCE_DIALOGUE_COUNT} source dialogues are present
     * (one per English {@code .dlb} file).
     */
    @Test
    public void testSourceDialogueCount() {
        int count = parseResult.getProject().getSourceDialogues().size();
        assertEquals("Expected " + SOURCE_DIALOGUE_COUNT + " source dialogues",
                SOURCE_DIALOGUE_COUNT, count);
    }

    /**
     * Verifies that the total dialogue map contains source dialogues plus translations for
     * two translation languages (nl-NL and pt-PT).
     */
    @Test
    public void testTotalDialogueCount() {
        int expected = SOURCE_DIALOGUE_COUNT * 3; // en + nl-NL + pt-PT
        int actual = parseResult.getProject().getDialogues().size();
        assertEquals("Expected " + expected + " total dialogues (source + 2 translations)",
                expected, actual);
    }

    // ------------------------------------------------------------------- //
    // -------------------- Tests: Language Map -------------------- //
    // ------------------------------------------------------------------- //

    /**
     * Verifies that the project metadata declares English as the single source language.
     */
    @Test
    public void testSourceLanguageIsEnglish() {
        ProjectMetaData meta = parseResult.getProject().getMetaData();
        assertNotNull("ProjectMetaData should be present", meta);
        List<String> sourceCodes = meta.getSourceLanguageCodes();
        assertEquals("Expected exactly one source language", 1, sourceCodes.size());
        assertEquals("Source language should be 'en'", LANG_EN, sourceCodes.get(0));
    }

    /**
     * Verifies that Dutch (nl-NL) and Portuguese (pt-PT) are declared as translation languages.
     */
    @Test
    public void testTranslationLanguagesPresent() {
        ProjectMetaData meta = parseResult.getProject().getMetaData();
        assertNotNull(meta);
        List<String> supportedCodes = meta.getSupportedLanguageCodes();
        assertTrue("nl-NL should be a supported language", supportedCodes.contains(LANG_NL));
        assertTrue("pt-PT should be a supported language", supportedCodes.contains(LANG_PT));
    }

    // ---------------------------------------------------------------------------- //
    // -------------------- Tests: Executing 'basic' in English -------------------- //
    // ---------------------------------------------------------------------------- //

    /**
     * Verifies that the English {@code basic} dialogue is present in the parsed project.
     */
    @Test
    public void testEnglishBasicDialogueExists() {
        assertNotNull("English 'basic' dialogue should be present", findDialogue(LANG_EN, "basic"));
    }

    /**
     * Verifies that the Start node of the English {@code basic} dialogue contains the expected
     * opening statement.
     */
    @Test
    public void testEnglishBasicStartNodeText() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(start);
        String text = agentText(start);
        assertTrue("Expected English opening text, got: " + text,
                text.contains("Hi, my name is Martin McOwl"));
    }

    /**
     * Verifies that the Start node of the English {@code basic} dialogue has exactly two reply
     * options.
     */
    @Test
    public void testEnglishBasicStartNodeReplyCount() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(start);
        assertEquals("Start node should have 2 reply options", 2,
                start.getBody().getReplies().size());
    }

    /**
     * Navigates the English {@code basic} dialogue along a simple path and verifies the text of
     * a subsequent node.
     */
    @Test
    public void testEnglishBasicDialogueNavigation() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());

        // Reply 0: "Nice to meet you Martin McOwl." → Continue node
        Node continueNode = selectReply(ad, start, 0);
        assertNotNull("Expected a 'Continue' node after first reply", continueNode);
        String text = agentText(continueNode);
        assertTrue("Continue node should describe basic dialogue flow, got: " + text,
                text.contains("This dialogue is very basic"));

        // Reply 0 from Continue: auto-forward to DialogueMenu
        Node menuNode = selectReply(ad, continueNode, 0);
        assertNotNull("Expected a DialogueMenu node", menuNode);
        assertTrue("DialogueMenu should mention reply options, got: " + agentText(menuNode),
                agentText(menuNode).contains("We deal with statements"));
    }

    /**
     * Verifies that selecting the "Goodbye" reply on the Start node ends the dialogue (returns
     * {@code null} for the next node).
     */
    @Test
    public void testEnglishBasicGoodbyeEndsDialogue() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());

        // Reply 1: "Goodbye." → End node → dialogue over
        Node next = selectReply(ad, start, 1);
        assertNull("Selecting 'Goodbye' should end the dialogue (null next node)", next);
    }

    // ------------------------------------------------------------------------------ //
    // -------------------- Tests: Executing 'basic' in Dutch (nl-NL) -------------------- //
    // ------------------------------------------------------------------------------ //

    /**
     * Verifies that the Dutch translation of the {@code basic} dialogue is present.
     */
    @Test
    public void testDutchBasicDialogueExists() {
        assertNotNull("Dutch 'basic' dialogue should be present", findDialogue(LANG_NL, "basic"));
    }

    /**
     * Verifies that the Start node of the Dutch {@code basic} dialogue contains the
     * Dutch-translated opening statement.
     */
    @Test
    public void testDutchBasicStartNodeText() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_NL, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(start);
        String text = agentText(start);
        assertTrue("Expected Dutch opening text, got: " + text,
                text.contains("Hallo, mijn naam is Martin McOwl"));
    }

    /**
     * Navigates the Dutch {@code basic} dialogue and verifies that translated text appears in
     * subsequent nodes.
     */
    @Test
    public void testDutchBasicDialogueNavigation() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_NL, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());

        Node continueNode = selectReply(ad, start, 0);
        assertNotNull(continueNode);
        String text = agentText(continueNode);
        assertTrue("Expected Dutch 'Continue' node text, got: " + text,
                text.contains("Dit dialoog is erg eenvoudig"));
    }

    // ------------------------------------------------------------------------------------- //
    // -------------------- Tests: Executing 'basic' in Portuguese (pt-PT) -------------------- //
    // ------------------------------------------------------------------------------------- //

    /**
     * Verifies that the Portuguese translation of the {@code basic} dialogue is present.
     */
    @Test
    public void testPortugueseBasicDialogueExists() {
        assertNotNull("Portuguese 'basic' dialogue should be present",
                findDialogue(LANG_PT, "basic"));
    }

    /**
     * Verifies that the Start node of the Portuguese {@code basic} dialogue contains the
     * Portuguese-translated opening statement.
     */
    @Test
    public void testPortugueseBasicStartNodeText() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_PT, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(start);
        String text = agentText(start);
        assertTrue("Expected Portuguese opening text, got: " + text,
                text.contains("Olá, o meu nome é Martin McOwl"));
    }

    /**
     * Navigates the Portuguese {@code basic} dialogue and verifies that translated text appears in
     * subsequent nodes.
     */
    @Test
    public void testPortugueseBasicDialogueNavigation() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_PT, "basic");
        Node start = ad.startDialogue(ZonedDateTime.now());

        Node continueNode = selectReply(ad, start, 0);
        assertNotNull(continueNode);
        String text = agentText(continueNode);
        assertTrue("Expected Portuguese 'Continue' node text, got: " + text,
                text.contains("Este diálogo é muito básico"));
    }

    // ------------------------------------------------------------------------------------------- //
    // -------------------- Tests: Executing 'bg1/bg1-menu' in Portuguese (pt-PT) -------------------- //
    // ------------------------------------------------------------------------------------------- //

    /**
     * Verifies that the Portuguese translation of {@code bg1/bg1-menu} is present.
     */
    @Test
    public void testPortugueseBg1MenuExists() {
        assertNotNull("Portuguese 'bg1/bg1-menu' dialogue should be present",
                findDialogue(LANG_PT, "bg1/bg1-menu"));
    }

    /**
     * Verifies that the Portuguese {@code bg1/bg1-menu} Start node contains the translated
     * menu statement.
     */
    @Test
    public void testPortugueseBg1MenuStartNodeText() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_PT, "bg1/bg1-menu");
        Node start = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(start);
        String text = agentText(start);
        assertTrue("Expected Portuguese bg1-menu text, got: " + text,
                text.contains("Este é o Menu do Baldur's Gate 1."));
    }

    // ----------------------------------------------------------------------------- //
    // -------------------- Tests: Executing 'conditionals' in English -------------------- //
    // ----------------------------------------------------------------------------- //

    /**
     * Verifies that the {@code conditionals} dialogue sets variables and evaluates conditional
     * branches correctly. The second node sets {@code $variableThatIsTrue} and
     * {@code $variableThatIsFalse}, and the third node should show only the "true" branch text.
     */
    @Test
    public void testConditionalsVariablesAndBranching() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "conditionals");
        ZonedDateTime now = ZonedDateTime.now();

        // Start node: auto-forward to "Conditionals"
        Node start = ad.startDialogue(now);
        assertNotNull(start);
        assertTrue("Start node should mention conditionals, got: " + agentText(start),
                agentText(start).contains("conditionals"));

        // Conditionals node: sets $variableThatIsTrue and $variableThatIsFalse — auto-forward
        Node conditionals = selectReply(ad, start, 0);
        assertNotNull(conditionals);

        // Conditionals2 node: should show the "true" branch only
        Node conditionals2 = selectReply(ad, conditionals, 0);
        assertNotNull(conditionals2);
        String text = agentText(conditionals2);
        assertTrue("Conditionals2 should show the true-branch text, got: " + text,
                text.contains("variableThatIsTrue"));
        assertFalse("Conditionals2 should not show the false-branch text, got: " + text,
                text.contains("you will never see this"));
    }

    // ----------------------------------------------------------------------------- //
    // -------------------- Tests: Executing 'bg1-candlekeep-imoen' in English -------------------- //
    // ----------------------------------------------------------------------------- //

    /**
     * Verifies that the Imoen dialogue's Start node shows the full introduction text when
     * {@code $ImoenIntroduced} is not set (i.e. first encounter).
     */
    @Test
    public void testImoenStartNodeFirstEncounter() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "bg1/bg1-candlekeep-imoen");
        Node start = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(start);
        String text = agentText(start);
        assertTrue("Imoen's first-encounter text should be shown, got: " + text,
                text.contains("I'm surprised that stuffy ol' Gorion"));
        assertEquals("Imoen start node should have 3 reply options on first encounter", 3,
                start.getBody().getReplies().size());
    }

    /**
     * Verifies that after selecting the rude reply (which sets {@code $ImoenIntroduced = true}),
     * re-starting the dialogue shows only the short repeated-encounter text.
     */
    @Test
    public void testImoenStartNodeAfterIntroduction() throws Exception {
        ActiveDialogue ad = createActiveDialogue(LANG_EN, "bg1/bg1-candlekeep-imoen");
        ZonedDateTime now = ZonedDateTime.now();

        // First encounter: select "Leave me be!" (reply 2) — sets $ImoenIntroduced = true
        Node start = ad.startDialogue(now);
        selectReply(ad, start, 2);

        // Re-start the dialogue — should now show the short "Don't keep Mr. G waiting" text
        Node restart = ad.startDialogue(now);
        assertNotNull(restart);
        String text = agentText(restart);
        assertTrue("After introduction, Imoen should say the short line, got: " + text,
                text.contains("Don't keep Mr. G waiting"));
        assertEquals("After introduction, Imoen start node should have 0 reply options", 0,
                restart.getBody().getReplies().size());
    }

}

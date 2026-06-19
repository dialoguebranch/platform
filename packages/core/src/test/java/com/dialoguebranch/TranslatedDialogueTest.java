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
import com.dialoguebranch.model.Dialogue;
import com.dialoguebranch.model.FileDescriptor;
import com.dialoguebranch.model.Node;
import com.dialoguebranch.model.ResourceType;
import com.dialoguebranch.parser.DirectoryFileLoader;
import com.dialoguebranch.parser.ProjectParser;
import com.dialoguebranch.parser.ProjectParserResult;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration tests that verify the core parse-and-execute pipeline using the bundled example
 * scripts in {@code src/test/resources/examples/}.
 */
public class TranslatedDialogueTest {

    // ------------------------------------------------- //
    // -------------------- Helpers -------------------- //
    // ------------------------------------------------- //

    /**
     * Returns a {@link ProjectParserResult} by parsing the bundled test-resource example folder
     * (contains {@code en/basic.dlb} and {@code nl/basic.json}).
     */
    private ProjectParserResult parseExamples() throws Exception {
        URL resourceUrl = getClass().getClassLoader().getResource("examples");
        assertNotNull("Test resource folder 'examples' not found on classpath", resourceUrl);
        File examplesDir = new File(resourceUrl.toURI());
        DirectoryFileLoader loader = new DirectoryFileLoader(examplesDir);
        return new ProjectParser(loader).parse();
    }

    /**
     * Finds the {@link Dialogue} for the given {@code language} and {@code dialogueName} in the
     * parsed project, or returns {@code null} if not found.
     */
    private Dialogue findDialogue(ProjectParserResult result, String language, String dialogueName)
            throws Exception {
        for (Map.Entry<FileDescriptor, Dialogue> entry
                : result.getProject().getDialogues().entrySet()) {
            FileDescriptor fd = entry.getKey();
            if (fd.getLanguage().equals(language)
                    && fd.getDialogueName().equals(dialogueName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ----------------------------------------------- //
    // -------------------- Tests -------------------- //
    // ----------------------------------------------- //

    /**
     * Verifies that the English source script parses without errors.
     */
    @Test
    public void testEnglishBasicParsesWithoutErrors() throws Exception {
        ProjectParserResult result = parseExamples();
        assertTrue("Parse errors: " + result.getParseErrors(),
                result.getParseErrors().isEmpty());
    }

    /**
     * Verifies that the Dutch translation of {@code basic} is present after parsing.
     */
    @Test
    public void testDutchTranslationIsLoaded() throws Exception {
        ProjectParserResult result = parseExamples();
        Dialogue dutch = findDialogue(result, "nl", "basic");
        assertNotNull("Dutch translated dialogue 'nl/basic' not found in parsed project", dutch);
    }

    /**
     * Verifies that executing the Start node of the Dutch {@code basic} dialogue returns the
     * Dutch-translated opening statement.
     */
    @Test
    public void testDutchStartNodeContainsTranslatedText() throws Exception {
        ProjectParserResult result = parseExamples();
        Dialogue dutch = findDialogue(result, "nl", "basic");
        assertNotNull("Dutch translated dialogue not found", dutch);

        FileDescriptor fd = new FileDescriptor("nl", "basic.dlb", ResourceType.TRANSLATION);
        ActiveDialogue ad = new ActiveDialogue(fd, dutch);
        ad.setVariableStore(new VariableStore(new User("test")));

        Node startNode = ad.startDialogue(ZonedDateTime.now());
        assertNotNull("startDialogue returned null node", startNode);

        String bodyText = startNode.getBody().toString();
        assertTrue(
                "Expected Dutch opening text, got: " + bodyText,
                bodyText.contains("Hallo, mijn naam is Martin McOwl"));
    }

    /**
     * Verifies that all reply options on the English Start node are present (sanity-check that
     * the English source itself is intact).
     */
    @Test
    public void testEnglishStartNodeHasExpectedReplies() throws Exception {
        ProjectParserResult result = parseExamples();
        Dialogue english = findDialogue(result, "en", "basic");
        assertNotNull("English source dialogue not found", english);

        FileDescriptor fd = new FileDescriptor("en", "basic.dlb", ResourceType.SCRIPT);
        ActiveDialogue ad = new ActiveDialogue(fd, english);
        ad.setVariableStore(new VariableStore(new User("test")));

        Node startNode = ad.startDialogue(ZonedDateTime.now());
        assertNotNull(startNode);
        assertEquals("Start node should have 2 reply options", 2,
                startNode.getBody().getReplies().size());
    }
}

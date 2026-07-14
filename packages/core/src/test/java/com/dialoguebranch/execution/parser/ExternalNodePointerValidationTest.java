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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.ResourcePointer;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests that {@link ProjectParser} reports a reply option's node-pointer target as an error
 * whether it points to a non-existing node in the same dialogue ({@code InternalNodePointer}) or
 * a non-existing node in another, otherwise-valid dialogue ({@code ExternalNodePointer}) — the
 * latter case previously went undetected because only the referenced dialogue's existence was
 * checked, never the referenced node's.
 *
 * @author Harm op den Akker
 */
public class ExternalNodePointerValidationTest {

    private ProjectParserResult parse(Map<String, String> scriptsByName) throws IOException {
        return new ProjectParser(new MapScriptLoader(scriptsByName)).parse();
    }

    @Test
    public void testExternalPointerToNonExistingNodeIsReported() throws IOException {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("main",
                "title: Start\nspeaker: Narrator\n---\n[[Go to other dialogue.|other.NoSuchNode]]\n===\n");
        scripts.put("other",
                "title: Start\nspeaker: Narrator\n---\n\n===\n");

        ProjectParserResult result = parse(scripts);

        assertFalse("Expected a parse error for the non-existing external node",
                result.getParseErrors().isEmpty());
        String allErrors = result.getParseErrors().toString();
        assertTrue("Expected the error to mention the missing node name, got: " + allErrors,
                allErrors.contains("NoSuchNode"));
        assertTrue("Expected the error to mention the originating node, got: " + allErrors,
                allErrors.contains("node Start"));
        assertFalse("Did not expect the origin dialogue name — it's already the map key, got: " +
                allErrors, allErrors.contains("of dialogue"));
    }

    @Test
    public void testExternalPointerToNonExistingDialogueIsReported() throws IOException {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("main",
                "title: Start\nspeaker: Narrator\n---\n[[Go nowhere.|nosuchdialogue.Start]]\n===\n");

        ProjectParserResult result = parse(scripts);

        assertFalse("Expected a parse error for the non-existing dialogue",
                result.getParseErrors().isEmpty());
        String allErrors = result.getParseErrors().toString();
        assertTrue("Expected the error to mention the missing dialogue name, got: " + allErrors,
                allErrors.contains("nosuchdialogue"));
        assertTrue("Expected the error to mention the originating node, got: " + allErrors,
                allErrors.contains("node Start"));
        assertFalse("Did not expect the origin dialogue name — it's already the map key, got: " +
                allErrors, allErrors.contains("of dialogue"));
    }

    @Test
    public void testExternalPointerToExistingNodeIsNotReported() throws IOException {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("main",
                "title: Start\nspeaker: Narrator\n---\n[[Go to other dialogue.|other.Start]]\n===\n");
        scripts.put("other",
                "title: Start\nspeaker: Narrator\n---\n\n===\n");

        ProjectParserResult result = parse(scripts);

        assertTrue("Did not expect any parse errors: " + result.getParseErrors(),
                result.getParseErrors().isEmpty());
    }

    @Test
    public void testInternalPointerToNonExistingNodeIsStillReported() throws IOException {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("main",
                "title: Start\nspeaker: Narrator\n---\n[[Go nowhere.|NoSuchNode]]\n===\n");

        ProjectParserResult result = parse(scripts);

        assertFalse("Expected a parse error for the non-existing internal node",
                result.getParseErrors().isEmpty());
        assertTrue("Expected the error to mention the missing node name",
                result.getParseErrors().toString().contains("NoSuchNode"));
    }

    /**
     * Regression test: an internal-pointer error makes the origin dialogue's own single-file
     * parse fail, which previously suppressed the (separate) project-level external-pointer
     * validation for the *entire* project — so a broken external link sitting right next to a
     * broken internal link in the same dialogue went unreported. Both must now be reported.
     */
    @Test
    public void testInternalAndExternalErrorsInSameDialogueAreBothReported() throws IOException {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("main",
                "title: Start\nspeaker: Narrator\n---\n" +
                "[[Bad internal link.|NoSuchInternalNode]]\n" +
                "[[Bad external link.|other.NoSuchExternalNode]]\n===\n");
        scripts.put("other",
                "title: Start\nspeaker: Narrator\n---\n\n===\n");

        ProjectParserResult result = parse(scripts);

        assertFalse("Expected parse errors", result.getParseErrors().isEmpty());
        String allErrors = result.getParseErrors().toString();
        assertTrue("Expected the internal-pointer error to be reported, got: " + allErrors,
                allErrors.contains("NoSuchInternalNode"));
        assertTrue("Expected the external-pointer error to be reported too, got: " + allErrors,
                allErrors.contains("NoSuchExternalNode"));
        assertFalse("Did not expect the origin dialogue name in the external-pointer error — " +
                "it's already the map key, got: " + allErrors, allErrors.contains("of dialogue"));
    }

    /**
     * A minimal {@link ScriptLoader} serving dialogue scripts (no translations) from an in-memory
     * map of dialogue name to {@code .dlb} script content, all in a single unnamed source
     * language — enough to exercise {@link ProjectParser} without touching the filesystem.
     */
    private static class MapScriptLoader implements ScriptLoader {

        private final Map<String, String> scripts;

        MapScriptLoader(Map<String, String> scripts) {
            this.scripts = scripts;
        }

        @Override
        public List<ResourcePointer> listDialogueBranchFiles() {
            List<ResourcePointer> pointers = new ArrayList<>();
            for (String dialogueName : scripts.keySet())
                pointers.add(new ResourcePointer("en", dialogueName, ResourceType.SCRIPT));
            return pointers;
        }

        @Override
        public Reader openFile(ResourcePointer fileDescription) throws IOException {
            String content = scripts.get(fileDescription.getDialogueName());
            if (content == null)
                throw new IOException("Script not found: " + fileDescription.getDialogueName());
            return new StringReader(content);
        }
    }
}

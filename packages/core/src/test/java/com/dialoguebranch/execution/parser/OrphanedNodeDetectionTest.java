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
 * Tests that {@link ProjectParser} reports a warning (not a parse error) for a node that no
 * reply link — internal or external — points to, unless that node is its own dialogue's Start
 * node. An orphaned node can never cause a runtime error (nothing requires every node to be
 * linked), so it must never appear in {@link ProjectParserResult#getParseErrors()}, only in
 * {@link ProjectParserResult#getWarnings()}.
 *
 * @author Harm op den Akker
 */
public class OrphanedNodeDetectionTest {

	private ProjectParserResult parse(Map<String, String> scriptsByName) throws IOException {
		return new ProjectParser(new MapScriptLoader(scriptsByName)).parse();
	}

	@Test
	public void testUnreferencedNodeIsReportedAsOrphan() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\nHello.\n===\n" +
				"title: Disconnected\nspeaker: Narrator\n---\nNobody links here.\n===\n");

		ProjectParserResult result = parse(scripts);

		assertTrue("Did not expect any parse errors: " + result.getParseErrors(),
				result.getParseErrors().isEmpty());
		assertFalse("Expected a warning for the orphaned node", result.getWarnings().isEmpty());
		String allWarnings = result.getWarnings().toString();
		assertTrue("Expected the warning to mention the orphaned node, got: " + allWarnings,
				allWarnings.contains("Disconnected"));
	}

	@Test
	public void testStartNodeIsNeverReportedAsOrphan() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main", "title: Start\nspeaker: Narrator\n---\nHello.\n===\n");

		ProjectParserResult result = parse(scripts);

		assertTrue("Did not expect any warnings: " + result.getWarnings(),
				result.getWarnings().isEmpty());
	}

	@Test
	public void testInternallyLinkedNodeIsNotOrphaned() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n[[Continue.|Next]]\n===\n" +
				"title: Next\nspeaker: Narrator\n---\nReached via reply link.\n===\n");

		ProjectParserResult result = parse(scripts);

		assertTrue("Did not expect any warnings: " + result.getWarnings(),
				result.getWarnings().isEmpty());
	}

	@Test
	public void testExternallyLinkedNodeIsNotOrphaned() throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main",
				"title: Start\nspeaker: Narrator\n---\n[[Go elsewhere.|other.Middle]]\n===\n");
		scripts.put("other",
				"title: Start\nspeaker: Narrator\n---\nHello.\n===\n" +
				"title: Middle\nspeaker: Narrator\n---\nReached only via another dialogue.\n===\n");

		ProjectParserResult result = parse(scripts);

		assertTrue("Did not expect any parse errors: " + result.getParseErrors(),
				result.getParseErrors().isEmpty());
		assertTrue("Did not expect any warnings: " + result.getWarnings(),
				result.getWarnings().isEmpty());
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

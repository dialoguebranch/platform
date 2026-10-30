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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.common.Speaker;
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
 * Tests that {@link ProjectParser} reports a warning (never a parse error) for a node whose
 * speaker isn't one of the project's speakers, and nothing for a project without speakers (#397).
 *
 * @author Harm op den Akker
 */
public class UndefinedSpeakerDetectionTest {

	private static final String SCRIPT =
			"title: Start\nspeaker: Narrator\n---\n[[Go on.|Next]]\n===\n" +
			"title: Next\nspeaker: Narator\n---\nA typo in the speaker.\n===\n";

	private ProjectParserResult parse(List<Speaker> speakers) throws IOException {
		Map<String, String> scripts = new LinkedHashMap<>();
		scripts.put("main", SCRIPT);
		return new ProjectParser(new MapScriptLoader(scripts), speakers).parse();
	}

	@Test
	public void testUndefinedSpeakerIsAWarning() throws IOException {
		ProjectParserResult result = parse(List.of(
				new Speaker("Narrator", Speaker.Gender.UNDEFINED, true)));

		assertTrue("Did not expect any parse errors: " + result.getParseErrors(),
				result.getParseErrors().isEmpty());
		assertNotNull("The project should still parse", result.getProject());
		String warnings = result.getWarnings().toString();
		assertEquals("Expected one warning, got: " + warnings, 1,
				result.getWarnings().values().stream().mapToInt(List::size).sum());
		assertTrue("Expected the warning to name the node and speaker, got: " + warnings,
				warnings.contains("\"Next\"") && warnings.contains("\"Narator\""));
	}

	@Test
	public void testNoWarningsWithoutSpeakers() throws IOException {
		assertTrue(parse(List.of()).getWarnings().isEmpty());
		assertTrue(parse(null).getWarnings().isEmpty());
	}

	@Test
	public void testNoWarningsWhenAllSpeakersAreDefined() throws IOException {
		ProjectParserResult result = parse(List.of(
				new Speaker("Narrator", Speaker.Gender.UNDEFINED, false),
				new Speaker("Narator", Speaker.Gender.FEMALE, false)));
		assertTrue("Did not expect any warnings: " + result.getWarnings(),
				result.getWarnings().isEmpty());
	}

	/** Serves dialogue scripts from an in-memory map, as in {@link OrphanedNodeDetectionTest}. */
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

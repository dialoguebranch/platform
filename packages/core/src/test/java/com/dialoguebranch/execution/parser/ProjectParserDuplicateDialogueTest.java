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
import com.dialoguebranch.model.execute.ResourcePointer;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Since a Dialogue Branch project has exactly one source language, the same dialogue name
 * appearing as a {@code .dlb} script in two language folders is a project error. {@link
 * ProjectScriptLoader} rejects that at construction, but {@link ProjectParser} also guards
 * against it independently of the loader — this test exercises that guard with a loader that
 * hands it two same-named scripts directly.
 *
 * @author Harm op den Akker
 */
public class ProjectParserDuplicateDialogueTest {

	@Test
	public void reportsADialogueNameDefinedInTwoLanguageFolders() throws IOException {
		ScriptLoader loader = new TwoLanguageScriptLoader(
				"title: Start\nspeaker: Narrator\n---\n\n===\n");

		ProjectParserResult result = new ProjectParser(loader).parse();

		assertFalse("Expected a parse error for the duplicated dialogue",
				result.getParseErrors().isEmpty());
		String allErrors = result.getParseErrors().toString();
		assertTrue("Expected the error to name the dialogue, got: " + allErrors,
				allErrors.contains("\"intro\""));
		assertTrue("Expected the error to name both language folders, got: " + allErrors,
				allErrors.contains("en") && allErrors.contains("nl"));
	}

	/** Serves the same script content as {@code en/intro.dlb} and {@code nl/intro.dlb}. */
	private static class TwoLanguageScriptLoader implements ScriptLoader {

		private final String script;

		TwoLanguageScriptLoader(String script) {
			this.script = script;
		}

		@Override
		public List<ResourcePointer> listDialogueBranchFiles() {
			List<ResourcePointer> pointers = new ArrayList<>();
			pointers.add(new ResourcePointer("en", "intro", ResourceType.SCRIPT));
			pointers.add(new ResourcePointer("nl", "intro", ResourceType.SCRIPT));
			return pointers;
		}

		@Override
		public Reader openFile(ResourcePointer fileDescription) {
			return new StringReader(script);
		}
	}
}

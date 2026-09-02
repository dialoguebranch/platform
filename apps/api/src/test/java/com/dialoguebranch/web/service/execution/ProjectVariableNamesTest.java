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

package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.controller.schema.ProjectVariableInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApplicationManager#getProjectVariables} returns every variable referenced by a project's
 * dialogues, sorted by name and flagged read/written, independent of stored values — backing the
 * {@code /variables/list-project} end-point. Uses the seeded {@code default-test} project.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectVariableNamesTest {

	@Autowired
	private Application application;

	private ProjectVariableInfo find(List<ProjectVariableInfo> vars, String name) {
		return vars.stream().filter(v -> v.name().equals(name)).findFirst().orElse(null);
	}

	@Test
	void listsEveryVariableReferencedByTheProjectSortedWithReadWriteFlags() {
		List<ProjectVariableInfo> vars = application.getApplicationManager()
				.getProjectVariables("default-test");

		assertFalse(vars.isEmpty(), "the seeded default-test project references variables");
		assertEquals(vars.stream().map(ProjectVariableInfo::name).sorted().toList(),
				vars.stream().map(ProjectVariableInfo::name).toList(),
				"the list should be sorted by name");

		// Names come back without the '$' sigil.
		ProjectVariableInfo userName = find(vars, "userName");
		assertNotNull(userName, "a read variable ('Hey $userName') should be included");
		assertTrue(userName.read());

		ProjectVariableInfo written = find(vars, "variableOne");
		assertNotNull(written, "a written variable ('<<set $variableOne ...>>') should be included");
		assertTrue(written.written());
	}

	@Test
	void returnsEmptyListForAnUnknownProject() {
		assertTrue(application.getApplicationManager()
				.getProjectVariables("no-such-project").isEmpty());
	}
}

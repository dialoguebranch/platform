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

package com.dialoguebranch.web.service.controller;

import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.NotFoundException;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.storage.VariableHistoryStorageHandler;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VariablesController#doGetVariableHistory}, with the storage handler and
 * project repository mocked: input validation, project resolution, and page clamping (#259).
 */
class VariablesControllerHistoryTest {

	private static final DialogueBranchUserId USER_ID =
			new DialogueBranchUserId("https://issuer.test", "sub-1", "alice");
	private static final String PROJECT_SLUG = "my-project";

	private VariablesController controller;
	private VariableHistoryStorageHandler historyHandler;
	private DBProjectRepository projectRepository;
	private DBProject project;

	@BeforeEach
	void setUp() {
		historyHandler = mock(VariableHistoryStorageHandler.class);
		projectRepository = mock(DBProjectRepository.class);
		project = new DBProject();
		when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));

		controller = new VariablesController();
		controller.historyStorageHandler = historyHandler;
		controller.projectRepository = projectRepository;
	}

	@Test
	void passesTheProjectNameAndPageThrough() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, "mood", null, null, 3, 20);

		verify(historyHandler).read(USER_ID, project, "mood", null, null, 3, 20);
	}

	@Test
	void aMissingOrBlankNameMeansAllVariables() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, null, null, 0, 50);
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, "   ", null, null, 0, 50);

		verify(historyHandler, times(2)).read(USER_ID, project, null, null, null, 0, 50);
	}

	@Test
	void surroundingWhitespaceIsTrimmedFromTheName() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, "  mood ", null, null, 0, 50);

		verify(historyHandler).read(USER_ID, project, "mood", null, null, 0, 50);
	}

	@Test
	void anOversizedPageIsCappedAt200() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, null, null, 0, 10_000);

		verify(historyHandler).read(USER_ID, project, null, null, null, 0, 200);
	}

	@Test
	void aZeroOrNegativePageSizeBecomesOne() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, null, null, 0, 0);
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, null, null, 0, -5);

		verify(historyHandler, times(2)).read(USER_ID, project, null, null, null, 0, 1);
	}

	@Test
	void aNegativePageIndexBecomesTheFirstPage() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, null, null, -2, 50);

		verify(historyHandler).read(USER_ID, project, null, null, null, 0, 50);
	}

	@Test
	void parsesTheTimeRangeAsEpochMillisAndPassesItThrough() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, "1000", " 2000 ", 0, 50);

		verify(historyHandler).read(USER_ID, project, null, 1000L, 2000L, 0, 50);
	}

	@Test
	void eitherBoundCanBeLeftOutOrBlank() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, "1000", null, 0, 50);
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, "  ", "2000", 0, 50);

		verify(historyHandler).read(USER_ID, project, null, 1000L, null, 0, 50);
		verify(historyHandler).read(USER_ID, project, null, null, 2000L, 0, 50);
	}

	@Test
	void anEmptyRangeWithEqualBoundsIsAllowed() throws Exception {
		controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, "1000", "1000", 0, 50);

		verify(historyHandler).read(USER_ID, project, null, 1000L, 1000L, 0, 50);
	}

	@Test
	void anUnparseableTimeIsRejectedBeforeAnyLookup() {
		for (String invalid : new String[] { "yesterday", "2026-10-14T12:00:00Z", "12.5", "1e3" }) {
			assertThrows(BadRequestException.class, () ->
					controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, invalid, null, 0, 50),
					"from='" + invalid + "' should be rejected");
			assertThrows(BadRequestException.class, () ->
					controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, null, invalid, 0, 50),
					"to='" + invalid + "' should be rejected");
		}
		verifyNoInteractions(projectRepository, historyHandler);
	}

	@Test
	void aFromAfterToIsRejectedBeforeAnyLookup() {
		assertThrows(BadRequestException.class, () ->
				controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, null, "2000", "1000", 0, 50));
		verifyNoInteractions(projectRepository, historyHandler);
	}

	@Test
	void anInvalidNameIsRejectedBeforeAnyLookup() {
		for (String invalid : new String[] { "1mood", "mood-score", "$mood", "mood score" }) {
			assertThrows(BadRequestException.class, () ->
					controller.doGetVariableHistory(USER_ID, PROJECT_SLUG, invalid, null, null, 0, 50),
					"'" + invalid + "' should be rejected");
		}
		verifyNoInteractions(projectRepository, historyHandler);
	}

	@Test
	void anUnknownProjectIsNotFoundAndReadsNothing() throws Exception {
		when(projectRepository.findBySlug("nope")).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, () ->
				controller.doGetVariableHistory(USER_ID, "nope", null, null, null, 0, 50));
		verify(historyHandler, never()).read(any(), any(), any(), any(), any(), anyInt(),
				anyInt());
	}

}

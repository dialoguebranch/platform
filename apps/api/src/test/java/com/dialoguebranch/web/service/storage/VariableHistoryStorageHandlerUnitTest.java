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

package com.dialoguebranch.web.service.storage;

import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.controller.schema.VariableHistoryEntry;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBUser;
import com.dialoguebranch.web.service.storage.model.DBVariableHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VariableHistoryStorageHandler} against mocked repositories — no Spring
 * context or database: how each kind of {@link VariableStoreChange} maps onto history rows, how
 * values are serialized, which timestamp the rows get, and how reads are routed and paged (#259).
 */
class VariableHistoryStorageHandlerUnitTest {

	private static final String PROJECT_SLUG = "my-project";
	private static final DialogueBranchUserId USER_ID =
			new DialogueBranchUserId("https://issuer.test", "sub-1", "alice");

	/** The recording time: the Web Service's clock, in the user's time zone. */
	private static final ZonedDateTime RECORDED =
			ZonedDateTime.of(2026, 10, 12, 10, 30, 0, 0, ZoneId.of("Europe/Lisbon"));

	/** A change's own time, deliberately different from {@link #RECORDED}. */
	private static final ZonedDateTime CHANGE_TIME =
			ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneId.of("UTC"));

	private DBUserRepository userRepository;
	private DBProjectRepository projectRepository;
	private DBVariableHistoryRepository historyRepository;
	private VariableHistoryStorageHandler handler;
	private DBUser user;
	private DBProject project;

	@BeforeEach
	void setUp() {
		userRepository = mock(DBUserRepository.class);
		projectRepository = mock(DBProjectRepository.class);
		historyRepository = mock(DBVariableHistoryRepository.class);
		handler = new VariableHistoryStorageHandler(userRepository, projectRepository,
				historyRepository);
		user = new DBUser(USER_ID.issuer(), USER_ID.subject(), USER_ID.username());
		project = new DBProject();
		when(userRepository.findByIssuerAndSubject(USER_ID.issuer(), USER_ID.subject()))
				.thenReturn(Optional.of(user));
		when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));
	}

	/** Records the given changes and returns the rows passed to {@code saveAll}. */
	@SuppressWarnings("unchecked")
	private List<DBVariableHistory> record(VariableStoreChange... changes) {
		handler.record(USER_ID, PROJECT_SLUG, List.of(changes), RECORDED);
		ArgumentCaptor<List<DBVariableHistory>> saved = ArgumentCaptor.forClass(List.class);
		verify(historyRepository).saveAll(saved.capture());
		return saved.getValue();
	}

	// -------------------- record() -------------------- //

	@Test
	void aSingleVariablePutRecordsOneRowForTheUserAndProject() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Put("mood", "fine",
				CHANGE_TIME, VariableUpdatedSource.WEB_SERVICE));

		assertEquals(1, rows.size());
		DBVariableHistory row = rows.get(0);
		assertSame(user, row.getUser());
		assertSame(project, row.getProject());
		assertEquals("mood", row.getName());
		assertEquals("\"fine\"", row.getValue());
		assertEquals(VariableUpdatedSource.WEB_SERVICE, row.getSource());
	}

	@Test
	void aMultiVariablePutRecordsOneRowPerVariableInOrder() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Put(
				List.of(new Variable("a", 1, CHANGE_TIME, VariableUpdatedSource.INPUT_REPLY),
						new Variable("b", true, CHANGE_TIME, VariableUpdatedSource.INPUT_REPLY),
						new Variable("c", 2.5, CHANGE_TIME, VariableUpdatedSource.INPUT_REPLY)),
				CHANGE_TIME, VariableUpdatedSource.INPUT_REPLY));

		assertEquals(List.of("a", "b", "c"), rows.stream().map(DBVariableHistory::getName).toList());
		assertEquals(List.of("1", "true", "2.5"),
				rows.stream().map(DBVariableHistory::getValue).toList());
		for (DBVariableHistory row : rows)
			assertEquals(VariableUpdatedSource.INPUT_REPLY, row.getSource());
	}

	@Test
	void structuredValuesAreStoredAsJson() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Put("scores",
				Map.of("week", 3), CHANGE_TIME, VariableUpdatedSource.DLB_SCRIPT));

		assertEquals("{\"week\":3}", rows.get(0).getValue());
	}

	@Test
	void aPutOfNullIsStoredAsSqlNullNotTheJsonLiteral() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Put("mood", null,
				CHANGE_TIME, VariableUpdatedSource.DLB_SCRIPT));

		assertNull(rows.get(0).getValue());
	}

	@Test
	void aRemoveRecordsEachNameWithANullValue() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Remove(List.of("a", "b"),
				CHANGE_TIME, VariableUpdatedSource.EXTERNAL));

		assertEquals(List.of("a", "b"), rows.stream().map(DBVariableHistory::getName).toList());
		for (DBVariableHistory row : rows) {
			assertNull(row.getValue());
			assertEquals(VariableUpdatedSource.EXTERNAL, row.getSource());
		}
	}

	@Test
	void aClearRecordsEachClearedNameWithANullValue() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Clear(List.of("x", "y"),
				CHANGE_TIME, VariableUpdatedSource.WEB_SERVICE));

		assertEquals(List.of("x", "y"), rows.stream().map(DBVariableHistory::getName).toList());
		for (DBVariableHistory row : rows) assertNull(row.getValue());
	}

	@Test
	void aClearOfAnEmptyStoreRecordsNothing() {
		List<DBVariableHistory> rows = record(new VariableStoreChange.Clear(List.of(),
				CHANGE_TIME, VariableUpdatedSource.WEB_SERVICE));

		assertTrue(rows.isEmpty());
	}

	@Test
	void severalChangesInOneCallAreRecordedInOrderWithTheirOwnSources() {
		List<DBVariableHistory> rows = record(
				new VariableStoreChange.Put("a", 1, CHANGE_TIME, VariableUpdatedSource.DLB_SCRIPT),
				new VariableStoreChange.Remove("a", CHANGE_TIME, VariableUpdatedSource.WEB_SERVICE));

		assertEquals(2, rows.size());
		assertEquals("1", rows.get(0).getValue());
		assertEquals(VariableUpdatedSource.DLB_SCRIPT, rows.get(0).getSource());
		assertNull(rows.get(1).getValue());
		assertEquals(VariableUpdatedSource.WEB_SERVICE, rows.get(1).getSource());
	}

	@Test
	void everyRowIsStampedWithTheRecordingTimeNotTheChangesOwnTime() {
		List<DBVariableHistory> rows = record(
				new VariableStoreChange.Put("a", 1, CHANGE_TIME, VariableUpdatedSource.EXTERNAL),
				new VariableStoreChange.Remove("b", CHANGE_TIME, VariableUpdatedSource.EXTERNAL));

		for (DBVariableHistory row : rows) {
			assertEquals(RECORDED.toInstant().toEpochMilli(), row.getRecordedTime());
			assertEquals("Europe/Lisbon", row.getRecordedTimeZone());
		}
	}

	@Test
	void anUnknownUserFailsWithoutSavingAnything() {
		when(userRepository.findByIssuerAndSubject(USER_ID.issuer(), USER_ID.subject()))
				.thenReturn(Optional.empty());

		assertThrows(IllegalStateException.class, () -> handler.record(USER_ID, PROJECT_SLUG,
				List.of(new VariableStoreChange.Put("a", 1, CHANGE_TIME)), RECORDED));
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	void anUnknownProjectFailsWithoutSavingAnything() {
		when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.empty());

		assertThrows(IllegalStateException.class, () -> handler.record(USER_ID, PROJECT_SLUG,
				List.of(new VariableStoreChange.Put("a", 1, CHANGE_TIME)), RECORDED));
		verify(historyRepository, never()).saveAll(any());
	}

	// -------------------- read() -------------------- //

	/** A stored row, with the id the database would have assigned it. */
	private static DBVariableHistory row(long id, String name, String json) {
		DBVariableHistory row = new DBVariableHistory(new DBUser(), new DBProject(), name, json,
				RECORDED.toInstant().toEpochMilli(), "Europe/Lisbon",
				VariableUpdatedSource.WEB_SERVICE);
		ReflectionTestUtils.setField(row, "id", id);
		return row;
	}

	@Test
	void readingWithoutANameQueriesAllVariablesWithTheRequestedPage() throws Exception {
		when(historyRepository.findByUserAndProjectOrderByIdDesc(any(), any(), any()))
				.thenReturn(List.of());

		handler.read(USER_ID, project, null, null, null, 2, 25);

		verify(historyRepository).findByUserAndProjectOrderByIdDesc(user, project,
				PageRequest.of(2, 25));
		verify(historyRepository, never()).findByUserAndProjectAndNameOrderByIdDesc(any(), any(),
				any(), any(Pageable.class));
	}

	@Test
	void readingWithANameQueriesThatVariableOnly() throws Exception {
		when(historyRepository.findByUserAndProjectAndNameOrderByIdDesc(any(), any(), any(),
				any())).thenReturn(List.of());

		handler.read(USER_ID, project, "mood", null, null, 0, 50);

		verify(historyRepository).findByUserAndProjectAndNameOrderByIdDesc(user, project, "mood",
				PageRequest.of(0, 50));
		verify(historyRepository, never()).findByUserAndProjectOrderByIdDesc(any(), any(),
				any(Pageable.class));
	}

	@Test
	void aRangeWithoutANameQueriesAllVariablesInTheRange() throws Exception {
		when(historyRepository.findInRangeOrderByIdDesc(any(), any(), anyLong(), anyLong(), any()))
				.thenReturn(List.of());

		handler.read(USER_ID, project, null, 1000L, 2000L, 1, 10);

		verify(historyRepository).findInRangeOrderByIdDesc(user, project, 1000L, 2000L,
				PageRequest.of(1, 10));
		verify(historyRepository, never()).findByUserAndProjectOrderByIdDesc(any(), any(),
				any(Pageable.class));
	}

	@Test
	void aRangeWithANameQueriesThatVariableInTheRange() throws Exception {
		when(historyRepository.findByNameInRangeOrderByIdDesc(any(), any(), any(), anyLong(),
				anyLong(), any())).thenReturn(List.of());

		handler.read(USER_ID, project, "mood", 1000L, 2000L, 0, 50);

		verify(historyRepository).findByNameInRangeOrderByIdDesc(user, project, "mood", 1000L,
				2000L, PageRequest.of(0, 50));
		verify(historyRepository, never()).findByUserAndProjectAndNameOrderByIdDesc(any(), any(),
				any(), any(Pageable.class));
	}

	@Test
	void aMissingLowerBoundIsOpenEnded() throws Exception {
		when(historyRepository.findInRangeOrderByIdDesc(any(), any(), anyLong(), anyLong(), any()))
				.thenReturn(List.of());

		handler.read(USER_ID, project, null, null, 2000L, 0, 50);

		verify(historyRepository).findInRangeOrderByIdDesc(user, project, Long.MIN_VALUE, 2000L,
				PageRequest.of(0, 50));
	}

	@Test
	void aMissingUpperBoundIsOpenEnded() throws Exception {
		when(historyRepository.findByNameInRangeOrderByIdDesc(any(), any(), any(), anyLong(),
				anyLong(), any())).thenReturn(List.of());

		handler.read(USER_ID, project, "mood", 1000L, null, 0, 50);

		verify(historyRepository).findByNameInRangeOrderByIdDesc(user, project, "mood", 1000L,
				Long.MAX_VALUE, PageRequest.of(0, 50));
	}

	@Test
	void readParsesStoredJsonBackIntoValuesAndKeepsNullsAndOrder() throws Exception {
		when(historyRepository.findByUserAndProjectOrderByIdDesc(any(), any(), any()))
				.thenReturn(List.of(row(12, "mood", null), row(11, "count", "3"),
						row(10, "mood", "\"fine\"")));

		List<VariableHistoryEntry> entries = handler.read(USER_ID, project, null, null, null, 0, 50);

		assertEquals(List.of("mood", "count", "mood"),
				entries.stream().map(VariableHistoryEntry::name).toList());
		assertEquals(List.of(12L, 11L, 10L),
				entries.stream().map(VariableHistoryEntry::id).toList());
		assertNull(entries.get(0).value());
		assertEquals(3, entries.get(1).value());
		assertEquals("fine", entries.get(2).value());
		assertEquals(RECORDED.toInstant().toEpochMilli(), entries.get(2).recordedTime());
		assertEquals("Europe/Lisbon", entries.get(2).recordedTimeZone());
		assertEquals(VariableUpdatedSource.WEB_SERVICE, entries.get(2).source());
	}

	@Test
	void readingForAUserWithNoRowYetReturnsEmptyWithoutQueryingHistory() throws Exception {
		when(userRepository.findByIssuerAndSubject(USER_ID.issuer(), USER_ID.subject()))
				.thenReturn(Optional.empty());

		assertTrue(handler.read(USER_ID, project, null, null, null, 0, 50).isEmpty());
		verifyNoInteractions(historyRepository);
	}

}

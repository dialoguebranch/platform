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

import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.Variable;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.execution.VariableUpdatedSource;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.controller.schema.VariableHistoryEntry;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link VariableHistoryStorageHandler#record}: how each kind of {@link
 * VariableStoreChange} becomes history rows, and which timestamp they get (#259).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VariableHistoryStorageHandlerTest {

	private static final String PROJECT = "default-test";
	private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

	@Autowired
	private VariableHistoryStorageHandler historyHandler;

	@Autowired
	private VariableStoreDatabaseStorageHandler storageHandler;

	@Autowired
	private DBProjectRepository projectRepository;

	/** Creates a user row the same way loading a variable store does in production. */
	private DialogueBranchUserId newUser() throws Exception {
		String subject = "history-" + UUID.randomUUID();
		DialogueBranchUserId id = new DialogueBranchUserId("https://issuer.test", subject, subject);
		storageHandler.read(id, PROJECT, new User(subject, LISBON));
		return id;
	}

	private List<VariableHistoryEntry> readAll(DialogueBranchUserId id) throws Exception {
		DBProject project = projectRepository.findBySlug(PROJECT).orElseThrow();
		return historyHandler.read(id, project, null, null, null, 0, 100);
	}

	@Test
	void aMultiVariablePutRecordsOneRowPerVariable() throws Exception {
		DialogueBranchUserId id = newUser();
		ZonedDateTime now = ZonedDateTime.now(LISBON);

		historyHandler.record(id, PROJECT, List.of(new VariableStoreChange.Put(
				List.of(new Variable("a", 1, now, VariableUpdatedSource.INPUT_REPLY),
						new Variable("b", "two", now, VariableUpdatedSource.INPUT_REPLY)),
				now, VariableUpdatedSource.INPUT_REPLY)), now);

		List<VariableHistoryEntry> entries = readAll(id);
		assertEquals(2, entries.size());
		assertEquals(Map.of("a", 1, "b", "two"), Map.of(
				entries.get(0).name(), entries.get(0).value(),
				entries.get(1).name(), entries.get(1).value()));
		assertEquals(VariableUpdatedSource.INPUT_REPLY, entries.get(0).source());
	}

	@Test
	void removeAndClearRecordEachNameWithANullValue() throws Exception {
		DialogueBranchUserId id = newUser();
		ZonedDateTime now = ZonedDateTime.now(LISBON);

		historyHandler.record(id, PROJECT, List.of(
				new VariableStoreChange.Remove("a", now, VariableUpdatedSource.WEB_SERVICE),
				new VariableStoreChange.Clear(List.of("b", "c"), now,
						VariableUpdatedSource.DLB_SCRIPT)), now);

		List<VariableHistoryEntry> entries = readAll(id);
		assertEquals(3, entries.size());
		for (VariableHistoryEntry entry : entries) assertNull(entry.value());
		assertEquals(List.of("c", "b", "a"),
				entries.stream().map(VariableHistoryEntry::name).toList());
		// Newest first means strictly decreasing ids — what a paging client de-duplicates on.
		assertTrue(entries.get(0).id() > entries.get(1).id());
		assertTrue(entries.get(1).id() > entries.get(2).id());
	}

	@Test
	void usesTheRecordingTimeNotTheChangesOwnTime() throws Exception {
		DialogueBranchUserId id = newUser();
		ZonedDateTime recorded = ZonedDateTime.now(LISBON);
		// As for an External Variable Service update: the change carries the EVS's (old) clock.
		ZonedDateTime evsTime = recorded.minusDays(3).withZoneSameInstant(ZoneId.of("UTC"));

		historyHandler.record(id, PROJECT, List.of(new VariableStoreChange.Put("mood", "fine",
				evsTime, VariableUpdatedSource.EXTERNAL)), recorded);

		VariableHistoryEntry entry = readAll(id).get(0);
		assertEquals(recorded.toInstant().toEpochMilli(), entry.recordedTime());
		assertEquals("Europe/Lisbon", entry.recordedTimeZone());
		assertEquals(VariableUpdatedSource.EXTERNAL, entry.source());
	}

	@Test
	void aClearOnTheStoreRecordsTheNamesItRemoved() throws Exception {
		DialogueBranchUserId id = newUser();
		ZonedDateTime now = ZonedDateTime.now(LISBON);
		VariableStore store = new VariableStore(new User(id.subject(), LISBON));
		store.setValue("x", 1, false, now);
		store.setValue("y", 2, false, now);
		store.addOnChangeListener((changed, changes) ->
				historyHandler.record(id, PROJECT, changes, now));

		store.getModifiableMap(true, now).clear();

		List<VariableHistoryEntry> entries = readAll(id);
		assertEquals(2, entries.size());
		assertEquals(List.of("x", "y"),
				entries.stream().map(VariableHistoryEntry::name).sorted().toList());
		for (VariableHistoryEntry entry : entries) assertNull(entry.value());
	}

}

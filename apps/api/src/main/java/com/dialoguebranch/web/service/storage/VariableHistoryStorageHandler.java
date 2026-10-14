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

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.VariableStoreChange;
import com.dialoguebranch.json.JsonMapper;
import com.dialoguebranch.web.service.auth.DialogueBranchUserId;
import com.dialoguebranch.web.service.controller.schema.VariableHistoryEntry;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBUserRepository;
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBUser;
import com.dialoguebranch.web.service.storage.model.DBVariableHistory;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Records and reads the append-only history of Dialogue Branch Variable values (#259).
 *
 * <p>{@link #record} is called from a {@link com.dialoguebranch.execution.VariableStoreOnChangeListener}
 * that every {@link com.dialoguebranch.web.service.execution.UserService} registers on each of its
 * per-project {@link com.dialoguebranch.execution.VariableStore}s, alongside the one that persists
 * the current values through {@link VariableStoreDatabaseStorageHandler}. Every change appends one
 * row per affected variable: a {@link VariableStoreChange.Put} records each new value, and a
 * {@link VariableStoreChange.Remove} or {@link VariableStoreChange.Clear} records each removed
 * variable with a {@code null} value. Repeated identical values are recorded as they are, not
 * deduplicated.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class VariableHistoryStorageHandler {

	private final DBUserRepository userRepository;
	private final DBProjectRepository projectRepository;
	private final DBVariableHistoryRepository historyRepository;

	/**
	 * Creates a new {@link VariableHistoryStorageHandler}. Instances are constructed through Spring.
	 *
	 * @param userRepository repository used to look up the user whose history is recorded or read.
	 * @param projectRepository repository used to look up the project by slug.
	 * @param historyRepository repository holding the history entries.
	 */
	public VariableHistoryStorageHandler(DBUserRepository userRepository,
										 DBProjectRepository projectRepository,
										 DBVariableHistoryRepository historyRepository) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.historyRepository = historyRepository;
	}

	/**
	 * Appends a history entry for every variable affected by the given changes.
	 *
	 * <p>Every entry is stamped with {@code recordedTime} — the Web Service's own clock at the
	 * moment of recording — rather than with the change's own {@link VariableStoreChange#getTime()
	 * time}, which for External Variable Service updates is the external service's clock and can be
	 * arbitrarily old.</p>
	 *
	 * @param id the identity of the user whose variable store changed.
	 * @param projectSlug the slug of the project the variable store belongs to.
	 * @param changes the changes to record.
	 * @param recordedTime the current time, in the user's time zone.
	 */
	@Transactional
	public void record(DialogueBranchUserId id, String projectSlug,
					   Collection<VariableStoreChange> changes, ZonedDateTime recordedTime) {
		// The user row always exists by now: loading the variable store this listener is
		// registered on creates it (VariableStoreDatabaseStorageHandler.read).
		DBUser user = userRepository.findByIssuerAndSubject(id.issuer(), id.subject())
				.orElseThrow(() -> new IllegalStateException(
						"No user '" + id.subject() + "' for issuer '" + id.issuer() + "'"));
		DBProject project = projectRepository.findBySlug(projectSlug)
				.orElseThrow(() -> new IllegalStateException(
						"No project with slug '" + projectSlug + "'"));
		long time = recordedTime.toInstant().toEpochMilli();
		String timeZone = recordedTime.getZone().getId();

		List<DBVariableHistory> entries = new ArrayList<>();
		for (VariableStoreChange change : changes) {
			if (change instanceof VariableStoreChange.Put put) {
				for (Map.Entry<String, Object> variable : put.getVariables().entrySet()) {
					entries.add(new DBVariableHistory(user, project, variable.getKey(),
							toJson(variable.getValue()), time, timeZone, change.getSource()));
				}
			} else if (change instanceof VariableStoreChange.Remove remove) {
				for (String name : remove.getVariableNames()) {
					entries.add(new DBVariableHistory(user, project, name, null, time, timeZone,
							change.getSource()));
				}
			} else if (change instanceof VariableStoreChange.Clear clear) {
				for (String name : clear.getVariableNames()) {
					entries.add(new DBVariableHistory(user, project, name, null, time, timeZone,
							change.getSource()));
				}
			}
		}
		historyRepository.saveAll(entries);
	}

	/**
	 * Reads a page of a user's variable history within a project, newest first — either for one
	 * variable, or across all of them, optionally restricted to the time range {@code [from, to)}.
	 *
	 * @param id the identity of the user whose history to read.
	 * @param project the project the variables are scoped to.
	 * @param variableName the variable to read the history of, or {@code null} for all variables.
	 * @param from only entries recorded at or after this time (epoch millis), or {@code null} for no
	 *             lower bound.
	 * @param to only entries recorded before this time (epoch millis), or {@code null} for no upper
	 *           bound.
	 * @param page the zero-based page index.
	 * @param pageSize the number of entries per page.
	 * @return the matching entries, newest first (empty if the user has no history at all).
	 * @throws ParseException if a stored value cannot be parsed back from JSON.
	 */
	@Transactional(readOnly = true)
	public List<VariableHistoryEntry> read(DialogueBranchUserId id, DBProject project,
			@Nullable String variableName, @Nullable Long from, @Nullable Long to, int page,
			int pageSize) throws ParseException {
		Optional<DBUser> user = userRepository.findByIssuerAndSubject(id.issuer(), id.subject());
		if (user.isEmpty()) return List.of();

		PageRequest pageRequest = PageRequest.of(page, pageSize);
		List<DBVariableHistory> rows;
		if (from == null && to == null) {
			rows = variableName == null
					? historyRepository.findByUserAndProjectOrderByIdDesc(user.get(), project,
							pageRequest)
					: historyRepository.findByUserAndProjectAndNameOrderByIdDesc(user.get(),
							project, variableName, pageRequest);
		} else {
			long lower = from == null ? Long.MIN_VALUE : from;
			long upper = to == null ? Long.MAX_VALUE : to;
			rows = variableName == null
					? historyRepository.findInRangeOrderByIdDesc(user.get(), project, lower,
							upper, pageRequest)
					: historyRepository.findByNameInRangeOrderByIdDesc(user.get(), project,
							variableName, lower, upper, pageRequest);
		}

		List<VariableHistoryEntry> result = new ArrayList<>(rows.size());
		for (DBVariableHistory row : rows) {
			Object value = row.getValue() == null
					? null : JsonMapper.parse(row.getValue(), Object.class);
			result.add(new VariableHistoryEntry(row.getId(), row.getName(), value, row.getRecordedTime(),
					row.getRecordedTimeZone(), row.getSource()));
		}
		return result;
	}

	private static @Nullable String toJson(@Nullable Object value) {
		return value == null ? null : JsonMapper.generate(value);
	}

}

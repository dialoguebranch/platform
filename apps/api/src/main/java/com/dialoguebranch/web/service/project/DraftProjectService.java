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

package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.repository.DBDraftTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing a project's draft (working-copy) metadata: its display name, description,
 * and translation language registry. All edits made through this service affect the draft layer
 * only; changes are not visible outside Authoring Mode until the project is published via
 * {@link PublishService}, which reconciles them into the project's published
 * {@code displayName}/{@code description} and its published {@link
 * com.dialoguebranch.web.service.storage.model.DBTranslationLanguage} registry.
 *
 * @author Harm op den Akker
 */
@Service
public class DraftProjectService {

	private final DBProjectRepository projectRepository;
	private final DBDraftTranslationLanguageRepository draftTranslationLanguageRepository;

	/**
	 * Creates a new {@link DraftProjectService}.
	 *
	 * @param projectRepository                   repository used to persist a project's draft
	 *                                             metadata fields.
	 * @param draftTranslationLanguageRepository   repository used to read and persist draft
	 *                                             translation languages.
	 */
	public DraftProjectService(DBProjectRepository projectRepository,
			DBDraftTranslationLanguageRepository draftTranslationLanguageRepository) {
		this.projectRepository = projectRepository;
		this.draftTranslationLanguageRepository = draftTranslationLanguageRepository;
	}

	// ------------------------------------------------------------------ //
	// -------------------- Draft Metadata Management -------------------- //
	// ------------------------------------------------------------------ //

	/**
	 * Updates the draft display name and description of the given project. This does not affect
	 * the project's published {@code displayName}/{@code description} until the project is next
	 * published.
	 *
	 * @param project     the project to update.
	 * @param displayName the new draft display name.
	 * @param description the new draft description.
	 * @return the updated {@link DBProject}.
	 */
	public DBProject updateDraftMetadata(DBProject project, String displayName,
			String description) {
		project.setDraftDisplayName(displayName);
		project.setDraftDescription(description);
		project.setUpdatedAt(Instant.now());
		return projectRepository.save(project);
	}

	// ----------------------------------------------------------------------- //
	// -------------------- Draft Language Management -------------------- //
	// ----------------------------------------------------------------------- //

	/**
	 * Returns all draft translation languages belonging to the given project, including ones
	 * pending deletion.
	 *
	 * @param project the owning project.
	 * @return list of draft translation languages.
	 */
	public List<DBDraftTranslationLanguage> listDraftLanguages(DBProject project) {
		return draftTranslationLanguageRepository.findByProject(project);
	}

	/**
	 * Returns the draft translation language with the given code within the given project, or
	 * {@link Optional#empty()} if not found.
	 *
	 * @param project the owning project.
	 * @param code    the language code.
	 * @return the matching draft translation language, or empty.
	 */
	public Optional<DBDraftTranslationLanguage> findDraftLanguage(DBProject project, String code) {
		return draftTranslationLanguageRepository.findByProjectAndTranslationLanguageCode(project,
				code);
	}

	/**
	 * Persists changes made directly to a {@link DBDraftTranslationLanguage} (e.g. its status
	 * flags). Used by {@link PublishService} to clear {@code isNew} once a language has been
	 * reconciled into the published registry.
	 *
	 * @param language the draft translation language to save.
	 * @return the saved draft translation language.
	 */
	public DBDraftTranslationLanguage save(DBDraftTranslationLanguage language) {
		return draftTranslationLanguageRepository.save(language);
	}

	/**
	 * Returns the draft translation language with the given {@code id}, or {@link
	 * Optional#empty()} if not found.
	 *
	 * @param id the draft translation language UUID.
	 * @return the matching draft translation language, or empty.
	 */
	public Optional<DBDraftTranslationLanguage> findById(UUID id) {
		return draftTranslationLanguageRepository.findById(id);
	}

	/**
	 * Adds a new draft translation language to the given project.
	 *
	 * @param project the owning project.
	 * @param name    the human-readable name of the translation language.
	 * @param code    the ISO code of the translation language.
	 * @return the newly created {@link DBDraftTranslationLanguage}.
	 * @throws ConflictException if a non-deleted draft translation language with {@code code}
	 * already exists for this project.
	 */
	@Transactional
	public DBDraftTranslationLanguage addDraftLanguage(DBProject project, String name,
			String code) throws ConflictException {
		boolean alreadyExists = draftTranslationLanguageRepository
				.findByProjectAndTranslationLanguageCode(project, code)
				.filter(existing -> !existing.getIsDeleted())
				.isPresent();
		if (alreadyExists) {
			throw new ConflictException(
					"A translation language with code '" + code + "' already exists.");
		}
		Instant now = Instant.now();
		DBDraftTranslationLanguage language = new DBDraftTranslationLanguage();
		language.setProject(project);
		language.setTranslationLanguageName(name);
		language.setTranslationLanguageCode(code);
		language.setIsNew(true);
		language.setIsDeleted(false);
		language.setCreatedAt(now);
		language.setUpdatedAt(now);
		return draftTranslationLanguageRepository.save(language);
	}

	/**
	 * Marks the given draft translation language as pending deletion ({@code isDeleted = true}).
	 * This is a soft delete: the row (and any draft translation content in this language) are
	 * left untouched, so the deletion can still be reverted via {@link #restoreDraftLanguage}. It
	 * only takes permanent effect the next time the project is published (see {@link
	 * PublishService#publish}).
	 *
	 * <p>Exception: if the language {@link DBDraftTranslationLanguage#getIsNew() has no published
	 * counterpart yet}, there is nothing to reconcile at publish time, so it is hard-deleted
	 * immediately instead — avoiding a pointless "new AND deleted" row lingering until the next
	 * publish.</p>
	 *
	 * @param language the draft translation language to remove.
	 */
	@Transactional
	public void removeDraftLanguage(DBDraftTranslationLanguage language) {
		if (language.getIsNew()) {
			hardDeleteDraftLanguage(language);
			return;
		}
		language.setIsDeleted(true);
		language.setUpdatedAt(Instant.now());
		draftTranslationLanguageRepository.save(language);
	}

	/**
	 * Reverts a pending deletion previously made via {@link #removeDraftLanguage}, marking the
	 * given draft translation language as {@code isDeleted = false} again.
	 *
	 * @param language the draft translation language to restore.
	 */
	@Transactional
	public void restoreDraftLanguage(DBDraftTranslationLanguage language) {
		language.setIsDeleted(false);
		language.setUpdatedAt(Instant.now());
		draftTranslationLanguageRepository.save(language);
	}

	/**
	 * Permanently deletes the given draft translation language row — unlike {@link
	 * #removeDraftLanguage}, this cannot be undone. Used by {@link PublishService#publish} once a
	 * language's pending deletion has actually been reconciled into the published registry, and
	 * internally by {@link #removeDraftLanguage} for languages that were never published.
	 *
	 * @param language the draft translation language to permanently delete.
	 */
	@Transactional
	public void hardDeleteDraftLanguage(DBDraftTranslationLanguage language) {
		draftTranslationLanguageRepository.delete(language);
	}

}

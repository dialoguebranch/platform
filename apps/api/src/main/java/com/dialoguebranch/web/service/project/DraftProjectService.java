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

import com.dialoguebranch.web.service.controller.schema.authoring.AddTranslationLanguagePayload;
import com.dialoguebranch.web.service.controller.schema.authoring.UpdateDraftLanguagePayload;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.exception.HttpFieldError;
import com.dialoguebranch.web.service.repository.DBDraftTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing a project's draft (working-copy) metadata: its display name, description,
 * and translation language registry. All edits made through this service affect the draft layer
 * only; changes are not visible outside Authoring Mode until the project is published via
 * {@link PublishService}, which snapshots them onto the new {@link
 * com.dialoguebranch.web.service.storage.model.DBProjectVersion}'s display name/description and
 * its published {@link
 * com.dialoguebranch.web.service.storage.model.DBPublishedTranslationLanguage} registry.
 *
 * @author Harm op den Akker
 */
@Service
public class DraftProjectService {

	private final DBProjectRepository projectRepository;
	private final DBDraftTranslationLanguageRepository draftTranslationLanguageRepository;
	private final DraftDialogueService draftDialogueService;

	/**
	 * Creates a new {@link DraftProjectService}.
	 *
	 * @param projectRepository                   repository used to persist a project's draft
	 *                                             metadata fields.
	 * @param draftTranslationLanguageRepository   repository used to read and persist draft
	 *                                             translation languages.
	 * @param draftDialogueService                 service used to strip a language's draft
	 *                                             translation content before that language is
	 *                                             hard-deleted.
	 */
	public DraftProjectService(DBProjectRepository projectRepository,
			DBDraftTranslationLanguageRepository draftTranslationLanguageRepository,
			DraftDialogueService draftDialogueService) {
		this.projectRepository = projectRepository;
		this.draftTranslationLanguageRepository = draftTranslationLanguageRepository;
		this.draftDialogueService = draftDialogueService;
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
	 * @throws ConflictException if a draft translation language with {@code code} already exists
	 * for this project — including a soft-deleted one still pending publish (see {@link
	 * #removeDraftLanguage}); reviving one is a distinct operation ({@link #restoreDraftLanguage}),
	 * not an implicit side effect of adding.
	 */
	@Transactional
	public DBDraftTranslationLanguage addDraftLanguage(DBProject project, String name,
			String code) throws ConflictException {
		boolean alreadyExists = draftTranslationLanguageRepository
				.findByProjectAndTranslationLanguageCode(project, code)
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
	 * publish. Its draft translation content (if any) is stripped first, the same way {@link
	 * PublishService#publish} already does for a language hard-deleted at publish time — the
	 * {@code draft_translation_language_id} foreign key on {@code draft_translations} is {@code
	 * NOT NULL} with no cascade, so deleting the language row first would otherwise violate it.</p>
	 *
	 * @param language the draft translation language to remove.
	 */
	@Transactional
	public void removeDraftLanguage(DBDraftTranslationLanguage language) {
		if (language.getIsNew()) {
			draftDialogueService.deleteAllTranslationsInLanguage(language);
			hardDeleteDraftLanguage(language);
			return;
		}
		language.setIsDeleted(true);
		language.setUpdatedAt(Instant.now());
		draftTranslationLanguageRepository.save(language);
	}

	/**
	 * Renames the given draft translation language's name and/or code. A code change takes effect
	 * on the published side the next time the project is published — since the published
	 * translation-language registry is a fresh per-version snapshot (not a single current-state
	 * table), there is nothing to retire or reconcile here.
	 *
	 * @param language the draft translation language to rename.
	 * @param name     the new human-readable name.
	 * @param code     the new language code.
	 * @return the updated draft translation language.
	 */
	@Transactional
	public DBDraftTranslationLanguage updateDraftLanguage(DBDraftTranslationLanguage language,
			String name, String code) {
		language.setTranslationLanguageName(name);
		language.setTranslationLanguageCode(code);
		language.setUpdatedAt(Instant.now());
		return draftTranslationLanguageRepository.save(language);
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

	// ------------------------------------------------------------------------------ //
	// -------------------- Batch Draft Update (Configure Project "Save") -------------------- //
	// ------------------------------------------------------------------------------ //

	/**
	 * Applies every change a single "Save Draft" in the Configure Project window can make —
	 * display name/description, translation-language removals, additions, and renames — as one
	 * atomic batch: the whole request is validated up front (collecting every problem found, not
	 * just the first), and either all of it is applied or, if any problems were found, none of it
	 * is.
	 *
	 * <p>Every language code conflict is checked against the batch's <em>final</em> resulting
	 * state in one read-only pass, rather than by mutating a shared "occupied codes" set one
	 * change at a time — the latter would incorrectly reject a valid swap (or rotation) of codes
	 * between two or more languages, since it would see a transient, not-yet-fully-applied
	 * conflict partway through even though the actual end state has none.</p>
	 *
	 * @param project           the project to update.
	 * @param displayName       the new draft display name.
	 * @param description       the new draft description.
	 * @param removeLanguageIds ids of existing draft translation languages to mark pending
	 *                          deletion.
	 * @param addLanguages      new draft translation languages to add.
	 * @param updateLanguages   existing draft translation languages to rename (name and/or code).
	 * @return the updated project.
	 * @throws BadRequestException if any translation language id doesn't resolve to one owned by
	 * this project, a name/code is missing, the same language id appears in both {@code
	 * removeLanguageIds} and {@code updateLanguages} (a contradictory instruction — rejected
	 * outright rather than guessed at, since which one was "intended" genuinely depends on
	 * whether that language has a published counterpart), or — in the batch's final resulting
	 * state — any two translation languages would end up sharing the same code.
	 */
	@Transactional
	public DBProject updateDraft(DBProject project, String displayName, String description,
			List<UUID> removeLanguageIds, List<AddTranslationLanguagePayload> addLanguages,
			List<UpdateDraftLanguagePayload> updateLanguages) throws BadRequestException {
		// The caller (ProjectController) loads `project` via its own, already-completed query, so
		// it arrives here detached with a stale, eagerly-fetched draftTranslationLanguages
		// snapshot — re-fetch as managed for the same reason PublishService.publish() does (see
		// its Javadoc for the full explanation).
		UUID projectId = project.getId();
		project = projectRepository.findById(projectId)
				.orElseThrow(() -> new IllegalStateException(
						"Project disappeared during draft update: " + projectId));

		List<HttpFieldError> errors = new ArrayList<>();
		List<DBDraftTranslationLanguage> existingLanguages =
				draftTranslationLanguageRepository.findByProject(project);
		// Resolve every id against this already-fetched, already-project-scoped list instead of
		// issuing a separate findById query per id in removeLanguageIds/updateLanguages below —
		// this single query already covers every id that could possibly be valid for this batch.
		Map<UUID, DBDraftTranslationLanguage> languageById = new HashMap<>();
		for (DBDraftTranslationLanguage existing : existingLanguages) {
			languageById.put(existing.getId(), existing);
		}

		List<DBDraftTranslationLanguage> resolvedRemovals = new ArrayList<>();
		Set<UUID> removalIds = new HashSet<>();
		for (UUID id : removeLanguageIds == null ? List.<UUID>of() : removeLanguageIds) {
			removalIds.add(id);
			DBDraftTranslationLanguage language = languageById.get(id);
			if (language == null) {
				errors.add(new HttpFieldError("removeLanguageIds",
						"Translation language not found: " + id));
				continue;
			}
			resolvedRemovals.add(language);
		}

		record ResolvedUpdate(DBDraftTranslationLanguage language, String name, String code) { }
		List<ResolvedUpdate> resolvedUpdates = new ArrayList<>();
		for (UpdateDraftLanguagePayload entry :
				updateLanguages == null ? List.<UpdateDraftLanguagePayload>of() : updateLanguages) {
			if (removalIds.contains(entry.getId())) {
				errors.add(new HttpFieldError("updateLanguages[" + entry.getId() + "]",
						"This translation language is also being removed in the same batch."));
				continue;
			}
			DBDraftTranslationLanguage language = languageById.get(entry.getId());
			if (language == null) {
				errors.add(new HttpFieldError("updateLanguages",
						"Translation language not found: " + entry.getId()));
				continue;
			}
			if (entry.getTranslationLanguageName() == null
					|| entry.getTranslationLanguageName().isBlank()
					|| entry.getTranslationLanguageCode() == null
					|| entry.getTranslationLanguageCode().isBlank()) {
				errors.add(new HttpFieldError("updateLanguages[" + entry.getId() + "]",
						"Both a name and a code are required."));
				continue;
			}
			resolvedUpdates.add(new ResolvedUpdate(language, entry.getTranslationLanguageName(),
					entry.getTranslationLanguageCode()));
		}

		List<AddTranslationLanguagePayload> resolvedAdditions = new ArrayList<>();
		for (AddTranslationLanguagePayload entry :
				addLanguages == null ? List.<AddTranslationLanguagePayload>of() : addLanguages) {
			String code = entry.getTranslationLanguageCode();
			String name = entry.getTranslationLanguageName();
			if (name == null || name.isBlank() || code == null || code.isBlank()) {
				errors.add(new HttpFieldError("addLanguages",
						"Both a name and a code are required."));
				continue;
			}
			resolvedAdditions.add(entry);
		}

		// Compute the code every surviving/new language would end up with in the batch's final
		// state, and count how many languages would share each one — a code is only a genuine
		// conflict if two or more languages would end up with it, regardless of who held it
		// (or wanted it) along the way.
		Map<UUID, String> updatedCodeById = new HashMap<>();
		for (ResolvedUpdate update : resolvedUpdates) {
			updatedCodeById.put(update.language().getId(), update.code());
		}
		Map<String, Integer> finalCodeCounts = new HashMap<>();
		for (DBDraftTranslationLanguage existing : existingLanguages) {
			// A never-published removal genuinely frees its code immediately (see
			// removeDraftLanguage); a soft-deleted one keeps occupying it until the next publish
			// reconciles it away (see addDraftLanguage's Javadoc), so it still counts here.
			if (removalIds.contains(existing.getId()) && existing.getIsNew()) continue;
			String finalCode = updatedCodeById.getOrDefault(existing.getId(),
					existing.getTranslationLanguageCode());
			finalCodeCounts.merge(finalCode, 1, Integer::sum);
		}
		for (AddTranslationLanguagePayload addition : resolvedAdditions) {
			finalCodeCounts.merge(addition.getTranslationLanguageCode(), 1, Integer::sum);
		}
		for (ResolvedUpdate update : resolvedUpdates) {
			if (finalCodeCounts.getOrDefault(update.code(), 0) > 1) {
				errors.add(new HttpFieldError("updateLanguages[" + update.language().getId() + "]",
						"A translation language with code '" + update.code() + "' already exists."));
			}
		}
		for (AddTranslationLanguagePayload addition : resolvedAdditions) {
			if (finalCodeCounts.getOrDefault(addition.getTranslationLanguageCode(), 0) > 1) {
				errors.add(new HttpFieldError(
						"addLanguages[" + addition.getTranslationLanguageCode() + "]",
						"A translation language with code '" + addition.getTranslationLanguageCode()
								+ "' already exists."));
			}
		}

		if (!errors.isEmpty()) {
			throw BadRequestException.withInvalidInput(errors);
		}

		// Nothing below can fail anymore — every id resolved, and the resulting set of codes is
		// conflict-free — so apply everything.
		updateDraftMetadata(project, displayName, description);
		for (DBDraftTranslationLanguage language : resolvedRemovals) {
			boolean wasNew = language.getIsNew();
			removeDraftLanguage(language);
			if (wasNew) {
				// Hard-deleted for real — keep project's already-loaded EAGER collection in sync
				// (see PublishService.reconcilePublishedTranslationLanguages for why this matters).
				project.getDraftTranslationLanguages().remove(language);
			}
		}
		for (ResolvedUpdate update : resolvedUpdates) {
			updateDraftLanguage(update.language(), update.name(), update.code());
		}
		for (AddTranslationLanguagePayload addition : resolvedAdditions) {
			try {
				addDraftLanguage(project, addition.getTranslationLanguageName(),
						addition.getTranslationLanguageCode());
			} catch (ConflictException e) {
				// Unreachable: already validated above against this exact occupied-codes state.
				throw new IllegalStateException(e);
			}
		}

		return project;
	}

}

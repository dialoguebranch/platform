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

package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.controller.schema.authoring.DiscardedDialogueChanges;
import com.dialoguebranch.web.service.controller.schema.authoring.DiscardedProjectChanges;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.repository.DBDraftTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedSpeaker;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslationLanguage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Discards draft changes: makes a draft dialogue (#363), or a whole project's draft (#364),
 * identical to its latest published version again.
 *
 * @author Harm op den Akker
 */
@Service
public class DraftDiscardService {

	private final DraftDialogueService draftDialogueService;
	private final DBPublishedDialogueRepository publishedDialogueRepository;
	private final DBPublishedTranslationRepository publishedTranslationRepository;
	private final DBDraftTranslationLanguageRepository draftLanguageRepository;
	private final DBPublishedTranslationLanguageRepository publishedLanguageRepository;
	private final DBProjectRepository projectRepository;
	private final DraftProjectService draftProjectService;

	/**
	 * Creates the service.
	 *
	 * @param draftDialogueService           the service storing draft dialogues.
	 * @param publishedDialogueRepository    the published dialogues.
	 * @param publishedTranslationRepository the published translations.
	 * @param draftLanguageRepository        the draft translation languages.
	 * @param publishedLanguageRepository    the published translation languages.
	 * @param projectRepository              the projects.
	 * @param draftProjectService            the service storing a project's draft metadata.
	 */
	public DraftDiscardService(DraftDialogueService draftDialogueService,
			DBPublishedDialogueRepository publishedDialogueRepository,
			DBPublishedTranslationRepository publishedTranslationRepository,
			DBDraftTranslationLanguageRepository draftLanguageRepository,
			DBPublishedTranslationLanguageRepository publishedLanguageRepository,
			DBProjectRepository projectRepository, DraftProjectService draftProjectService) {
		this.draftDialogueService = draftDialogueService;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
		this.draftLanguageRepository = draftLanguageRepository;
		this.publishedLanguageRepository = publishedLanguageRepository;
		this.projectRepository = projectRepository;
		this.draftProjectService = draftProjectService;
	}

	/**
	 * Makes a draft dialogue identical to its latest published version again: its nodes (headers
	 * and positions included) and preamble from the published script, its translations from the
	 * published translations, and its published name if it was renamed. It is then no longer
	 * marked as changed.
	 *
	 * <p>Translations are matched to the draft's translation languages by code: a draft
	 * translation in a language the published version has no translation in (for example a
	 * language added since) is removed. If a language the published version has a translation in
	 * no longer exists under that code (it was renamed), nothing is discarded: that translation
	 * couldn't be restored.</p>
	 *
	 * @param project  the project.
	 * @param dialogue the draft dialogue; not pending deletion.
	 * @return the dialogue's name now and before.
	 * @throws BadRequestException if the dialogue was never published, or a translation language
	 * was renamed since it was published.
	 * @throws ConflictException if the dialogue was renamed and another dialogue now has its
	 * published name.
	 */
	@Transactional(rollbackFor = Exception.class)
	public DiscardedDialogueChanges discardChanges(DBProject project, DBDraftDialogue dialogue)
			throws BadRequestException, ConflictException {
		String draftName = dialogue.getName();
		DBProjectVersion latest = project.getLatestVersion();
		if (dialogue.getIsNew() || latest == null) {
			throw new BadRequestException("Dialogue '" + draftName + "' was never published, so " +
					"there are no published changes to go back to; delete it instead.");
		}
		String publishedName = Objects.requireNonNullElse(dialogue.getPreviousPublishedName(),
				draftName);
		DBPublishedDialogue published = publishedDialogueRepository
				.findByVersionAndName(latest, publishedName)
				.orElseThrow(() -> new BadRequestException("Dialogue '" + draftName +
						"' isn't part of the latest published version (" + publishedName + ")."));
		if (!publishedName.equals(draftName) &&
				draftDialogueService.findDialogue(project, publishedName).isPresent()) {
			throw new ConflictException("Dialogue '" + draftName + "' was published as '" +
					publishedName + "', but another dialogue now has that name; rename that " +
					"dialogue first.");
		}

		restoreFromPublished(project, dialogue, published, publishedName);
		return new DiscardedDialogueChanges(publishedName, draftName);
	}

	/**
	 * Discards all of a project's draft changes (#364): makes its draft identical to its latest
	 * published version again. Its display name and description, and its translation languages
	 * (added ones removed, removed ones restored, renamed ones renamed back), become the published
	 * ones; dialogues that were never published are removed; and every other dialogue, including
	 * one pending deletion, is rebuilt from its published version, with its published name.
	 *
	 * @param project the project.
	 * @return how many dialogues were reverted and removed.
	 * @throws BadRequestException if the project was never published.
	 */
	@Transactional(rollbackFor = Exception.class)
	public DiscardedProjectChanges discardAllChanges(DBProject project)
			throws BadRequestException {
		// Work on a managed copy: the caller's instance was loaded by an earlier, completed query.
		UUID projectId = project.getId();
		project = projectRepository.findById(projectId)
				.orElseThrow(() -> new IllegalStateException("Project disappeared: " + projectId));
		DBProjectVersion latest = project.getLatestVersion();
		if (latest == null) {
			throw new BadRequestException("Project '" + project.getSlug() + "' was never " +
					"published, so there are no published changes to go back to.");
		}

		project.setDraftDisplayName(latest.getDisplayName());
		project.setDraftDescription(latest.getDescription());
		project.setUpdatedAt(Instant.now());
		projectRepository.save(project);
		// The published speakers (#394), in their order
		draftProjectService.setDraftSpeakers(project, latest.getSpeakers().stream()
				.sorted(Comparator.comparingInt(DBPublishedSpeaker::getPosition))
				.map(DBPublishedSpeaker::toSpeaker).toList());

		// Dialogues that were never published go first, freeing the names they may have taken.
		int removed = 0;
		List<DBDraftDialogue> remaining = new ArrayList<>();
		for (DBDraftDialogue dialogue : draftDialogueService.listDialogues(project)) {
			if (dialogue.getIsNew()) {
				draftDialogueService.hardDeleteDialogue(dialogue);
				removed++;
			} else {
				remaining.add(dialogue);
			}
		}

		restorePublishedLanguages(project, latest);

		Map<String, DBPublishedDialogue> published = new HashMap<>();
		for (DBPublishedDialogue dialogue : publishedDialogueRepository.findByVersion(latest)) {
			published.put(dialogue.getName(), dialogue);
		}
		// Renamed dialogues first take a temporary name, so swapped names can't collide with the
		// unique (project, name) constraint while they're put back.
		List<DBDraftDialogue> reverting = new ArrayList<>();
		for (DBDraftDialogue dialogue : remaining) {
			String publishedName = publishedName(dialogue);
			if (!published.containsKey(publishedName)) {
				draftDialogueService.hardDeleteDialogue(dialogue);
				removed++;
				continue;
			}
			if (!publishedName.equals(dialogue.getName())) {
				dialogue.setName("~discard~" + dialogue.getId());
				draftDialogueService.save(dialogue);
			}
			reverting.add(dialogue);
		}
		draftLanguageRepository.flush();
		for (DBDraftDialogue dialogue : reverting) {
			String publishedName = publishedName(dialogue);
			restoreFromPublished(project, dialogue, published.get(publishedName), publishedName);
		}
		return new DiscardedProjectChanges(reverting.size(), removed);
	}

	private static String publishedName(DBDraftDialogue dialogue) {
		return Objects.requireNonNullElse(dialogue.getPreviousPublishedName(), dialogue.getName());
	}

	/**
	 * Makes the project's draft translation languages the published ones: a draft language whose
	 * code the published version doesn't have is removed with its translations, the others get
	 * their published name back and are no longer new or pending deletion, and a published
	 * language missing from the draft is added.
	 */
	private void restorePublishedLanguages(DBProject project, DBProjectVersion latest) {
		Map<String, DBPublishedTranslationLanguage> publishedByCode = new LinkedHashMap<>();
		for (DBPublishedTranslationLanguage language :
				publishedLanguageRepository.findByVersion(latest)) {
			publishedByCode.put(language.getTranslationLanguageCode(), language);
		}
		Instant now = Instant.now();
		List<String> draftCodes = new ArrayList<>();
		for (DBDraftTranslationLanguage language : draftLanguageRepository.findByProject(project)) {
			DBPublishedTranslationLanguage published =
					publishedByCode.get(language.getTranslationLanguageCode());
			if (published == null) {
				draftDialogueService.deleteAllTranslationsInLanguage(language);
				draftLanguageRepository.delete(language);
				continue;
			}
			draftCodes.add(language.getTranslationLanguageCode());
			language.setTranslationLanguageName(published.getTranslationLanguageName());
			language.setIsNew(false);
			language.setIsDeleted(false);
			language.setUpdatedAt(now);
			draftLanguageRepository.save(language);
		}
		draftLanguageRepository.flush();
		for (DBPublishedTranslationLanguage published : publishedByCode.values()) {
			if (draftCodes.contains(published.getTranslationLanguageCode())) continue;
			DBDraftTranslationLanguage language = new DBDraftTranslationLanguage();
			language.setProject(project);
			language.setTranslationLanguageName(published.getTranslationLanguageName());
			language.setTranslationLanguageCode(published.getTranslationLanguageCode());
			language.setIsNew(false);
			language.setIsDeleted(false);
			language.setCreatedAt(now);
			language.setUpdatedAt(now);
			draftLanguageRepository.save(language);
		}
	}

	/**
	 * Rebuilds a draft dialogue from its published version: nodes and preamble from the published
	 * script, translations from the published translations (matched by language code; a draft
	 * translation in a language the published version has none in is removed), and the published
	 * name. It is then no longer changed, renamed or pending deletion.
	 *
	 * @throws BadRequestException if a language the published version has a translation in no
	 * longer exists in the draft under that code; nothing is changed then.
	 */
	private void restoreFromPublished(DBProject project, DBDraftDialogue dialogue,
			DBPublishedDialogue published, String publishedName) throws BadRequestException {
		String draftName = dialogue.getName();
		// Keyed by the draft language's id: entities have no equals of their own.
		Map<UUID, DBDraftTranslationLanguage> languages = new LinkedHashMap<>();
		Map<UUID, String> translations = new LinkedHashMap<>();
		for (DBPublishedTranslation translation :
				publishedTranslationRepository.findByPublishedDialogue(published)) {
			String code = translation.getTranslationLanguage().getTranslationLanguageCode();
			DBDraftTranslationLanguage language = draftLanguageRepository
					.findByProjectAndTranslationLanguageCode(project, code)
					.orElseThrow(() -> new BadRequestException("Translation language '" + code +
							"' was renamed since dialogue '" + draftName + "' was published, so " +
							"its published translation can't be restored; discard the project's " +
							"draft changes instead."));
			languages.put(language.getId(), language);
			translations.put(language.getId(), translation.getContent());
		}

		draftDialogueService.replaceDialogueFromScript(dialogue, published.getContent());
		for (DBDraftTranslation translation : draftDialogueService.listTranslations(dialogue)) {
			if (!translations.containsKey(translation.getLanguage().getId())) {
				draftDialogueService.deleteTranslation(dialogue, translation);
			}
		}
		translations.forEach((languageId, content) -> draftDialogueService
				.createOrUpdateTranslation(dialogue, languages.get(languageId), content));

		dialogue.setName(publishedName);
		dialogue.setPreviousPublishedName(null);
		dialogue.setIsDeleted(false);
		dialogue.setIsChanged(false);
		dialogue.setUpdatedAt(Instant.now());
		draftDialogueService.save(dialogue);
	}
}

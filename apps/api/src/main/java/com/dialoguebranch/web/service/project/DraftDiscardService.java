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
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.repository.DBDraftTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Discards a draft dialogue's changes: makes the draft identical to the dialogue's latest
 * published version again (#363).
 *
 * @author Harm op den Akker
 */
@Service
public class DraftDiscardService {

	private final DraftDialogueService draftDialogueService;
	private final DBPublishedDialogueRepository publishedDialogueRepository;
	private final DBPublishedTranslationRepository publishedTranslationRepository;
	private final DBDraftTranslationLanguageRepository draftLanguageRepository;

	/**
	 * Creates the service.
	 *
	 * @param draftDialogueService           the service storing draft dialogues.
	 * @param publishedDialogueRepository    the published dialogues.
	 * @param publishedTranslationRepository the published translations.
	 * @param draftLanguageRepository        the draft translation languages.
	 */
	public DraftDiscardService(DraftDialogueService draftDialogueService,
			DBPublishedDialogueRepository publishedDialogueRepository,
			DBPublishedTranslationRepository publishedTranslationRepository,
			DBDraftTranslationLanguageRepository draftLanguageRepository) {
		this.draftDialogueService = draftDialogueService;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
		this.draftLanguageRepository = draftLanguageRepository;
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
		dialogue.setIsChanged(false);
		dialogue.setUpdatedAt(Instant.now());
		draftDialogueService.save(dialogue);
		return new DiscardedDialogueChanges(publishedName, draftName);
	}
}

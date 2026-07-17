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

package com.dialoguebranch.web.service.repository;

import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import com.dialoguebranch.web.service.storage.model.DBTranslationLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DBPublishedTranslation} entities.
 *
 * @author Harm op den Akker
 */
public interface DBPublishedTranslationRepository
		extends JpaRepository<DBPublishedTranslation, UUID> {

	/**
	 * Finds all translations belonging to the given published dialogue.
	 *
	 * @param publishedDialogue the published dialogue whose translations should be retrieved.
	 * @return the list of translations belonging to {@code publishedDialogue}.
	 */
	List<DBPublishedTranslation> findByPublishedDialogue(DBPublishedDialogue publishedDialogue);

	/**
	 * Finds all translations belonging to any of the given published dialogues.
	 *
	 * @param dialogues the published dialogues whose translations should be retrieved.
	 * @return the combined list of translations belonging to {@code dialogues}.
	 */
	List<DBPublishedTranslation> findByPublishedDialogueIn(
			List<DBPublishedDialogue> dialogues);

	/**
	 * Finds the translation for the given language within the given published dialogue.
	 *
	 * @param publishedDialogue the published dialogue the translation belongs to.
	 * @param translationLanguage the target language of the translation.
	 * @return an {@link Optional} containing the matching {@link DBPublishedTranslation}, or
	 * empty if no translation for the given language exists for the published dialogue.
	 */
	Optional<DBPublishedTranslation> findByPublishedDialogueAndTranslationLanguage(
			DBPublishedDialogue publishedDialogue, DBTranslationLanguage translationLanguage);

}

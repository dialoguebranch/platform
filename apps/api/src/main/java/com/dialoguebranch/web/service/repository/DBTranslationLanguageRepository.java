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

import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBTranslationLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DBTranslationLanguage} entities.
 *
 * @author Harm op den Akker
 */
public interface DBTranslationLanguageRepository
		extends JpaRepository<DBTranslationLanguage, UUID> {

	/**
	 * Finds all translation languages ever registered for the given project, including removed
	 * ones. Used only when tearing down a whole project ({@code ProjectService.deleteProject}) —
	 * everywhere else, {@link #findByProjectAndIsRemovedFalse} is the right call.
	 *
	 * @param project the project whose translation languages should be retrieved.
	 * @return the list of translation languages belonging to {@code project}.
	 */
	List<DBTranslationLanguage> findByProject(DBProject project);

	/**
	 * Finds all currently active (non-removed) translation languages configured for the given
	 * project.
	 *
	 * @param project the project whose active translation languages should be retrieved.
	 * @return the list of active translation languages belonging to {@code project}.
	 */
	List<DBTranslationLanguage> findByProjectAndIsRemovedFalse(DBProject project);

	/**
	 * Finds the (possibly removed) translation language with the given code for the given
	 * project. Used by publish reconciliation to find-or-create/un-remove a published language
	 * row matching a draft language.
	 *
	 * @param project the project the language belongs to.
	 * @param translationLanguageCode the language code to look up.
	 * @return an {@link Optional} containing the matching {@link DBTranslationLanguage}, or empty.
	 */
	Optional<DBTranslationLanguage> findByProjectAndTranslationLanguageCode(
			DBProject project, String translationLanguageCode);

}

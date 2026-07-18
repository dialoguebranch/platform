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

package com.dialoguebranch.web.service.controller.schema.authoring;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for {@code /project/update-draft}: every change a "Save Draft" in the Configure
 * Project window can make in one batch — the project's display name/description, translation
 * languages to remove, translation languages to add, and existing translation languages to rename
 * (name and/or code) — applied atomically: the whole batch is validated up front, and either all
 * of it is applied or none of it is (see {@code DraftProjectService#updateDraft}).
 *
 * @author Harm op den Akker
 */
public class UpdateProjectDraftPayload {
	private String displayName;
	private String description;
	private List<UUID> removeLanguageIds;
	private List<AddTranslationLanguagePayload> addLanguages;
	private List<UpdateDraftLanguagePayload> updateLanguages;

	/**
	 * @return the new draft display name for the project.
	 */
	public String getDisplayName() { return displayName; }

	/**
	 * @param displayName the new draft display name for the project.
	 */
	public void setDisplayName(String displayName) { this.displayName = displayName; }

	/**
	 * @return the new draft description for the project.
	 */
	public String getDescription() { return description; }

	/**
	 * @param description the new draft description for the project.
	 */
	public void setDescription(String description) { this.description = description; }

	/**
	 * @return the ids of existing draft translation languages to mark pending deletion.
	 */
	public List<UUID> getRemoveLanguageIds() { return removeLanguageIds; }

	/**
	 * @param removeLanguageIds the ids of existing draft translation languages to mark pending
	 *                          deletion.
	 */
	public void setRemoveLanguageIds(List<UUID> removeLanguageIds) { this.removeLanguageIds = removeLanguageIds; }

	/**
	 * @return the new draft translation languages to add.
	 */
	public List<AddTranslationLanguagePayload> getAddLanguages() { return addLanguages; }

	/**
	 * @param addLanguages the new draft translation languages to add.
	 */
	public void setAddLanguages(List<AddTranslationLanguagePayload> addLanguages) { this.addLanguages = addLanguages; }

	/**
	 * @return the existing draft translation languages to rename (name and/or code).
	 */
	public List<UpdateDraftLanguagePayload> getUpdateLanguages() { return updateLanguages; }

	/**
	 * @param updateLanguages the existing draft translation languages to rename (name and/or
	 *                        code).
	 */
	public void setUpdateLanguages(List<UpdateDraftLanguagePayload> updateLanguages) { this.updateLanguages = updateLanguages; }
}

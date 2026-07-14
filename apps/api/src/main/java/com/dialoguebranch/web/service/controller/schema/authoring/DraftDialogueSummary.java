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

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;

/**
 * Summary of a draft dialogue as returned by the {@code /list-dialogues} end-point, exposing its
 * persisted status flags rather than the full entity.
 *
 * @author Harm op den Akker
 */
public class DraftDialogueSummary {

	private final String name;
	private final boolean isNew;
	private final boolean isChanged;
	private final boolean isDeleted;

	/**
	 * Creates a {@link DraftDialogueSummary} from the given {@link DBDraftDialogue} entity,
	 * copying its name and status flags.
	 *
	 * @param dialogue the draft dialogue entity to summarize.
	 */
	public DraftDialogueSummary(DBDraftDialogue dialogue) {
		this.name = dialogue.getName();
		this.isNew = dialogue.getIsNew();
		this.isChanged = dialogue.getIsChanged();
		this.isDeleted = dialogue.getIsDeleted();
	}

	/**
	 * @return the logical name of the draft dialogue.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return whether this dialogue has no published counterpart yet.
	 */
	public boolean getIsNew() {
		return isNew;
	}

	/**
	 * @return whether this dialogue's draft content currently differs from its latest published
	 * version (or there is no published version at all).
	 */
	public boolean getIsChanged() {
		return isChanged;
	}

	/**
	 * @return whether this dialogue is pending deletion.
	 */
	public boolean getIsDeleted() {
		return isDeleted;
	}

}

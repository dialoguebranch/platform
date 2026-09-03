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

package com.dialoguebranch.web.service.controller.schema.authoring;

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;

/**
 * Summary of a draft dialogue as returned by the {@code /list-dialogues} end-point, exposing its
 * name, last-updated time, node count and persisted status flags rather than the full entity.
 *
 * @author Harm op den Akker
 */
public class DraftDialogueSummary {

	private final String name;
	private final String updatedAt;
	private final int nodeCount;
	private final boolean isNew;
	private final boolean isChanged;
	private final boolean isDeleted;
	private final String previousPublishedName;

	/**
	 * Creates a {@link DraftDialogueSummary} from the given {@link DBDraftDialogue} entity and a
	 * separately-computed node count (the entity's node collection is not touched).
	 *
	 * @param dialogue  the draft dialogue entity to summarize.
	 * @param nodeCount the number of nodes the draft dialogue contains.
	 */
	public DraftDialogueSummary(DBDraftDialogue dialogue, int nodeCount) {
		this.name = dialogue.getName();
		this.updatedAt = dialogue.getUpdatedAt() != null ? dialogue.getUpdatedAt().toString() : null;
		this.nodeCount = nodeCount;
		this.isNew = dialogue.getIsNew();
		this.isChanged = dialogue.getIsChanged();
		this.isDeleted = dialogue.getIsDeleted();
		this.previousPublishedName = dialogue.getPreviousPublishedName();
	}

	/**
	 * Returns the logical name of the draft dialogue.
	 *
	 * @return the logical name of the draft dialogue.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the instant at which this draft dialogue was last modified.
	 *
	 * @return the ISO-8601 instant at which this draft dialogue was last modified, or {@code
	 * null} if it has no recorded update time.
	 */
	public String getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Returns the number of nodes in this draft dialogue.
	 *
	 * @return the number of nodes in this draft dialogue.
	 */
	public int getNodeCount() {
		return nodeCount;
	}

	/**
	 * Returns whether this dialogue has no published counterpart yet.
	 *
	 * @return whether this dialogue has no published counterpart yet.
	 */
	public boolean getIsNew() {
		return isNew;
	}

	/**
	 * Returns whether this dialogue's draft content currently differs from its latest published
	 * version.
	 *
	 * @return whether this dialogue's draft content currently differs from its latest published
	 * version (or there is no published version at all).
	 */
	public boolean getIsChanged() {
		return isChanged;
	}

	/**
	 * Returns whether this dialogue is pending deletion.
	 *
	 * @return whether this dialogue is pending deletion.
	 */
	public boolean getIsDeleted() {
		return isDeleted;
	}

	/**
	 * Returns the published name this dialogue is still known by, if it has been renamed since it
	 * was last published.
	 *
	 * @return the published name this dialogue is still known by, if it has been renamed since it
	 * was last published, or {@code null} otherwise.
	 */
	public String getPreviousPublishedName() {
		return previousPublishedName;
	}

}

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

package com.dialoguebranch.web.service.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * JPA entity for a speaker in the editable draft of a project (#394), in the
 * {@code draft_speakers} table. Edited in Authoring Mode through {@code update-draft}, and copied
 * into a {@link DBPublishedSpeaker} for every new version when the project is published.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "draft_speakers",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_draft_speakers", columnNames = { "project_id", "name" })
	}
)
public class DBDraftSpeaker extends DBSpeakerFields {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private DBProject project;

	/** Creates an empty speaker row. */
	public DBDraftSpeaker() {
	}

	/**
	 * Returns the project this speaker belongs to.
	 * @return the project.
	 */
	public DBProject getProject() { return project; }

	/**
	 * Sets the project this speaker belongs to.
	 * @param project the project.
	 */
	public void setProject(DBProject project) { this.project = project; }
}

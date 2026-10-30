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
 * JPA entity for a speaker in a published version of a project (#394), in the
 * {@code published_speakers} table: a snapshot of the draft speakers at the moment it was
 * published.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "published_speakers",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_published_speakers", columnNames = { "version_id", "name" })
	}
)
public class DBPublishedSpeaker extends DBSpeakerFields {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "version_id", nullable = false)
	@JsonIgnore
	private DBProjectVersion version;

	/** Creates an empty speaker row. */
	public DBPublishedSpeaker() {
	}

	/**
	 * Returns the version this speaker belongs to.
	 * @return the version.
	 */
	public DBProjectVersion getVersion() { return version; }

	/**
	 * Sets the version this speaker belongs to.
	 * @param version the version.
	 */
	public void setVersion(DBProjectVersion version) { this.version = version; }
}

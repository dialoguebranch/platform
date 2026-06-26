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

package com.dialoguebranch.web.service.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single node within a draft dialogue, stored in the
 * {@code draft_nodes} table. Each node has a {@code title} (its identifier within the dialogue),
 * a raw {@code header} string, and a raw {@code body} string — matching the structure of a node
 * in a {@code .dlb} script file.
 *
 * @author Harm op den Akker
 */
@Entity
@Table(
	name = "draft_nodes",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_draft_nodes",
			columnNames = { "draft_dialogue_id", "title" }
		)
	}
)
public class DBDraftNode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "draft_dialogue_id", nullable = false)
	@JsonIgnore
	private DBDraftDialogue draftDialogue;

	private String title;

	@Column(columnDefinition = "TEXT")
	private String header;

	@Column(columnDefinition = "TEXT")
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Creates an empty instance of {@link DBDraftNode}.
	 */
	public DBDraftNode() {
	}

	/**
	 * Returns the unique UUID identifier of this draft node.
	 *
	 * @return the UUID identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Sets the unique UUID identifier of this draft node.
	 *
	 * @param id the UUID identifier.
	 */
	public void setId(UUID id) {
		this.id = id;
	}

	/**
	 * Returns the {@link DBDraftDialogue} this node belongs to.
	 *
	 * @return the owning draft dialogue.
	 */
	public DBDraftDialogue getDraftDialogue() {
		return draftDialogue;
	}

	/**
	 * Sets the {@link DBDraftDialogue} this node belongs to.
	 *
	 * @param draftDialogue the owning draft dialogue.
	 */
	public void setDraftDialogue(DBDraftDialogue draftDialogue) {
		this.draftDialogue = draftDialogue;
	}

	/**
	 * Returns the title (node identifier) of this node within its dialogue.
	 *
	 * @return the node title.
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Sets the title (node identifier) of this node.
	 *
	 * @param title the node title.
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Returns the raw header content of this node.
	 *
	 * @return the header string.
	 */
	public String getHeader() {
		return header;
	}

	/**
	 * Sets the raw header content of this node.
	 *
	 * @param header the header string.
	 */
	public void setHeader(String header) {
		this.header = header;
	}

	/**
	 * Returns the raw body content of this node.
	 *
	 * @return the body string.
	 */
	public String getBody() {
		return body;
	}

	/**
	 * Sets the raw body content of this node.
	 *
	 * @param body the body string.
	 */
	public void setBody(String body) {
		this.body = body;
	}

	/**
	 * Returns the timestamp at which this node was created.
	 *
	 * @return the creation timestamp.
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the timestamp at which this node was created.
	 *
	 * @param createdAt the creation timestamp.
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Returns the timestamp at which this node was last updated.
	 *
	 * @return the last-updated timestamp.
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Sets the timestamp at which this node was last updated.
	 *
	 * @param updatedAt the last-updated timestamp.
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}

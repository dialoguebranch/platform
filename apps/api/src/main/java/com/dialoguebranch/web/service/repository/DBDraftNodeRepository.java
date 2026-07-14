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

import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DBDraftNode} entities.
 *
 * @author Harm op den Akker
 */
public interface DBDraftNodeRepository extends JpaRepository<DBDraftNode, UUID> {

	/**
	 * Finds all nodes belonging to the given draft dialogue, ordered by their creation time.
	 *
	 * @param draftDialogue the draft dialogue whose nodes should be retrieved.
	 * @return the list of nodes belonging to {@code draftDialogue}, ordered by creation time.
	 */
	List<DBDraftNode> findByDraftDialogueOrderByCreatedAt(DBDraftDialogue draftDialogue);

	/**
	 * Finds the node with the given title within the given draft dialogue.
	 *
	 * @param draftDialogue the draft dialogue the node belongs to.
	 * @param title the title of the node.
	 * @return an {@link Optional} containing the matching {@link DBDraftNode}, or empty if no
	 * node with the given title exists in the draft dialogue.
	 */
	Optional<DBDraftNode> findByDraftDialogueAndTitle(DBDraftDialogue draftDialogue, String title);

}

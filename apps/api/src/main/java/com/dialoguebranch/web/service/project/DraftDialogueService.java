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

package com.dialoguebranch.web.service.project;

import com.dialoguebranch.web.service.repository.DBDraftDialogueRepository;
import com.dialoguebranch.web.service.repository.DBDraftNodeRepository;
import com.dialoguebranch.web.service.repository.DBDraftTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftNode;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing draft dialogues, their nodes, and their translations. All edits made
 * through this service affect the draft layer only; changes are not visible to the execution
 * engine until a project is published via {@link PublishService}.
 *
 * @author Harm op den Akker
 */
@Service
public class DraftDialogueService {

	private final DBDraftDialogueRepository dialogueRepository;
	private final DBDraftNodeRepository nodeRepository;
	private final DBDraftTranslationRepository translationRepository;

	public DraftDialogueService(DBDraftDialogueRepository dialogueRepository,
								DBDraftNodeRepository nodeRepository,
								DBDraftTranslationRepository translationRepository) {
		this.dialogueRepository = dialogueRepository;
		this.nodeRepository = nodeRepository;
		this.translationRepository = translationRepository;
	}

	// --------------------------------------------------------------- //
	// -------------------- Dialogue Management -------------------- //
	// --------------------------------------------------------------- //

	/**
	 * Returns all draft dialogues belonging to the given project.
	 *
	 * @param project the owning project.
	 * @return list of draft dialogues.
	 */
	public List<DBDraftDialogue> listDialogues(DBProject project) {
		return dialogueRepository.findByProject(project);
	}

	/**
	 * Returns the draft dialogue with the given name within the given project, or
	 * {@link Optional#empty()} if not found.
	 *
	 * @param project the owning project.
	 * @param name    the dialogue name.
	 * @return the matching draft dialogue, or empty.
	 */
	public Optional<DBDraftDialogue> findDialogue(DBProject project, String name) {
		return dialogueRepository.findByProjectAndName(project, name);
	}

	/**
	 * Returns the draft dialogue with the given {@code id}, or {@link Optional#empty()} if not
	 * found.
	 *
	 * @param id the dialogue UUID.
	 * @return the matching draft dialogue, or empty.
	 */
	public Optional<DBDraftDialogue> findDialogueById(UUID id) {
		return dialogueRepository.findById(id);
	}

	/**
	 * Creates and persists a new draft dialogue with the given name in the given project.
	 *
	 * @param project the owning project.
	 * @param name    the dialogue name.
	 * @return the newly created {@link DBDraftDialogue}.
	 */
	public DBDraftDialogue createDialogue(DBProject project, String name) {
		Instant now = Instant.now();
		DBDraftDialogue dialogue = new DBDraftDialogue();
		dialogue.setProject(project);
		dialogue.setName(name);
		dialogue.setCreatedAt(now);
		dialogue.setUpdatedAt(now);
		return dialogueRepository.save(dialogue);
	}

	/**
	 * Deletes the given draft dialogue and all its associated nodes and translations.
	 *
	 * @param dialogue the draft dialogue to delete.
	 */
	public void deleteDialogue(DBDraftDialogue dialogue) {
		dialogueRepository.delete(dialogue);
	}

	// ---------------------------------------------------------- //
	// -------------------- Node Management -------------------- //
	// ---------------------------------------------------------- //

	/**
	 * Returns all nodes of the given draft dialogue, ordered by creation time.
	 *
	 * @param dialogue the owning draft dialogue.
	 * @return list of draft nodes ordered by {@code createdAt}.
	 */
	public List<DBDraftNode> listNodes(DBDraftDialogue dialogue) {
		return nodeRepository.findByDraftDialogueOrderByCreatedAt(dialogue);
	}

	/**
	 * Returns the node with the given title within the given draft dialogue, or
	 * {@link Optional#empty()} if not found.
	 *
	 * @param dialogue the owning draft dialogue.
	 * @param title    the node title.
	 * @return the matching draft node, or empty.
	 */
	public Optional<DBDraftNode> findNode(DBDraftDialogue dialogue, String title) {
		return nodeRepository.findByDraftDialogueAndTitle(dialogue, title);
	}

	/**
	 * Creates and persists a new node in the given draft dialogue.
	 *
	 * @param dialogue the owning draft dialogue.
	 * @param title    the node title (identifier within the dialogue).
	 * @param header   the raw header content.
	 * @param body     the raw body content.
	 * @return the newly created {@link DBDraftNode}.
	 */
	public DBDraftNode createNode(DBDraftDialogue dialogue, String title, String header,
								  String body) {
		Instant now = Instant.now();
		DBDraftNode node = new DBDraftNode();
		node.setDraftDialogue(dialogue);
		node.setTitle(title);
		node.setHeader(header);
		node.setBody(body);
		node.setCreatedAt(now);
		node.setUpdatedAt(now);
		return nodeRepository.save(node);
	}

	/**
	 * Updates the header and body of the given node.
	 *
	 * @param node   the node to update.
	 * @param header the new raw header content.
	 * @param body   the new raw body content.
	 * @return the updated {@link DBDraftNode}.
	 */
	public DBDraftNode updateNode(DBDraftNode node, String header, String body) {
		node.setHeader(header);
		node.setBody(body);
		node.setUpdatedAt(Instant.now());
		return nodeRepository.save(node);
	}

	/**
	 * Deletes the given draft node.
	 *
	 * @param node the node to delete.
	 */
	public void deleteNode(DBDraftNode node) {
		nodeRepository.delete(node);
	}

	// --------------------------------------------------------------- //
	// -------------------- Translation Management -------------------- //
	// --------------------------------------------------------------- //

	/**
	 * Returns all translations for the given draft dialogue.
	 *
	 * @param dialogue the owning draft dialogue.
	 * @return list of draft translations.
	 */
	public List<DBDraftTranslation> listTranslations(DBDraftDialogue dialogue) {
		return translationRepository.findByDraftDialogue(dialogue);
	}

	/**
	 * Returns the translation for the given language within the given draft dialogue, or
	 * {@link Optional#empty()} if not found.
	 *
	 * @param dialogue the owning draft dialogue.
	 * @param language the target language code.
	 * @return the matching draft translation, or empty.
	 */
	public Optional<DBDraftTranslation> findTranslation(DBDraftDialogue dialogue, String language) {
		return translationRepository.findByDraftDialogueAndLanguage(dialogue, language);
	}

	/**
	 * Creates or updates the translation for the given language in the given draft dialogue.
	 * If a translation for that language already exists, its content is updated; otherwise a new
	 * record is created.
	 *
	 * @param dialogue the owning draft dialogue.
	 * @param language the target language code.
	 * @param content  the raw JSON translation content.
	 * @return the created or updated {@link DBDraftTranslation}.
	 */
	public DBDraftTranslation createOrUpdateTranslation(DBDraftDialogue dialogue, String language,
														String content) {
		Instant now = Instant.now();
		DBDraftTranslation translation = translationRepository
				.findByDraftDialogueAndLanguage(dialogue, language)
				.orElseGet(() -> {
					DBDraftTranslation t = new DBDraftTranslation();
					t.setDraftDialogue(dialogue);
					t.setLanguage(language);
					t.setCreatedAt(now);
					return t;
				});
		translation.setContent(content);
		translation.setUpdatedAt(now);
		return translationRepository.save(translation);
	}

	/**
	 * Deletes the given draft translation.
	 *
	 * @param translation the translation to delete.
	 */
	public void deleteTranslation(DBDraftTranslation translation) {
		translationRepository.delete(translation);
	}

	// ---------------------------------------------------------------- //
	// -------------------- Script Reconstruction -------------------- //
	// ---------------------------------------------------------------- //

	/**
	 * Reconstructs the full {@code .dlb} script content for the given draft dialogue by
	 * concatenating the header and body of each node (ordered by creation time) in the standard
	 * Dialogue Branch node format.
	 *
	 * @param dialogue the draft dialogue to reconstruct.
	 * @return the reconstructed {@code .dlb} script as a string.
	 */
	public String reconstructScript(DBDraftDialogue dialogue) {
		List<DBDraftNode> nodes = nodeRepository.findByDraftDialogueOrderByCreatedAt(dialogue);
		StringBuilder script = new StringBuilder();
		for (DBDraftNode node : nodes) {
			script.append(node.getHeader()).append("\n");
			script.append("---\n");
			script.append(node.getBody()).append("\n");
			script.append("===\n");
		}
		return script.toString();
	}

}

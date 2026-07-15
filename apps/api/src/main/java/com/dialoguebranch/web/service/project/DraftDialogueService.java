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

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.i18n.SourceTranslatable;
import com.dialoguebranch.i18n.Translatable;
import com.dialoguebranch.i18n.TranslatableExtractor;
import com.dialoguebranch.i18n.Translator;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.web.service.controller.schema.authoring.TranslatableTermSummary;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.exception.ProjectParseHttpError;
import com.dialoguebranch.web.service.repository.DBDraftDialogueRepository;
import com.dialoguebranch.web.service.repository.DBDraftNodeRepository;
import com.dialoguebranch.web.service.repository.DBDraftTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftNode;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing draft dialogues, their nodes, and their translations. All edits made
 * through this service affect the draft layer only; changes are not visible to the execution
 * engine until a project is published via {@link PublishService}.
 *
 * @author Harm op den Akker
 */
@Service
public class DraftDialogueService {

	private static final Logger logger = LoggerFactory.getLogger(DraftDialogueService.class);

	/** Matches a {@code [[...]]} reply bracket; group 1 is everything between the brackets. */
	private static final Pattern REPLY_PATTERN = Pattern.compile("\\[\\[(.*?)]]");

	private final DBDraftDialogueRepository dialogueRepository;
	private final DBDraftNodeRepository nodeRepository;
	private final DBDraftTranslationRepository translationRepository;

	/**
	 * Creates a new {@link DraftDialogueService}.
	 *
	 * @param dialogueRepository    repository used to read and persist draft dialogues.
	 * @param nodeRepository        repository used to read and persist draft nodes.
	 * @param translationRepository repository used to read and persist draft translations.
	 */
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
	 * Creates a new draft dialogue in {@code project} named {@code name}, populated by splitting
	 * the given full {@code .dlb} script {@code content} into per-node header/body pairs
	 * (mirroring the inverse of {@link #reconstructScript}) and creating a {@link DBDraftNode} for
	 * each. Used to seed a project's initial drafts directly from its {@code .dlb} source files
	 * (see {@code ProjectSeedService}), before they're published for the first time.
	 *
	 * @param project the owning project.
	 * @param name    the dialogue name.
	 * @param content the full {@code .dlb} script content to populate the new draft from.
	 * @return the newly created {@link DBDraftDialogue}.
	 */
	@Transactional
	public DBDraftDialogue createDialogueFromScript(DBProject project, String name,
													String content) {
		DBDraftDialogue dialogue = createDialogue(project, name);
		List<String[]> nodeBlocks = splitPublishedScript(content);
		Instant now = Instant.now();
		int index = 0;
		for (String[] block : nodeBlocks) {
			String header = block[0];
			String body = block[1];
			String title = extractHeaderTag(header, "title");
			if (title == null || title.isEmpty()) {
				logger.warn("Skipping a node with no 'title' tag while creating draft dialogue " +
						"'{}' (project '{}') from script content.", name, project.getSlug());
				continue;
			}
			DBDraftNode node = new DBDraftNode();
			node.setDraftDialogue(dialogue);
			node.setTitle(title);
			node.setHeader(header);
			node.setBody(body);
			// Strictly increasing timestamps preserve the source script's node order when nodes
			// are later re-read via findByDraftDialogueOrderByCreatedAt.
			node.setCreatedAt(now.plus(index, ChronoUnit.MILLIS));
			node.setUpdatedAt(now.plus(index, ChronoUnit.MILLIS));
			nodeRepository.save(node);
			index++;
		}
		return dialogue;
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
	 * Persists changes made directly to a {@link DBDraftDialogue} (e.g. its status flags).
	 *
	 * @param dialogue the draft dialogue to save.
	 * @return the saved dialogue.
	 */
	public DBDraftDialogue save(DBDraftDialogue dialogue) {
		return dialogueRepository.save(dialogue);
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
		// A freshly-created dialogue has no published counterpart yet by definition — it only
		// stops being new/changed once it's actually included in a publish (see
		// PublishService#publish).
		dialogue.setIsNew(true);
		dialogue.setIsChanged(true);
		dialogue.setIsDeleted(false);
		return dialogueRepository.save(dialogue);
	}

	/**
	 * Marks the given draft dialogue as pending deletion ({@code isDeleted = true}). This is a
	 * soft delete: the row and its nodes/translations are left untouched, so the deletion can
	 * still be reverted via {@link #restoreDialogue}. It only takes permanent effect — dropping
	 * the dialogue's published counterpart (if any) from the new version, and hard-deleting this
	 * row — the next time the project is published (see {@link PublishService#publish}).
	 *
	 * @param dialogue the draft dialogue to mark for deletion.
	 */
	@Transactional
	public void deleteDialogue(DBDraftDialogue dialogue) {
		dialogue.setIsDeleted(true);
		dialogue.setUpdatedAt(Instant.now());
		dialogueRepository.save(dialogue);
	}

	/**
	 * Reverts a pending deletion previously made via {@link #deleteDialogue}, marking the given
	 * draft dialogue as {@code isDeleted = false} again.
	 *
	 * @param dialogue the draft dialogue to restore.
	 */
	@Transactional
	public void restoreDialogue(DBDraftDialogue dialogue) {
		dialogue.setIsDeleted(false);
		dialogue.setUpdatedAt(Instant.now());
		dialogueRepository.save(dialogue);
	}

	/**
	 * Permanently deletes the given draft dialogue and all its nodes/translations — unlike {@link
	 * #deleteDialogue}, this cannot be undone. Used by {@link PublishService#publish} once a
	 * dialogue's pending deletion has actually taken effect in a new published version.
	 *
	 * @param dialogue the draft dialogue to permanently delete.
	 */
	@Transactional
	public void hardDeleteDialogue(DBDraftDialogue dialogue) {
		translationRepository.deleteAll(translationRepository.findByDraftDialogue(dialogue));
		nodeRepository.deleteAll(nodeRepository.findByDraftDialogueOrderByCreatedAt(dialogue));
		dialogueRepository.delete(dialogue);
	}

	/**
	 * Marks the given draft dialogue as having unpublished changes, and persists it. Called by
	 * every operation that mutates a dialogue's effective content (node create/update/delete/
	 * rename, or a sibling dialogue's rename rewriting one of its references).
	 *
	 * @param dialogue the draft dialogue to mark as changed.
	 */
	private void markChanged(DBDraftDialogue dialogue) {
		dialogue.setIsChanged(true);
		dialogue.setUpdatedAt(Instant.now());
		dialogueRepository.save(dialogue);
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
		DBDraftNode saved = nodeRepository.save(node);
		markChanged(dialogue);
		return saved;
	}

	/**
	 * Updates the header and body of the given node.
	 *
	 * @param dialogue the dialogue {@code node} belongs to (passed explicitly rather than read via
	 *                 {@code node.getDraftDialogue()}, which is a lazy relation that may no longer
	 *                 have an active Hibernate session attached by this point).
	 * @param node     the node to update.
	 * @param header   the new raw header content.
	 * @param body     the new raw body content.
	 * @return the updated {@link DBDraftNode}.
	 */
	public DBDraftNode updateNode(DBDraftDialogue dialogue, DBDraftNode node, String header,
								  String body) {
		node.setHeader(header);
		node.setBody(body);
		node.setUpdatedAt(Instant.now());
		DBDraftNode saved = nodeRepository.save(node);
		markChanged(dialogue);
		return saved;
	}

	/**
	 * Deletes the given draft node.
	 *
	 * @param dialogue the dialogue {@code node} belongs to (passed explicitly — see {@link
	 *                 #updateNode(DBDraftDialogue, DBDraftNode, String, String)} for why).
	 * @param node     the node to delete.
	 */
	public void deleteNode(DBDraftDialogue dialogue, DBDraftNode node) {
		nodeRepository.delete(node);
		markChanged(dialogue);
	}

	/**
	 * Scans every draft dialogue in the given project for {@code [[...]]} reply links whose
	 * target resolves to the given {@code (dialogueName, nodeTitle)} — whether written as a
	 * same-dialogue (internal) bare title, or a cross-dialogue (external)
	 * {@code <dialogueReference>.<nodeTitle>} reference resolved relative to the referencing
	 * node's own dialogue (via {@link ExternalNodePointer}, the same resolution the runtime
	 * parser uses). Used both to preview what a rename would affect, and to warn about dangling
	 * links before a delete (which this method does not itself prevent).
	 *
	 * @param project     the project to scan.
	 * @param dialogueName the name (including path) of the dialogue the target node belongs to.
	 * @param nodeTitle    the title of the target node.
	 * @return every referencing reply found, across all draft dialogues in the project.
	 */
	public List<NodeReference> findNodeReferences(DBProject project, String dialogueName,
												  String nodeTitle) {
		List<NodeReference> references = new ArrayList<>();
		for (DBDraftDialogue dialogue : dialogueRepository.findByProject(project)) {
			for (DBDraftNode node : nodeRepository.findByDraftDialogueOrderByCreatedAt(dialogue)) {
				if (node.getBody() == null) continue;
				Matcher matcher = REPLY_PATTERN.matcher(node.getBody());
				while (matcher.find()) {
					if (replyTargetMatches(matcher.group(1), dialogue.getName(), node.getTitle(),
							dialogueName, nodeTitle)) {
						references.add(new NodeReference(dialogue.getName(), node.getTitle(),
								matcher.group(0)));
					}
				}
			}
		}
		return references;
	}

	/**
	 * Scans every draft dialogue in the given project for {@code [[...]]} reply links whose
	 * external node pointer resolves to the given {@code dialogueName} — regardless of which node
	 * within it they target. Used when renaming a whole dialogue (unlike {@link #findNodeReferences},
	 * which targets one specific node).
	 *
	 * @param project      the project to scan.
	 * @param dialogueName the name (including path) of the dialogue being searched for.
	 * @return every referencing reply found, across all draft dialogues in the project.
	 */
	public List<NodeReference> findDialogueReferences(DBProject project, String dialogueName) {
		List<NodeReference> references = new ArrayList<>();
		for (DBDraftDialogue dialogue : dialogueRepository.findByProject(project)) {
			for (DBDraftNode node : nodeRepository.findByDraftDialogueOrderByCreatedAt(dialogue)) {
				if (node.getBody() == null) continue;
				Matcher matcher = REPLY_PATTERN.matcher(node.getBody());
				while (matcher.find()) {
					if (replyTargetDialogueMatches(matcher.group(1), dialogue.getName(),
							node.getTitle(), dialogueName)) {
						references.add(new NodeReference(dialogue.getName(), node.getTitle(),
								matcher.group(0)));
					}
				}
			}
		}
		return references;
	}

	/**
	 * Renames a draft node: updates its {@code title} column and rewrites the {@code title:} line
	 * in its header. If {@code updateReferences} is {@code true}, every reply link elsewhere in
	 * the project that points to the node's old title (found via {@link
	 * #findNodeReferences(DBProject, String, String)}) is rewritten in place to point to the new
	 * title instead; otherwise those links are left as-is (and will now be dangling).
	 *
	 * @param project           the owning project (needed to scan for references).
	 * @param dialogue          the dialogue the node belongs to.
	 * @param node              the node to rename.
	 * @param newTitle          the new title.
	 * @param updateReferences  whether to rewrite references elsewhere in the project.
	 * @return the renamed node plus how many individual reply links were rewritten (a single
	 *         referencing node may contribute more than one, if it links to the renamed node more
	 *         than once) — matching the count {@link #findNodeReferences} would report.
	 * @throws BadRequestException if {@code newTitle} is not a valid node name.
	 * @throws ConflictException   if a node with {@code newTitle} already exists in the dialogue.
	 */
	@Transactional
	public RenameResult renameNode(DBProject project, DBDraftDialogue dialogue, DBDraftNode node,
								   String newTitle, boolean updateReferences)
			throws BadRequestException, ConflictException {
		if (!newTitle.matches(DialogueBranchParser.NODE_NAME_REGEX)) {
			throw new BadRequestException("Invalid node title: '" + newTitle + "'.");
		}
		String oldTitle = node.getTitle();
		if (!oldTitle.equals(newTitle)
				&& nodeRepository.findByDraftDialogueAndTitle(dialogue, newTitle).isPresent()) {
			throw new ConflictException(
					"A node with title '" + newTitle + "' already exists in this dialogue.");
		}

		int referencesUpdated = 0;
		if (updateReferences) {
			List<NodeReference> references = findNodeReferences(project, dialogue.getName(), oldTitle);
			referencesUpdated = references.size();

			// A single referencing node may appear more than once above (if it links to the
			// renamed node multiple times) — rewrite each referencing node's body exactly once,
			// since rewriteReplyTargets already replaces every matching link within it in one pass.
			List<String> distinctReferencingNodes = new ArrayList<>();
			for (NodeReference reference : references) {
				String key = reference.getDialogueName() + " " + reference.getNodeTitle();
				if (!distinctReferencingNodes.contains(key)) {
					distinctReferencingNodes.add(key);
				}
			}

			for (String key : distinctReferencingNodes) {
				String[] parts = key.split(" ", 2);
				DBDraftDialogue refDialogue = dialogueRepository
						.findByProjectAndName(project, parts[0])
						.orElseThrow(() -> new IllegalStateException(
								"Referencing dialogue disappeared during rename: " + parts[0]));
				DBDraftNode refNode = nodeRepository
						.findByDraftDialogueAndTitle(refDialogue, parts[1])
						.orElseThrow(() -> new IllegalStateException(
								"Referencing node disappeared during rename: " + parts[1]));
				String rewrittenBody = rewriteReplyTargets(refNode.getBody(), refDialogue.getName(),
						refNode.getTitle(), dialogue.getName(), oldTitle, newTitle);
				refNode.setBody(rewrittenBody);
				refNode.setUpdatedAt(Instant.now());
				nodeRepository.save(refNode);
				markChanged(refDialogue);
			}
		}

		node.setTitle(newTitle);
		node.setHeader(rewriteTitleTag(node.getHeader(), newTitle));
		node.setUpdatedAt(Instant.now());
		DBDraftNode renamed = nodeRepository.save(node);
		markChanged(dialogue);

		return new RenameResult(renamed, referencesUpdated);
	}

	/**
	 * Renames a draft dialogue: updates its {@code name} column. If it has a published counterpart
	 * (i.e. it isn't {@link DBDraftDialogue#getIsNew() new}) and hasn't already been renamed since
	 * its last publish, its current name is remembered in
	 * {@link DBDraftDialogue#getPreviousPublishedName()} — this is how the dialogue list knows to
	 * match this draft against its stale published entry instead of showing both, and how a later
	 * rename in the same unpublished chain (e.g. {@code A -> B -> C}) keeps remembering the
	 * original published name ({@code A}) rather than the intermediate one.
	 *
	 * <p>If {@code updateReferences} is {@code true}, every reply link elsewhere in the project
	 * that points into this dialogue (found via {@link #findDialogueReferences}) is rewritten in
	 * place to point at the new name instead; otherwise those links are left as-is (and will now be
	 * dangling). Rewritten references are always written as an explicit {@code ./<name>} absolute
	 * reference (see {@code ExternalNodePointer.getAbsoluteDialogueId}) rather than reconstructing
	 * whatever relative form the author originally used, since that form is always unambiguous
	 * regardless of the referencing dialogue's own folder.</p>
	 *
	 * @param project          the owning project.
	 * @param dialogue         the dialogue to rename.
	 * @param newName          the new dialogue name.
	 * @param updateReferences whether to rewrite references elsewhere in the project.
	 * @return the renamed dialogue plus how many individual reply links were rewritten.
	 * @throws BadRequestException if {@code newName} is not a valid dialogue name.
	 * @throws ConflictException   if a dialogue with {@code newName} already exists in the project.
	 */
	@Transactional
	public DialogueRenameResult renameDialogue(DBProject project, DBDraftDialogue dialogue,
											   String newName, boolean updateReferences)
			throws BadRequestException, ConflictException {
		if (!newName.matches(DialogueBranchParser.DIALOGUE_NAME_REGEX)) {
			throw new BadRequestException("Invalid dialogue name: '" + newName + "'.");
		}
		String oldName = dialogue.getName();
		if (!oldName.equals(newName)
				&& dialogueRepository.findByProjectAndName(project, newName).isPresent()) {
			throw new ConflictException(
					"A dialogue named '" + newName + "' already exists in this project.");
		}

		int referencesUpdated = 0;
		if (updateReferences) {
			List<NodeReference> references = findDialogueReferences(project, oldName);
			referencesUpdated = references.size();

			// A single referencing node may appear more than once above (if it links into the
			// renamed dialogue multiple times) — rewrite each referencing node's body exactly once.
			List<String> distinctReferencingNodes = new ArrayList<>();
			for (NodeReference reference : references) {
				String key = reference.getDialogueName() + " " + reference.getNodeTitle();
				if (!distinctReferencingNodes.contains(key)) {
					distinctReferencingNodes.add(key);
				}
			}

			for (String key : distinctReferencingNodes) {
				String[] parts = key.split(" ", 2);
				DBDraftDialogue refDialogue = dialogueRepository
						.findByProjectAndName(project, parts[0])
						.orElseThrow(() -> new IllegalStateException(
								"Referencing dialogue disappeared during rename: " + parts[0]));
				DBDraftNode refNode = nodeRepository
						.findByDraftDialogueAndTitle(refDialogue, parts[1])
						.orElseThrow(() -> new IllegalStateException(
								"Referencing node disappeared during rename: " + parts[1]));
				String rewrittenBody = rewriteReplyDialogueTargets(refNode.getBody(),
						refDialogue.getName(), refNode.getTitle(), oldName, newName);
				refNode.setBody(rewrittenBody);
				refNode.setUpdatedAt(Instant.now());
				nodeRepository.save(refNode);
				markChanged(refDialogue);
			}
		}

		if (!dialogue.getIsNew() && dialogue.getPreviousPublishedName() == null) {
			dialogue.setPreviousPublishedName(oldName);
		}
		dialogue.setName(newName);
		markChanged(dialogue);
		DBDraftDialogue renamed = dialogueRepository.save(dialogue);

		return new DialogueRenameResult(renamed, referencesUpdated);
	}

	/**
	 * Determines whether the target of a single {@code [[...]]} reply (given the text between its
	 * brackets) resolves to {@code (targetDialogueName, targetNodeTitle)}, as seen from the given
	 * origin dialogue/node the reply is written in. Mirrors the same target-token conventions as
	 * {@code ReplyParser} in {@code packages/core}: a bare {@code NODE_NAME_REGEX} token is an
	 * internal (same-dialogue) pointer; a {@code <dialogueRef>.<nodeTitle>} token is external and
	 * resolved via {@link ExternalNodePointer}.
	 *
	 * @param bracketInnerText  the text between the {@code [[} and {@code ]]} of one reply.
	 * @param originDialogueName the name of the dialogue the reply is written in.
	 * @param originNodeTitle    the title of the node the reply is written in.
	 * @param targetDialogueName the dialogue name being searched for.
	 * @param targetNodeTitle    the node title being searched for.
	 * @return whether this reply's target resolves to the given dialogue/node.
	 */
	private boolean replyTargetMatches(String bracketInnerText, String originDialogueName,
									   String originNodeTitle, String targetDialogueName,
									   String targetNodeTitle) {
		String token = replyTargetToken(bracketInnerText);
		if (token == null) return false;

		if (token.matches(DialogueBranchParser.NODE_NAME_REGEX)) {
			return originDialogueName.equals(targetDialogueName) && token.equals(targetNodeTitle);
		}
		if (token.matches(DialogueBranchParser.EXTERNAL_NODE_POINTER_REGEX)) {
			int sep = token.lastIndexOf('.');
			String dialogueRef = token.substring(0, sep);
			String title = token.substring(sep + 1);
			if (!title.equals(targetNodeTitle)) return false;
			try {
				ExternalNodePointer pointer = new ExternalNodePointer(originDialogueName,
						originNodeTitle, dialogueRef, title);
				return pointer.getAbsoluteTargetDialogue().equals(targetDialogueName);
			} catch (ParseException e) {
				return false;
			}
		}
		return false;
	}

	/**
	 * Determines whether the target dialogue of a single {@code [[...]]} reply (given the text
	 * between its brackets) resolves to {@code targetDialogueName} — regardless of which node
	 * within it is targeted. A bare {@code NODE_NAME_REGEX} token is always an internal
	 * (same-dialogue) pointer and thus never a reference to another dialogue.
	 *
	 * @param bracketInnerText   the text between the {@code [[} and {@code ]]} of one reply.
	 * @param originDialogueName the name of the dialogue the reply is written in.
	 * @param originNodeTitle    the title of the node the reply is written in.
	 * @param targetDialogueName the dialogue name being searched for.
	 * @return whether this reply's target dialogue resolves to {@code targetDialogueName}.
	 */
	private boolean replyTargetDialogueMatches(String bracketInnerText, String originDialogueName,
											   String originNodeTitle, String targetDialogueName) {
		String token = replyTargetToken(bracketInnerText);
		if (token == null || token.matches(DialogueBranchParser.NODE_NAME_REGEX)) return false;
		if (!token.matches(DialogueBranchParser.EXTERNAL_NODE_POINTER_REGEX)) return false;

		int sep = token.lastIndexOf('.');
		String dialogueRef = token.substring(0, sep);
		String title = token.substring(sep + 1);
		try {
			ExternalNodePointer pointer = new ExternalNodePointer(originDialogueName,
					originNodeTitle, dialogueRef, title);
			return pointer.getAbsoluteTargetDialogue().equals(targetDialogueName);
		} catch (ParseException e) {
			return false;
		}
	}

	/**
	 * Extracts the reply's target token (the node-pointer segment) from the text between a
	 * {@code [[...]]} reply's brackets: a bare token for a 1-segment reply (e.g. {@code
	 * [[TargetNode]]}), or the second {@code |}-separated segment for 2- or 3-segment replies
	 * (e.g. {@code [[Reply text|TargetNode]]} or {@code [[Reply text|TargetNode|<<command>>]]}).
	 *
	 * @param bracketInnerText the text between the {@code [[} and {@code ]]} of one reply.
	 * @return the trimmed target token, or {@code null} if none could be determined.
	 */
	private String replyTargetToken(String bracketInnerText) {
		String[] parts = bracketInnerText.split("\\|", -1);
		if (parts.length == 0) return null;
		String token = (parts.length == 1 ? parts[0] : parts[1]).trim();
		return token.isEmpty() ? null : token;
	}

	/**
	 * Rewrites every {@code [[...]]} reply in {@code body} whose target resolves to {@code
	 * (targetDialogueName, oldTitle)} so that it points to {@code newTitle} instead, leaving every
	 * other reply untouched. Only the target token itself is changed — for an internal pointer the
	 * whole token becomes {@code newTitle}; for an external pointer only the portion after the last
	 * {@code .} is changed, preserving the dialogue-reference prefix.
	 *
	 * @param body               the raw body script to rewrite.
	 * @param originDialogueName the name of the dialogue {@code body} belongs to.
	 * @param originNodeTitle    the title of the node {@code body} belongs to.
	 * @param targetDialogueName the dialogue name of the node being renamed.
	 * @param oldTitle           the old title of the node being renamed.
	 * @param newTitle           the new title of the node being renamed.
	 * @return the rewritten body.
	 */
	private String rewriteReplyTargets(String body, String originDialogueName,
									   String originNodeTitle, String targetDialogueName,
									   String oldTitle, String newTitle) {
		if (body == null) return null;
		Matcher matcher = REPLY_PATTERN.matcher(body);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String inner = matcher.group(1);
			String replacement = matcher.group(0);
			if (replyTargetMatches(inner, originDialogueName, originNodeTitle, targetDialogueName,
					oldTitle)) {
				String[] parts = inner.split("\\|", -1);
				int targetIndex = parts.length == 1 ? 0 : 1;
				String token = parts[targetIndex].trim();
				if (token.matches(DialogueBranchParser.NODE_NAME_REGEX)) {
					parts[targetIndex] = newTitle;
				} else {
					int sep = token.lastIndexOf('.');
					parts[targetIndex] = token.substring(0, sep + 1) + newTitle;
				}
				replacement = "[[" + String.join("|", parts) + "]]";
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * Rewrites every {@code [[...]]} reply in {@code body} whose target dialogue resolves to
	 * {@code oldDialogueName} so that it points at {@code newDialogueName} instead — regardless of
	 * which node within it was targeted — leaving every other reply, and the node-title portion of
	 * the token, untouched. The rewritten dialogue reference is always written as an explicit
	 * {@code ./<name>} absolute reference (see {@code ExternalNodePointer.getAbsoluteDialogueId}),
	 * which resolves correctly regardless of the referencing dialogue's own folder — rather than
	 * reconstructing whatever relative form ({@code ../}, bare, etc.) the author originally used.
	 *
	 * @param body               the raw body script to rewrite.
	 * @param originDialogueName the name of the dialogue {@code body} belongs to.
	 * @param originNodeTitle    the title of the node {@code body} belongs to.
	 * @param oldDialogueName    the old name of the dialogue being renamed.
	 * @param newDialogueName    the new name of the dialogue being renamed.
	 * @return the rewritten body.
	 */
	private String rewriteReplyDialogueTargets(String body, String originDialogueName,
											   String originNodeTitle, String oldDialogueName,
											   String newDialogueName) {
		if (body == null) return null;
		Matcher matcher = REPLY_PATTERN.matcher(body);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String inner = matcher.group(1);
			String replacement = matcher.group(0);
			if (replyTargetDialogueMatches(inner, originDialogueName, originNodeTitle,
					oldDialogueName)) {
				String[] parts = inner.split("\\|", -1);
				int targetIndex = parts.length == 1 ? 0 : 1;
				String token = parts[targetIndex].trim();
				int sep = token.lastIndexOf('.');
				String title = token.substring(sep + 1);
				parts[targetIndex] = "." + DialogueBranchConstants.DLB_PATH_SEPARATOR
						+ newDialogueName + "." + title;
				replacement = "[[" + String.join("|", parts) + "]]";
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * Rewrites the {@code title:} line of a raw header string to the given new title, mirroring
	 * {@code EditableHeaderParser}'s line-parsing semantics (first {@code :} splits key/value,
	 * first occurrence of a key wins) just for the {@code title} tag specifically. If no existing
	 * {@code title:} line is found, one is prepended.
	 *
	 * @param header   the raw header string to rewrite.
	 * @param newTitle the new title.
	 * @return the rewritten header.
	 */
	private String rewriteTitleTag(String header, String newTitle) {
		String[] lines = header == null ? new String[0] : header.split("\n", -1);
		boolean found = false;
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String withoutComment = line.contains("//")
					? line.substring(0, line.indexOf("//")) : line;
			int sep = withoutComment.indexOf(':');
			String key = sep >= 0 ? withoutComment.substring(0, sep).trim() : null;
			if (!found && "title".equals(key)) {
				result.append("title: ").append(newTitle);
				found = true;
			} else {
				result.append(line);
			}
			if (i < lines.length - 1) result.append("\n");
		}
		if (!found) {
			String titleLine = "title: " + newTitle;
			return (header == null || header.isEmpty()) ? titleLine : titleLine + "\n" + header;
		}
		return result.toString();
	}

	/**
	 * A single reply link, found somewhere in the project, that references a particular node.
	 */
	public static class NodeReference {

		private final String dialogueName;
		private final String nodeTitle;
		private final String snippet;

		/**
		 * Creates a new {@link NodeReference}.
		 *
		 * @param dialogueName the name of the dialogue containing the referencing node.
		 * @param nodeTitle    the title of the node that contains the reference.
		 * @param snippet      the raw {@code [[...]]} text of the referencing reply.
		 */
		public NodeReference(String dialogueName, String nodeTitle, String snippet) {
			this.dialogueName = dialogueName;
			this.nodeTitle = nodeTitle;
			this.snippet = snippet;
		}

		/** The name of the dialogue containing the referencing node. */
		public String getDialogueName() {
			return dialogueName;
		}

		/** The title of the node that contains the reference. */
		public String getNodeTitle() {
			return nodeTitle;
		}

		/** The raw {@code [[...]]} text of the referencing reply. */
		public String getSnippet() {
			return snippet;
		}

	}

	/**
	 * The outcome of a {@link #renameNode(DBProject, DBDraftDialogue, DBDraftNode, String,
	 * boolean)} call.
	 */
	public static class RenameResult {

		private final DBDraftNode node;
		private final int referencesUpdated;

		/**
		 * Creates a new {@link RenameResult}.
		 *
		 * @param node              the renamed node.
		 * @param referencesUpdated how many individual reply links elsewhere in the project were
		 *                          rewritten (0 if not requested).
		 */
		public RenameResult(DBDraftNode node, int referencesUpdated) {
			this.node = node;
			this.referencesUpdated = referencesUpdated;
		}

		/** The renamed node. */
		public DBDraftNode getNode() {
			return node;
		}

		/**
		 * How many individual reply links elsewhere in the project were rewritten (0 if not
		 * requested) — one referencing node can contribute more than one, matching the count
		 * {@link #findNodeReferences(DBProject, String, String)} would report for it.
		 */
		public int getReferencesUpdated() {
			return referencesUpdated;
		}

	}

	/**
	 * The outcome of a {@link #renameDialogue(DBProject, DBDraftDialogue, String, boolean)} call.
	 */
	public static class DialogueRenameResult {

		private final DBDraftDialogue dialogue;
		private final int referencesUpdated;

		/**
		 * Creates a new {@link DialogueRenameResult}.
		 *
		 * @param dialogue          the renamed dialogue.
		 * @param referencesUpdated how many individual reply links elsewhere in the project were
		 *                          rewritten (0 if not requested).
		 */
		public DialogueRenameResult(DBDraftDialogue dialogue, int referencesUpdated) {
			this.dialogue = dialogue;
			this.referencesUpdated = referencesUpdated;
		}

		/** The renamed dialogue. */
		public DBDraftDialogue getDialogue() {
			return dialogue;
		}

		/**
		 * How many individual reply links elsewhere in the project were rewritten (0 if not
		 * requested) — one referencing node can contribute more than one, matching the count
		 * {@link #findDialogueReferences(DBProject, String)} would report for it.
		 */
		public int getReferencesUpdated() {
			return referencesUpdated;
		}

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
		DBDraftTranslation saved = translationRepository.save(translation);
		markChanged(dialogue);
		return saved;
	}

	/**
	 * Deletes the given draft translation.
	 *
	 * @param dialogue    the dialogue {@code translation} belongs to (passed explicitly rather
	 *                    than read via a lazy relation — see {@link #updateNode(DBDraftDialogue,
	 *                    DBDraftNode, String, String)} for why).
	 * @param translation the translation to delete.
	 */
	public void deleteTranslation(DBDraftDialogue dialogue, DBDraftTranslation translation) {
		translationRepository.delete(translation);
		markChanged(dialogue);
	}

	/**
	 * Extracts every translatable term from the given draft dialogue's current content,
	 * de-duplicated by {@code (speaker, term)} — the same key pair a translation file's content map
	 * uses (see {@code TranslationFile} in {@code packages/core}) — so the result can be used
	 * directly to look up or store a translation for each term.
	 *
	 * <p>Each term is {@link Translatable#toNormalizedString() whitespace-normalized}, not just
	 * trimmed — the same canonicalization {@link Translator} applies on both sides of its
	 * translation lookup (the dialogue's source text and the translation file's term keys), so a
	 * term whose source text spans multiple script lines (i.e. contains internal line breaks)
	 * still matches its stored translation instead of always appearing untranslated.</p>
	 *
	 * <p>Only this one dialogue's own content is parsed (via {@link DialogueBranchParser}, not the
	 * full-project {@code ProjectParser} pipeline {@code DraftExecutionService} uses to start a
	 * test session) since translatable-term extraction only needs this dialogue's own node bodies,
	 * not cross-dialogue reply-link resolution.</p>
	 *
	 * @param dialogue the draft dialogue to extract translatable terms from.
	 * @return the dialogue's translatable terms, in the order they first appear.
	 * @throws BadRequestException if the dialogue's current draft content does not currently parse.
	 */
	public List<TranslatableTermSummary> listTranslatableTerms(DBDraftDialogue dialogue)
			throws BadRequestException {
		String script = reconstructScript(dialogue);
		ParserResult parserResult;
		try (DialogueBranchParser parser =
					 new DialogueBranchParser(dialogue.getName(), new StringReader(script))) {
			parserResult = parser.readDialogue();
		} catch (IOException e) {
			throw new BadRequestException(
					"Failed to read draft dialogue content: " + e.getMessage());
		}
		if (!parserResult.getParseErrors().isEmpty()) {
			List<String> messages = parserResult.getParseErrors().stream()
					.map(Throwable::getMessage).toList();
			throw new BadRequestException(new ProjectParseHttpError("Dialogue '" +
					dialogue.getName() + "' contains errors, preventing translation.",
					Map.of(dialogue.getName(), messages)));
		}

		TranslatableExtractor extractor = new TranslatableExtractor();
		Set<String> seen = new LinkedHashSet<>();
		List<TranslatableTermSummary> terms = new ArrayList<>();
		for (Node node : parserResult.getDialogue().getNodes()) {
			for (SourceTranslatable sourceTranslatable : extractor.extractFromNode(node)) {
				String term = sourceTranslatable.translatable().toNormalizedString();
				if (term.isEmpty()) continue;
				String key = sourceTranslatable.speaker() + " " + term;
				if (seen.add(key)) {
					terms.add(new TranslatableTermSummary(sourceTranslatable.speaker(), term));
				}
			}
		}
		return terms;
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
			script.append(DialogueBranchConstants.DLB_HEADER_SEPARATOR).append("\n");
			script.append(node.getBody()).append("\n");
			script.append(DialogueBranchConstants.DLB_NODE_SEPARATOR).append("\n");
		}
		return script.toString();
	}

	/**
	 * Splits a full {@code .dlb} script (as produced by {@link #reconstructScript}, i.e. the
	 * {@code content} of a {@link DBPublishedDialogue}) back into its per-node {@code {header,
	 * body}} pairs, mirroring the exact {@code "{header}\n---\n{body}\n===\n"} format that method
	 * writes.
	 *
	 * @param content the full script content to split.
	 * @return a list of {@code [header, body]} pairs, one per node, in their original order.
	 */
	private List<String[]> splitPublishedScript(String content) {
		List<String[]> blocks = new ArrayList<>();
		if (content == null) return blocks;
		String nodeSeparator = "\n" + DialogueBranchConstants.DLB_NODE_SEPARATOR + "\n";
		String headerSeparator = "\n" + DialogueBranchConstants.DLB_HEADER_SEPARATOR + "\n";
		for (String rawNode : content.split(Pattern.quote(nodeSeparator))) {
			if (rawNode.isBlank()) continue;
			int sep = rawNode.indexOf(headerSeparator);
			if (sep < 0) {
				logger.warn("Skipping an unparseable node block (no '{}' separator found) while " +
						"splitting a published dialogue script.",
						DialogueBranchConstants.DLB_HEADER_SEPARATOR);
				continue;
			}
			String header = rawNode.substring(0, sep);
			String body = rawNode.substring(sep + headerSeparator.length());
			blocks.add(new String[] { header, body });
		}
		return blocks;
	}

	/**
	 * Extracts the value of a single header tag from a raw header string, mirroring {@code
	 * EditableHeaderParser}'s line-parsing semantics (first {@code :} splits key/value, trailing
	 * {@code //} comment stripped, first occurrence of a duplicate key wins) — the same convention
	 * already used by {@link #rewriteTitleTag}.
	 *
	 * @param header the raw header string to search.
	 * @param key    the tag key to look for.
	 * @return the trimmed tag value, or {@code null} if not found.
	 */
	private String extractHeaderTag(String header, String key) {
		if (header == null) return null;
		for (String line : header.split("\n")) {
			String withoutComment = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
			int sep = withoutComment.indexOf(':');
			if (sep < 0) continue;
			if (key.equals(withoutComment.substring(0, sep).trim())) {
				return withoutComment.substring(sep + 1).trim();
			}
		}
		return null;
	}

}

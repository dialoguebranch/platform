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

package com.dialoguebranch.web.service.project;

import com.dialoguebranch.model.execute.NodeHeader;
import com.dialoguebranch.web.service.repository.DBDraftTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBLoggedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
import com.dialoguebranch.web.service.repository.DBVariableHistoryRepository;
import com.dialoguebranch.web.service.repository.DBVariableRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing {@link DBProject} records — creation, retrieval, update and deletion of
 * projects. A project always has exactly one source language (set at creation time, stored
 * directly on {@link DBProject}); its display name/description and translation languages are
 * draft → publish cycled — see {@link DraftProjectService} for the editable draft copy and
 * {@link PublishService} for how a publish snapshots it onto a new {@link DBProjectVersion}.
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectService {

	private static final String DEFAULT_DIALOGUE_NAME = "default";
	private static final String DEFAULT_NODE_TITLE = "Start";
	private static final String DEFAULT_NODE_SPEAKER = "Agent";
	private static final String DEFAULT_NODE_BODY = "Start your dialogue here...";

	private final DBProjectRepository projectRepository;
	private final DBDraftTranslationLanguageRepository draftTranslationLanguageRepository;
	private final DBProjectVersionRepository projectVersionRepository;
	private final DBPublishedDialogueRepository publishedDialogueRepository;
	private final DBPublishedTranslationRepository publishedTranslationRepository;
	private final DBPublishedTranslationLanguageRepository publishedTranslationLanguageRepository;
	private final DraftDialogueService draftDialogueService;
	private final DBVariableRepository variableRepository;
	private final DBVariableHistoryRepository variableHistoryRepository;
	private final DBLoggedDialogueRepository loggedDialogueRepository;

	/**
	 * Creates a new {@link ProjectService}.
	 *
	 * @param projectRepository                repository used to read and persist project records.
	 * @param draftTranslationLanguageRepository repository used to delete a project's draft
	 *                                         translation languages when the project itself is
	 *                                         deleted.
	 * @param projectVersionRepository         repository used to read and delete a project's
	 *                                         published versions.
	 * @param publishedDialogueRepository      repository used to read and delete a project
	 *                                         version's published dialogues.
	 * @param publishedTranslationRepository   repository used to read and delete a published
	 *                                         dialogue's translations.
	 * @param publishedTranslationLanguageRepository repository used to read and delete a project
	 *                                         version's published translation languages.
	 * @param draftDialogueService             service used to create a project's starter draft
	 *                                         dialogue and to delete all of a project's draft
	 *                                         dialogues when the project itself is deleted.
	 * @param variableRepository               repository used to delete a deleted project's users'
	 *                                         variables, when asked to.
	 * @param variableHistoryRepository        repository used to delete a deleted project's users'
	 *                                         variable history, when asked to.
	 * @param loggedDialogueRepository         repository used to delete a deleted project's users'
	 *                                         dialogue logs when asked to, or to move them to its
	 *                                         renamed slug.
	 */
	public ProjectService(DBProjectRepository projectRepository,
						  DBDraftTranslationLanguageRepository draftTranslationLanguageRepository,
						  DBProjectVersionRepository projectVersionRepository,
						  DBPublishedDialogueRepository publishedDialogueRepository,
						  DBPublishedTranslationRepository publishedTranslationRepository,
						  DBPublishedTranslationLanguageRepository publishedTranslationLanguageRepository,
						  DraftDialogueService draftDialogueService,
						  DBVariableRepository variableRepository,
						  DBVariableHistoryRepository variableHistoryRepository,
						  DBLoggedDialogueRepository loggedDialogueRepository) {
		this.projectRepository = projectRepository;
		this.draftTranslationLanguageRepository = draftTranslationLanguageRepository;
		this.projectVersionRepository = projectVersionRepository;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
		this.publishedTranslationLanguageRepository = publishedTranslationLanguageRepository;
		this.draftDialogueService = draftDialogueService;
		this.variableRepository = variableRepository;
		this.variableHistoryRepository = variableHistoryRepository;
		this.loggedDialogueRepository = loggedDialogueRepository;
	}

	// ------------------------------------------------------------ //
	// -------------------- Project Management -------------------- //
	// ------------------------------------------------------------ //

	/**
	 * Returns all projects that weren't deleted (#315).
	 *
	 * @return list of all {@link DBProject}s that weren't deleted.
	 */
	public List<DBProject> listProjects() {
		return projectRepository.findByIsDeletedFalse();
	}

	/**
	 * Returns the project with the given {@code slug}, or {@link Optional#empty()} if not found or
	 * deleted (#315): a deleted project is never found by slug, so every project end-point treats
	 * it as not found.
	 *
	 * @param slug the unique project slug.
	 * @return the matching project, or empty.
	 */
	public Optional<DBProject> findBySlug(String slug) {
		return projectRepository.findBySlugAndIsDeletedFalse(slug);
	}

	/**
	 * Returns the project with the given {@code id}, or {@link Optional#empty()} if not found.
	 *
	 * @param id the project UUID.
	 * @return the matching project, or empty.
	 */
	public Optional<DBProject> findById(UUID id) {
		return projectRepository.findById(id);
	}

	/**
	 * Creates and persists a new project with the given attributes and source language. Does not
	 * create any dialogue content — used both by seed projects (which define their own dialogues
	 * from source files) and by {@link #createProjectWithStarterDialogue} as its base step.
	 *
	 * @param slug               the unique project slug.
	 * @param displayName        the human-readable display name.
	 * @param description        the project description.
	 * @param sourceLanguageCode the ISO code of the project's source language.
	 * @param sourceLanguageName the human-readable name of the project's source language.
	 * @return the newly created {@link DBProject}.
	 */
	public DBProject createProject(String slug, String displayName, String description,
									String sourceLanguageCode, String sourceLanguageName) {
		Instant now = Instant.now();
		DBProject project = new DBProject();
		project.setSlug(slug);
		// The project has no published snapshot yet until its first publish — only the draft copy
		// is seeded here, with the values provided at creation.
		project.setDraftDisplayName(displayName);
		project.setDraftDescription(description);
		project.setSourceLanguageCode(sourceLanguageCode);
		project.setSourceLanguageName(sourceLanguageName);
		project.setCreatedAt(now);
		project.setUpdatedAt(now);
		return projectRepository.save(project);
	}

	/**
	 * Creates and persists a new project exactly as {@link #createProject}, and additionally
	 * creates a starter draft dialogue named {@code "default"} with a single {@code "Start"} node,
	 * so every new project authored through the API has something to run immediately. Used only by
	 * the {@code /create-project} endpoint (the "Create New Project" wizard flow).
	 *
	 * @param slug               the unique project slug.
	 * @param displayName        the human-readable display name.
	 * @param description        the project description.
	 * @param sourceLanguageCode the ISO code of the project's source language.
	 * @param sourceLanguageName the human-readable name of the project's source language.
	 * @return the newly created {@link DBProject}.
	 */
	@Transactional
	public DBProject createProjectWithStarterDialogue(String slug, String displayName,
			String description, String sourceLanguageCode, String sourceLanguageName) {
		DBProject project = createProject(slug, displayName, description,
				sourceLanguageCode, sourceLanguageName);

		DBDraftDialogue dialogue = draftDialogueService.createDialogue(project, DEFAULT_DIALOGUE_NAME);
		NodeHeader header = new NodeHeader(DEFAULT_NODE_TITLE, DEFAULT_NODE_SPEAKER);
		draftDialogueService.createNode(dialogue, DEFAULT_NODE_TITLE, header.toString(), DEFAULT_NODE_BODY);

		return project;
	}

	/**
	 * Saves the given project, persisting any changes made to it.
	 *
	 * @param project the project to save.
	 * @return the saved {@link DBProject}.
	 */
	public DBProject save(DBProject project) {
		return projectRepository.save(project);
	}

	/**
	 * The outcome of {@link #deleteProject}.
	 *
	 * @param removed     {@code true} if the project was removed for good, with all its users'
	 *                    data; {@code false} if it is kept as a deleted project.
	 * @param deletedSlug the slug the deleted project got, or {@code null} if it was removed.
	 */
	public record DeletedProject(boolean removed, String deletedSlug) {
	}

	/**
	 * Deletes the given project (#315): the definition of its dialogues, that is its draft
	 * dialogues (with their nodes and translations), translation-language registry and published
	 * versions (with their dialogues, translations and translation languages).
	 *
	 * <p>Its users' data, their variables and variable history and their dialogue logs, is kept
	 * unless asked otherwise. The project row then stays, marked as deleted, so that data keeps
	 * referring to it; its slug is renamed to {@code <slug>__deleted} (or {@code
	 * <slug>__deleted-2}, …), and its logs (which refer to a project by slug) are moved along, so
	 * the original slug is free for a new project that can't inherit any of it. Only when both
	 * kinds of user data are deleted too is nothing left referring to the project, and its row is
	 * removed for good.</p>
	 *
	 * <p>None of these relationships cascade at the database or JPA level, and
	 * {@code latest_version_id} is a circular reference back into a table that references the
	 * project, so children are deleted (and that reference nulled out) first.</p>
	 *
	 * @param project         the project to delete.
	 * @param deleteVariables whether to also delete all users' variables and variable history in
	 *                        the project.
	 * @param deleteLogs      whether to also delete all users' dialogue logs of the project.
	 * @return whether the project was removed for good, or the slug it was kept under.
	 */
	@Transactional
	public DeletedProject deleteProject(DBProject project, boolean deleteVariables,
			boolean deleteLogs) {
		// Work on a managed copy: the caller's instance may have been loaded by an earlier,
		// completed query, and saving it below would merge its stale, eagerly fetched collections
		// (such as the translation languages deleted here). See PublishService#publish.
		UUID projectId = project.getId();
		project = projectRepository.findById(projectId)
				.orElseThrow(() -> new IllegalStateException("Project disappeared: " + projectId));

		// Break the circular reference back into this project before deleting anything.
		project.setLatestVersion(null);
		projectRepository.save(project);

		// Whole-project deletion is final, so every draft dialogue must actually be removed here
		// (deleteDialogue is a revertible soft-delete, which would leave rows behind referencing
		// this project and violate the foreign key when the project row itself is deleted below).
		for (DBDraftDialogue dialogue : draftDialogueService.listDialogues(project)) {
			draftDialogueService.hardDeleteDialogue(dialogue);
		}

		for (DBProjectVersion version :
				projectVersionRepository.findByProjectOrderByVersionNumberDesc(project)) {
			for (DBPublishedDialogue publishedDialogue :
					publishedDialogueRepository.findByVersion(version)) {
				publishedTranslationRepository.deleteAll(
						publishedTranslationRepository.findByPublishedDialogue(publishedDialogue));
			}
			publishedDialogueRepository.deleteAll(publishedDialogueRepository.findByVersion(version));
			// Each version owns its own published translation-language snapshot — deleted here
			// alongside that version's dialogues/translations, rather than as a separate
			// whole-project step (there is no more single, project-wide registry to clean up).
			publishedTranslationLanguageRepository.deleteAll(
					publishedTranslationLanguageRepository.findByVersion(version));
			projectVersionRepository.delete(version);
		}

		// Only once nothing references them anymore: draft_translations (deleted above via
		// hardDeleteDialogue) hold a NOT NULL foreign key into this registry.
		draftTranslationLanguageRepository.deleteAll(
				draftTranslationLanguageRepository.findByProject(project));
		// The project's (eagerly fetched) view of them must not keep pointing at deleted rows, or
		// saving the project below would try to re-resolve them.
		project.getDraftTranslationLanguages().clear();

		String slug = project.getSlug();
		if (deleteVariables) {
			variableHistoryRepository.deleteByProject(project);
			variableRepository.deleteByProject(project);
		}
		if (deleteLogs) {
			loggedDialogueRepository.deleteByProjectSlug(slug);
		}
		if (deleteVariables && deleteLogs) {
			projectRepository.delete(project);
			return new DeletedProject(true, null);
		}

		String deletedSlug = freeDeletedSlug(slug);
		if (!deleteLogs) loggedDialogueRepository.renameProjectSlug(slug, deletedSlug);
		Instant now = Instant.now();
		project.setSlug(deletedSlug);
		project.setIsDeleted(true);
		project.setDeletedAt(now);
		project.setUpdatedAt(now);
		projectRepository.save(project);
		return new DeletedProject(false, deletedSlug);
	}

	/**
	 * The first of {@code <slug>__deleted}, {@code <slug>__deleted-2}, … that no project uses.
	 */
	private String freeDeletedSlug(String slug) {
		String candidate = slug + "__deleted";
		for (int n = 2; projectRepository.findBySlug(candidate).isPresent(); n++) {
			candidate = slug + "__deleted-" + n;
		}
		return candidate;
	}

}

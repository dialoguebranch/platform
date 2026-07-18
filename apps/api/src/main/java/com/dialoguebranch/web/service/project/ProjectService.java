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

import com.dialoguebranch.model.execute.NodeHeader;
import com.dialoguebranch.web.service.repository.DBDraftTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationLanguageRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
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
	 */
	public ProjectService(DBProjectRepository projectRepository,
						  DBDraftTranslationLanguageRepository draftTranslationLanguageRepository,
						  DBProjectVersionRepository projectVersionRepository,
						  DBPublishedDialogueRepository publishedDialogueRepository,
						  DBPublishedTranslationRepository publishedTranslationRepository,
						  DBPublishedTranslationLanguageRepository publishedTranslationLanguageRepository,
						  DraftDialogueService draftDialogueService) {
		this.projectRepository = projectRepository;
		this.draftTranslationLanguageRepository = draftTranslationLanguageRepository;
		this.projectVersionRepository = projectVersionRepository;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
		this.publishedTranslationLanguageRepository = publishedTranslationLanguageRepository;
		this.draftDialogueService = draftDialogueService;
	}

	// ------------------------------------------------------------ //
	// -------------------- Project Management -------------------- //
	// ------------------------------------------------------------ //

	/**
	 * Returns all projects.
	 *
	 * @return list of all {@link DBProject}s.
	 */
	public List<DBProject> listProjects() {
		return projectRepository.findAll();
	}

	/**
	 * Returns the project with the given {@code slug}, or {@link Optional#empty()} if not found.
	 *
	 * @param slug the unique project slug.
	 * @return the matching project, or empty.
	 */
	public Optional<DBProject> findBySlug(String slug) {
		return projectRepository.findBySlug(slug);
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
		NodeHeader header = new NodeHeader(DEFAULT_NODE_TITLE);
		header.setSpeaker(DEFAULT_NODE_SPEAKER);
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
	 * Deletes the given project and all its associated data: its draft translation-language
	 * registry, draft dialogues (and their nodes/translations), and published versions (and their
	 * published dialogues/translations/translation languages).
	 *
	 * <p>None of these relationships cascade at the database or JPA level, and
	 * {@code latest_version_id} is a circular reference back into a table that references the
	 * project — so children must be deleted (and that reference nulled out) before the project
	 * row itself can be deleted.</p>
	 *
	 * @param project the project to delete.
	 */
	@Transactional
	public void deleteProject(DBProject project) {
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

		projectRepository.delete(project);
	}

}

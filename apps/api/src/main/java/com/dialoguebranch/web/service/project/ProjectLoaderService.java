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

import com.dialoguebranch.model.execute.LanguageMap;
import com.dialoguebranch.model.execute.LanguageSet;
import com.dialoguebranch.web.service.Application;
import com.dialoguebranch.web.service.execution.DatabasePublishedScriptLoader;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the latest published version of every project from the database into the in-memory
 * {@link com.dialoguebranch.web.service.execution.ApplicationManager} on application startup,
 * making all projects available for dialogue execution.
 *
 * <p>This service listens to the same {@link ApplicationReadyEvent} as
 * {@link ProjectSeedService}, but runs after it via {@link Order}. This guarantees that any
 * seed projects are written to the database before the load phase begins.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectLoaderService {

	private static final Logger logger = LoggerFactory.getLogger(ProjectLoaderService.class);

	private final Application application;
	private final ProjectService projectService;
	private final DBPublishedDialogueRepository publishedDialogueRepository;
	private final DBPublishedTranslationRepository publishedTranslationRepository;

	public ProjectLoaderService(Application application,
								ProjectService projectService,
								DBPublishedDialogueRepository publishedDialogueRepository,
								DBPublishedTranslationRepository publishedTranslationRepository) {
		this.application = application;
		this.projectService = projectService;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
	}

	// ------------------------------------------------------------------ //
	// -------------------- Startup Loading -------------------- //
	// ------------------------------------------------------------------ //

	/**
	 * Triggered after the application context is fully started, after {@link ProjectSeedService}
	 * has run. Loads the latest published version of every project in the database into the
	 * in-memory {@link com.dialoguebranch.web.service.execution.ApplicationManager}.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Order(1)
	public void loadOnStartup() {
		List<DBProject> allProjects = projectService.listProjects();
		if (allProjects.isEmpty()) {
			logger.info("No projects found in database — nothing to load into memory.");
			return;
		}

		for (DBProject project : allProjects) {
			DBProjectVersion latestVersion = project.getLatestVersion();
			if (latestVersion == null) {
				logger.info("Project '{}' has no published version — skipping.", project.getName());
				continue;
			}
			loadProject(project, latestVersion);
		}
	}

	// --------------------------------------------------------------- //
	// -------------------- Per-project Loading -------------------- //
	// --------------------------------------------------------------- //

	/**
	 * Loads a single project from its latest published database version into the in-memory
	 * {@link com.dialoguebranch.web.service.execution.ApplicationManager}.
	 *
	 * @param project       the database project record.
	 * @param latestVersion the published version to load.
	 */
	public void loadProject(DBProject project, DBProjectVersion latestVersion) {
		String projectName = project.getName();

		// Determine the source language from the project's language mappings.
		String sourceLanguage = "en";
		LanguageMap languageMap = projectService.getLanguageMap(project);
		if (languageMap != null && !languageMap.getLanguageSets().isEmpty()) {
			LanguageSet firstSet = languageMap.getLanguageSets().iterator().next();
			sourceLanguage = firstSet.getSourceLanguage().getCode();
		}

		// Fetch all published dialogues for this version and build the script content map.
		List<DBPublishedDialogue> dialogues =
				publishedDialogueRepository.findByVersion(latestVersion);

		Map<String, String> scriptContents = new LinkedHashMap<>();
		Map<UUID, String> dialogueNamesById = new LinkedHashMap<>();
		for (DBPublishedDialogue d : dialogues) {
			scriptContents.put(d.getName(), d.getContent());
			dialogueNamesById.put(d.getId(), d.getName());
		}

		// Fetch all translations in bulk and build the translation content map.
		// Accessing getId() on a Hibernate proxy is safe (ID is cached in the proxy).
		List<DBPublishedTranslation> translations =
				publishedTranslationRepository.findByPublishedDialogueIn(dialogues);

		Map<String, Map<String, String>> translationContents = new LinkedHashMap<>();
		for (DBPublishedTranslation t : translations) {
			String dialogueName = dialogueNamesById.get(t.getPublishedDialogue().getId());
			if (dialogueName == null) continue;
			translationContents
					.computeIfAbsent(t.getLanguage(), k -> new LinkedHashMap<>())
					.put(dialogueName, t.getContent());
		}

		DatabasePublishedScriptLoader scriptLoader =
				new DatabasePublishedScriptLoader(sourceLanguage, scriptContents, translationContents);

		application.getApplicationManager().loadProject(projectName, scriptLoader);
	}

}

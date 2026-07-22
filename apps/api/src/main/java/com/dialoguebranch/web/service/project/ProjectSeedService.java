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

import com.dialoguebranch.execution.parser.ProjectMetaDataParser;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.exception.ConflictException;
import com.dialoguebranch.web.service.execution.SpringResourceScriptLoader;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/**
 * Seeds the database with projects from the {@code projects-seed/} classpath directory on first
 * startup. Each sub-directory of {@code projects-seed/} is treated as a potential Dialogue Branch
 * project. If a project with the same name does not yet exist in the database, the seed project is
 * validated and, if it passes without errors, created as draft dialogues and then published as the
 * project's version 1 — the exact same path any other project takes from authoring to publishing,
 * so seeded projects never start with published dialogues that lack a corresponding draft.
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectSeedService {

	private static final String SEED_ROOT = "projects-seed";
	private static final String PROJECT_MARKER_FILE = "dlb-project.xml";

	private static final Logger logger = LoggerFactory.getLogger(ProjectSeedService.class);

	private final ProjectService projectService;
	private final DraftDialogueService draftDialogueService;
	private final DraftProjectService draftProjectService;
	private final PublishService publishService;
	private final ProjectSeedService self;

	/**
	 * Creates a new {@link ProjectSeedService}.
	 *
	 * @param projectService       service used to check for and create seed project records.
	 * @param draftDialogueService service used to create the seeded draft dialogues and their
	 *                             translations from the seed source files.
	 * @param draftProjectService  service used to seed the project's draft translation language
	 *                             registry from the seed source's {@code dlb-project.xml}.
	 * @param publishService       service used to publish the seeded drafts as the project's
	 *                             version 1.
	 * @param self                 a {@code @Lazy} self-reference resolving to this same bean's
	 *                             Spring proxy. Plain {@code this.seedProject(...)}
	 *                             self-invocation bypasses Spring's proxy-based AOP entirely, so
	 *                             {@code @Transactional} on {@link #seedProject} would silently
	 *                             never apply — calling through {@code self} instead is the
	 *                             standard way to make a bean's own transactional methods actually
	 *                             transactional when called from within the same bean.
	 */
	public ProjectSeedService(ProjectService projectService,
							  DraftDialogueService draftDialogueService,
							  DraftProjectService draftProjectService,
							  PublishService publishService,
							  @Lazy ProjectSeedService self) {
		this.projectService = projectService;
		this.draftDialogueService = draftDialogueService;
		this.draftProjectService = draftProjectService;
		this.publishService = publishService;
		this.self = self;
	}

	// ------------------------------------------------------------------ //
	// -------------------- Startup Seeding -------------------- //
	// ------------------------------------------------------------------ //

	/**
	 * Triggered after the application context is fully started. Scans {@code projects-seed/} for
	 * sub-directories containing a {@code dlb-project.xml} and seeds any that are not yet present
	 * in the database.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Order(0)
	public void seedOnStartup() {
		PathMatchingResourcePatternResolver resolver =
				new PathMatchingResourcePatternResolver(getClass().getClassLoader());

		Resource[] markerResources;
		try {
			markerResources = resolver.getResources(
					"classpath*:" + SEED_ROOT + "/*/" + PROJECT_MARKER_FILE);
		} catch (IOException e) {
			logger.error("Failed to scan '{}' for seed projects: {}", SEED_ROOT, e.getMessage());
			return;
		}

		if (markerResources.length == 0) {
			logger.info("No seed projects found under '{}'.", SEED_ROOT);
			return;
		}

		for (Resource markerResource : markerResources) {
			String projectFolderName = deriveFolderName(markerResource);
			if (projectFolderName == null) {
				logger.warn("Could not determine project folder name from resource: {}",
						markerResource);
				continue;
			}
			try {
				// Through `self`, not a plain `seedProject(...)` self-call — see the constructor's
				// Javadoc for why that matters here.
				self.seedProject(projectFolderName, markerResource);
			} catch (RuntimeException e) {
				// A single broken/invalid seed project (e.g. a duplicate translation language in
				// its dlb-project.xml) must not abort seeding of every other project in this loop.
				// Now that seedProject's @Transactional actually applies (see above), everything it
				// persisted before throwing has already been rolled back at this point.
				logger.error("Seed project '{}': failed to seed — rolled back: {}",
						projectFolderName, e.getMessage());
			}
		}
	}

	// --------------------------------------------------------------------- //
	// -------------------- Per-project Seed Logic -------------------- //
	// --------------------------------------------------------------------- //

	/**
	 * Seeds a single project if it does not yet exist in the database. Public so that {@link
	 * #seedOnStartup} can call it through {@code self} (see the constructor's Javadoc) rather than
	 * a plain self-invocation, which would silently bypass {@code @Transactional} below —
	 * everything this method persists (the project row, its draft translation languages, draft
	 * dialogues) is rolled back if it throws, since a failure partway through (e.g. a duplicate
	 * translation language in {@code dlb-project.xml}) must never leave a half-seeded project stuck
	 * in the database forever (the existence check at the top of this method would otherwise skip
	 * it on every future startup, with no way to retry).
	 *
	 * @param projectFolderName the folder name of the seed project (used as the project slug).
	 * @param markerResource    the Spring {@link Resource} pointing to its {@code dlb-project.xml}.
	 * @throws RuntimeException if seeding fails after the project row has already been persisted —
	 * callers must catch this (see {@link #seedOnStartup}) so one broken seed project doesn't
	 * abort seeding of the rest.
	 */
	@Transactional
	public void seedProject(String projectFolderName, Resource markerResource) {
		if (projectService.findBySlug(projectFolderName).isPresent()) {
			logger.info("Seed project '{}' already exists in database — skipping.",
					projectFolderName);
			return;
		}

		logger.info("Seeding project '{}' from classpath resource.", projectFolderName);

		// -- Step 1: Parse dlb-project.xml --
		ProjectMetaData metaData;
		try {
			metaData = parseMetaData(markerResource);
		} catch (ParseException | IOException e) {
			logger.error("Seed project '{}': failed to parse {}: {}", projectFolderName,
					PROJECT_MARKER_FILE, e.getMessage());
			return;
		}

		// -- Step 2: Validate the full project --
		String resourcePath = SEED_ROOT + "/" + projectFolderName;
		SpringResourceScriptLoader scriptLoader = new SpringResourceScriptLoader(resourcePath);
		ProjectParserResult parserResult;
		try {
			parserResult = new ProjectParser(scriptLoader).parse();
		} catch (IOException e) {
			logger.error("Seed project '{}': error during project parsing: {}", projectFolderName,
					e.getMessage());
			return;
		}

		if (!parserResult.getParseErrors().isEmpty()) {
			logger.error("Seed project '{}': validation failed — not seeding:", projectFolderName);
			parserResult.getParseErrors().forEach((path, errors) ->
					errors.forEach(err -> logger.error("  {}: {}", path, err.getMessage())));
			return;
		}

		if (!parserResult.getWarnings().isEmpty()) {
			parserResult.getWarnings().forEach((path, warnings) ->
					warnings.forEach(w -> logger.warn("Seed project '{}' — {}: {}", projectFolderName,
							path, w)));
		}

		// -- Steps 3-7: create the project record, its draft translation languages and draft
		// dialogues, and publish them as version 1 — shared with project import (see
		// createAndPublishProject's Javadoc). --
		try {
			createAndPublishProject(projectFolderName, metaData, scriptLoader);
		} catch (IOException e) {
			throw new UncheckedIOException("Seed project '" + projectFolderName +
					"': failed to seed from source: " + e.getMessage(), e);
		}

		logger.info("Successfully seeded project '{}' as version 1.", projectFolderName);
	}

	/**
	 * Creates a new project record, seeds it with draft dialogues and translations read from {@code
	 * scriptLoader}, and publishes those drafts as the project's version 1 — the exact same path any
	 * other project takes from authoring to publishing, so a project created this way never starts
	 * with published content that lacks a corresponding draft.
	 *
	 * <p>Shared by two callers: classpath startup seeding ({@link #seedProject}, which has already
	 * validated the project and derives {@code slug} from the seed folder name) and project import
	 * ({@code ProjectImportService}, which derives {@code slug} from the imported {@code
	 * dlb-project.xml}'s {@code slug} attribute after its own validation pass). Callers are
	 * responsible for checking {@code slug} doesn't already exist before calling this method, and
	 * for validating {@code scriptLoader}'s content (e.g. via {@link ProjectParser}) beforehand —
	 * this method does not repeat either check.</p>
	 *
	 * @param slug         the unique slug for the new project.
	 * @param metaData     the parsed project metadata (display name, description, language map).
	 * @param scriptLoader the source to read {@code .dlb}/{@code .json} files from.
	 * @return the newly created and published project.
	 * @throws IOException           if {@code scriptLoader}'s files cannot be listed.
	 * @throws IllegalStateException if {@code metaData} has no source language, its translation
	 * languages contain a duplicate code, or publishing the seeded drafts fails after passing
	 * pre-validation.
	 */
	@Transactional
	public DBProject createAndPublishProject(String slug, ProjectMetaData metaData,
			ScriptLoader scriptLoader) throws IOException {
		// -- Step 3: Create the project record, including its (required) source language --
		Language source = metaData.getLanguageMap() != null
				? metaData.getLanguageMap().getSourceLanguage() : null;
		if (source == null) {
			throw new IllegalStateException("Project '" + slug +
					"': dlb-project.xml has no <source-language>.");
		}
		DBProject project = projectService.createProject(slug, metaData.getName(),
				metaData.getDescription(), source.getCode(), source.getName());

		// -- Step 4: Store translation languages (as drafts — reconciled into the published
		// registry alongside everything else when this project is published in step 7) --
		try {
			for (Language translation : metaData.getLanguageMap().getTranslationLanguages()) {
				draftProjectService.addDraftLanguage(project, translation.getName(),
						translation.getCode());
			}
		} catch (ConflictException e) {
			// Must throw, not just log-and-return: the project row (and any languages already
			// added this loop) are already persisted at this point, and only an exception
			// propagating out of this method actually rolls them back.
			throw new IllegalStateException("Project '" + slug +
					"': duplicate translation language in " + PROJECT_MARKER_FILE + ": " +
					e.getMessage(), e);
		}

		// -- Step 5: List the source files --
		List<ResourcePointer> files = scriptLoader.listDialogueBranchFiles();

		// -- Step 6: Create draft dialogues (scripts first, then translations, since a
		// translation needs its dialogue's draft to already exist) --
		for (ResourcePointer pointer : files) {
			if (pointer.getResourceType() != ResourceType.SCRIPT) continue;
			String content = readContent(scriptLoader, pointer);
			if (content == null) continue;
			draftDialogueService.createDialogueFromScript(project, pointer.getDialogueName(),
					content);
		}
		for (ResourcePointer pointer : files) {
			if (pointer.getResourceType() != ResourceType.TRANSLATION) continue;
			String content = readContent(scriptLoader, pointer);
			if (content == null) continue;
			Optional<DBDraftTranslationLanguage> language =
					draftProjectService.findDraftLanguage(project, pointer.getLanguage());
			if (language.isEmpty()) {
				logger.warn("Project '{}': translation file for unregistered language '{}' " +
						"— skipping.", slug, pointer.getLanguage());
				continue;
			}
			draftDialogueService.findDialogue(project, pointer.getDialogueName())
					.ifPresent(dialogue -> draftDialogueService.createOrUpdateTranslation(
							dialogue, language.get(), content));
		}

		// -- Step 7: Publish the drafts as version 1 — the same path as any later publish --
		try {
			PublishService.PublishResult publishResult = publishService.publish(project, null);
			if (!publishResult.isSuccess()) {
				throw new IllegalStateException("Project '" + slug +
						"' failed to publish after passing its own pre-validation: " +
						publishResult.getErrors());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Project '" + slug +
					"': failed to publish seeded drafts: " + e.getMessage(), e);
		}

		return project;
	}

	// --------------------------------------------------------------- //
	// -------------------- Private Helpers -------------------- //
	// --------------------------------------------------------------- //

	/**
	 * Derives the seed project folder name from the URI of its {@code dlb-project.xml} marker
	 * resource. The folder name is the path segment immediately before the filename.
	 *
	 * @param markerResource the {@code dlb-project.xml} resource.
	 * @return the folder name, or {@code null} if it cannot be determined.
	 */
	private String deriveFolderName(Resource markerResource) {
		try {
			URI uri = markerResource.getURI();
			String uriString = uri.toString();
			String withoutFile = uriString.substring(0,
					uriString.length() - ("/" + PROJECT_MARKER_FILE).length());
			return withoutFile.substring(withoutFile.lastIndexOf('/') + 1);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Parses the {@code dlb-project.xml} from the given Spring {@link Resource} by copying it to a
	 * temporary file first, which allows {@link ProjectMetaDataParser} to work in both filesystem
	 * and JAR classpath environments.
	 *
	 * @param markerResource the {@code dlb-project.xml} resource.
	 * @return the parsed {@link ProjectMetaData}.
	 * @throws ParseException if the XML content is invalid.
	 * @throws IOException    if the resource cannot be read.
	 */
	private ProjectMetaData parseMetaData(Resource markerResource)
			throws ParseException, IOException {
		File tempFile = Files.createTempFile("dlb-project-", ".xml").toFile();
		tempFile.deleteOnExit();
		try (InputStream in = markerResource.getInputStream();
			 OutputStream out = new FileOutputStream(tempFile)) {
			in.transferTo(out);
		}
		return ProjectMetaDataParser.parse(tempFile);
	}

	/**
	 * Reads the full content of the file identified by the given {@link ResourcePointer} as a
	 * string, or returns {@code null} and logs an error if reading fails.
	 *
	 * @param scriptLoader the {@link ScriptLoader} to use.
	 * @param pointer    the resource to read.
	 * @return the file content, or {@code null} on error.
	 */
	private String readContent(ScriptLoader scriptLoader, ResourcePointer pointer) {
		try (Reader reader = scriptLoader.openFile(pointer)) {
			StringBuilder sb = new StringBuilder();
			char[] buf = new char[4096];
			int n;
			while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
			return sb.toString();
		} catch (IOException e) {
			logger.error("Failed to read content for {}/{}: {}", pointer.getLanguage(),
					pointer.getDialogueName(), e.getMessage());
			return null;
		}
	}

}

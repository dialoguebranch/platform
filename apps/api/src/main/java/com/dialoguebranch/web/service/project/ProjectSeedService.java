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
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageSet;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.execution.SpringResourceScriptLoader;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import nl.rrd.utils.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

/**
 * Seeds the database with projects from the {@code projects-seed/} classpath directory on first
 * startup. Each sub-directory of {@code projects-seed/} is treated as a potential Dialogue Branch
 * project. If a project with the same name does not yet exist in the database, the seed project is
 * validated and, if it passes without errors, inserted as a published project version.
 *
 * <p>Seed projects bypass the draft layer entirely and are inserted directly into the published
 * tables.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class ProjectSeedService {

	private static final String SEED_ROOT = "projects-seed";
	private static final String PROJECT_MARKER_FILE = "dlb-project.xml";

	private static final Logger logger = LoggerFactory.getLogger(ProjectSeedService.class);

	private final ProjectService projectService;
	private final DBProjectVersionRepository versionRepository;
	private final DBPublishedDialogueRepository publishedDialogueRepository;
	private final DBPublishedTranslationRepository publishedTranslationRepository;

	public ProjectSeedService(ProjectService projectService,
							  DBProjectVersionRepository versionRepository,
							  DBPublishedDialogueRepository publishedDialogueRepository,
							  DBPublishedTranslationRepository publishedTranslationRepository) {
		this.projectService = projectService;
		this.versionRepository = versionRepository;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
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
			seedProject(projectFolderName, markerResource);
		}
	}

	// --------------------------------------------------------------------- //
	// -------------------- Per-project Seed Logic -------------------- //
	// --------------------------------------------------------------------- //

	/**
	 * Seeds a single project if it does not yet exist in the database.
	 *
	 * @param projectFolderName the folder name of the seed project (used as the project slug).
	 * @param markerResource    the Spring {@link Resource} pointing to its {@code dlb-project.xml}.
	 */
	@Transactional
	private void seedProject(String projectFolderName, Resource markerResource) {
		if (projectService.findByName(projectFolderName).isPresent()) {
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

		// -- Step 3: Create the project record --
		DBProject project = projectService.createProject(
				projectFolderName, metaData.getName(), metaData.getDescription());

		// -- Step 4: Store language mappings --
		if (metaData.getLanguageMap() != null) {
			for (LanguageSet languageSet : metaData.getLanguageMap().getLanguageSets()) {
				Language source = languageSet.getSourceLanguage();
				for (Language translation : languageSet.getTranslationLanguages()) {
					projectService.addLanguageMapping(project, source, translation);
				}
			}
		}

		// -- Step 5: Create version 1 as the published snapshot --
		DBProjectVersion version = new DBProjectVersion();
		version.setProject(project);
		version.setVersionNumber(1);
		version.setPublishedAt(Instant.now());
		version.setPublishedBy(null);
		version = versionRepository.save(version);

		// -- Step 6: Copy dialogue and translation content into published tables --
		List<ResourcePointer> files;
		try {
			files = scriptLoader.listDialogueBranchFiles();
		} catch (IOException e) {
			logger.error("Seed project '{}': failed to list dialogue files: {}", projectFolderName,
					e.getMessage());
			return;
		}

		for (ResourcePointer pointer : files) {
			if (pointer.getResourceType().name().equals("SCRIPT")) {
				String content = readContent(scriptLoader, pointer);
				if (content == null) continue;

				DBPublishedDialogue publishedDialogue = new DBPublishedDialogue();
				publishedDialogue.setVersion(version);
				publishedDialogue.setName(pointer.getDialogueName());
				publishedDialogue.setContent(content);
				publishedDialogueRepository.save(publishedDialogue);

			} else {
				// Find the matching published dialogue for this translation
				String dialogueName = pointer.getDialogueName();
				publishedDialogueRepository.findByVersionAndName(version, dialogueName)
						.ifPresent(publishedDialogue -> {
							String content = readContent(scriptLoader, pointer);
							if (content == null) return;

							DBPublishedTranslation translation = new DBPublishedTranslation();
							translation.setPublishedDialogue(publishedDialogue);
							translation.setLanguage(pointer.getLanguage());
							translation.setContent(content);
							publishedTranslationRepository.save(translation);
						});
			}
		}

		// -- Step 7: Set latest_version_id on the project --
		project.setLatestVersion(version);
		project.setUpdatedAt(Instant.now());
		projectService.save(project);

		logger.info("Successfully seeded project '{}' as version 1 ({} files).",
				projectFolderName, files.size());
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
	 * @param scriptLoader the {@link SpringResourceScriptLoader} to use.
	 * @param pointer    the resource to read.
	 * @return the file content, or {@code null} on error.
	 */
	private String readContent(SpringResourceScriptLoader scriptLoader, ResourcePointer pointer) {
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

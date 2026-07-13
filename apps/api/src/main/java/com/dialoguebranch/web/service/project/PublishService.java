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

import com.dialoguebranch.execution.parser.ScriptLoader;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.web.service.repository.DBProjectRepository;
import com.dialoguebranch.web.service.repository.DBProjectVersionRepository;
import com.dialoguebranch.web.service.repository.DBPublishedDialogueRepository;
import com.dialoguebranch.web.service.repository.DBPublishedTranslationRepository;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslation;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.dialoguebranch.web.service.storage.model.DBProjectVersion;
import com.dialoguebranch.web.service.storage.model.DBPublishedDialogue;
import com.dialoguebranch.web.service.storage.model.DBPublishedTranslation;
import com.dialoguebranch.web.service.storage.model.DBUser;
import nl.rrd.utils.exception.ParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that validates a project's full draft and, if valid, creates a new published
 * {@link DBProjectVersion}. The published version is an immutable snapshot of all draft dialogues
 * and translations at the time of publishing, and is the only content the execution engine reads.
 *
 * @author Harm op den Akker
 */
@Service
public class PublishService {

	private final DraftDialogueService draftDialogueService;
	private final DBProjectVersionRepository versionRepository;
	private final DBPublishedDialogueRepository publishedDialogueRepository;
	private final DBPublishedTranslationRepository publishedTranslationRepository;
	private final DBProjectRepository projectRepository;
	private final ProjectLoaderService projectLoaderService;

	public PublishService(DraftDialogueService draftDialogueService,
						  DBProjectVersionRepository versionRepository,
						  DBPublishedDialogueRepository publishedDialogueRepository,
						  DBPublishedTranslationRepository publishedTranslationRepository,
						  DBProjectRepository projectRepository,
						  ProjectLoaderService projectLoaderService) {
		this.draftDialogueService = draftDialogueService;
		this.versionRepository = versionRepository;
		this.publishedDialogueRepository = publishedDialogueRepository;
		this.publishedTranslationRepository = publishedTranslationRepository;
		this.projectRepository = projectRepository;
		this.projectLoaderService = projectLoaderService;
	}

	// ---------------------------------------------------------------------- //
	// -------------------- Version Listing -------------------- //
	// ---------------------------------------------------------------------- //

	/**
	 * Returns all published versions for the given project, ordered from newest to oldest.
	 *
	 * @param project the project whose versions to list.
	 * @return list of {@link DBProjectVersion}s ordered by version number descending.
	 */
	public List<DBProjectVersion> listVersions(DBProject project) {
		return versionRepository.findByProjectOrderByVersionNumberDesc(project);
	}

	// ----------------------------------------------------------- //
	// -------------------- Publish Operation -------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Attempts to publish the given project. All draft dialogues are reconstructed into
	 * {@code .dlb} script content and validated by {@link ProjectParser}. If validation passes,
	 * a new {@link DBProjectVersion} is created, the draft content is copied into
	 * {@code published_dialogues} and {@code published_translations}, and
	 * {@link DBProject#getLatestVersion()} is updated.
	 *
	 * @param project     the project to publish.
	 * @param publishedBy the user initiating the publish, or {@code null} for system-triggered
	 *                    publishes.
	 * @return the result of the publish attempt.
	 * @throws IOException if script reconstruction fails unexpectedly.
	 */
	@Transactional
	public PublishResult publish(DBProject project, DBUser publishedBy) throws IOException {
		List<DBDraftDialogue> drafts = draftDialogueService.listDialogues(project);

		// Build an in-memory map of dialogue name → script content for validation
		Map<String, String> scriptsByName = new LinkedHashMap<>();
		Map<String, Map<String, String>> translationsByDialogue = new LinkedHashMap<>();

		for (DBDraftDialogue draft : drafts) {
			String script = draftDialogueService.reconstructScript(draft);
			scriptsByName.put(draft.getName(), script);

			Map<String, String> translations = new LinkedHashMap<>();
			for (DBDraftTranslation t : draftDialogueService.listTranslations(draft)) {
				translations.put(t.getLanguage(), t.getContent());
			}
			translationsByDialogue.put(draft.getName(), translations);
		}

		// Validate the full project via ProjectParser using an in-memory ScriptLoader
		ScriptLoader scriptLoader = new InMemoryScriptLoader(scriptsByName, translationsByDialogue);
		ProjectParserResult result = new ProjectParser(scriptLoader).parse();

		if (!result.getParseErrors().isEmpty()) {
			return PublishResult.failure(result.getParseErrors());
		}

		// Determine next version number
		int nextVersion = versionRepository
				.findByProjectOrderByVersionNumberDesc(project)
				.stream()
				.findFirst()
				.map(v -> v.getVersionNumber() + 1)
				.orElse(1);

		// Create the project version record
		DBProjectVersion version = new DBProjectVersion();
		version.setProject(project);
		version.setVersionNumber(nextVersion);
		version.setPublishedAt(Instant.now());
		version.setPublishedBy(publishedBy);
		version = versionRepository.save(version);

		// Copy draft content into published tables
		for (DBDraftDialogue draft : drafts) {
			DBPublishedDialogue published = new DBPublishedDialogue();
			published.setVersion(version);
			published.setName(draft.getName());
			published.setContent(scriptsByName.get(draft.getName()));
			published = publishedDialogueRepository.save(published);

			for (Map.Entry<String, String> entry :
					translationsByDialogue.get(draft.getName()).entrySet()) {
				DBPublishedTranslation translation = new DBPublishedTranslation();
				translation.setPublishedDialogue(published);
				translation.setLanguage(entry.getKey());
				translation.setContent(entry.getValue());
				publishedTranslationRepository.save(translation);
			}
		}

		// Update the project's latest version pointer
		project.setLatestVersion(version);
		project.setUpdatedAt(Instant.now());
		projectRepository.save(project);

		// Reload the project into the running execution engine so the newly published version
		// is immediately listed and runnable, instead of only taking effect after a restart.
		projectLoaderService.loadProject(project, version);

		return PublishResult.success(version);
	}

	// --------------------------------------------------------------- //
	// -------------------- In-memory ScriptLoader -------------------- //
	// --------------------------------------------------------------- //

	/**
	 * A {@link ScriptLoader} implementation that serves reconstructed dialogue scripts and
	 * translation JSON from in-memory maps, allowing {@link ProjectParser} to validate project
	 * content without touching the filesystem.
	 */
	private static class InMemoryScriptLoader implements ScriptLoader {

		private final Map<String, String> scripts;
		private final Map<String, Map<String, String>> translations;

		InMemoryScriptLoader(Map<String, String> scripts,
						   Map<String, Map<String, String>> translations) {
			this.scripts = scripts;
			this.translations = translations;
		}

		@Override
		public List<ResourcePointer> listDialogueBranchFiles() {
			List<ResourcePointer> pointers = new ArrayList<>();
			for (String dialogueName : scripts.keySet()) {
				pointers.add(new ResourcePointer("", dialogueName, ResourceType.SCRIPT));
				Map<String, String> langs = translations.getOrDefault(dialogueName, Map.of());
				for (String language : langs.keySet()) {
					pointers.add(new ResourcePointer(language, dialogueName,
							ResourceType.TRANSLATION));
				}
			}
			return pointers;
		}

		@Override
		public Reader openFile(ResourcePointer fileDescription) throws IOException {
			String dialogueName = fileDescription.getDialogueName();

			if (fileDescription.getResourceType() == ResourceType.SCRIPT) {
				String content = scripts.get(dialogueName);
				if (content == null)
					throw new IOException("Script not found: " + dialogueName);
				return new StringReader(content);
			} else {
				String language = fileDescription.getLanguage();
				String content = translations
						.getOrDefault(dialogueName, Map.of())
						.get(language);
				if (content == null)
					throw new IOException(
							"Translation not found: " + dialogueName + "/" + language);
				return new StringReader(content);
			}
		}
	}

	// -------------------------------------------------------- //
	// -------------------- Publish Result -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Represents the outcome of a publish operation, containing either the new
	 * {@link DBProjectVersion} on success or a map of parse errors on failure.
	 */
	public static class PublishResult {

		private final boolean success;
		private final DBProjectVersion version;
		private final Map<String, List<ParseException>> errors;

		private PublishResult(boolean success, DBProjectVersion version,
							  Map<String, List<ParseException>> errors) {
			this.success = success;
			this.version = version;
			this.errors = errors;
		}

		/**
		 * Creates a successful {@link PublishResult} containing the new project version.
		 *
		 * @param version the newly created {@link DBProjectVersion}.
		 * @return a successful result.
		 */
		public static PublishResult success(DBProjectVersion version) {
			return new PublishResult(true, version, null);
		}

		/**
		 * Creates a failed {@link PublishResult} containing the parse errors that prevented
		 * publishing.
		 *
		 * @param errors a map of file path to list of parse errors.
		 * @return a failed result.
		 */
		public static PublishResult failure(Map<String, List<ParseException>> errors) {
			return new PublishResult(false, null, errors);
		}

		/**
		 * Returns {@code true} if the publish succeeded.
		 *
		 * @return whether the publish succeeded.
		 */
		public boolean isSuccess() {
			return success;
		}

		/**
		 * Returns the newly created {@link DBProjectVersion}, or {@code null} if the publish
		 * failed.
		 *
		 * @return the project version, or {@code null}.
		 */
		public DBProjectVersion getVersion() {
			return version;
		}

		/**
		 * Returns the parse errors that prevented publishing, or {@code null} if the publish
		 * succeeded.
		 *
		 * @return the parse errors, or {@code null}.
		 */
		public Map<String, List<ParseException>> getErrors() {
			return errors;
		}
	}

}

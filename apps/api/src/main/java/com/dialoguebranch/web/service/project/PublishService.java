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
import com.dialoguebranch.web.service.execution.DatabasePublishedScriptLoader;
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
import java.time.Instant;
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

	/**
	 * Returns the version number that would be assigned to the next published version of the
	 * given project, without creating one.
	 *
	 * @param project the project to compute the next version number for.
	 * @return the next version number.
	 */
	public int getNextVersionNumber(DBProject project) {
		return versionRepository
				.findByProjectOrderByVersionNumberDesc(project)
				.stream()
				.findFirst()
				.map(v -> v.getVersionNumber() + 1)
				.orElse(1);
	}

	// ----------------------------------------------------------- //
	// -------------------- Verify Operation ---------------------- //
	// ----------------------------------------------------------- //

	/**
	 * Validates the given project's current draft exactly as {@link #publish} would, but without
	 * any side effects — no version is created and no draft state is changed. Used to let a client
	 * check for errors before committing to an actual publish.
	 *
	 * @param project the project to validate.
	 * @return the result of the validation.
	 * @throws IOException if script reconstruction fails unexpectedly.
	 */
	public VerifyResult verify(DBProject project) throws IOException {
		List<DBDraftDialogue> drafts = draftDialogueService.listDialogues(project).stream()
				.filter(draft -> !draft.getIsDeleted())
				.toList();
		ProjectParserResult result = validateDrafts(project, drafts).result();
		if (result.getParseErrors().isEmpty()) return VerifyResult.valid();
		return VerifyResult.invalid(toErrorMessages(result.getParseErrors()));
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
		// Every dialogue in the project always has a draft — seeded projects create drafts first
		// and publish from them (see ProjectSeedService), and any dialogue added since is created
		// as a draft too. Dialogues pending deletion are excluded from what gets published, but
		// stay around as drafts until the publish actually succeeds (see below).
		List<DBDraftDialogue> allDrafts = draftDialogueService.listDialogues(project);
		List<DBDraftDialogue> drafts = allDrafts.stream()
				.filter(draft -> !draft.getIsDeleted())
				.toList();

		DraftValidation validation = validateDrafts(project, drafts);
		if (!validation.result().getParseErrors().isEmpty()) {
			return PublishResult.failure(validation.result().getParseErrors());
		}
		Map<String, String> scriptsByName = validation.scripts();
		Map<String, Map<String, String>> translationsByDialogue = validation.translations();

		// Determine next version number
		int nextVersion = getNextVersionNumber(project);

		// Create the project version record
		DBProjectVersion version = new DBProjectVersion();
		version.setProject(project);
		version.setVersionNumber(nextVersion);
		version.setPublishedAt(Instant.now());
		version.setPublishedBy(publishedBy);
		version = versionRepository.save(version);

		// Copy draft content into published tables, and reconcile each dialogue's draft status —
		// it's no longer new, no longer changed, and no longer remembers a prior name, since this
		// version now reflects it exactly.
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

			draft.setIsNew(false);
			draft.setIsChanged(false);
			draft.setRenamedFrom(null);
			draftDialogueService.save(draft);
		}

		// Dialogues that were pending deletion are now genuinely gone from the published set (they
		// were excluded above) — their draft rows can be permanently removed.
		for (DBDraftDialogue draft : allDrafts) {
			if (draft.getIsDeleted()) {
				draftDialogueService.hardDeleteDialogue(draft);
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

	// ------------------------------------------------------------- //
	// -------------------- Shared Draft Validation ------------------ //
	// ------------------------------------------------------------- //

	/**
	 * Reconstructs {@code .dlb} script content and translations for the given draft dialogues and
	 * validates the result with {@link ProjectParser}. Used by both {@link #verify} (which only
	 * needs the parse result) and {@link #publish} (which also needs the reconstructed content to
	 * copy into the published tables), so both stay in sync on exactly what gets validated.
	 *
	 * @param project the project {@code drafts} belongs to, used only to look up its source
	 *                language code for the reconstructed scripts (see
	 *                {@link DatabasePublishedScriptLoader}).
	 * @param drafts  the non-deleted draft dialogues to validate.
	 * @return the reconstructed content alongside the parse result.
	 * @throws IOException if script reconstruction fails unexpectedly.
	 */
	private DraftValidation validateDrafts(DBProject project, List<DBDraftDialogue> drafts)
			throws IOException {
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

		String sourceLanguage = project.getSourceLanguageCode() != null
				? project.getSourceLanguageCode() : "";
		ScriptLoader scriptLoader = new DatabasePublishedScriptLoader(
				sourceLanguage, scriptsByName, translationsByLanguage(translationsByDialogue));
		ProjectParserResult result = new ProjectParser(scriptLoader).parse();
		return new DraftValidation(scriptsByName, translationsByDialogue, result);
	}

	/**
	 * Re-keys a dialogue-name → language → content map into the language → dialogue-name → content
	 * shape {@link DatabasePublishedScriptLoader} expects (matching how {@link ProjectLoaderService}
	 * already builds it from published records).
	 */
	private static Map<String, Map<String, String>> translationsByLanguage(
			Map<String, Map<String, String>> translationsByDialogue) {
		Map<String, Map<String, String>> byLanguage = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> dialogueEntry :
				translationsByDialogue.entrySet()) {
			String dialogueName = dialogueEntry.getKey();
			for (Map.Entry<String, String> langEntry : dialogueEntry.getValue().entrySet()) {
				byLanguage.computeIfAbsent(langEntry.getKey(), (k) -> new LinkedHashMap<>())
						.put(dialogueName, langEntry.getValue());
			}
		}
		return byLanguage;
	}

	/**
	 * Reduces {@link ParseException}s down to their message text, which is all a client needs to
	 * display a list of validation errors.
	 */
	private static Map<String, List<String>> toErrorMessages(
			Map<String, List<ParseException>> parseErrors) {
		Map<String, List<String>> messages = new LinkedHashMap<>();
		for (Map.Entry<String, List<ParseException>> entry : parseErrors.entrySet()) {
			messages.put(entry.getKey(),
					entry.getValue().stream().map(Throwable::getMessage).toList());
		}
		return messages;
	}

	/**
	 * The reconstructed script/translation content for a set of draft dialogues, alongside the
	 * {@link ProjectParser} result of validating that content. See {@link #validateDrafts}.
	 */
	private record DraftValidation(Map<String, String> scripts,
									Map<String, Map<String, String>> translations,
									ProjectParserResult result) { }

	// -------------------------------------------------------- //
	// -------------------- Verify Result ---------------------- //
	// -------------------------------------------------------- //

	/**
	 * Represents the outcome of validating a project's draft without publishing it, containing
	 * either nothing (on success) or a map of dialogue name to error messages (on failure).
	 */
	public static class VerifyResult {

		private final boolean valid;
		private final Map<String, List<String>> errors;

		private VerifyResult(boolean valid, Map<String, List<String>> errors) {
			this.valid = valid;
			this.errors = errors;
		}

		/**
		 * Creates a {@link VerifyResult} indicating the project's draft is valid.
		 *
		 * @return a valid result.
		 */
		public static VerifyResult valid() {
			return new VerifyResult(true, null);
		}

		/**
		 * Creates a {@link VerifyResult} indicating the project's draft has validation errors.
		 *
		 * @param errors a map of dialogue name to the list of error messages for that dialogue.
		 * @return an invalid result.
		 */
		public static VerifyResult invalid(Map<String, List<String>> errors) {
			return new VerifyResult(false, errors);
		}

		/**
		 * Returns {@code true} if the project's draft is valid and ready to publish.
		 *
		 * @return whether the draft is valid.
		 */
		public boolean isValid() {
			return valid;
		}

		/**
		 * Returns the validation errors, or {@code null} if the draft is valid.
		 *
		 * @return a map of dialogue name to error messages, or {@code null}.
		 */
		public Map<String, List<String>> getErrors() {
			return errors;
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

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

import com.dialoguebranch.execution.parser.DialogueBranchParser;
import com.dialoguebranch.web.service.controller.schema.authoring.ImportedTranslation;
import com.dialoguebranch.web.service.controller.schema.authoring.TranslatableTermSummary;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ProjectParseHttpError;
import com.dialoguebranch.web.service.project.DraftDialogueService.CheckedTranslationContent;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBDraftTranslationLanguage;
import com.dialoguebranch.web.service.storage.model.DBProject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Imports translation files ({@code .json}) into one translation language of an existing
 * project, as draft translations of dialogues in one folder (#340). All or nothing: every file is
 * checked first, and if any is refused, nothing is imported.
 *
 * <p>Each file is stored re-serialised by {@link DraftDialogueService#checkTranslationContent},
 * the same check {@code /update-translation} applies (#376). Entries that match no translatable
 * term of the dialogue are kept, and terms without an entry stay untranslated; both are counted
 * in the result rather than refused. An entry matches a term on its speaker (the speaker group it
 * is in) and its text, whitespace-normalised.</p>
 *
 * @author Harm op den Akker
 */
@Service
public class TranslationImportService {

	private static final String TRANSLATION_EXTENSION = ".json";

	private static final ObjectMapper JSON = new ObjectMapper();

	private final DraftDialogueService draftDialogueService;

	/**
	 * Creates the service.
	 *
	 * @param draftDialogueService the service storing draft dialogues and their translations.
	 */
	public TranslationImportService(DraftDialogueService draftDialogueService) {
		this.draftDialogueService = draftDialogueService;
	}

	/** A file that passed every check, ready to be written. */
	private record CheckedFile(String fileName, DBDraftDialogue dialogue, String content,
			boolean replaces) {
	}

	/**
	 * Imports translation files into a translation language of a project. Each file becomes the
	 * translation of the draft dialogue named after the folder and the file name without
	 * {@code .json}.
	 *
	 * @param project  the project.
	 * @param language the translation language to import into; not pending deletion.
	 * @param folder   the folder of the dialogues; empty or {@code null} for the project root.
	 * @param files    the {@code .json} translation files.
	 * @param replace  whether a file may replace a dialogue's existing translation in this
	 *                 language; if not, an existing translation refuses the import.
	 * @return the result per file, in the order given.
	 * @throws BadRequestException if any file is refused; its errors list the problems per file
	 * name, and nothing is imported.
	 */
	@Transactional(rollbackFor = Exception.class)
	public List<ImportedTranslation> importTranslations(DBProject project,
			DBDraftTranslationLanguage language, String folder, List<MultipartFile> files,
			boolean replace) throws BadRequestException {
		String folderPath = ImportFiles.requireFolder(folder);
		ImportFiles.requireFileCount(files);

		Map<String, List<String>> problems = new LinkedHashMap<>();
		Set<String> namesInImport = new HashSet<>();
		List<CheckedFile> checked = new ArrayList<>();
		for (MultipartFile file : files) {
			String fileName = ImportFiles.baseName(file.getOriginalFilename());
			List<String> fileProblems = new ArrayList<>();
			CheckedFile checkedFile = check(project, language, folderPath, fileName, file,
					replace, namesInImport, fileProblems);
			if (!fileProblems.isEmpty()) problems.put(fileName, fileProblems);
			else checked.add(checkedFile);
		}
		if (!problems.isEmpty()) {
			throw new BadRequestException(new ProjectParseHttpError(
					"Nothing was imported: " + problems.size() + " of " + files.size() +
					" file(s) can't be imported.", problems));
		}

		List<ImportedTranslation> results = new ArrayList<>();
		for (CheckedFile file : checked) {
			draftDialogueService.createOrUpdateTranslation(file.dialogue(), language,
					file.content());
			results.add(withCoverage(file));
		}
		return results;
	}

	private CheckedFile check(DBProject project, DBDraftTranslationLanguage language,
			String folderPath, String fileName, MultipartFile file, boolean replace,
			Set<String> namesInImport, List<String> problems) {
		if (!fileName.toLowerCase().endsWith(TRANSLATION_EXTENSION)) {
			problems.add("not a .json file");
			return null;
		}
		String baseName = fileName.substring(0,
				fileName.length() - TRANSLATION_EXTENSION.length());
		String dialogueName = folderPath.isEmpty() ? baseName : folderPath + "/" + baseName;
		if (!dialogueName.matches(DialogueBranchParser.DIALOGUE_NAME_REGEX)) {
			problems.add("'" + baseName + "' isn't a valid dialogue name: use letters, digits, " +
					"'_' and '-'");
			return null;
		}
		if (!namesInImport.add(dialogueName)) {
			problems.add("more than one file in this import is named '" + fileName + "'");
			return null;
		}
		String text = ImportFiles.readText(file, problems);
		if (text == null) return null;

		Optional<DBDraftDialogue> dialogue = draftDialogueService.findDialogue(project,
				dialogueName);
		boolean replaces = false;
		if (dialogue.isEmpty()) {
			problems.add("there is no dialogue '" + dialogueName + "' in this project");
		} else if (dialogue.get().getIsDeleted()) {
			problems.add("dialogue '" + dialogueName + "' is pending deletion");
		} else {
			replaces = draftDialogueService.findTranslation(dialogue.get(), language).isPresent();
			if (replaces && !replace) {
				problems.add("dialogue '" + dialogueName + "' already has a translation in " +
						language.getTranslationLanguageName());
			}
		}

		CheckedTranslationContent content = DraftDialogueService.checkTranslationContent(text);
		problems.addAll(content.problems());
		if (content.problems().isEmpty() && content.translationCount() == 0) {
			problems.add("contains no translations");
		}
		if (!problems.isEmpty()) return null;
		return new CheckedFile(fileName, dialogue.get(), content.content(), replaces);
	}

	/**
	 * The result for an imported file, with how well it covers the dialogue's translatable
	 * terms, or without that when the dialogue's draft doesn't parse.
	 */
	private ImportedTranslation withCoverage(CheckedFile file) {
		String dialogueName = file.dialogue().getName();
		List<TranslatableTermSummary> terms;
		try {
			terms = draftDialogueService.listTranslatableTerms(file.dialogue());
		} catch (BadRequestException e) {
			return new ImportedTranslation(file.fileName(), dialogueName, file.replaces(),
					null, null);
		}
		Set<String> termKeys = new HashSet<>();
		for (TranslatableTermSummary term : terms) {
			termKeys.add(key(term.getSpeaker(), term.getTerm()));
		}
		Set<String> entryKeys = entryKeys(file.content());
		int unmatched = (int) entryKeys.stream().filter(k -> !termKeys.contains(k)).count();
		int untranslated = (int) termKeys.stream().filter(k -> !entryKeys.contains(k)).count();
		return new ImportedTranslation(file.fileName(), dialogueName, file.replaces(), unmatched,
				untranslated);
	}

	/**
	 * The speaker and normalised text of every entry in checked translation content: the
	 * speaker is the top-level group an entry is in, also for entries in context groups nested
	 * inside it.
	 */
	private static Set<String> entryKeys(String content) {
		JsonNode root;
		try {
			root = JSON.readTree(content);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Checked translation content doesn't parse", e);
		}
		Set<String> keys = new HashSet<>();
		root.properties().forEach(speaker ->
				addEntryKeys(speaker.getKey(), speaker.getValue(), keys));
		return keys;
	}

	private static void addEntryKeys(String speaker, JsonNode group, Set<String> keys) {
		group.properties().forEach(entry -> {
			if (entry.getValue().isObject()) addEntryKeys(speaker, entry.getValue(), keys);
			else keys.add(key(speaker, entry.getKey()));
		});
	}

	private static String key(String speaker, String text) {
		return speaker + "\n" + String.join(" ", text.trim().split("\\s+"));
	}
}

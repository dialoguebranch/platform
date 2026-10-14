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
import com.dialoguebranch.execution.parser.ParserResult;
import com.dialoguebranch.web.service.controller.schema.authoring.ImportedScript;
import com.dialoguebranch.web.service.exception.BadRequestException;
import com.dialoguebranch.web.service.exception.ProjectParseHttpError;
import com.dialoguebranch.web.service.storage.model.DBDraftDialogue;
import com.dialoguebranch.web.service.storage.model.DBProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Imports {@code .dlb} scripts into one folder of an existing project, as draft content (#339).
 * All or nothing: every file is checked first, and if any is refused, nothing is imported.
 *
 * @author Harm op den Akker
 */
@Service
public class ScriptImportService {

	private static final String SCRIPT_EXTENSION = ".dlb";

	private final DraftDialogueService draftDialogueService;

	/**
	 * Creates the service.
	 *
	 * @param draftDialogueService the service storing draft dialogues.
	 */
	public ScriptImportService(DraftDialogueService draftDialogueService) {
		this.draftDialogueService = draftDialogueService;
	}

	/** A file that passed every check, ready to be written. */
	private record CheckedScript(String fileName, String dialogueName, String content,
			Optional<DBDraftDialogue> existing, List<String> parseErrors) {
	}

	/**
	 * Imports scripts into a folder of a project. Each file becomes the draft dialogue named
	 * after the folder and the file name without {@code .dlb}.
	 *
	 * @param project the project.
	 * @param folder  the folder to import into; empty or {@code null} for the project root.
	 * @param files   the {@code .dlb} files.
	 * @param replace whether a file may replace the content of an existing dialogue (including one
	 *                pending deletion, which is then restored); if not, an existing dialogue
	 *                refuses the import.
	 * @return the result per file, in the order given.
	 * @throws BadRequestException if any file is refused; its errors list the problems per file
	 * name, and nothing is imported.
	 */
	@Transactional(rollbackFor = Exception.class)
	public List<ImportedScript> importScripts(DBProject project, String folder,
			List<MultipartFile> files, boolean replace) throws BadRequestException {
		String folderPath = ImportFiles.requireFolder(folder);
		ImportFiles.requireFileCount(files);

		Map<String, List<String>> problems = new LinkedHashMap<>();
		Set<String> namesInImport = new HashSet<>();
		List<CheckedScript> checked = new ArrayList<>();
		for (MultipartFile file : files) {
			String fileName = ImportFiles.baseName(file.getOriginalFilename());
			List<String> fileProblems = new ArrayList<>();
			CheckedScript script = check(project, folderPath, fileName, file, replace,
					namesInImport, fileProblems);
			if (!fileProblems.isEmpty()) problems.put(fileName, fileProblems);
			else checked.add(script);
		}
		if (!problems.isEmpty()) {
			throw new BadRequestException(new ProjectParseHttpError(
					"Nothing was imported: " + problems.size() + " of " + files.size() +
					" file(s) can't be imported.", problems));
		}

		List<ImportedScript> results = new ArrayList<>();
		for (CheckedScript script : checked) {
			if (script.existing().isPresent()) {
				draftDialogueService.replaceDialogueFromScript(script.existing().get(),
						script.content());
			} else {
				draftDialogueService.createDialogueFromScript(project, script.dialogueName(),
						script.content());
			}
			results.add(new ImportedScript(script.fileName(), script.dialogueName(),
					script.existing().isPresent(), script.parseErrors()));
		}
		return results;
	}

	private CheckedScript check(DBProject project, String folderPath, String fileName,
			MultipartFile file, boolean replace, Set<String> namesInImport,
			List<String> problems) {
		if (!fileName.toLowerCase().endsWith(SCRIPT_EXTENSION)) {
			problems.add("not a .dlb file");
			return null;
		}
		String baseName = fileName.substring(0, fileName.length() - SCRIPT_EXTENSION.length());
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
		String content = ImportFiles.readText(file, problems);
		if (content == null) return null;
		Optional<DBDraftDialogue> existing = draftDialogueService.findDialogue(project,
				dialogueName);
		if (existing.isPresent() && !replace) {
			problems.add("dialogue '" + dialogueName + "' already exists" +
					(existing.get().getIsDeleted() ? " (pending deletion)" : ""));
		}
		problems.addAll(draftDialogueService.checkScriptForDraft(content));
		return new CheckedScript(fileName, dialogueName, content, existing,
				parseErrors(dialogueName, content));
	}

	private static List<String> parseErrors(String dialogueName, String content) {
		try (DialogueBranchParser parser = new DialogueBranchParser(dialogueName,
				new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))) {
			ParserResult result = parser.readDialogue();
			return result.getParseErrors().stream().map(Throwable::getMessage).toList();
		} catch (IOException e) {
			return List.of("Could not be parsed: " + e.getMessage());
		}
	}
}

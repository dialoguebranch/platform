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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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

	/** The most files one import may contain. */
	static final int MAX_FILES = 100;

	/** The largest file one import may contain, in bytes. */
	static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

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
		String folderPath = normalizeFolder(folder);
		if (folderPath == null) {
			throw new BadRequestException("Invalid folder '" + folder + "': use names of " +
					"letters, digits, '_' and '-', separated by '/'.");
		}
		if (files == null || files.isEmpty()) {
			throw new BadRequestException("No files to import.");
		}
		if (files.size() > MAX_FILES) {
			throw new BadRequestException("Too many files: at most " + MAX_FILES + " per import.");
		}

		Map<String, List<String>> problems = new LinkedHashMap<>();
		Set<String> namesInImport = new HashSet<>();
		List<CheckedScript> checked = new ArrayList<>();
		for (MultipartFile file : files) {
			String fileName = baseName(file.getOriginalFilename());
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
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			problems.add("larger than " + (MAX_FILE_SIZE_BYTES / 1024) + " KB");
			return null;
		}
		String content;
		try {
			content = decodeUtf8(file.getBytes());
		} catch (CharacterCodingException e) {
			problems.add("not a UTF-8 text file");
			return null;
		} catch (IOException e) {
			problems.add("could not be read: " + e.getMessage());
			return null;
		}
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

	/**
	 * Returns the folder as a dialogue name prefix, without leading or trailing slashes; empty
	 * for the project root, or {@code null} if it isn't a valid folder.
	 */
	static String normalizeFolder(String folder) {
		if (folder == null) return "";
		String trimmed = folder.trim();
		while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
		while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
		if (trimmed.isEmpty()) return "";
		return trimmed.matches(DialogueBranchParser.DIALOGUE_NAME_REGEX) ? trimmed : null;
	}

	/** The file name without any directory part a browser or client may have sent along. */
	private static String baseName(String originalFilename) {
		if (originalFilename == null) return "";
		String name = originalFilename.replace('\\', '/');
		return name.substring(name.lastIndexOf('/') + 1);
	}

	/** Strict UTF-8 decoding, without a leading byte order mark. */
	static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
		String text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		return text.startsWith("﻿") ? text.substring(1) : text;
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

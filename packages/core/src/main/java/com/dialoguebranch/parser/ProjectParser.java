/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *      as outlined below. Based on original source code licensed under the following terms:
 *
 *                                            ----------
 *
 * Copyright 2019-2022 WOOL Foundation - Licensed under the MIT License:
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

package com.dialoguebranch.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.dialoguebranch.model.*;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.i18n.I18nLanguageFinder;
import com.dialoguebranch.i18n.ContextTranslation;
import com.dialoguebranch.i18n.Translatable;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.i18n.TranslationParser;
import com.dialoguebranch.i18n.TranslationParserResult;
import com.dialoguebranch.i18n.Translator;

/**
 * This class can read an entire Dialogue Branch project consisting of dialogue script files (files
 * with an extension of {@link Constants#DLB_SCRIPT_FILE_EXTENSION}) and translation files (with an
 * extension of {@link Constants#DLB_TRANSLATION_FILE_EXTENSION} as provided through the given
 * {@link FileLoader} implementation.
 *
 * @author Dennis Hofs (Roessingh Research and Development)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ProjectParser {
	private final FileLoader fileLoader;

	private final Map<FileDescriptor, Dialogue> dialogues = new LinkedHashMap<>();
	private final Map<FileDescriptor,Map<Translatable,List<ContextTranslation>>>
			translations = new LinkedHashMap<>();
	private final Map<FileDescriptor, Dialogue> translatedDialogues = new LinkedHashMap<>();

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an instance of a {@link ProjectParser} with a given {@link FileLoader} that is used
	 * to retrieve a complete set of files (both script and translation files) to use in this
	 * parser.
	 *
	 * @param fileLoader the {@link FileLoader} implementation.
	 */
	public ProjectParser(FileLoader fileLoader) {
		this.fileLoader = fileLoader;
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	public ProjectParserResult parse() throws IOException {
		ProjectParserResult projectParserResult = new ProjectParserResult(fileLoader);

		List<FileDescriptor> files = fileLoader.listDialogueBranchFiles();

		parseFiles(files, projectParserResult);

		if (!projectParserResult.getParseErrors().isEmpty())
			return projectParserResult;

		createTranslatedDialogues(projectParserResult);

		if (!projectParserResult.getParseErrors().isEmpty())
			return projectParserResult;

		Project project = new Project();
		project.setDialogues(translatedDialogues);

		Map<FileDescriptor, Dialogue> sourceDialogues = new LinkedHashMap<>();
		for (FileDescriptor fileDescription : dialogues.keySet()) {
			sourceDialogues.put(fileDescription, dialogues.get(fileDescription));
		}
		project.setSourceDialogues(sourceDialogues);

		Map<FileDescriptor,Map<Translatable,List<ContextTranslation>>> dlgTranslations =
				new LinkedHashMap<>();
		for (FileDescriptor fileDescription : translations.keySet()) {
			dlgTranslations.put(fileDescription, translations.get(fileDescription));
		}

		project.setTranslations(dlgTranslations);
		projectParserResult.setProject(project);
		return projectParserResult;
	}

	/**
	 * Tries to parse all project files (dialogue and translation files). This method fills
	 * variables "dialogues" and "translations". Any parse errors will be added to the provided
	 * {@code readResult}.
	 *
	 * <p>It uses "dialogueFiles" and "translationFiles". They will be cleared in the end.</p>
	 *
	 * @param fileDescriptions the project files
	 * @param readResult the read result
	 * @throws IOException if a reading error occurs
	 */
	private void parseFiles(List<FileDescriptor> fileDescriptions,
							ProjectParserResult readResult) throws IOException {
		Set<FileDescriptor> fileDescriptionsSet = new HashSet<>();
		List<FileDescriptor> dialogueFiles = new ArrayList<>();
		List<FileDescriptor> translationFiles = new ArrayList<>();

		// Split the given fileDescriptions into dialogueFiles and translationFiles
		for (FileDescriptor fileDescription : fileDescriptions) {
			if (fileDescription.getFileType() == ResourceType.SCRIPT)
				dialogueFiles.add(fileDescription);
			else if (fileDescription.getFileType() == ResourceType.TRANSLATION)
				translationFiles.add(fileDescription);
		}

		Set<String> dialogueNames = new HashSet<>();
		for (FileDescriptor fileDescription : dialogueFiles) {
			fileDescriptionsSet.add(fileDescription);
			ParserResult dlgReadResult = parseDialogueFile(fileDescription);
			if (dlgReadResult.getParseErrors().isEmpty()) {
				dialogues.put(fileDescription, dlgReadResult.getDialogue());
				dialogueNames.add(dlgReadResult.getDialogue().getDialogueName());
			} else {
				getParseErrors(readResult, fileDescription).addAll(dlgReadResult.getParseErrors());
			}
		}

		if (readResult.getParseErrors().isEmpty()) {
			// validate referenced dialogues in external node pointers
			for (FileDescriptor fileDescription : dialogues.keySet()) {
				Dialogue dlg = dialogues.get(fileDescription);
				for (String refName : dlg.getDialoguesReferenced()) {
					if (!dialogueNames.contains(refName)) {
						getParseErrors(readResult, fileDescription).add(
							new ParseException(String.format(
							"Found external node pointer in dialogue %s to unknown dialogue %s",
							dlg.getDialogueName(), refName)));
					}
				}
			}
		}

		for (FileDescriptor fileDescription : translationFiles) {
			if (fileDescriptionsSet.contains(fileDescription)) {
				getParseErrors(readResult, fileDescription).add(new ParseException(
					String.format("Found both translation file \"%s\" and dialogue file \"%s.dlb\"",
					fileDescription.getFilePath(), fileDescription.getFilePath()) + ": " +
					fileDescription));
				continue;
			}
			TranslationParserResult transParseResult = parseTranslationFile(fileDescription);
			if (!transParseResult.getParseErrors().isEmpty()) {
				getParseErrors(readResult, fileDescription).addAll(
						transParseResult.getParseErrors());
			}
			if (!transParseResult.getWarnings().isEmpty()) {
				getWarnings(readResult, fileDescription).addAll(transParseResult.getWarnings());
			}
			if (transParseResult.getParseErrors().isEmpty())
				translations.put(fileDescription, transParseResult.getTranslations());
		}
	}

	private List<ParseException> getParseErrors(ProjectParserResult readResult,
												FileDescriptor fileDescription) {
		String path = fileDescriptionToPath(fileDescription);
		List<ParseException> errors = readResult.getParseErrors().get(path);
		if (errors != null)
			return errors;
		errors = new ArrayList<>();
		readResult.getParseErrors().put(path, errors);
		return errors;
	}

	private List<String> getWarnings(ProjectParserResult readResult,
									 FileDescriptor fileDescription) {
		String path = fileDescriptionToPath(fileDescription);
		List<String> warnings = readResult.getWarnings().get(path);
		if (warnings != null)
			return warnings;
		warnings = new ArrayList<>();
		readResult.getWarnings().put(path, warnings);
		return warnings;
	}

	/**
	 * Tries to create translated dialogues for all translation files. This method fills variable
	 * "translatedDialogues" with the dialogues from "dialogues" plus translated dialogues from
	 * "translations". Any parse errors will be added to "readResult".
	 *
	 * <p>It uses "dialogues" and "translations". They will be cleared in the end.</p>
	 *
	 * @param readResult the read result
	 */
	private void createTranslatedDialogues(ProjectParserResult readResult) {
		for (FileDescriptor fileDescription : dialogues.keySet()) {
			Dialogue dlg = dialogues.get(fileDescription);
			translatedDialogues.put(fileDescription, dlg);
		}

		for (FileDescriptor fileDescription : translations.keySet()) {
			Dialogue source = findSourceDialogue(fileDescription.getDialogueName());
			if (source == null) {
				getParseErrors(readResult, fileDescription).add(new ParseException(
						"No source dialogue found for translation: " + fileDescription));
				continue;
			}
			Translator translator = new Translator(
					new TranslationContext(), translations.get(fileDescription));
			Dialogue translated = translator.translate(source);
			translatedDialogues.put(fileDescription, translated);
		}
	}

	private Dialogue findSourceDialogue(String dlgName) {
		List<FileDescriptor> matches = new ArrayList<>();
		for (FileDescriptor fileDescription : dialogues.keySet()) {
			String currDlgName = fileDescription.getDialogueName();
			if (currDlgName.equals(dlgName))
				matches.add(fileDescription);
		}
		if (matches.isEmpty())
			return null;
		if (matches.size() == 1)
			return dialogues.get(matches.get(0));
		Map<String, FileDescriptor> lngMap = new HashMap<>();
		for (FileDescriptor match : matches) {
			lngMap.put(match.getLanguage(), match);
		}
		I18nLanguageFinder finder = new I18nLanguageFinder(new ArrayList<>(
				lngMap.keySet()));
		finder.setUserLocale(Locale.ENGLISH);
		String language = finder.find();
		if (language == null)
			return dialogues.get(matches.get(0));
		else
			return dialogues.get(lngMap.get(language));
	}

	private ParserResult parseDialogueFile(FileDescriptor description)
			throws IOException {
		String dlgName = description.getDialogueName();
		try (DialogueBranchParser dialogueBranchParser = new DialogueBranchParser(dlgName,
				fileLoader.openFile(description))) {
			return dialogueBranchParser.readDialogue();
		}
	}

	private TranslationParserResult parseTranslationFile(FileDescriptor description)
			throws IOException {
		try (Reader reader = fileLoader.openFile(description)) {
			return TranslationParser.parse(reader);
		}
	}

	private String fileDescriptionToPath(FileDescriptor fileDescription) {
		return fileDescription.getLanguage() + "/" + fileDescription.getFilePath();
	}
}

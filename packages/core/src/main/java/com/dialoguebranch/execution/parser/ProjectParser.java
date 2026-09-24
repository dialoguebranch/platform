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

package com.dialoguebranch.execution.parser;

import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.i18n.ContextTranslation;
import com.dialoguebranch.i18n.Translatable;
import com.dialoguebranch.i18n.TranslationContext;
import com.dialoguebranch.i18n.TranslationParser;
import com.dialoguebranch.i18n.TranslationParserResult;
import com.dialoguebranch.i18n.Translator;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.execute.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class can read an entire Dialogue Branch project consisting of dialogue script files (files
 * with an extension of {@link DialogueBranchConstants#DLB_SCRIPT_FILE_EXTENSION}) and translation files (with an
 * extension of {@link DialogueBranchConstants#DLB_TRANSLATION_FILE_EXTENSION} as provided through the given
 * {@link ScriptLoader} implementation.
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class ProjectParser {
	private final ScriptLoader scriptLoader;

	private final Map<ResourcePointer, Dialogue> dialogues = new LinkedHashMap<>();
	private final Map<ResourcePointer,Map<Translatable,List<ContextTranslation>>>
			translations = new LinkedHashMap<>();
	private final Map<ResourcePointer, Dialogue> translatedDialogues = new LinkedHashMap<>();

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a {@link ProjectParser} with a given {@link ScriptLoader} that is used
	 * to retrieve a complete set of files (both script and translation files) to use in this
	 * parser.
	 *
	 * @param scriptLoader the {@link ScriptLoader} implementation.
	 */
	public ProjectParser(ScriptLoader scriptLoader) {
		this.scriptLoader = scriptLoader;
	}

	// ------------------------------------------------------- //
	// -------------------- Other Methods -------------------- //
	// ------------------------------------------------------- //

	/**
	 * Parses the complete Dialogue Branch project (all script and translation files provided by
	 * the {@link ScriptLoader}) and returns a {@link ProjectParserResult} containing either the
	 * fully assembled {@link ExecutableProject} or a map of per-file parse errors.
	 *
	 * @return the result of parsing the project.
	 * @throws IOException if a file cannot be read.
	 */
	public ProjectParserResult parse() throws IOException {
		ProjectParserResult projectParserResult = new ProjectParserResult(scriptLoader);

		List<ResourcePointer> files = scriptLoader.listDialogueBranchFiles();

		parseFiles(files, projectParserResult);

		if (!projectParserResult.getParseErrors().isEmpty())
			return projectParserResult;

		createTranslatedDialogues(projectParserResult);

		if (!projectParserResult.getParseErrors().isEmpty())
			return projectParserResult;

		ExecutableProject project = new ExecutableProject();
		project.setDialogues(translatedDialogues);

		Map<ResourcePointer, Dialogue> sourceDialogues = new LinkedHashMap<>();
		for (ResourcePointer fileDescription : dialogues.keySet()) {
			sourceDialogues.put(fileDescription, dialogues.get(fileDescription));
		}
		project.setSourceDialogues(sourceDialogues);

		Map<ResourcePointer,Map<Translatable,List<ContextTranslation>>> dlgTranslations =
				new LinkedHashMap<>();
		for (ResourcePointer fileDescription : translations.keySet()) {
			dlgTranslations.put(fileDescription, translations.get(fileDescription));
		}

		project.setTranslations(dlgTranslations);

		if (scriptLoader instanceof ProjectScriptLoader projectScriptLoader)
			project.setMetaData(projectScriptLoader.getProjectMetaData());

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
	private void parseFiles(List<ResourcePointer> fileDescriptions,
							ProjectParserResult readResult) throws IOException {
		Set<ResourcePointer> fileDescriptionsSet = new HashSet<>();
		List<ResourcePointer> dialogueFiles = new ArrayList<>();
		List<ResourcePointer> translationFiles = new ArrayList<>();

		// Split the given fileDescriptions into dialogueFiles and translationFiles
		for (ResourcePointer fileDescription : fileDescriptions) {
			if (fileDescription.getResourceType() == ResourceType.SCRIPT)
				dialogueFiles.add(fileDescription);
			else if (fileDescription.getResourceType() == ResourceType.TRANSLATION)
				translationFiles.add(fileDescription);
		}

		// Every dialogue file that produced a Dialogue object at all, whether or not it also has
		// parse errors of its own — used below so a dialogue's external node pointers are still
		// checked even when that same dialogue has an unrelated (e.g. internal-pointer) error.
		Map<ResourcePointer, Dialogue> allParsedDialogues = new LinkedHashMap<>();

		for (ResourcePointer fileDescription : dialogueFiles) {
			fileDescriptionsSet.add(fileDescription);
			ParserResult dlgReadResult = parseDialogueFile(fileDescription);
			if (dlgReadResult.getDialogue() != null)
				allParsedDialogues.put(fileDescription, dlgReadResult.getDialogue());
			if (dlgReadResult.getParseErrors().isEmpty()) {
				dialogues.put(fileDescription, dlgReadResult.getDialogue());
			} else {
				getParseErrors(readResult, fileDescription).addAll(dlgReadResult.getParseErrors());
			}
		}

		// The three checks below are independent validation passes, each scanning whichever
		// dialogues parsed successfully above regardless of whether some OTHER dialogue file
		// failed to parse — those are unrelated errors and must not suppress reporting of these
		// ones (previously this whole block was gated on readResult.getParseErrors().isEmpty(),
		// which meant a single unrelated parse error anywhere in the project — e.g. an
		// internal-pointer error in the very same dialogue — silently hid every
		// external-pointer error project-wide).
		Map<String, Dialogue> dialoguesByName = DuplicateDialogueNameValidator.buildDialoguesByName(
				dialogues, (file, error) -> getParseErrors(readResult, file).add(error));

		ExternalNodePointerValidator.validate(allParsedDialogues, dialoguesByName,
				(file, error) -> getParseErrors(readResult, file).add(error));

		OrphanedNodeValidator.detect(allParsedDialogues, dialoguesByName,
				(file, warning) -> getWarnings(readResult, file).add(warning));

		for (ResourcePointer fileDescription : translationFiles) {
			if (fileDescriptionsSet.contains(fileDescription)) {
				getParseErrors(readResult, fileDescription).add(new ParseException(
					String.format("Found both translation file \"%s\" and dialogue file \"%s.dlb\"",
					fileDescription.getDialogueName(), fileDescription.getDialogueName()) + ": " +
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
												ResourcePointer fileDescription) {
		return getOrCreateList(readResult.getParseErrors(),
				fileDescriptionToPath(fileDescription));
	}

	private List<String> getWarnings(ProjectParserResult readResult,
									 ResourcePointer fileDescription) {
		return getOrCreateList(readResult.getWarnings(),
				fileDescriptionToPath(fileDescription));
	}

	/** Shared compute-if-absent logic behind {@link #getParseErrors}/{@link #getWarnings} — the
	 *  two differ only in the list's element type. */
	private static <T> List<T> getOrCreateList(Map<String, List<T>> map, String key) {
		List<T> list = map.get(key);
		if (list != null)
			return list;
		list = new ArrayList<>();
		map.put(key, list);
		return list;
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
		for (ResourcePointer fileDescription : dialogues.keySet()) {
			Dialogue dlg = dialogues.get(fileDescription);
			translatedDialogues.put(fileDescription, dlg);
		}

		for (ResourcePointer fileDescription : translations.keySet()) {
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

	/**
	 * Finds the source {@link Dialogue} with the given dialogue name. A project has exactly one
	 * source language, so a name resolves to at most one source script (a name in two language
	 * folders is rejected as a parse error before this point).
	 *
	 * @param dlgName the dialogue name to look up.
	 * @return the source {@link Dialogue}, or {@code null} if there is none.
	 */
	private @Nullable Dialogue findSourceDialogue(String dlgName) {
		for (Map.Entry<ResourcePointer, Dialogue> entry : dialogues.entrySet()) {
			if (entry.getKey().getDialogueName().equals(dlgName))
				return entry.getValue();
		}
		return null;
	}

	private ParserResult parseDialogueFile(ResourcePointer description)
			throws IOException {
		String dlgName = description.getDialogueName();
		try (DialogueBranchParser dialogueBranchParser = new DialogueBranchParser(dlgName,
				scriptLoader.openFile(description))) {
			return dialogueBranchParser.readDialogue();
		}
	}

	private TranslationParserResult parseTranslationFile(ResourcePointer description)
			throws IOException {
		try (Reader reader = scriptLoader.openFile(description)) {
			return TranslationParser.parse(reader);
		}
	}

	private String fileDescriptionToPath(ResourcePointer fileDescription) {
		return fileDescription.getLanguage() + "/" + fileDescription.getDialogueName();
	}
}

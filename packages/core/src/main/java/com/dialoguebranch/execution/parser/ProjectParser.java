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
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

		// Validate external node pointers among whichever dialogues parsed successfully above,
		// regardless of whether some OTHER dialogue file failed to parse — those are unrelated
		// errors and must not suppress reporting of these ones (previously this whole block was
		// gated on readResult.getParseErrors().isEmpty(), which meant a single unrelated parse
		// error anywhere in the project — e.g. an internal-pointer error in the very same
		// dialogue — silently hid every external-pointer error project-wide).

		// Build a lookup from dialogue name to its (single) source Dialogue. A project has exactly
		// one source language, so a name resolving to more than one script file means the same
		// dialogue was placed in two language folders — reported here as a parse error rather
		// than silently picking one.
		Map<String, Dialogue> dialoguesByName = new HashMap<>();
		Map<String, ResourcePointer> fileByName = new HashMap<>();
		for (Map.Entry<ResourcePointer, Dialogue> entry : dialogues.entrySet()) {
			String name = entry.getValue().getDialogueName();
			ResourcePointer previous = fileByName.putIfAbsent(name, entry.getKey());
			if (previous != null) {
				getParseErrors(readResult, entry.getKey()).add(new ParseException(String.format(
					"Dialogue \"%s\" is defined by more than one script file (found in language " +
					"folders \"%s\" and \"%s\")", name, previous.getLanguage(),
					entry.getKey().getLanguage())));
				continue;
			}
			dialoguesByName.put(name, entry.getValue());
		}

		// validate referenced dialogues and nodes in external node pointers — scanning every
		// dialogue that parsed at all (not just error-free ones), so a broken external pointer
		// sitting next to some other, unrelated error in the same dialogue is still reported.
		// Iterating the pointers themselves (rather than just the set of referenced dialogue
		// names) keeps the originating node's title on hand for the error message.
		for (ResourcePointer fileDescription : allParsedDialogues.keySet()) {
			Dialogue dlg = allParsedDialogues.get(fileDescription);
			for (ExternalNodePointer pointer : dlg.getExternalNodePointers()) {
				Dialogue target = dialoguesByName.get(pointer.getAbsoluteTargetDialogue());
				if (target == null) {
					getParseErrors(readResult, fileDescription).add(
						new ParseException(String.format(
						"Found external node pointer in node %s to unknown dialogue %s",
						pointer.getOriginNodeId(), pointer.getAbsoluteTargetDialogue())));
					continue;
				}
				if (!target.nodeExists(pointer.getTargetNodeId())) {
					getParseErrors(readResult, fileDescription).add(
						new ParseException(String.format(
						"Found external node pointer in node %s to non-existing node %s in " +
						"dialogue %s", pointer.getOriginNodeId(), pointer.getTargetNodeId(),
						pointer.getAbsoluteTargetDialogue())));
				}
			}
		}

		// Detecting orphaned nodes never affects parsing success or dialogue execution — a node
		// that nothing points to can simply never be reached, which is not an error by design
		// (a dialogue is not required to link every node it defines). It usually does indicate
		// an authoring mistake though (a branch left disconnected while editing), so it is
		// reported as a warning rather than a parse error.
		detectOrphanedNodes(allParsedDialogues, dialoguesByName, readResult);

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

	/**
	 * Reports a warning for every {@link Node} that no reply link (internal or external) points
	 * to and that is not its own {@link Dialogue}'s Start node. A Start node is always treated as
	 * reachable in its own right, since it is a valid standalone entry point (e.g. via the Web
	 * Service's {@code dialogue/start} end-point) regardless of whether anything within the
	 * project links to it.
	 *
	 * <p>An external node pointer into another dialogue marks its target node reachable, since the
	 * project parser has no way to know whether an external caller will address it.</p>
	 *
	 * @param allParsedDialogues every dialogue that parsed at all, keyed by its source file.
	 * @param dialoguesByName    every parsed dialogue, keyed by dialogue name.
	 * @param readResult         the result to add warnings to.
	 */
	private void detectOrphanedNodes(Map<ResourcePointer, Dialogue> allParsedDialogues,
									 Map<String, Dialogue> dialoguesByName,
									 ProjectParserResult readResult) {
		Map<Dialogue, Set<String>> reachableNodeIds = new HashMap<>();

		for (Dialogue dlg : allParsedDialogues.values()) {
			Set<String> reachable = reachableNodeIds.computeIfAbsent(dlg, (d) -> new HashSet<>());
			Node startNode = dlg.getStartNode();
			if (startNode != null)
				reachable.add(Objects.requireNonNull(startNode.getTitle()).toLowerCase());
			for (Node node : dlg.getNodes()) {
				for (NodePointer pointer
						: Objects.requireNonNull(node.getBody()).getNodePointers()) {
					if (pointer instanceof InternalNodePointer)
						reachable.add(pointer.getTargetNodeId().toLowerCase());
				}
			}
		}

		// External pointers can target nodes in OTHER dialogues, so fold those in across the
		// whole project after the per-dialogue internal pass above.
		for (Dialogue dlg : allParsedDialogues.values()) {
			for (ExternalNodePointer pointer : dlg.getExternalNodePointers()) {
				Dialogue targetDlg = dialoguesByName.get(pointer.getAbsoluteTargetDialogue());
				if (targetDlg == null)
					continue; // unknown target dialogue — already reported as a parse error
				reachableNodeIds.computeIfAbsent(targetDlg, (d) -> new HashSet<>())
						.add(pointer.getTargetNodeId().toLowerCase());
			}
		}

		for (Map.Entry<ResourcePointer, Dialogue> entry : allParsedDialogues.entrySet()) {
			Dialogue dlg = entry.getValue();
			Set<String> reachable = reachableNodeIds.getOrDefault(dlg, Set.of());
			for (Node node : dlg.getNodes()) {
				String nodeTitle = Objects.requireNonNull(node.getTitle());
				if (!reachable.contains(nodeTitle.toLowerCase())) {
					getWarnings(readResult, entry.getKey()).add(String.format(
							"Node \"%s\" is orphaned: no reply link points to it, and it is " +
							"not this dialogue's Start node", nodeTitle));
				}
			}
		}
	}

	private List<ParseException> getParseErrors(ProjectParserResult readResult,
												ResourcePointer fileDescription) {
		String path = fileDescriptionToPath(fileDescription);
		List<ParseException> errors = readResult.getParseErrors().get(path);
		if (errors != null)
			return errors;
		errors = new ArrayList<>();
		readResult.getParseErrors().put(path, errors);
		return errors;
	}

	private List<String> getWarnings(ProjectParserResult readResult,
									 ResourcePointer fileDescription) {
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

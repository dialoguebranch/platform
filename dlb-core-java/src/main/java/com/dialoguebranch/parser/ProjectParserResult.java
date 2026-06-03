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

import com.dialoguebranch.i18n.ContextTranslation;
import com.dialoguebranch.i18n.Translatable;
import com.dialoguebranch.model.Dialogue;
import com.dialoguebranch.model.FileDescriptor;
import nl.rrd.utils.exception.ParseException;
import com.dialoguebranch.model.Project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains the result of parsing a DialogueBranch project with the {@link
 * ProjectParser}.
 *
 * @author Dennis Hofs (RRD)
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class ProjectParserResult {

	private FileLoader fileLoader;
	private Project project = null;
	private Map<String,List<ParseException>> parseErrors = new LinkedHashMap<>();
	private Map<String,List<String>> warnings = new LinkedHashMap<>();

	// --------------------------------------------------------
	// -------------------- Constructor(s) --------------------
	// --------------------------------------------------------

	/**
	 * Creates an empty instance of a {@link ProjectParserResult}.
	 */
	public ProjectParserResult() { }

	/**
	 * Creates an instance of a {@link ProjectParserResult} with a given {@code fileLoader},
	 * indicating which {@link FileLoader} was used in the creation of these results.
	 * @param fileLoader the {@link FileLoader} implementation.
	 */
	public ProjectParserResult(FileLoader fileLoader) {
		this.fileLoader = fileLoader;
	}

	// -----------------------------------------------------------
	// -------------------- Getters & Setters --------------------
	// -----------------------------------------------------------

	/**
	 * Returns the {@link FileLoader} implementation that was used for generating this
	 * {@link ProjectParserResult}.
	 * @return the {@link FileLoader} implementation.
	 */
	public FileLoader getFileLoader() {
		return fileLoader;
	}

	public void setFileLoader(FileLoader fileLoader) {
		this.fileLoader = fileLoader;
	}

	/**
	 * Returns the project if parsing succeeded. Otherwise, it returns null.
	 *
	 * @return the project or null
	 */
	public Project getProject() {
		return project;
	}

	/**
	 * Sets the project if parsing succeeded.
	 *
	 * @param project the project
	 */
	public void setProject(Project project) {
		this.project = project;
	}

	/**
	 * Returns the parse errors. The keys are the paths to files with parse errors. A value should
	 * be a list with 1 or more errors.
	 *
	 * @return the parse errors
	 */
	public Map<String,List<ParseException>> getParseErrors() {
		return parseErrors;
	}

	/**
	 * Sets the parse errors. The keys are the paths to files with parse errors. A value should be a
	 * list with 1 or more errors.
	 *
	 * @param parseErrors the parse errors
	 */
	public void setParseErrors(Map<String,List<ParseException>> parseErrors) {
		this.parseErrors = parseErrors;
	}

	/**
	 * Returns the warnings. The keys are the paths to files with warnings. A value should be a list
	 * with 1 or more warnings.
	 *
	 * @return the warnings
	 */
	public Map<String, List<String>> getWarnings() {
		return warnings;
	}

	/**
	 * Sets the warnings. The keys are the paths to files with warnings. A value should be a list
	 * with 1 or more warnings.
	 *
	 * @param warnings the warnings
	 */
	public void setWarnings(Map<String, List<String>> warnings) {
		this.warnings = warnings;
	}

	/**
	 * Generates a human-readable summary {@link String} of this {@link ProjectParserResult}.
	 * @return human-readable summary {@link String} of this {@link ProjectParserResult}.
	 */
	public String generateSummaryString() {
		StringBuilder result = new StringBuilder();

		result.append("===== Summary of Results for Parsing DialogueBranch Project =====\n");

		// Get a string description of the project location (depending on the file loader used)
		String projectLocationDescription = "unknown";
		if(fileLoader instanceof ProjectFileLoader) {
			projectLocationDescription =
					((ProjectFileLoader)fileLoader).getProjectMetaData().getBasePath();
		} else if(fileLoader instanceof DirectoryFileLoader) {
			projectLocationDescription =
					((DirectoryFileLoader)fileLoader).rootDirectory().toString();
		}

		// In case of parse errors, print them and then return
		if(!this.getParseErrors().isEmpty()) {
			result.append("DialogueBranch project at ")
					.append(projectLocationDescription)
					.append(" contains ")
					.append("the following errors.\n");
			for (String key : this.getParseErrors().keySet()) {
				result.append("ERROR: Failed to parse file: ")
						.append(key)
						.append("\n");
				List<ParseException> parseExceptions = this.getParseErrors().get(key);
				for (ParseException parseException : parseExceptions) {
					result.append("  - ")
							.append(parseException.getMessage())
							.append("\n");
				}
			}
			return result.toString();
		}

		// In case there are no errors, first list all warnings
		if(!this.getWarnings().isEmpty()) {
			result.append("DialogueBranch project at ")
					.append(projectLocationDescription)
					.append(" contains ")
					.append("the following warnings.\n");
			for (String key : this.getWarnings().keySet()) {
				result.append("WARNING: ")
						.append(key)
						.append("\n");
				List<String> warningStrings = this.getWarnings().get(key);
				for (String warningString : warningStrings) {
					result.append("  - ")
							.append(warningString)
							.append("\n");
				}
			}
		}

		Project project = this.getProject();
		result.append("Project Summary:\n");
		result.append("Location: ")
				.append(projectLocationDescription)
				.append("\n");

		Map<FileDescriptor, Dialogue> sourceDialogues = project.getSourceDialogues();
		result.append("Number of Dialogue Scripts: ")
				.append(sourceDialogues.size())
				.append("\n");
		for(FileDescriptor dialogueDescription : sourceDialogues.keySet()) {
			result.append("  - ")
					.append(dialogueDescription)
					.append("\n");
		}

		Map<FileDescriptor,Map<Translatable,List<ContextTranslation>>> translations =
				project.getTranslations();
		result.append("Number of Translation Scripts: ")
				.append(translations.size())
				.append("\n");
		for(FileDescriptor dialogueDescription : translations.keySet()) {
			result.append("  - ")
					.append(dialogueDescription)
					.append("\n");
		}

		Map<FileDescriptor, Dialogue> dialogues = this.getProject().getDialogues();

		for (FileDescriptor dialogue : dialogues.keySet()) {
			result.append("----------");
			result.append("DIALOGUE ")
					.append(dialogue.getFilePath())
					.append(" (")
					.append(dialogue.getLanguage())
					.append(")\n");
			result.append(dialogues.get(dialogue)).append("\n");
		}

		return result.toString();
	}

}

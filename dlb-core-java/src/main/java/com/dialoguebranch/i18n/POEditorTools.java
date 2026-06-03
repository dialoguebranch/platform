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

package com.dialoguebranch.i18n;

import com.dialoguebranch.model.Constants;
import com.dialoguebranch.model.Dialogue;
import com.dialoguebranch.model.Node;
import com.dialoguebranch.parser.DialogueBranchParser;
import com.dialoguebranch.parser.ParserResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * POEditorTools is a runnable class that provides a Command-Line-Interface allowing you to
 * conveniently execute different scripts covering different scenarios related to importing from- or
 * exporting to POEditor.
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 * @author Tessa Beinema (University of Twente)
 */
public class POEditorTools {

	/**
	 * Takes a Key-Value JSON export from POEditor and generates a set of {@link TranslationFile}
	 * objects for each different dialogue found in the JSON file. The input JSON-file has the
	 * following structure:
	 * <pre>
	 * {
	 *     "dialogue-name Speaker": {
	 *         "term": "translation",
	 *         "term": "translation",
	 *         "term": "translation"
	 *     },
	 *     "dialogue-name _user": {
	 *         "term": "translation",
	 *         ...
	 *     }
	 * }</pre>
	 *
	 * @param jsonFile a {@link File} object pointing to a Key-Value-JSON export from POEditor with
	 *                 the format as defined above.
	 * @return a mapping from {@link String}s (dialogue names) to {@link TranslationFile} objects.
	 * @throws IOException in case any error occurs in parsing the JSON from the input file.
	 */
	public Map<String, TranslationFile> generateTranslationFilesFromPOEditorExport(File jsonFile)
			throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		TypeReference<Map<String, Map<String, String>>> mapType = new TypeReference<>(){};
		Map<String, Map<String,String>> terms = mapper.readValue(jsonFile, mapType);

		Map<String, TranslationFile> translationFiles = new HashMap<>();

		// Iterate over all contexts available in the JSON body
		for (String contextString : terms.keySet()) {
			String dialogueName = contextString.split(" ")[0];
			String speakerName = contextString.split(" ")[1];

			TranslationFile translationFile;
			if (translationFiles.containsKey(dialogueName)) {
				translationFile = translationFiles.get(dialogueName);
			} else {
				translationFile = new TranslationFile(dialogueName);
				translationFiles.put(dialogueName, translationFile);
			}

			Map<String, String> termsMap = terms.get(contextString);
			for (String term : termsMap.keySet()) {
				String translation = termsMap.get(term);
				translationFile.addTerm(speakerName, term, translation);
			}

		}
		return translationFiles;
	}

	/**
	 * Generates a {@link List} of {@link TranslationTerm}s from a .dlb script located at the given
	 * {@link File} location.
	 *
	 * @param dlbScriptFile a {@link File} link to a .dlb script.
	 * @return all translatable terms as a {@link List} of {@link TranslationTerm}s
	 * @throws IOException in case of an IO error when reading in the .dlb script
	 */
	public List<TranslationTerm> extractTranslationTermsFromDLBScript(File dlbScriptFile)
			throws IOException {
		// Read in the dialogue from the .dlb script file
		Dialogue dialogue = readDialogueFile(dlbScriptFile);

		System.out.println("===== Processing: " + dialogue.getDialogueName() + " with "
				+ dialogue.getNodeCount() + " nodes. =====");

		ArrayList<TranslationTerm> terms = new ArrayList<>();

		for (Node node : dialogue.getNodes()) {
			TranslatableExtractor extractor = new TranslatableExtractor();
			List<SourceTranslatable> translatables = extractor.extractFromBody(
					node.getHeader().getSpeaker(), SourceTranslatable.USER, node.getBody());

			for(SourceTranslatable translatable : translatables) {
				TranslationTerm term = new TranslationTerm(translatable.translatable()
						.toExportFriendlyString(),dialogue.getDialogueName() + " "
						+ translatable.speaker());
				terms.add(term);
			}

		}
		return terms;
	}

	/**
	 * If the given {@code dlbScriptFile} is a correct {@link File} pointer to a .dlb script, this
	 * function will return a {@link Set} of {@link File}s that contains all the .dlb scripts that
	 * are linked from the given {@code dlbScriptFile} and recursively from those referenced .dlb
	 * scripts.
	 *
	 * @param allDialogueFiles call this method with an empty set of files, {@code allDialogueFiles}
	 *                         is used to store progressively the encountered .dlb script {@link
	 *                         File}s as the method recursively traverses the dialogue tree.
	 * @param dlbScriptFile the origin .dlb script {@link File} pointer.
	 * @return a {@link Set} of {@link File}s that represent all .dlb scripts that are linked
	 *         through {@code dlbScriptFile} (including itself).
	 * @throws IOException in case of a read error for any of the .dlb scripts.
	 */
	public Set<File> getCompleteReferencedDialoguesSet(Set<File> allDialogueFiles,
													   File dlbScriptFile) throws IOException {
		Dialogue dialogue = readDialogueFile(dlbScriptFile);

		// Include the given root dlbScriptFile if not already in the result set
		allDialogueFiles.add(dlbScriptFile);

		// Get all dialogues that are referenced from the given dlbScriptFile
		Set<String> referencedDialogues = dialogue.getDialoguesReferenced();
		for(String referencedDialogue : referencedDialogues) {
			File referencedDialogueFile = new File(dlbScriptFile.getParent()
					+ File.separator + referencedDialogue + Constants.DLB_SCRIPT_FILE_EXTENSION);
			if(!allDialogueFiles.contains(referencedDialogueFile)) {
				allDialogueFiles.add(referencedDialogueFile);
				Set<File> additionalDialogueFiles = getCompleteReferencedDialoguesSet(
						allDialogueFiles,
						new File(dlbScriptFile.getParent() + File.separator
								+ referencedDialogue + Constants.DLB_SCRIPT_FILE_EXTENSION));
				allDialogueFiles.addAll(additionalDialogueFiles);
			}
		}
		return allDialogueFiles;
	}

	/**
	 * Returns a {@link Dialogue} object as read in from a DialogueBranch script identified by the
	 * given {@code fileName}.
	 *
	 * @param dlbScriptFile the .dlb script {@link File} to read
	 * @return a {@link Dialogue} object representation of the given .dlb script
	 */
	public Dialogue readDialogueFile (File dlbScriptFile) {
		try(DialogueBranchParser dialogueBranchParser = new DialogueBranchParser(dlbScriptFile)) {
			ParserResult parserResult = dialogueBranchParser.readDialogue();
			return parserResult.getDialogue();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Takes a given {@link List} of {@link TranslationTerm}s and writes them to the given {@code
	 * exportFile} in JSON format.
	 *
	 * @param terms the {@link List} of {@link TranslationTerm}s to write to file.
	 * @param exportFile the file to write to.
	 * @throws IOException in case of any write error.
	 */
	public void writeTranslationTermsToJSON(List<TranslationTerm> terms, File exportFile)
			throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
		writer.writeValue(exportFile, terms);
	}

	public static void main(String[] args) {
		POEditorTools tools = new POEditorTools();

		System.out.println("""
                Welcome to the DialogueBranch POEditor Command Line Tool.

                This command line tool is used for a number of different scenarios for converting Dialogue Branch and POEditor file formats.
                Since you haven't provided command line arguments, we will take you through an interactive menu to determine your desired scenario and parameters.""");

		System.out.println("The following scenarios are currently supported:\n");
		System.out.println("  1. Generate a single POEditor Terms file from a .dlb script, " +
				"including all linked scripts.");
		System.out.println("  2. Generate multiple POEditor Terms files from a .dlb script, " +
				"including all linked scripts.");
		System.out.println("  3. Convert a single POEditor Key-Value JSON export to one or " +
				"many DialogueBranch Translation JSON files.");

		Scanner userInputScanner = new Scanner(System.in);  // Create a Scanner object
		System.out.print("\nChoose scenario: ");

		String scenario = userInputScanner.nextLine();  // Read user input

		File outputDirectory;
		String dlbScriptFile;
		Set<File> allDialogues;

		switch(scenario) {
			case "1":
				System.out.println("Please provide the full file path to the starting " +
						".dlb script.");

				System.out.print("DialogueBranch Script File: ");
				dlbScriptFile = userInputScanner.nextLine();

				try {
					allDialogues = tools.getCompleteReferencedDialoguesSet(new HashSet<>(),
							new File(dlbScriptFile));
					System.out.println("Found a total of " + allDialogues.size() +
							" linked dialogue scripts: ");
					for(File dialogueFile : allDialogues) {
						System.out.println(dialogueFile);
					}

				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				// A list for collection all terms from all files
				List<TranslationTerm> allTerms = new ArrayList<>();

				for(File dialogueFile : allDialogues) {
					try {
						List<TranslationTerm> terms =
								tools.extractTranslationTermsFromDLBScript(dialogueFile);
						allTerms.addAll(terms);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}

				// Write to outputFile
				System.out.println("Where would you like to save the exported terms?");

				System.out.print("POEditor Terms export file: ");
				String poEditorTermsExportFileName = userInputScanner.nextLine();

				try {
					tools.writeTranslationTermsToJSON(allTerms,
							new File(poEditorTermsExportFileName));
				} catch (
						IOException e) {
					throw new RuntimeException(e);
				}
				break;
			case "2":
				System.out.println("Please provide the full file path to the starting " +
						".dlb script.");

				System.out.print("DialogueBranch Script File: ");
				dlbScriptFile = userInputScanner.nextLine();

				try {
					allDialogues = tools.getCompleteReferencedDialoguesSet(new HashSet<>(),
							new File(dlbScriptFile));
					System.out.println("Found a total of " + allDialogues.size() +
							" linked dialogue scripts: ");
					for(File dialogueFile : allDialogues) {
						System.out.println(dialogueFile);
					}

				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				System.out.println("Please choose a directory where you would like to store " +
						"the POEditor Terms files.");
				outputDirectory = tools.getOutputDirectoryInteractive();

				for(File dialogueFile : allDialogues) {
					try {
						List<TranslationTerm> terms = tools.extractTranslationTermsFromDLBScript(dialogueFile);
						tools.writeTranslationTermsToJSON(terms,new File(outputDirectory+File.separator+dialogueFile.getName()+".json"));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
				break;

			case "3":
				System.out.println("Please provide the full file path to the POEditor Key-Value-pair export file.");

				System.out.print("POEditor export file: ");
				String poEditorKeyValueFileName = userInputScanner.nextLine();

				File poEditorKeyValueFile = new File(poEditorKeyValueFileName);
				try {
					Map<String, TranslationFile> dlbTranslationFiles = tools.generateTranslationFilesFromPOEditorExport(poEditorKeyValueFile);
					System.out.println("Successfully read translations for "+dlbTranslationFiles.keySet().size()+" files.");
					System.out.println("Please choose a directory where you would like to store the DialogueBranch Translation files.");
					outputDirectory = tools.getOutputDirectoryInteractive();

					// The output directory should exist at this point...
					for (String s : dlbTranslationFiles.keySet()) {
						TranslationFile wtf = dlbTranslationFiles.get(s);
						wtf.writeToDirectory(outputDirectory);
					}
				} catch(IOException e) {
					System.out.println("An error has occurred reading from the given file '"
							+ poEditorKeyValueFile + "'.");
					System.exit(1);
				}
				break;
			default:
				System.out.println("Unknown scenario '"+scenario+"', please provide a number from the list provided above.");
				System.exit(1);
		}

		System.out.println("Finished.");
		System.exit(0);
	}

	private File getOutputDirectoryInteractive() {
		Scanner userInputScanner = new Scanner(System.in);
		System.out.print("Output directory: ");
		String outputDirectoryName = userInputScanner.nextLine();

		File outputDirectory = new File(outputDirectoryName);
		if(!outputDirectory.exists()) {
			System.out.println("The given directory '"+outputDirectoryName+"' does not exist, do you want to create it?");
			boolean inputUnderstood = false;
			boolean createDirectory = false;
			while(!inputUnderstood) {
				System.out.print("Create directory? ");
				String createDirectoryConfirm = userInputScanner.nextLine();
				if(createDirectoryConfirm.equals("y") || createDirectoryConfirm.equals("yes")) {
					createDirectory = true;
					inputUnderstood = true;
				} else if(createDirectoryConfirm.equals("n") || createDirectoryConfirm.equals("no")) {
					inputUnderstood = true;
				}
				if(!inputUnderstood) System.out.println("I don't know what you mean by '"+createDirectoryConfirm+"', why don't you try 'y' or 'n'?");
			}
			if(createDirectory) {
				if(outputDirectory.mkdir()) {
					System.out.println("Created directory '" + outputDirectory + "'.");
				} else {
					System.out.println("An error occurred in attempting to create the following directory: '"+outputDirectoryName+"', please try again.");
					System.exit(1);
				}
			} else {
				System.out.println("Please provide a valid output directory.");
				System.exit(0);
			}
		}
		return outputDirectory;
	}

}

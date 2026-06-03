/*
 *
 *                Copyright (c) 2023-2025 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
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

package com.dialoguebranch.cli;

import com.dialoguebranch.exception.DialogueBranchException;
import com.dialoguebranch.exception.InvalidInputException;
import com.dialoguebranch.exception.ScriptParseException;
import com.dialoguebranch.model.Language;
import com.dialoguebranch.model.LanguageSet;
import com.dialoguebranch.model.ProjectMetaData;
import com.dialoguebranch.parser.*;
import com.dialoguebranch.script.model.EditableProject;
import com.dialoguebranch.script.model.EditableScript;
import com.dialoguebranch.script.model.EditableNode;
import com.dialoguebranch.script.model.ScriptTreeNode;
import com.dialoguebranch.script.parser.EditableProjectParser;
import com.dialoguebranch.script.parser.EditableScriptParser;
import nl.rrd.utils.exception.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * The CommandLineRunner is the single runnable class for the Dialogue Branch Core Java Library.
 *
 * <p>This class can be used to test various functions of the library.</p>
 *
 * @author Harm op den Akker (Fruit Tree Labs)
 */
public class CommandLineRunner {

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of a CommandLineRunner, which serves no purpose as the CommandLineRunner
	 * class is a collection of static methods.
	 */
	public CommandLineRunner() { }

	// ----------------------------------------------------- //
	// -------------------- Main Method -------------------- //
	// ----------------------------------------------------- //

	/**
	 * Execute the command line runner, either without parameters ("interactive mode"), or by
	 * providing certain command line options for quickly executing a specific task (To Be Defined).
	 *
	 * @param args Command line arguments
	 */
	public static void main(String... args) {
		System.out.println("""
			Welcome to the Dialogue Branch Command Line Runner.

			This command line tool can be used for a number of different scenarios.
			Since you haven't provided command line arguments, we will take you through an interactive menu to determine your desired scenario and parameters.

			The following scenarios are currently supported:
			  1. Open a Dialogue Branch Folder and generate a summary.
			  2. Open a Dialogue Branch Project (from metadata.xml) and generate a summary.
			  3. Open a .dlb script file and parse it with the EditableScriptParser.
			  4. Open a .xml metadata file and parse it with the EditableProjectParser.
			  5. Open a Dialogue Branch Project and generate all its translation .json files.
			  6. Open a Dialogue Branch Project and generate translation CSV files.
		""");

		Scanner userInputScanner = new Scanner(System.in);
		System.out.print("\nChoose scenario: ");

		String scenario = userInputScanner.nextLine();
		switch (scenario) {
			case "1" -> generateProjectSummaryFromFolder();
			case "2" -> generateProjectSummaryFromXML();
			case "3" -> parseScriptFile();
			case "4" -> parseEditableProject();
			case "5" -> generateTranslationFiles();
			case "6" -> generateTranslationCSVs();
			default -> {
				System.out.println("Unknown scenario '" + scenario + "', please provide a valid " +
						"number from the list provided above.");
				System.exit(0);
			}
		}
	}

	// -------------------------------------------------------
	// -------------------- Other Methods --------------------
	// -------------------------------------------------------

	/**
	 * Asks the user to provide a folder, then read its contents and output a summary of the loaded
	 * Dialogue Branch project.
	 */
	private static void generateProjectSummaryFromFolder() {

		File rootDirectory = null;
		boolean rootDirectoryValid = false;

		while(!rootDirectoryValid) {
			System.out.println("Please provide the root directory of the DialogueBranch project:");
			try {
				rootDirectory = askUserInputDirectory();
				rootDirectoryValid = true;
			} catch (InvalidInputException e) {
				System.err.println("Error: " + e.getMessage());
			}
		}

		ProjectParserResult readResult;
		try {
			FileLoader fileLoader = new DirectoryFileLoader(rootDirectory);
			ProjectParser parser = new ProjectParser(fileLoader);
			readResult = parser.parse();
		} catch (IOException ex) {
			System.err.println("ERROR: Can't read DialogueBranch project from directory: " +
				rootDirectory.getAbsolutePath() + ": " + ex.getMessage());
			System.exit(0);
			return;
		}

		System.out.println(readResult.generateSummaryString());
	}

	/**
	 * Asks the user to provide the location of a project metadata .xml file, then parse the
	 * contents of the Dialogue Branch project and print out a summary.
	 */
	private static void generateProjectSummaryFromXML() {
		File projectMetadataFile = null;
		boolean projectMetadataFileValid = false;

		// Get a pointer to the projectMetadataFile (dlb-project.xml file)
		while(!projectMetadataFileValid) {
			System.out.println("Please provide the project metadata (.xml) file of the " +
					"DialogueBranch project:");
			try {
				projectMetadataFile = askUserInputXMLFile();
				projectMetadataFileValid = true;
			} catch (InvalidInputException e) {
				System.err.println("Error: " + e.getMessage());
			}
		}

		ProjectParserResult readResult;
		try {
			ProjectFileLoader fileLoader = new ProjectFileLoader(projectMetadataFile);
			ProjectParser parser = new ProjectParser(fileLoader);
			readResult = parser.parse();
		} catch (IOException ex) {
			System.err.println("ERROR: Can't read DialogueBranch project from given project XML " +
					"file: " + projectMetadataFile + ": " + ex.getMessage());
			System.exit(0);
			return;
		} catch (ParseException e) {
			System.err.println("ERROR: Unable to parse Project MetaData XML file: "+e.getMessage());
			System.exit(0);
			return;
		}

		System.out.println(readResult.generateSummaryString());
	}

	private static void parseScriptFile() {
		File scriptFile = null;
		boolean scriptFileValid = false;

		while(!scriptFileValid) {
			System.out.println("Please provide location of a .dlb file:");
			try {
				scriptFile = askUserInputDialogueBranchFile();
				scriptFileValid = true;
			} catch (InvalidInputException e) {
				System.err.println("Error: " + e.getMessage());
			}
		}

        try {
            EditableScript editableScript
					= EditableScriptParser.read(scriptFile, null);
			System.out.println("Finished reading EditableScript with the following result:");
			System.out.println(editableScript);
			System.out.println("Contains the following nodes: ");
			for(EditableNode node : editableScript.getNodes()) {
				System.out.println(node.toStringSummary());
			}
        } catch (IOException | ScriptParseException e) {
			System.err.println("Error parsing EditableScript: "+e.getMessage());
			System.exit(0);
        }
    }

	private static void parseEditableProject() {
		File projectMetadataFile = null;
		boolean projectMetadataFileValid = false;

		// Get a pointer to the projectMetadataFile (dlb-project.xml file)
		while(!projectMetadataFileValid) {
			System.out.println("Please provide the project metadata (.xml) file of the " +
					"DialogueBranch project:");
			try {
				projectMetadataFile = askUserInputXMLFile();
				projectMetadataFileValid = true;
			} catch (InvalidInputException e) {
				System.err.println("Error: " + e.getMessage());
			}
		}

		System.out.println("""
				
				---------- Task Output: ----------
				""");

        try {
            EditableProject editableProject = EditableProjectParser.read(projectMetadataFile);
			ProjectMetaData metaData = editableProject.getProjectMetaData();
			System.out.println("Loaded EditableProject: ");
			System.out.println("  - Name:        " + metaData.getName());
			System.out.println("  - Version:     " + metaData.getVersion());
			System.out.println("  - Description: " + metaData.getDescription());
			System.out.println("  - Base Path:   " + metaData.getBasePath());
			System.out.println("  - Supported Languages: ");

			int longestLanguageName = 0;
			for (Language l: metaData.getSupportedLanguages()) {
				if(l.toString().length() > longestLanguageName) {
					longestLanguageName = l.toString().length();
				}
			}
			for(Language l : metaData.getSupportedLanguages()) {

				System.out.print("      - " + l.toString());
				for(int i=longestLanguageName+3; i>l.toString().length(); i--) {
					System.out.print(" ");
				}
				System.out.println("(" + editableProject
						.getAvailableScriptsForLanguage(l)
						.getTotalNumberOfScripts() + " scripts)");
			}
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

	/**
	 * Collects input, interactively from the command line, to read in a Dialogue Branch Project.
	 * Then, makes sure that for all source languages, the corresponding translation languages have
	 * their .json files available.
	 */
	private static void generateTranslationFiles() {

		// Get a pointer to the projectMetadataFile (dlb-project.xml file)
		File projectMetadataFile = null;
		boolean projectMetadataFileValid = false;

		while(!projectMetadataFileValid) {
			System.out.println("Please provide the project metadata (.xml) file of the " +
					"Dialogue Branch project:");
			try {
				projectMetadataFile = askUserInputXMLFile();
				projectMetadataFileValid = true;
			} catch (InvalidInputException e) {
				System.err.println("Error: " + e.getMessage());
			}
		}

		System.out.println("""
				
				---------- Task Output: ----------
				""");

		try {
			// Read in the Dialogue Branch project as "Editable"
			EditableProject editableProject = EditableProjectParser.read(projectMetadataFile);
			ProjectMetaData metaData = editableProject.getProjectMetaData();

			// Print out some project summary information
			System.out.println("Loaded EditableProject: ");
			System.out.println("  - Name:        " + metaData.getName());
			System.out.println("  - Version:     " + metaData.getVersion());
			System.out.println("  - Description: " + metaData.getDescription());
			System.out.println("  - Base Path:   " + metaData.getBasePath());
			System.out.println("  - Supported Languages: ");

			int longestLanguageName = 0;
			for (Language l: metaData.getSupportedLanguages()) {
				if(l.toString().length() > longestLanguageName) {
					longestLanguageName = l.toString().length();
				}
			}
			for(Language l : metaData.getSupportedLanguages()) {
				System.out.print("      - " + l.toString());
				for(int i=longestLanguageName+3; i>l.toString().length(); i--) {
					System.out.print(" ");
				}
				System.out.println("(" + editableProject
						.getAvailableScriptsForLanguage(l)
						.getTotalNumberOfScripts() + " scripts)");
			}

			// For every source language, go through their corresponding translation languages
			// and make sure that a translation file (.json) exists for every matching source script

			for(Language sourceLanguage : metaData.getSourceLanguages()) {
				try {
					LanguageSet languageSet = metaData.getLanguageSetForSourceLanguage(
							sourceLanguage.getCode());
					for(Language translationLanguage : languageSet.getTranslationLanguages()) {

						System.out.print("Generating translation scripts for source language '"
								+ sourceLanguage.getCode()+"' and translation language '" +
								translationLanguage.getCode()+"' - ");

						ScriptTreeNode sourceLanguageTree =
								editableProject.getAvailableScriptsForLanguage(sourceLanguage);
						ScriptTreeNode translationLanguageTree =
								editableProject.getAvailableScriptsForLanguage(translationLanguage);

						int generatedScriptsCount
								= editableProject.generateTranslationFiles(
										sourceLanguageTree,translationLanguageTree);

						System.out.println(generatedScriptsCount +" scripts generated.");
					}
				} catch (DialogueBranchException e) {
                    throw new RuntimeException(e);
                }
            }

		} catch (IOException | ParseException e) {
			throw new RuntimeException(e);
		}

	}

	private static void generateTranslationCSVs() {
		// Get a pointer to the projectMetadataFile (dlb-project.xml file)
		File projectMetadataFile = null;
		boolean projectMetadataFileValid = false;

		while(!projectMetadataFileValid) {
			System.out.println("Please provide the project metadata (.xml) file of the " +
					"Dialogue Branch project:");
			try {
				projectMetadataFile = askUserInputXMLFile();
				projectMetadataFileValid = true;
			} catch (InvalidInputException e) {
				System.err.println("Error: " + e.getMessage());
			}
		}

		System.out.println("""
				
				---------- Task Output: ----------
				""");

		try {
			// Read in the Dialogue Branch project as "Editable"
			EditableProject editableProject = EditableProjectParser.read(projectMetadataFile);
			ProjectMetaData metaData = editableProject.getProjectMetaData();

			// Print out some project summary information
			System.out.println("Loaded EditableProject: ");
			System.out.println("  - Name:        " + metaData.getName());
			System.out.println("  - Version:     " + metaData.getVersion());
			System.out.println("  - Description: " + metaData.getDescription());
			System.out.println("  - Base Path:   " + metaData.getBasePath());
			System.out.println("  - Supported Languages: ");

			int longestLanguageName = 0;
			for (Language l: metaData.getSupportedLanguages()) {
				if(l.toString().length() > longestLanguageName) {
					longestLanguageName = l.toString().length();
				}
			}
			for(Language l : metaData.getSupportedLanguages()) {
				System.out.print("      - " + l.toString());
				for(int i=longestLanguageName+3; i>l.toString().length(); i--) {
					System.out.print(" ");
				}
				System.out.println("(" + editableProject
						.getAvailableScriptsForLanguage(l)
						.getTotalNumberOfScripts() + " scripts)");
			}

			// For every source language, go through their corresponding translation languages
			// and make sure that a translation file (.json) exists for every matching source script

			for(Language sourceLanguage : metaData.getSourceLanguages()) {
				try {
					LanguageSet languageSet = metaData.getLanguageSetForSourceLanguage(
							sourceLanguage.getCode());
					for (Language translationLanguage : languageSet.getTranslationLanguages()) {

						System.out.println("Generating CSV files for source language '"
								+ sourceLanguage.getCode() + "' and translation language '" +
								translationLanguage.getCode() + "'.");

						ScriptTreeNode translationLanguageTree =
								editableProject.getAvailableScriptsForLanguage(translationLanguage);

						editableProject.generateTranslationTSVs(translationLanguageTree);

					}
				} catch (DialogueBranchException e) {
					throw new RuntimeException(e);
				}
			}

		} catch (IOException | ParseException e) {
			throw new RuntimeException(e);
		}
	}

	// ------------------------------------------------------------------------------------------ //
	// -------------------- Helper Functions: Interactive Command Line Input -------------------- //
	// ------------------------------------------------------------------------------------------ //

	/**
	 * Open the command line for user input and check whether the given value is a valid directory.
	 * If so, return this directory as a {@link File}, otherwise throws a
	 * {@link InvalidInputException}.
	 *
	 * @return A {@link File} representing the directory that the user has provided as CLI input.
	 * @throws InvalidInputException in case the given input was invalid.
	 */
	public static File askUserInputDirectory() throws InvalidInputException {
		Scanner userInputScanner = new Scanner(System.in);
		String directoryString = userInputScanner.nextLine();

		// Check if the input is not null
		if(directoryString == null)
			throw new InvalidInputException("Provided input is null.");

		// Check if the given input path exists
		File directory = new File(directoryString);
		if (!directory.exists()) {
			throw new InvalidInputException("Provided directory '" + directoryString +
					"' does not exist.");
		}

		// Check if the given input path is a directory
		try {
			directory = directory.getCanonicalFile();
		} catch (IOException ex) {
			throw new InvalidInputException("Error while checking whether the given input '" +
					directoryString + "' is a directory.");
		}
		if (!directory.isDirectory()) {
			throw new InvalidInputException("Given path '"+ directory + "' is not " +
					"a directory.");
		}

		return directory;
	}

	/**
	 * Open the command line for user input and check whether the given value represents a valid,
	 * existing .xml file.
	 * If so, return a pointer to this file as a {@link File}, otherwise throws a
	 * {@link InvalidInputException}.
	 *
	 * @return A {@link File} representing the XML file that the user has provided as CLI input.
	 * @throws InvalidInputException in case the given input was invalid.
	 */
	public static File askUserInputXMLFile() throws InvalidInputException {
		Scanner userInputScanner = new Scanner(System.in);
		String fileString = userInputScanner.nextLine();

		// Check if the input is not null
		if(fileString == null)
			throw new InvalidInputException("Provided input is null.");

		// Check if the given input path exists
		File file = getFileFromString(fileString);

		// Check if the given input is an .xml file
		try {
			String extension = fileString.substring(fileString.lastIndexOf(".") + 1);
			if(extension.equals("xml")) return file;
			else throw new InvalidInputException("The given input is not an .xml file.");
		} catch(IndexOutOfBoundsException e) {
			throw new InvalidInputException("The given input is not an .xml file.");
		}
	}

	public static File askUserInputDialogueBranchFile() throws InvalidInputException {
		Scanner userInputScanner = new Scanner(System.in);
		String fileString = userInputScanner.nextLine();

		// Check if the input is not null
		if(fileString == null)
			throw new InvalidInputException("Provided input is null.");

		// Check if the given input path exists
		File file = getFileFromString(fileString);

		// Check if the given input is an .xml file
		try {
			String extension = fileString.substring(fileString.lastIndexOf(".") + 1);
			if(extension.equals("dlb")) return file;
			else throw new InvalidInputException("The given input is not a .dlb file.");
		} catch(IndexOutOfBoundsException e) {
			throw new InvalidInputException("The given input is not a .dlb file.");
		}
	}

	/**
	 * Creates a {@link File} object based on the provided {@link String}, which should be the path
	 * to a valid file on the file system.
	 *
	 * @param fileString the path to a valid file on the file system
	 * @return A {@link File} object, representing the given file path.
	 * @throws InvalidInputException in case of any error, e.g. the file doesn't exist, or the given
	 *                               input points to a folder instead of a file.
	 */
	private static File getFileFromString(String fileString) throws InvalidInputException {
		File file = new File(fileString);
		if (!file.exists()) {
			throw new InvalidInputException("Provided file '" + fileString + "' does not exist.");
		}

		// Check if the given input is a file
		try {
			file = file.getCanonicalFile();
		} catch (IOException ex) {
			throw new InvalidInputException("Error while checking whether the given input '" +
					fileString + "' is a file.");
		}
		if (file.isDirectory()) {
			throw new InvalidInputException("Given path '" + file +
					"' is a directory (not a file).");
		}
		return file;
	}

}
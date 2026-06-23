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

package com.dialoguebranch.cli;

import com.dialoguebranch.exception.InvalidInputException;
import com.dialoguebranch.exception.ScriptParseException;
import com.dialoguebranch.model.execute.Language;
import com.dialoguebranch.model.execute.LanguageSet;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.common.ResourceType;
import com.dialoguebranch.model.edit.EditableProject;
import com.dialoguebranch.model.edit.EditableScript;
import com.dialoguebranch.model.common.FileStorageSource;
import com.dialoguebranch.model.common.ScriptTreeNode;
import com.dialoguebranch.model.common.StorageSource;
import com.dialoguebranch.editing.parser.EditableProjectParser;
import com.dialoguebranch.editing.parser.EditableScriptParser;
import nl.rrd.utils.exception.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * The {@link ProjectTool} is an interactive command-line tool for inspecting and working with
 * Dialogue Branch projects. It presents a top-level menu, then a project-level sub-menu once a
 * {@code dlb-project.xml} file has been loaded.
 *
 * @author Harm op den Akker
 */
public class ProjectTool {

    // -------------------------------------------------------- //
    // -------------------- Constructor(s) -------------------- //
    // -------------------------------------------------------- //

    /**
     * Creates an instance of {@link ProjectTool}. This class is a collection of static methods and
     * is not intended to be instantiated directly.
     */
    public ProjectTool() { }

    // ----------------------------------------------------- //
    // -------------------- Main Method -------------------- //
    // ----------------------------------------------------- //

    /**
     * Entry point for the Dialogue Branch Project Tool.
     *
     * @param args command-line arguments (not used in interactive mode)
     */
    public static void main(String... args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("""
            ============================================================
             Dialogue Branch Project Tool
            ============================================================
            Interactive tool for inspecting Dialogue Branch projects.
            """);

        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> openProject(scanner);
                case "0" -> {
                    System.out.println("Goodbye.");
                    running = false;
                }
                default -> System.out.println("Unknown option '" + choice +
                        "'. Please enter a number from the menu.\n");
            }
        }
    }

    // ------------------------------------------------------------ //
    // -------------------- Menu: Top-Level  -------------------- //
    // ------------------------------------------------------------ //

    private static void printMainMenu() {
        System.out.println("""
            ---- Main Menu ----------------------------------------
              1. Open a dlb-project.xml file
              0. Exit
            -------------------------------------------------------""");
        System.out.print("Choice: ");
    }

    // --------------------------------------------------------------- //
    // -------------------- Menu: Project-Level  -------------------- //
    // --------------------------------------------------------------- //

    /**
     * Prompts the user for a {@code dlb-project.xml} file path, loads the project, then enters
     * the project-level sub-menu.
     */
    private static void openProject(Scanner scanner) {
        File projectFile = null;

        while (projectFile == null) {
            System.out.println("\nEnter the path to a dlb-project.xml file:");
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            try {
                projectFile = resolveXmlFile(input);
            } catch (InvalidInputException e) {
                System.err.println("Error: " + e.getMessage() + "\n");
            }
        }

        EditableProject project;
        try {
            project = EditableProjectParser.read(projectFile);
        } catch (IOException | ParseException e) {
            System.err.println("Failed to load project: " + e.getMessage() + "\n");
            return;
        }

        ProjectMetaData meta = project.getProjectMetaData();
        System.out.println("\nLoaded project: " + meta.getName() + " (v" + meta.getVersion() + ")\n");

        boolean inProject = true;
        while (inProject) {
            printProjectMenu(meta.getName());
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> printProjectSummary(project);
                case "0" -> {
                    System.out.println("Returning to main menu.\n");
                    inProject = false;
                }
                default -> System.out.println("Unknown option '" + choice + "'.\n");
            }
        }
    }

    private static void printProjectMenu(String projectName) {
        System.out.println("---- Project: " + projectName + " ----");
        System.out.println("""
              1. Print project summary
              0. Back to main menu
            -------------------------------------------------------""");
        System.out.print("Choice: ");
    }

    // ---------------------------------------------------------- //
    // -------------------- Project Summary -------------------- //
    // ---------------------------------------------------------- //

    /**
     * Prints a comprehensive summary of the given {@link EditableProject}: project metadata,
     * language mappings, and node counts per dialogue.
     */
    private static void printProjectSummary(EditableProject project) {
        ProjectMetaData meta = project.getProjectMetaData();

        System.out.println();
        System.out.println("============================================================");
        System.out.println(" Project Summary");
        System.out.println("============================================================");
        System.out.println("  Name:        " + meta.getName());
        System.out.println("  Version:     " + meta.getVersion());
        System.out.println("  Description: " + meta.getDescription());
        System.out.println("  Base Path:   " + meta.getBasePath());

        // ---- Language Map ----
        System.out.println();
        System.out.println("  Language Mappings:");
        if (meta.getLanguageMap() == null || meta.getLanguageMap().getLanguageSets().isEmpty()) {
            System.out.println("    (no language map defined)");
        } else {
            for (LanguageSet languageSet : meta.getLanguageMap().getLanguageSets()) {
                Language source = languageSet.getSourceLanguage();
                List<Language> translations = languageSet.getTranslationLanguages();

                System.out.print("    [source] " + source.getName() + " (" + source.getCode() + ")");
                if (translations.isEmpty()) {
                    System.out.println("  →  (no translations)");
                } else {
                    System.out.println();
                    for (Language translation : translations) {
                        System.out.println("        →  " + translation.getName()
                                + " (" + translation.getCode() + ")");
                    }
                }
            }
        }

        // ---- Dialogues per source language ----
        System.out.println();
        System.out.println("  Dialogues (source languages):");

        if (meta.getLanguageMap() == null) {
            System.out.println("    (no language map — cannot determine source languages)");
        } else {
            for (LanguageSet languageSet : meta.getLanguageMap().getLanguageSets()) {
                Language sourceLanguage = languageSet.getSourceLanguage();
                ScriptTreeNode sourceTree = project.getAvailableScriptsForLanguage(sourceLanguage);

                System.out.println();
                System.out.println("    Language: " + sourceLanguage.getName()
                        + " (" + sourceLanguage.getCode() + ")");

                if (sourceTree == null) {
                    System.out.println("      (no scripts found)");
                    continue;
                }

                Map<String, Integer> dialogueNodeCounts = new LinkedHashMap<>();
                collectNodeCounts(sourceTree, "", dialogueNodeCounts);

                if (dialogueNodeCounts.isEmpty()) {
                    System.out.println("      (no scripts found)");
                } else {
                    int maxLen = dialogueNodeCounts.keySet().stream()
                            .mapToInt(String::length).max().orElse(0);
                    for (Map.Entry<String, Integer> entry : dialogueNodeCounts.entrySet()) {
                        String pad = " ".repeat(maxLen - entry.getKey().length() + 2);
                        System.out.println("      " + entry.getKey() + pad
                                + entry.getValue() + " node"
                                + (entry.getValue() == 1 ? "" : "s"));
                    }
                }
            }
        }

        System.out.println();
        System.out.println("============================================================");
        System.out.println();
    }

    /**
     * Recursively collects dialogue names and their node counts from the given
     * {@link ScriptTreeNode} tree, populating the provided {@code result} map.
     *
     * @param node      the current node in the script tree
     * @param pathPrefix the accumulated folder prefix for building relative dialogue names
     * @param result    the map to populate with dialogue name → node count entries
     */
    private static void collectNodeCounts(ScriptTreeNode node, String pathPrefix,
                                          Map<String, Integer> result) {
        for (ScriptTreeNode child : node.getChildren()) {
            if (child.getResourceType() == ResourceType.FOLDER) {
                String folderPrefix = pathPrefix.isEmpty()
                        ? child.getName()
                        : pathPrefix + "/" + child.getName();
                collectNodeCounts(child, folderPrefix, result);
            } else if (child.getResourceType() == ResourceType.SCRIPT) {
                String dialogueName = pathPrefix.isEmpty()
                        ? child.getName()
                        : pathPrefix + "/" + child.getName();
                int nodeCount = countNodesInScript(child.getStorageSource());
                result.put(dialogueName, nodeCount);
            }
        }
    }

    /**
     * Parses the script at the given {@link StorageSource} and returns the number of nodes it
     * contains. Returns {@code -1} if the script could not be parsed.
     */
    private static int countNodesInScript(StorageSource storageSource) {
        if (!(storageSource instanceof FileStorageSource fileSource)) {
            return -1;
        }
        try {
            EditableScript script = EditableScriptParser.read(fileSource.getSourceFile(), null);
            return script.getNodes().size();
        } catch (IOException | ScriptParseException e) {
            System.err.println("    Warning: could not parse script '"
                    + fileSource.getDescriptor() + "': " + e.getMessage());
            return -1;
        }
    }

    // ------------------------------------------------------------------------------- //
    // -------------------- Helper: Interactive Command-Line Input -------------------- //
    // ------------------------------------------------------------------------------- //

    /**
     * Resolves the given {@code input} string to a valid, existing {@code .xml} file.
     *
     * @param input the file path entered by the user
     * @return a canonical {@link File} object for the XML file
     * @throws InvalidInputException if the path does not exist, is a directory, or is not a
     *                               {@code .xml} file
     */
    private static File resolveXmlFile(String input) throws InvalidInputException {
        if (input == null || input.isEmpty())
            throw new InvalidInputException("No input provided.");

        File file = new File(input);
        if (!file.exists())
            throw new InvalidInputException("File '" + input + "' does not exist.");

        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            throw new InvalidInputException("Cannot resolve path '" + input + "'.");
        }

        if (file.isDirectory())
            throw new InvalidInputException("'" + input + "' is a directory, not a file.");

        String name = file.getName();
        if (!name.endsWith(".xml"))
            throw new InvalidInputException("'" + input + "' is not an .xml file.");

        return file;
    }

}

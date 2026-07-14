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

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.exception.InvalidInputException;
import com.dialoguebranch.exception.ScriptParseException;
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.ProjectScriptLoader;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import com.dialoguebranch.model.execute.Language;
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
import nl.rrd.utils.expressions.EvaluationException;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    // ------------------------------------------------------------- //
    // -------------------- Menu: Execute Script -------------------- //
    // ------------------------------------------------------------- //

    /**
     * Prompts the user to choose a language and dialogue, then loads the project via the execution
     * parser and runs the selected dialogue interactively in the terminal.
     *
     * @param scanner the shared {@link Scanner} for reading user input.
     * @param projectFile the {@code dlb-project.xml} file to load the project from.
     */
    private static void executeDialogue(Scanner scanner, File projectFile) {
        ExecutableProject execProject;
        try {
            ProjectScriptLoader scriptLoader = new ProjectScriptLoader(projectFile);
            ProjectParser parser = new ProjectParser(scriptLoader);
            ProjectParserResult result = parser.parse();
            if (!result.getParseErrors().isEmpty()) {
                System.err.println("Project contains parse errors:");
                result.getParseErrors().forEach((file, errors) ->
                        errors.forEach(e -> System.err.println("  [" + file + "] " + e.getMessage())));
                return;
            }
            execProject = result.getProject();
        } catch (IOException | ParseException e) {
            System.err.println("Failed to load project for execution: " + e.getMessage() + "\n");
            return;
        }

        // Collect the available languages from the loaded dialogues
        List<String> languages = new ArrayList<>();
        for (ResourcePointer rp : execProject.getDialogues().keySet()) {
            if (!languages.contains(rp.getLanguage()))
                languages.add(rp.getLanguage());
        }

        if (languages.isEmpty()) {
            System.out.println("No dialogue scripts found in this project.\n");
            return;
        }

        System.out.println("\nAvailable languages:");
        for (int i = 0; i < languages.size(); i++)
            System.out.println("  " + (i + 1) + ". " + languages.get(i));
        System.out.print("Choose language (code or number): ");
        String langInput = scanner.nextLine().trim();

        String selectedLanguage = null;
        try {
            int idx = Integer.parseInt(langInput) - 1;
            if (idx >= 0 && idx < languages.size())
                selectedLanguage = languages.get(idx);
        } catch (NumberFormatException ignored) {
            if (languages.contains(langInput))
                selectedLanguage = langInput;
        }

        if (selectedLanguage == null) {
            System.out.println("Unknown language '" + langInput + "'.\n");
            return;
        }

        // Collect dialogues available for the selected language
        List<String> dialogueNames = new ArrayList<>();
        for (ResourcePointer rp : execProject.getDialogues().keySet()) {
            if (rp.getLanguage().equals(selectedLanguage))
                dialogueNames.add(rp.getDialogueName());
        }

        System.out.println("\nAvailable dialogues for language '" + selectedLanguage + "':");
        for (int i = 0; i < dialogueNames.size(); i++)
            System.out.println("  " + (i + 1) + ". " + dialogueNames.get(i));
        System.out.print("Choose dialogue (name or number): ");
        String dlgInput = scanner.nextLine().trim();

        String selectedDialogue = null;
        try {
            int idx = Integer.parseInt(dlgInput) - 1;
            if (idx >= 0 && idx < dialogueNames.size())
                selectedDialogue = dialogueNames.get(idx);
        } catch (NumberFormatException ignored) {
            if (dialogueNames.contains(dlgInput))
                selectedDialogue = dlgInput;
        }

        if (selectedDialogue == null) {
            System.out.println("Unknown dialogue '" + dlgInput + "'.\n");
            return;
        }

        ResourcePointer pointer = null;
        Dialogue dialogue = null;
        for (Map.Entry<ResourcePointer, Dialogue> entry : execProject.getDialogues().entrySet()) {
            ResourcePointer rp = entry.getKey();
            if (rp.getLanguage().equals(selectedLanguage)
                    && rp.getDialogueName().equals(selectedDialogue)) {
                pointer = rp;
                dialogue = entry.getValue();
                break;
            }
        }
        if (dialogue == null) {
            System.out.println("Could not locate dialogue '" + selectedDialogue
                    + "' (" + selectedLanguage + ") in the parsed project.\n");
            return;
        }

        runDialogueLoop(scanner, execProject, pointer, dialogue);
    }

    /**
     * Runs an interactive execution loop for the given {@link Dialogue}. Prints agent statements,
     * lists reply options, and advances the dialogue until it ends or jumps to another script.
     *
     * @param scanner     the shared {@link Scanner} for reading user input.
     * @param project     the {@link ExecutableProject} used for cross-dialogue navigation.
     * @param pointer     the {@link ResourcePointer} identifying the starting dialogue.
     * @param dialogue    the {@link Dialogue} to execute.
     */
    private static void runDialogueLoop(Scanner scanner, ExecutableProject project,
                                        ResourcePointer pointer, Dialogue dialogue) {
        User user = new User("cli-user");
        VariableStore variableStore = new VariableStore(user);

        ActiveDialogue activeDialogue = new ActiveDialogue(pointer, dialogue);
        activeDialogue.setVariableStore(variableStore);

        System.out.println("\n============================================================");
        System.out.println(" Executing: " + pointer.getDialogueName()
                + " [" + pointer.getLanguage() + "]");
        System.out.println("============================================================\n");

        Node currentNode;
        try {
            currentNode = activeDialogue.startDialogue(ZonedDateTime.now());
        } catch (ExecutionException | EvaluationException e) {
            System.err.println("Failed to start dialogue: " + e.getMessage() + "\n");
            return;
        }

        while (currentNode != null) {
            printAgentStatement(currentNode.getBody());
            List<Reply> replies = currentNode.getBody().getReplies();

            if (replies.isEmpty()) {
                System.out.println("\n[Dialogue ended — no reply options.]\n");
                break;
            }

            // Auto-forward if the only reply has no statement
            if (replies.size() == 1 && replies.get(0).getStatement() == null) {
                System.out.println("\n[Auto-forward]\n");
                NodePointer np;
                try {
                    np = activeDialogue.processReplyAndGetNodePointer(
                            replies.get(0).getReplyId(), ZonedDateTime.now());
                } catch (EvaluationException e) {
                    System.err.println("Evaluation error: " + e.getMessage() + "\n");
                    break;
                }
                currentNode = advanceDialogue(activeDialogue, project, pointer, np, scanner);
                continue;
            }

            printReplyOptions(replies);
            System.out.print("Your reply: ");
            String replyInput = scanner.nextLine().trim();

            int chosenReplyId = -1;
            try {
                int chosenIndex = Integer.parseInt(replyInput) - 1;
                if (chosenIndex >= 0 && chosenIndex < replies.size())
                    chosenReplyId = replies.get(chosenIndex).getReplyId();
            } catch (NumberFormatException ignored) { }

            if (chosenReplyId == -1) {
                System.out.println("Invalid choice. Please enter a number from the list.\n");
                continue;
            }

            NodePointer np;
            try {
                np = activeDialogue.processReplyAndGetNodePointer(chosenReplyId, ZonedDateTime.now());
            } catch (EvaluationException e) {
                System.err.println("Evaluation error: " + e.getMessage() + "\n");
                break;
            }

            currentNode = advanceDialogue(activeDialogue, project, pointer, np, scanner);
        }

        System.out.println("============================================================");
        System.out.println(" Dialogue finished.");
        System.out.println("============================================================\n");
    }

    /**
     * Advances the dialogue based on a {@link NodePointer}. Handles both internal (same script)
     * and external (different script) node pointers.
     *
     * @param activeDialogue the currently active dialogue
     * @param project        the full executable project (for cross-dialogue lookup)
     * @param currentPointer the {@link ResourcePointer} of the current dialogue
     * @param np             the next node pointer
     * @param scanner        the shared scanner for reading user input
     * @return the next {@link Node} to present, or {@code null} if the dialogue has ended
     */
    private static Node advanceDialogue(ActiveDialogue activeDialogue, ExecutableProject project,
                                        ResourcePointer currentPointer, NodePointer np,
                                        Scanner scanner) {
        if (np instanceof InternalNodePointer internalPointer) {
            if (internalPointer.getTargetNodeId()
                    .equalsIgnoreCase(DialogueBranchConstants.DLB_NODE_END_ID)) {
                return null;
            }
            try {
                return activeDialogue.progressDialogue(internalPointer, ZonedDateTime.now());
            } catch (EvaluationException e) {
                System.err.println("Evaluation error while progressing dialogue: "
                        + e.getMessage() + "\n");
                return null;
            }
        }

        if (np instanceof ExternalNodePointer externalPointer) {
            String targetDialogueName = externalPointer.getAbsoluteTargetDialogue();
            String targetLanguage = currentPointer.getLanguage();
            ResourcePointer targetPointer = null;
            Dialogue targetDialogue = null;
            for (Map.Entry<ResourcePointer, Dialogue> entry : project.getDialogues().entrySet()) {
                ResourcePointer rp = entry.getKey();
                if (rp.getLanguage().equals(targetLanguage)
                        && rp.getDialogueName().equals(targetDialogueName)) {
                    targetPointer = rp;
                    targetDialogue = entry.getValue();
                    break;
                }
            }
            if (targetDialogue == null) {
                System.err.println("Could not find external dialogue '" + targetDialogueName
                        + "' (" + targetLanguage + ").\n");
                return null;
            }
            System.out.println("\n[Jumping to dialogue: " + targetDialogueName + "]\n");
            runDialogueLoop(scanner, project, targetPointer, targetDialogue);
            return null;
        }

        return null;
    }

    /**
     * Prints the agent's statement text from the given {@link NodeBody} (segments only, not
     * replies).
     *
     * @param body the {@link NodeBody} whose text segments to render
     */
    private static void printAgentStatement(NodeBody body) {
        StringBuilder sb = new StringBuilder();
        for (NodeBody.Segment segment : body.getSegments())
            sb.append(segment);
        String text = sb.toString().trim();
        System.out.println("\nAgent: " + text + "\n");
    }

    /**
     * Prints the numbered list of reply options from the given list of {@link Reply} objects.
     *
     * @param replies the list of replies to render
     */
    private static void printReplyOptions(List<Reply> replies) {
        System.out.println("---- Reply options ----");
        for (int i = 0; i < replies.size(); i++) {
            Reply reply = replies.get(i);
            String label = reply.getStatement() != null
                    ? reply.getStatement().toString().trim()
                    : "(continue)";
            System.out.println("  " + (i + 1) + ". " + label);
        }
        System.out.println("-----------------------");
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

        ProjectMetaData meta = project.getMetaData();
        System.out.println("\nLoaded project: " + meta.getName() + " (v" + meta.getVersion() + ")\n");

        boolean inProject = true;
        while (inProject) {
            printProjectMenu(meta.getName());
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> printProjectSummary(project);
                case "2" -> executeDialogue(scanner, projectFile);
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
              2. Execute a dialogue script
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
        ProjectMetaData meta = project.getMetaData();

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
        Language source = meta.getLanguageMap() != null ? meta.getLanguageMap().getSourceLanguage() : null;
        if (source == null) {
            System.out.println("    (no source language defined)");
        } else {
            List<Language> translations = meta.getLanguageMap().getTranslationLanguages();

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

        // ---- Dialogues (source language) ----
        System.out.println();
        System.out.println("  Dialogues (source language):");

        if (source == null) {
            System.out.println("    (no source language — cannot determine dialogues)");
        } else {
            ScriptTreeNode sourceTree = project.getAvailableScriptsForLanguage(source);

            System.out.println();
            System.out.println("    Language: " + source.getName()
                    + " (" + source.getCode() + ")");

            if (sourceTree == null) {
                System.out.println("      (no scripts found)");
            } else {
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

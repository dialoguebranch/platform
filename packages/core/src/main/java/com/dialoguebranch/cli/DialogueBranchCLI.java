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
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.execution.parser.ProjectParser;
import com.dialoguebranch.execution.parser.ProjectParserResult;
import com.dialoguebranch.execution.parser.ProjectScriptLoader;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.model.common.DialogueBranchConstants;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import com.dialoguebranch.model.execute.nodepointer.ExternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.InternalNodePointer;
import com.dialoguebranch.model.execute.nodepointer.NodePointer;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

/**
 * The {@link DialogueBranchCLI} is a command-line tool for inspecting and working with Dialogue
 * Branch projects, in two modes:
 * <ul>
 *     <li>Run with no arguments for an interactive, menu-driven session: a top-level menu, then a
 *     project-level sub-menu once a {@code dlb-project.xml} file has been loaded.</li>
 *     <li>Run with a project file path (and optional flags) for a non-interactive, scriptable
 *     invocation suited to CI — see {@link #printUsage()} for the exact syntax. Dialogue
 *     execution, even when triggered this way, is still an interactive terminal conversation,
 *     since stepping through a dialogue is inherently a back-and-forth.</li>
 * </ul>
 *
 * @author Harm op den Akker
 */
public class DialogueBranchCLI {

	/** Process exit status: success. */
	private static final int EXIT_OK = 0;

	/** Process exit status: the project failed to load or contains parse errors. */
	private static final int EXIT_PARSE_ERROR = 1;

	/** Process exit status: invalid command-line usage (bad or missing arguments). */
	private static final int EXIT_USAGE_ERROR = 2;

	// -------------------------------------------------------- //
	// -------------------- Constructor(s) -------------------- //
	// -------------------------------------------------------- //

	/**
	 * Creates an instance of {@link DialogueBranchCLI}. This class is a collection of static
	 * methods and is not intended to be instantiated directly.
	 */
	public DialogueBranchCLI() { }

	// ----------------------------------------------------- //
	// -------------------- Main Method -------------------- //
	// ----------------------------------------------------- //

	/**
	 * Entry point for the Dialogue Branch CLI. With no arguments, launches the
	 * interactive menu-driven session. With arguments, runs non-interactively — see
	 * {@link #printUsage()} for the exact syntax — and exits with a non-zero status on failure,
	 * making it suitable as a CI gate.
	 *
	 * @param args command-line arguments; empty for the interactive session.
	 */
	public static void main(String... args) {
		if (args.length == 0) {
			runInteractive();
			return;
		}
		int exitCode = runNonInteractive(args);
		if (exitCode != EXIT_OK)
			System.exit(exitCode);
	}

	/**
	 * Runs the interactive, menu-driven session on {@link System#in}/{@link System#out}.
	 */
	private static void runInteractive() {
		Scanner scanner = new Scanner(System.in);

		System.out.println("""
			============================================================
			 Dialogue Branch CLI
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

	// ------------------------------------------------------------------ //
	// -------------------- Non-Interactive Invocation -------------------- //
	// ------------------------------------------------------------------ //

	/**
	 * Parses and dispatches a non-interactive invocation. See {@link #printUsage()} for the
	 * accepted syntax.
	 *
	 * @param args the raw command-line arguments (guaranteed non-empty by {@link #main}).
	 * @return the process exit status to use.
	 */
	private static int runNonInteractive(String[] args) {
		List<String> argList = new ArrayList<>(List.of(args));

		if (argList.contains("-h") || argList.contains("--help") || argList.contains("-?")) {
			printUsage();
			return EXIT_OK;
		}

		if (argList.isEmpty() || argList.get(0).startsWith("-")) {
			System.err.println("ERROR: Missing path to a dlb-project.xml file.\n");
			printUsage();
			return EXIT_USAGE_ERROR;
		}

		File projectFile;
		try {
			projectFile = resolveXmlFile(argList.remove(0));
		} catch (InvalidInputException e) {
			System.err.println("ERROR: " + e.getMessage());
			return EXIT_USAGE_ERROR;
		}

		boolean validate = argList.remove("--validate");
		int executeIndex = argList.indexOf("--execute");

		if (validate && executeIndex >= 0) {
			System.err.println("ERROR: --validate and --execute cannot be combined.\n");
			printUsage();
			return EXIT_USAGE_ERROR;
		}

		if (executeIndex < 0)
			return validateNonInteractive(projectFile);

		if (executeIndex + 2 >= argList.size()) {
			System.err.println(
					"ERROR: --execute requires a <language> and a <dialogue> argument.\n");
			printUsage();
			return EXIT_USAGE_ERROR;
		}
		String language = argList.get(executeIndex + 1);
		String dialogueName = argList.get(executeIndex + 2);
		return executeNonInteractive(projectFile, language, dialogueName);
	}

	/**
	 * Parses the given project and prints its summary (metadata, dialogue/translation counts,
	 * per-dialogue node/speaker/variable details, and any warnings such as orphaned nodes) to
	 * {@link System#out}. Parse errors, if any, go to {@link System#err} instead.
	 *
	 * @param projectFile the {@code dlb-project.xml} file to load the project from.
	 * @return {@link #EXIT_OK} if the project parsed without errors, {@link #EXIT_PARSE_ERROR}
	 *         otherwise. Warnings do not affect the exit status — by design, they can never
	 *         indicate a runtime error, only something worth an author's attention.
	 */
	private static int validateNonInteractive(File projectFile) {
		ProjectParserResult result;
		try {
			ProjectScriptLoader scriptLoader = new ProjectScriptLoader(projectFile);
			ProjectParser parser = new ProjectParser(scriptLoader);
			result = parser.parse();
		} catch (IOException | ParseException e) {
			System.err.println("ERROR: Failed to load project: " + e.getMessage());
			return EXIT_PARSE_ERROR;
		}
		if (!result.getParseErrors().isEmpty()) {
			System.err.println("Project contains parse errors:");
			result.getParseErrors().forEach((file, errors) ->
					errors.forEach(e -> System.err.println("  [" + file + "] " + e.getMessage())));
			return EXIT_PARSE_ERROR;
		}
		System.out.println(result.generateSummaryString());
		return EXIT_OK;
	}

	/**
	 * Parses the given project, then runs the named dialogue interactively on the terminal — the
	 * same conversational execution loop ({@link #runDialogueLoop}) used by the interactive
	 * session's "Execute a dialogue script" option.
	 *
	 * @param projectFile   the {@code dlb-project.xml} file to load the project from.
	 * @param language      the language code of the dialogue to execute.
	 * @param dialogueName  the name of the dialogue to execute.
	 * @return {@link #EXIT_OK} once the dialogue finishes, {@link #EXIT_PARSE_ERROR} if the
	 *         project fails to parse, or {@link #EXIT_USAGE_ERROR} if no such dialogue exists.
	 */
	private static int executeNonInteractive(File projectFile, String language,
											 String dialogueName) {
		ExecutableProject execProject;
		try {
			ProjectScriptLoader scriptLoader = new ProjectScriptLoader(projectFile);
			ProjectParser parser = new ProjectParser(scriptLoader);
			ProjectParserResult result = parser.parse();
			if (!result.getParseErrors().isEmpty()) {
				System.err.println("Project contains parse errors:");
				result.getParseErrors().forEach((file, errors) ->
						errors.forEach(e -> System.err.println("  [" + file + "] " + e.getMessage())));
				return EXIT_PARSE_ERROR;
			}
			execProject = Objects.requireNonNull(result.getProject(),
					"A project that parsed without errors has no ExecutableProject");
		} catch (IOException | ParseException e) {
			System.err.println("ERROR: Failed to load project for execution: " + e.getMessage());
			return EXIT_PARSE_ERROR;
		}

		Map.Entry<ResourcePointer, Dialogue> found =
				findDialogue(execProject, language, dialogueName);
		if (found == null) {
			System.err.println("ERROR: Could not find dialogue '" + dialogueName
					+ "' (" + language + ") in the parsed project.");
			return EXIT_USAGE_ERROR;
		}

		runDialogueLoop(new Scanner(System.in), execProject, found.getKey(), found.getValue());
		return EXIT_OK;
	}

	/**
	 * Prints the usage message for non-interactive invocation.
	 */
	private static void printUsage() {
		System.out.println("""
			Usage:
			  DialogueBranchCLI
				  Launch the interactive, menu-driven session (the default with no arguments).

			  DialogueBranchCLI <path-to-dlb-project.xml> [--validate]
				  Parse the project non-interactively and print its summary. Prints any parse
				  errors to stderr and exits with a non-zero status if the project fails to
				  parse. Warnings (e.g. orphaned nodes) are printed but do not affect the exit
				  status. This is the default when only a path is given.

			  DialogueBranchCLI <path-to-dlb-project.xml> --execute <language> <dialogue>
				  Parse the project, then run the given dialogue interactively on the terminal —
				  the same conversational execution as the interactive session's "Execute a
				  dialogue script" option.

			  DialogueBranchCLI -h | --help | -?
				  Print this usage message.""");
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
			execProject = Objects.requireNonNull(result.getProject(),
					"A project that parsed without errors has no ExecutableProject");
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
			if (selectedLanguage.equals(rp.getLanguage()))
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

		Map.Entry<ResourcePointer, Dialogue> found =
				findDialogue(execProject, selectedLanguage, selectedDialogue);
		if (found == null) {
			System.out.println("Could not locate dialogue '" + selectedDialogue
					+ "' (" + selectedLanguage + ") in the parsed project.\n");
			return;
		}

		runDialogueLoop(scanner, execProject, found.getKey(), found.getValue());
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
			NodeBody body = Objects.requireNonNull(currentNode.getBody(),
					"An executing node has no body");
			printAgentStatement(body);
			List<Reply> replies = body.getReplies();

			if (replies.isEmpty()) {
				System.out.println("\n[Dialogue ended — no reply options.]\n");
				break;
			}

			// Auto-forward if the only reply has no statement
			if (replies.size() == 1 && replies.get(0).isAutoForward()) {
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
	private static @Nullable Node advanceDialogue(ActiveDialogue activeDialogue, ExecutableProject project,
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
			String targetLanguage = Objects.requireNonNull(currentPointer.getLanguage(),
					"External node pointer has no language");
			Map.Entry<ResourcePointer, Dialogue> found =
					findDialogue(project, targetLanguage, targetDialogueName);
			if (found == null) {
				System.err.println("Could not find external dialogue '" + targetDialogueName
						+ "' (" + targetLanguage + ").\n");
				return null;
			}
			System.out.println("\n[Jumping to dialogue: " + targetDialogueName + "]\n");
			runDialogueLoop(scanner, project, found.getKey(), found.getValue());
			return null;
		}

		return null;
	}

	/**
	 * Looks up the dialogue for the given {@code language} and {@code dialogueName} in the
	 * project's dialogue map.
	 *
	 * @param project      the {@link ExecutableProject} to search.
	 * @param language     the language code to match.
	 * @param dialogueName the dialogue name to match.
	 * @return the matching {@link ResourcePointer}/{@link Dialogue} entry, or {@code null} if no
	 *         dialogue matches both.
	 */
	private static Map.@Nullable Entry<ResourcePointer, Dialogue> findDialogue(ExecutableProject project,
																	 String language,
																	 String dialogueName) {
		for (Map.Entry<ResourcePointer, Dialogue> entry : project.getDialogues().entrySet()) {
			ResourcePointer rp = entry.getKey();
			if (language.equals(rp.getLanguage()) && dialogueName.equals(rp.getDialogueName()))
				return entry;
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
			String label = reply.isAutoForward()
					? "(continue)"
					: Objects.requireNonNull(reply.getStatement()).toString().trim();
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

		ProjectParserResult result;
		try {
			ProjectScriptLoader scriptLoader = new ProjectScriptLoader(projectFile);
			ProjectParser parser = new ProjectParser(scriptLoader);
			result = parser.parse();
		} catch (IOException | ParseException e) {
			System.err.println("Failed to load project: " + e.getMessage() + "\n");
			return;
		}
		if (!result.getParseErrors().isEmpty()) {
			System.err.println("Project contains parse errors:");
			result.getParseErrors().forEach((file, errors) ->
					errors.forEach(e -> System.err.println("  [" + file + "] " + e.getMessage())));
			return;
		}

		ExecutableProject project = Objects.requireNonNull(result.getProject(),
				"A project that parsed without errors has no ExecutableProject");
		ProjectMetaData meta = Objects.requireNonNull(project.getMetaData(),
				"A project loaded from a metadata file has no ProjectMetaData");
		System.out.println("\nLoaded project: " + meta.getName() + " (v" + meta.getVersion() + ")\n");

		boolean inProject = true;
		while (inProject) {
			printProjectMenu(meta.getName());
			String choice = scanner.nextLine().trim();
			switch (choice) {
				case "1" -> System.out.println("\n" + result.generateSummaryString());
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

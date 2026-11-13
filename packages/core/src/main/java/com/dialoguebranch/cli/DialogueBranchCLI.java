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

package com.dialoguebranch.cli;

import com.dialoguebranch.exception.ExecutionException;
import com.dialoguebranch.exception.InvalidInputException;
import com.dialoguebranch.exception.ParseException;
import com.dialoguebranch.exception.ProjectParseException;
import com.dialoguebranch.execution.ActiveDialogue;
import com.dialoguebranch.execution.User;
import com.dialoguebranch.execution.VariableStore;
import com.dialoguebranch.expression.EvaluationException;
import com.dialoguebranch.model.common.ProjectMetaData;
import com.dialoguebranch.model.execute.Dialogue;
import com.dialoguebranch.model.execute.ExecutableProject;
import com.dialoguebranch.model.execute.Node;
import com.dialoguebranch.model.execute.NodeBody;
import com.dialoguebranch.model.execute.NodeContent;
import com.dialoguebranch.model.execute.Reply;
import com.dialoguebranch.model.execute.ResourcePointer;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
	 * accepted syntax. Package-private (rather than {@code private}) so a test can drive it
	 * without {@link #main} calling {@link System#exit}.
	 *
	 * @param args the raw command-line arguments (guaranteed non-empty by {@link #main}).
	 * @return the process exit status to use.
	 */
	static int runNonInteractive(String[] args) {
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
		ExecutableProject project = loadProject(projectFile, "ERROR: Failed to load project: ");
		if (project == null)
			return EXIT_PARSE_ERROR;
		System.out.println(project.generateSummaryString());
		return EXIT_OK;
	}

	/**
	 * Parses the given project, then runs the named dialogue interactively on the terminal — the
	 * same conversational execution loop ({@link #runDialogueLoop}) used by the interactive
	 * session's "Execute a dialogue script" option.
	 *
	 * @param projectFile   the {@code dlb-project.xml} file to load the project from.
	 * @param language      the language code of the dialogue to execute; if the dialogue
	 *                      isn't available in it, it runs in the project's source language, with
	 *                      a warning if the project doesn't have the language at all.
	 * @param dialogueName  the name of the dialogue to execute.
	 * @return {@link #EXIT_OK} once the dialogue finishes, {@link #EXIT_PARSE_ERROR} if the
	 *         project fails to parse, or {@link #EXIT_USAGE_ERROR} if no such dialogue exists.
	 */
	private static int executeNonInteractive(File projectFile, String language,
											 String dialogueName) {
		ExecutableProject execProject = loadProject(projectFile,
				"ERROR: Failed to load project for execution: ");
		if (execProject == null)
			return EXIT_PARSE_ERROR;

		if (!execProject.getLanguages().contains(language)) {
			System.err.println("WARNING: the project has no language '" + language
					+ "'; playing in '" + execProject.getSourceLanguage() + "'.");
		}
		ResourcePointer pointer = findDialogue(execProject, language, dialogueName);
		if (pointer == null) {
			System.err.println("ERROR: Could not find dialogue '" + dialogueName
					+ "' (" + language + ") in the parsed project.");
			return EXIT_USAGE_ERROR;
		}

		runDialogueLoop(new Scanner(System.in), execProject, pointer);
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
				  dialogue script" option. A dialogue that isn't available in the given
				  language runs in the project's source language.

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
		ExecutableProject execProject = loadProject(projectFile,
				"Failed to load project for execution: ");
		if (execProject == null)
			return;

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

		ResourcePointer pointer = findDialogue(execProject, selectedLanguage, selectedDialogue);
		if (pointer == null) {
			System.out.println("Could not locate dialogue '" + selectedDialogue
					+ "' (" + selectedLanguage + ") in the parsed project.\n");
			return;
		}

		runDialogueLoop(scanner, execProject, pointer);
	}

	/**
	 * Runs an interactive execution loop for the given dialogue. Prints agent statements, lists
	 * reply options, and follows the selected replies, also into other dialogues of the project,
	 * until the dialogue ends. The variables carry over from one dialogue to the next.
	 *
	 * @param scanner     the shared {@link Scanner} for reading user input.
	 * @param project     the {@link ExecutableProject} the dialogue is part of.
	 * @param pointer     the {@link ResourcePointer} identifying the dialogue to execute.
	 */
	private static void runDialogueLoop(Scanner scanner, ExecutableProject project,
										ResourcePointer pointer) {
		VariableStore variableStore = new VariableStore(new User("cli-user"));

		ActiveDialogue activeDialogue;
		Node currentNode;
		try {
			activeDialogue = new ActiveDialogue(project,
					project.resolve(pointer.getDialogueName(), pointer.getLanguage()),
					variableStore);
			activeDialogue.setDialogueSwitchListener((from, to) -> {
				System.out.println("\n[Jumping to dialogue: " + to.pointer().getDialogueName()
						+ "]\n");
				printDialogueHeader(to.pointer());
			});
			printDialogueHeader(activeDialogue.getDialogueFileDescription());
			currentNode = activeDialogue.startDialogue(ZonedDateTime.now());
		} catch (ExecutionException | EvaluationException e) {
			System.err.println("Failed to start dialogue: " + e.getMessage() + "\n");
			return;
		}

		while (currentNode != null) {
			NodeContent body = Objects.requireNonNull(currentNode.getBody(),
					"An executing node has no body");
			printAgentStatement(body);
			List<Reply> replies = body.getReplies();

			if (activeDialogue.isFinished()) {
				System.out.println("\n[Dialogue ended — no reply options.]\n");
				break;
			}

			int chosenReplyId = -1;
			// Auto-forward if the only reply has no statement
			if (replies.size() == 1 && replies.get(0).isAutoForward()) {
				System.out.println("\n[Auto-forward]\n");
				chosenReplyId = replies.get(0).getReplyId();
			} else {
				printReplyOptions(replies);
				System.out.print("Your reply: ");
				String replyInput = scanner.nextLine().trim();
				try {
					int chosenIndex = Integer.parseInt(replyInput) - 1;
					if (chosenIndex >= 0 && chosenIndex < replies.size())
						chosenReplyId = replies.get(chosenIndex).getReplyId();
				} catch (NumberFormatException ignored) { }

				if (chosenReplyId == -1) {
					System.out.println("Invalid choice. Please enter a number from the list.\n");
					continue;
				}
			}

			try {
				currentNode = activeDialogue.selectReply(chosenReplyId, ZonedDateTime.now());
			} catch (EvaluationException e) {
				System.err.println("Evaluation error: " + e.getMessage() + "\n");
				break;
			} catch (ExecutionException e) {
				System.err.println("Could not continue the dialogue: " + e.getMessage() + "\n");
				break;
			}
		}

		System.out.println("============================================================");
		System.out.println(" Dialogue finished.");
		System.out.println("============================================================\n");
	}

	/**
	 * Prints the banner that says which dialogue runs, in which language.
	 *
	 * @param pointer the dialogue.
	 */
	private static void printDialogueHeader(ResourcePointer pointer) {
		System.out.println("\n============================================================");
		System.out.println(" Executing: " + pointer.getDialogueName()
				+ " [" + pointer.getLanguage() + "]");
		System.out.println("============================================================\n");
	}

	/**
	 * Looks up the dialogue with the given name in the given language, falling back to the
	 * project's source language if it isn't translated into that language (see {@link
	 * ExecutableProject#getDialoguePointer(String, String)}).
	 *
	 * @param project      the {@link ExecutableProject} to search.
	 * @param language     the language code.
	 * @param dialogueName the dialogue name.
	 * @return the dialogue's {@link ResourcePointer}, or {@code null} if the project has no
	 *         dialogue with that name.
	 */
	private static @Nullable ResourcePointer findDialogue(ExecutableProject project,
			String language, String dialogueName) {
		try {
			return project.getDialoguePointer(dialogueName, language);
		} catch (ExecutionException e) {
			return null;
		}
	}

	/**
	 * Loads the project from the given metadata file. If it can't be loaded, prints why to
	 * {@link System#err}: every parse error with its file, or the given prefix and the reason.
	 *
	 * @param projectFile the {@code dlb-project.xml} file to load the project from.
	 * @param failurePrefix the start of the message if the project can't be read at all.
	 * @return the project, or {@code null} if it can't be loaded.
	 */
	private static @Nullable ExecutableProject loadProject(File projectFile,
			String failurePrefix) {
		try {
			return ExecutableProject.load(projectFile.toPath());
		} catch (ProjectParseException e) {
			System.err.println("Project contains parse errors:");
			e.getErrors().forEach((file, errors) ->
					errors.forEach(error -> System.err.println("  [" + file + "] "
							+ error.getMessage())));
			return null;
		} catch (IOException | ParseException e) {
			System.err.println(failurePrefix + e.getMessage());
			return null;
		}
	}

	/**
	 * Prints the agent's statement text from the given {@link NodeBody} (segments only, not
	 * replies).
	 *
	 * @param body the body whose text segments to render
	 */
	private static void printAgentStatement(NodeContent body) {
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

		ExecutableProject project = loadProject(projectFile, "Failed to load project: ");
		if (project == null)
			return;
		ProjectMetaData meta = Objects.requireNonNull(project.getMetaData(),
				"A project loaded from a metadata file has no ProjectMetaData");
		System.out.println("\nLoaded project: " + meta.getName() + " (v" + meta.getVersion() + ")\n");

		boolean inProject = true;
		while (inProject) {
			printProjectMenu(meta.getName());
			String choice = scanner.nextLine().trim();
			switch (choice) {
				case "1" -> System.out.println("\n" + project.generateSummaryString());
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

# Dialogue Branch Core Java Library

## Introduction

The "Core" of the Dialogue Branch software is a library that can read and execute Dialogue Branch (.dlb) scripts. This core library is written in Java, and its source lives in this monorepo under [`packages/core`](https://github.com/dialoguebranch/platform/tree/main/packages/core). It is published to Maven Central as `com.dialoguebranch:dlb-core-java`.

```groovy
dependencies {
    implementation 'com.dialoguebranch:dlb-core-java:<version>'
}
```

## Playing a Dialogue

Load a project from its `dlb-project.xml`, start one of its dialogues, and select replies until it ends:

```java
ExecutableProject project = ExecutableProject.load(Path.of("dlb-project.xml"));
ActiveDialogue dialogue = project.startDialogue("welcome", new User("sam"));

Node node = dialogue.getCurrentNode();
while (node != null && !node.getBody().getReplies().isEmpty()) {
    System.out.println(node.getBody());
    Reply reply = chooseReply(node.getBody().getReplies()); // e.g. ask the user
    node = dialogue.selectReply(reply.getReplyId(), ZonedDateTime.now());
}
```

* `ExecutableProject.load` throws a `ProjectParseException` that lists every parse error, per file; warnings are available from `project.getWarnings()`.
* `startDialogue` has overloads that take a language, an existing `VariableStore` and the start time. A dialogue that isn't available in the given language runs in the project's source language.
* `selectReply` runs the reply's `set` commands and returns the next node, or `null` when the reply ends the dialogue. When a reply leads into another dialogue, the `ActiveDialogue` switches to it, in the same language and with the same variables; a `DialogueSwitchListener` hears of the switch, before the first node there runs.
* Where a translation depends on the end user's gender, the dialogue follows the value of `$dlb.userGender` in its variables at every step.
* To look up a dialogue without starting it, use `project.getDialogue(name)` or `getDialogue(name, language)`.

## Package Overview

The `com.dialoguebranch` package is organised as follows:

* `model/common` — Shared types used by the execution model, e.g. `ProjectMetaData`, `StorageSource`.
* `model/execute` — The immutable *runtime* model (`ExecutableProject`, `Dialogue`, `Node`, `NodeHeader`, `NodeBody`, `ResolvedNodeBody`, `Reply`, `VariableString`, `LoggedDialogue`, `DialogueState`), the command types executed within a node body (`SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, the `InputCommand` variants), and the API protocol types used to serialise dialogue state (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`).
* `execution` — The runtime engine itself: `ActiveDialogue` drives a live conversation, following replies into other dialogues through a `DialogueResolver`, `VariableStore` holds session variables, and `parser/` contains the parsers used at execution time (`DialogueBranchParser`, `BodyParser`, `CommandParser`, `ProjectParser`, `ProjectScriptLoader`, and others).
* `editing` — `ProjectMetaDataWriter`, used to export a project's metadata.
* `i18n` — Translation support: `Translator` applies a `.json` translation file to a `Dialogue`, and `TranslatableExtractor` walks a node's body to extract translatable segments.
* `exception` — Typed exceptions used throughout the library (`NodeParseException`, `ExecutionException`, and others).
* `cli` — `DialogueBranchCLI` (the module's default main class): an interactive, menu-driven inspector when run with no arguments, or a non-interactive, scriptable validator/executor (suited to CI) when run with a project path and flags — see its own `--help`.

For the exact syntax of the `.dlb` script format this library parses and executes, see the [Dialogue Branch Language Definition](/language/).

## Command-Line Tool

`cli.DialogueBranchCLI` (the module's default main class) can be run two ways:

```bash
# Interactive, menu-driven session (default with no arguments)
./gradlew run -q --console=plain

# Non-interactive: parse a project and print its summary, exiting non-zero on parse errors
./gradlew run -q --console=plain --args="<path-to-dlb-project.xml> [--validate]"

# Non-interactive: run a specific dialogue interactively on the terminal
./gradlew run -q --console=plain --args="<path-to-dlb-project.xml> --execute <language> <dialogue>"

# Full syntax
./gradlew run -q --console=plain --args="--help"
```

The non-interactive validate mode is suited to CI. Alongside parse errors, it also reports
**warnings** for issues that can never cause a runtime error but likely indicate an authoring
mistake — such as an **orphaned node**: a node that no reply link points to and that isn't its
own dialogue's Start node, or a node whose **speaker isn't one of the project's speakers** (only
when the project defines speakers). Warnings are printed but do not affect the exit status.

## Documentation

Javadoc for the library can be generated locally with `./gradlew javadoc` (output in `packages/core/build/reports/javadoc/`). Since the library is published to Maven Central, its Javadoc is also browsable via [javadoc.io](https://javadoc.io/doc/com.dialoguebranch/dlb-core-java) for any released version, without a local build.

# Dialogue Branch Core Java Library

## Introduction

The "Core" of the Dialogue Branch software is a library that can read and execute Dialogue Branch (.dlb) scripts. This core library is written in Java, and its source lives in this monorepo under [`packages/core`](https://github.com/dialoguebranch/platform/tree/main/packages/core). It is published to Maven Central as `com.dialoguebranch:dlb-core-java`.

```groovy
dependencies {
    implementation 'com.dialoguebranch:dlb-core-java:<version>'
}
```

## Package Overview

The `com.dialoguebranch` package is organised as follows:

* `model/common` — Shared types used by the execution model, e.g. `ProjectMetaData`, `StorageSource`.
* `model/execute` — The immutable *runtime* model (`ExecutableProject`, `Dialogue`, `Node`, `NodeBody`, `Reply`, `VariableString`, `LoggedDialogue`, `DialogueState`), the command types executed within a node body (`SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, the `InputCommand` variants), and the API protocol types used to serialise dialogue state (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`).
* `execution` — The runtime engine itself: `ActiveDialogue` drives a single live dialogue session, `VariableStore` holds session variables, and `parser/` contains the parsers used at execution time (`DialogueBranchParser`, `BodyParser`, `CommandParser`, `ProjectParser`, `ProjectScriptLoader`, and others).
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
own dialogue's Start node. Warnings are printed but do not affect the exit status.

## Documentation

Javadoc for the library can be generated locally with `./gradlew javadoc` (output in `packages/core/build/reports/javadoc/`). Since the library is published to Maven Central, its Javadoc is also browsable via [javadoc.io](https://javadoc.io/doc/com.dialoguebranch/dlb-core-java) for any released version, without a local build.

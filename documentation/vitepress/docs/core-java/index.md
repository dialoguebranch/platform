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

* `model/common` — Shared types used by both the editing and execution models, e.g. `ProjectMetaData`, `ScriptTreeNode`, `StorageSource`.
* `model/edit` — The mutable *editor* model (`EditableProject`, `EditableScript`, `EditableNode`), used by authoring tools — not by the runtime execution engine.
* `model/execute` — The immutable *runtime* model (`Project`, `Dialogue`, `Node`, `NodeBody`, `Reply`, `VariableString`, `LoggedDialogue`, `DialogueState`), the command types executed within a node body (`SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, the `InputCommand` variants), and the API protocol types used to serialise dialogue state (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`).
* `execution` — The runtime engine itself: `ActiveDialogue` drives a single live dialogue session, `VariableStore` holds session variables, and `parser/` contains the parsers used at execution time (`DialogueBranchParser`, `BodyParser`, `CommandParser`, `ProjectParser`, `DirectoryScriptLoader`, and others).
* `editing` — Parsers and writers for the *editor* model (`EditableScriptParser`, `EditableScriptWriter`, `EditableTranslationParser`, `EditableHeaderParser`, and others), used by authoring tools such as the web client's visual editor.
* `i18n` — Translation support: `Translator` applies a `.json` translation file to a `Dialogue`, and `TranslatableExtractor` walks a node's body to extract translatable segments.
* `exception` — Typed exceptions used throughout the library (`ScriptParseException`, `ExecutionException`, `VariableException`, and others).
* `cli` — `ProjectTool`, an interactive command-line inspector (the module's default main class), and `CommandLineRunner`.

For the exact syntax of the `.dlb` script format this library parses and executes, see the [Dialogue Branch Language Definition](/language/).

## Documentation

Javadoc for the library can be generated locally with `./gradlew javadoc` (output in `packages/core/build/reports/javadoc/`). Since the library is published to Maven Central, its Javadoc is also browsable via [javadoc.io](https://javadoc.io/doc/com.dialoguebranch/dlb-core-java) for any released version, without a local build.

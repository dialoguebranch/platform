# Dialogue Branch Core Java Library

## Introduction

The "Core" of the Dialogue Branch software is a library that can read and execute Dialogue Branch (.dlb) scripts. This core library is written in Java, and its source lives in this monorepo under [`packages/core`](https://github.com/dialoguebranch/platform/tree/main/packages/core). It is published to Maven Central as `com.dialoguebranch:dlb-core-java`.

```groovy
dependencies {
    implementation 'com.dialoguebranch:dlb-core-java:<version>'
}
```

## Playing a Dialogue

Load a project from its `dlb-project.xml`, start one of its dialogues, and show each step and select replies until it finishes:

```java
ExecutableProject project = ExecutableProject.load(Path.of("dlb-project.xml"));
ActiveDialogue dialogue = project.startDialogue("welcome", new User("sam"));

DialogueMessage step = dialogue.getCurrentMessage();
while (step != null) {
    System.out.println(step.getSpeaker() + ": " + step.getStatement().getText());
    if (dialogue.isFinished()) break;
    dialogue.selectReply(chooseReply(step.getReplies())); // e.g. ask the user
    step = dialogue.getCurrentMessage();
}
```

`chooseReply` stands for your own code: it shows the replies (`reply.getText()`, or a "Continue" button where `reply.isAutoForward()`) and returns the one the user picks.

* `ExecutableProject.load` throws a `ProjectParseException` that lists every parse error, per file; warnings are available from `project.getWarnings()`.
* `startDialogue` has overloads that take a language, an existing `VariableStore` and the start time. A dialogue that isn't available in the given language runs in the project's source language.
* `getCurrentMessage` returns the current step as a `DialogueMessage`, the same shape the Web Service sends: the speaker, the statement with its variables filled in, its actions and input fields, and the replies with their IDs. `getStatement().getText()` and `reply.getText()` give the text without the input fields and actions, which `getSegments()` has. The step's `loggedDialogueId` and `loggedInteractionIndex` say where it is in the log, when a log is kept (see below); without a log they are `null` and 0, which has no meaning.
* `getCurrentMessage` returns `null` only after a reply to `End`: there is nothing left to show then. That is why the loop checks for `null` and for `isFinished` separately.
* `isFinished` is true once a reply led to `End`, or the dialogue is at a node without replies. That last node still has a statement to show.
* `selectReply` runs the reply's `set` commands and returns the next node, or `null` when the reply ends the dialogue. It takes the reply, or its ID, and optionally the time of the selection; without one, it uses now, in the time zone of the `VariableStore`'s user, as `startDialogue` does. When a reply leads into another dialogue, the `ActiveDialogue` switches to it, in the same language and with the same variables; a `DialogueSwitchListener` hears of the switch, before the first node there runs.
* Where a translation depends on the end user's gender, the dialogue follows the value of `$dlb.userGender` in its variables at every step.
* To look up a dialogue without starting it, use `project.getDialogue(name)` or `getDialogue(name, language)`.

### Keeping a log, going back and resuming

An `ActiveDialogue` can keep a log of the conversation, as the Web Service does: the agent's statements and the user's replies, across dialogues. The log is a `LoggedDialogue` that you give it, so you decide where it lives; `InMemoryLoggedDialogue` is a simple one for a transcript or a back button.

```java
ActiveDialogue dialogue = new ActiveDialogue(project, project.resolve("welcome", null),
        new VariableStore(new User("sam")));
InMemoryLoggedDialogue log = dialogue.keepInMemoryLog(ZonedDateTime.now()); // before starting
dialogue.startDialogue();
// ... select replies; every step is recorded. A reply into another dialogue starts a new log in
// the same session: the current one is dialogue.getLoggedDialogue(), and each in-memory log
// points back to the one before it with getPrevious().

dialogue.back();      // the previous agent step, shown again: nothing is added to the log
dialogue.cancel();    // marks the log cancelled
```

* Each step is an interaction of the log, `AGENT` for what the agent said and `USER` for the reply, with the index of the interaction before it. The log is marked completed when the dialogue ends.
* A reply that leads into another dialogue completes the log, and the conversation continues in a new one, made by the `LoggedDialogueFactory` you pass to `setLoggedDialogue(log, factory)`; `InMemoryLoggedDialogue.factory()` makes in-memory logs of the same session.
* `back()` goes to the previous agent step in the current dialogue and executes it again, so its `set` commands run again. A later reply continues from that step. It refuses a log that is completed or cancelled.
* `ActiveDialogue.restore(resolver, log, index, variables, factory)` rebuilds a dialogue from a log at a step, to resume later: the Web Service does this on every request. It leaves the step's node unexecuted so a reply can be selected right away; `executeCurrentNode` executes it to show it again. Until then `getCurrentMessage` and `isFinished` throw an `IllegalStateException`, since the replies a node has depend on its `<<if>>`s. Only the position is restored: the variables are those of the `VariableStore` you pass.

## Package Overview

The `com.dialoguebranch` package is organised as follows:

* `model/common` — Shared types used by the execution model, e.g. `ProjectMetaData`, `StorageSource`.
* `model/execute` — The immutable *runtime* model (`ExecutableProject`, `Dialogue`, `Node`, `NodeHeader`, `NodeBody`, `ResolvedNodeBody`, `Reply`, `VariableString`, `LoggedDialogue`), the command types executed within a node body (`SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, the `InputCommand` variants), and the API protocol types used to serialise dialogue state (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`).
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

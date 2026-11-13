# Dialogue Branch Core Java Library

The Dialogue Branch Core Java Library is the foundation of the Dialogue Branch platform. It
provides everything needed to define, parse, translate, and execute Dialogue Branch scripts
(`.dlb` files), as well as tooling for managing projects and generating translation artefacts.

For a short guide to loading a project, playing a dialogue, keeping a log, going back and resuming,
see the [Core Java](https://www.dialoguebranch.com/docs/core-java/) documentation page.

```java
ExecutableProject project = ExecutableProject.load(Path.of("my-project/dlb-project.xml"));
ActiveDialogue dialogue = project.startDialogue("welcome", new User("sam"));
DialogueMessage step = dialogue.getCurrentMessage();
```

## Package Structure

All classes live under the root package `com.dialoguebranch`. The main packages are described
below; `expression` (the `<<if>>`/`<<set>>` expression language), `io`, `json` and `util` hold
supporting code.

---

### `cli` — Command-Line Tool

| Class | Description |
|---|---|
| `DialogueBranchCLI` | Interactive menu-driven tool with no arguments — loads a `dlb-project.xml` and offers options such as printing a project summary or playing a dialogue — or a non-interactive, scriptable validator/executor when given a project path and flags (see its own `--help`). This is the default main class. |

---

### `model` — Data Model

#### `model/common` — Shared Structures

| Class | Description |
|---|---|
| `ProjectMetaData` | Name, version, description, base path, language map and speakers of a project, as declared in `dlb-project.xml`. |
| `DialogueBranchProject` | The common contract of every representation of a project. |
| `Speaker` | A speaker of the project: name, gender, and whether new nodes get it by default. |
| `StorageSource` / `FileStorageSource` / `DescribedStorageSource` | Where a script or translation was read from: a file, or a described source such as a database row. |
| `ResourceType` | Enum: `SCRIPT`, `TRANSLATION`, or `FOLDER`. |
| `ReservedVariables` / `VariableNames` | The reserved `$dlb.` variables and their allowed values; what a variable name is. |
| `DialogueBranchConstants` | File extensions (`.dlb`, `.json`), reserved node and header names, and other shared constants. |

#### `model/execute` — Execution Model

The parsed model used at runtime.

| Class | Description |
|---|---|
| `ExecutableProject` | A loaded project: its dialogues in every language and their translations. `load(Path)` loads one from its `dlb-project.xml`; `getDialogue`, `resolve` and `getDialoguePointer` look a dialogue up by name and language (falling back to the source language); `startDialogue` starts one, and `createDialogue` creates one without starting it, for example to keep a log. It is the `DialogueResolver` of the dialogues started from it. |
| `Dialogue` | A parsed dialogue script: its nodes, the variables it reads and writes, and the dialogues it links to. |
| `Node` | A single dialogue node with a `NodeHeader` and a body. |
| `NodeHeader` | The header of a node: its title, its speaker (optional only for the `End` node), and other `key: value` tags. |
| `NodeContent` / `NodeBody` / `ResolvedNodeBody` | The body of a node: as parsed (`NodeBody`, with every command and `<<if>>`/`<<random>>` branch), and as executed (`ResolvedNodeBody`, only what applies, ready to show). |
| `Reply` | A reply option in a node body, pointing to another node via a `NodePointer`. |
| `VariableString` | A string that may contain embedded variable references (`$varName`). |
| `ResourcePointer` | Identifies a script or translation by language, dialogue name and resource type. |
| `Language` / `LanguageMap` | A language of the project, and its source and translation languages. |
| `LoggedDialogue` / `LoggedInteraction` / `MessageSource` | The log of a conversation in one dialogue: its steps, each by the agent or the user. |
| `command/` | `Command` subtypes: `SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, and the family of `InputCommand`s (`InputTextCommand`, `InputNumericCommand`, etc.). |
| `nodepointer/` | `InternalNodePointer` (same dialogue) and `ExternalNodePointer` (another dialogue). |
| `protocol/` | The step as a client gets it: `DialogueMessage` (made by `DialogueMessageFactory`), `DialogueStatement`, `DialogueAction`, `ReplyMessage`; the Web Service sends these as JSON. |

---

### `execution` — Dialogue Execution Engine

| Class | Description |
|---|---|
| `ActiveDialogue` | A conversation in progress: starts a dialogue (`startDialogue`), gives the current step (`getCurrentMessage`), selects a reply (`selectReply`), following replies into other dialogues, and says when it has finished (`isFinished`). It can keep a log (`setLoggedDialogue`, `keepInMemoryLog`), go back a step (`back`), be cancelled (`cancel`), and be rebuilt from a log to resume (`restore`). |
| `DialogueResolver` / `ResolvedDialogue` | Finds the dialogue a reply leads into, by name and language; `ExecutableProject` is one. |
| `DialogueSwitchListener` | Hears when a reply leads into another dialogue, before its first node runs. |
| `LoggedDialogueFactory` / `InMemoryLoggedDialogue` | Makes the log of a dialogue the conversation enters; a log kept in memory, for an application that doesn't store it. |
| `ExecuteNodeResult` | An executed node with its dialogue, and its log and place in it, if one is kept. |
| `User` | The person playing the dialogue: an ID and a time zone. |
| `VariableStore` / `Variable` | The variables of a user, with change listeners (`VariableStoreOnChangeListener`, `VariableStoreChange`) and the source of each change (`VariableUpdatedSource`). |
| `parser/` | Parsers for `.dlb` files (`DialogueBranchParser`, `BodyParser`, `BodyTokenizer`, `CommandParser`, `ReplyParser`), a node header (`DialogueBranchHeader`) and how a script splits into nodes (`DialogueBranchScriptSplitter`); for a project, `ProjectParser` with a `ScriptLoader` (`ProjectScriptLoader` reads a `dlb-project.xml` and its files), `ProjectMetaDataParser`, and the validators it runs. |

---

### `editing` — Editing-Layer Writers

| Class | Description |
|---|---|
| `writer/ProjectMetaDataWriter` | Writes a `ProjectMetaData` object back to `dlb-project.xml`. |

---

### `i18n` — Internationalisation

Translation extraction, storage, and application.

| Class | Description |
|---|---|
| `Translator` | Applies a set of translations to a `Dialogue`, returning a translated copy. A text is only translated by an entry of its own speaker; matching is exact (trimmed), then with normalised whitespace. |
| `TranslatableExtractor` | Walks a node body and extracts all translatable text segments as `SourceTranslatable` records, descending into `<<if>>` and `<<random>>` branches. |
| `Translatable` | A single segment of translatable text, which may span multiple body parts. |
| `SourceTranslatable` | A `Translatable` paired with the speaker name that provides its translation context key. |
| `TranslationContext` | The genders of the speaker and the user, which select a translation where a text changes with them. |
| `ContextTranslation` | A translation of one speaker's text, for a set of contexts. |
| `TranslationFile` | In-memory representation of a `.json` translation file, with read/write support. |
| `TranslationParser` / `TranslationParserResult` | Parses a `.json` translation file. |

---

### `exception` — Exception Types

| Class | Meaning |
|---|---|
| `DialogueBranchException` | Base class for all library exceptions. |
| `ParseException` | Content with an invalid format; the base of the parse errors below. |
| `LineNumberParseException` | A parse error at a line and column. |
| `NodeParseException` | A parse error within a node. |
| `ProjectParseException` | A project can't be loaded: every parse error, per file. |
| `InvalidInputException` | Invalid user or programmatic input. |
| `ExecutionException` | An error while playing a dialogue, with a type such as `DIALOGUE_NOT_FOUND`, `NODE_NOT_FOUND`, `REPLY_NOT_FOUND`, `DIALOGUE_COMPLETED` or `DIALOGUE_CANCELLED`. |
| `DuplicateLanguageCodeException` | Adding a language code that already exists in the project. |
| `UnknownLanguageCodeException` | Looking up a language code that does not exist. |

`EvaluationException` (in `expression`) is thrown when an expression can't be evaluated.

---

## Using the Gradle Build Script

The library includes a Gradle Build Script (`build.gradle`) that can be used to compile, build, and
run the library among other things. You don't need to install Gradle on your system to use this
build script, as the repository provides a "Gradle Wrapper" (see
https://docs.gradle.org/current/userguide/gradle_wrapper.html) which is an executable script that
will download a pre-defined version of Gradle before executing any of the defined tasks in the build
script. Using the Gradle Wrapper (`./gradlew` or `gradlew.bat`) is the recommended way of working
with the Gradle build script.

Here is a list of common useful tasks:
- `./gradlew clean` - Cleans all generated output build files (deletes the `/build/` folder).
- `./gradlew build` - Compiles and builds everything.
- `./gradlew run -q --console=plain` - Runs the library's main class (`DialogueBranchCLI`) in its
  interactive, menu-driven mode. The `-q` flag tells Gradle to be "quiet", while
  `--console=plain` hides the `<=========----> 75% EXECUTING` progress bar. Both flags are needed
  for `DialogueBranchCLI`'s interactive command-line input to work properly.
- `./gradlew run -q --console=plain --args="<path-to-dlb-project.xml> [--validate]"` - Runs
  `DialogueBranchCLI` non-interactively instead: parses the project, prints its summary, and exits
  non-zero on parse errors — usable as a CI gate. `--args="--help"` prints the full syntax,
  including `--execute <language> <dialogue>` to run a specific dialogue.
- `./gradlew test` - Runs all unit tests. You can run a single test class with
  `./gradlew test --tests "com.dialoguebranch.ClassName"`, or a single method with
  `./gradlew test --tests "com.dialoguebranch.ClassName.methodName"`. The HTML test report is
  written to `build/reports/tests/test/index.html`.

Some more advanced tasks:
- `./gradlew javadoc` - Generate the Javadoc HTML pages in `/build/reports/javadoc/`.
- `./gradlew wrapper --gradle-version latest` - Generates the Gradle Wrapper files, targeting the
  latest Gradle version. Replace "latest" with a specific version number to generate wrapper scripts
  for the indicated version. This can be used e.g. to upgrade the Gradle version. Note that the
  Gradle Wrapper files are part of the source code committed into Git. *NOTE:* If this task doesn't
  work, you can also manually change the value of `distributionUrl` in the
  `gradle-wrapper.properties` file.
- `./gradlew tasks` - Outputs the full list of available tasks supported by the build script, in
  case you're interested in exploring this.

## Publishing to Maven Central

The library is published to Maven Central under the coordinates
`com.dialoguebranch:dlb-core-java`. Publishing uses the
[NMCP plugin](https://gradleup.com/nmcp/) (New Maven Central Publishing) via the Sonatype Central
Portal API.

### Prerequisites

Before publishing, you need the following configured in your local `gradle.properties` file
(found at `packages/core/gradle.properties`, which is excluded from version control):

**GPG signing credentials** — Maven Central requires all artifacts to be signed. If you do not yet
have a GPG key, generate one with:
```bash
gpg --gen-key
```
Then export your secret keyring:
```bash
gpg --export-secret-keys <YOUR_KEY_ID> > ~/.gnupg/secring.gpg
```

Add the following to `gradle.properties`:
```properties
# The last 8 characters of your GPG key ID
signing.keyId=<LAST_8_CHARS_OF_KEY_ID>

# Your GPG passphrase (leave empty if none was set)
signing.password=<YOUR_GPG_PASSPHRASE>

# Absolute path to your exported GPG keyring file
signing.secretKeyRingFile=/Users/<your-username>/.gnupg/secring.gpg
```

**Sonatype Central Portal token** — Log in to [central.sonatype.com](https://central.sonatype.com),
go to your profile → **View Account** → **Generate User Token**, and add the generated credentials
to `gradle.properties`:
```properties
# Token username generated at central.sonatype.com
centralPortal.username=<TOKEN_USERNAME>

# Token password generated at central.sonatype.com
centralPortal.password=<TOKEN_PASSWORD>
```

### Publishing

The version comes from `global.json` at the repository root, which a release sets (see the
root `CLAUDE.md`). Check out the release tag, then run:

```bash
./gradlew publishToMaven
```

This will build the library (including sources and Javadoc JARs), sign all artifacts, upload them
to the Sonatype Central Portal, and automatically release them to Maven Central.

It may take a few hours for the new version to be indexed by Maven Central mirrors such as
[MVN Repository](https://mvnrepository.com/artifact/com.dialoguebranch/dlb-core-java). The
authoritative place to confirm a release is live is:
[central.sonatype.com/artifact/com.dialoguebranch/dlb-core-java](https://central.sonatype.com/artifact/com.dialoguebranch/dlb-core-java)

To test the published artifact locally before releasing, install it to your local Maven repository
first:
```bash
./gradlew publishToMavenLocal
```

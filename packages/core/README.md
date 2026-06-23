# Dialogue Branch Core Java Library

The Dialogue Branch Core Java Library is the foundation of the Dialogue Branch platform. It
provides everything needed to define, parse, translate, and execute Dialogue Branch scripts
(`.dlb` files), as well as tooling for managing projects and generating translation artefacts.

## Package Structure

All classes live under the root package `com.dialoguebranch`. The source is divided into six
top-level packages.

---

### `cli` — Command-Line Tools

Runnable entry points for interacting with the library from the command line.

| Class | Description |
|---|---|
| `ProjectTool` | Interactive menu-driven tool for inspecting Dialogue Branch projects. Loads a `dlb-project.xml` and presents options such as printing a full project summary (language map, node counts per dialogue). This is the default main class. |
| `CommandLineRunner` | Earlier interactive runner covering a broader set of ad-hoc tasks: parsing individual scripts, generating translation files and TSV export files, and generating project summaries from a folder or metadata file. |

---

### `model` — Data Model

The data model is split into three sub-packages reflecting the lifecycle of a Dialogue Branch
project.

#### `model/common` — Shared Structures

Types used by both the editing and execution layers.

| Class | Description |
|---|---|
| `ProjectMetaData` | Name, version, description, base path, and language map for a project. |
| `ScriptTreeNode` | Node in a tree representing the folder/file hierarchy of scripts for one language. |
| `StorageSource` / `FileStorageSource` | Abstraction over where a script or translation file is physically stored. |
| `ResourceType` | Enum: `SCRIPT`, `TRANSLATION`, or `FOLDER`. |
| `DialogueBranchConstants` | File extensions (`.dlb`, `.json`) and other shared constants. |

#### `model/edit` — Editable Model

A mutable, editor-friendly representation of a project and its scripts. Used by tools that need
to inspect or modify scripts without executing them.

| Class | Description |
|---|---|
| `EditableProject` | Top-level container for an editable project: metadata plus a map from language to `ScriptTreeNode`. Provides methods for generating translation `.json` files and TSV exports. |
| `EditableScript` | An editable script: a list of `EditableNode`s with property-change support. |
| `EditableNode` | A single dialogue node in its editable form, consisting of an `EditableHeader` and `EditableBody`. |
| `EditableHeader` / `EditableBody` | Header (title, tags) and body (raw text content) of a node. |
| `EditableTranslation` / `EditableTranslationSet` | Editable representation of a translation file and a set of such files. |
| `Editable` | Base class providing `PropertyChangeSupport` for all editable model objects. |

#### `model/execute` — Execution Model

The fully parsed, immutable model used at runtime when executing dialogues.

| Class | Description |
|---|---|
| `Project` | Top-level runtime project: source dialogues, translations, and lookup logic. |
| `Dialogue` | A parsed dialogue script: an ordered list of `Node`s. |
| `Node` | A single dialogue node with a `NodeHeader` and `NodeBody`. |
| `NodeBody` | The body of a node: a list of segments that may be plain text, variable references, commands, or reply options. |
| `NodeHeader` | The header of a node: title and optional tags. |
| `Reply` | A reply option in a node body, pointing to another node via a `NodePointer`. |
| `VariableString` | A string that may contain embedded variable references (`$varName`). |
| `Language` / `LanguageSet` / `LanguageMap` | Language definitions and source-to-translation mappings as declared in `dlb-project.xml`. |
| `FileDescriptor` | Identifies a dialogue by name and language code. |
| `Project` | Holds all dialogues and translation maps; provides `getTranslatedDialogue()` for runtime lookup. |
| `LoggedDialogue` / `LoggedInteraction` | Record types for persisting a user's dialogue session history. |
| `DialogueState` / `DialogueStatus` | State tracking for a dialogue that is currently in progress. |
| `command/` | `Command` subtypes: `SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, and the family of `InputCommand`s (`InputTextCommand`, `InputNumericCommand`, etc.). |
| `nodepointer/` | `InternalNodePointer` (same script) and `ExternalNodePointer` (different script). |
| `protocol/` | API-layer message types: `DialogueMessage`, `DialogueStatement`, `DialogueAction`, `ReplyMessage`. Used for serialising dialogue state to JSON for REST clients. |

---

### `execution` — Dialogue Execution Engine

Runtime classes that drive an active dialogue session for a given user.

| Class | Description |
|---|---|
| `ActiveDialogue` | Manages a single in-progress dialogue: tracks the current node, resolves variable references, and evaluates `<<if>>` conditions. |
| `ExecuteNodeResult` | The result of executing one node: the rendered statement text, available reply options, and any triggered actions. |
| `User` | Represents an end-user participating in dialogue sessions. |
| `VariableStore` / `Variable` | Key-value store for dialogue variables, with change-listener support via `VariableStoreOnChangeListener` and `VariableStoreChange`. |
| `parser/` | Low-level parsers for `.dlb` files: `DialogueBranchParser`, `BodyParser`, `BodyTokenizer`, `CommandParser`, `ReplyParser`, as well as project-level loaders (`ProjectParser`, `ProjectFileLoader`, `DirectoryFileLoader`, `ResourceFileLoader`) and their result types. |

---

### `editing` — Editing-Layer Parsers and Writers

Parsers and writers for the editable model. These operate on raw files and produce or consume
`model/edit` objects.

| Class | Description |
|---|---|
| `parser/EditableScriptParser` | Parses a `.dlb` file into an `EditableScript`. |
| `parser/EditableProjectParser` | Reads a `dlb-project.xml` and assembles an `EditableProject` by scanning the filesystem for scripts and translation files. |
| `parser/EditableTranslationParser` | Parses a translation `.json` file into an `EditableTranslation`. |
| `parser/EditableHeaderParser` / `EditableBodyParser` | Parsers for the header and body sections of a `.dlb` node. |
| `writer/EditableScriptWriter` | Serialises an `EditableScript` back to `.dlb` format. |
| `writer/EditableTranslationWriter` | Serialises translation data to a `.json` file. |
| `writer/ProjectMetaDataWriter` | Writes a `ProjectMetaData` object back to `dlb-project.xml`. |
| `warning/ParserWarning` | Carries a non-fatal warning message emitted during parsing. |

---

### `i18n` — Internationalisation

Translation extraction, storage, and application.

| Class | Description |
|---|---|
| `Translator` | Applies a set of translations to a `Dialogue`, returning a translated copy. Uses exact-match (trimmed) then normalised-whitespace fallback. |
| `TranslatableExtractor` | Walks a node body and extracts all translatable text segments as `SourceTranslatable` records, descending into `<<if>>` and `<<random>>` branches. |
| `Translatable` | A single segment of translatable text, which may span multiple body parts. |
| `SourceTranslatable` | A `Translatable` paired with the speaker name that provides its translation context key. |
| `TranslationContext` | Carries the locale/context in which a translation is applied. |
| `ContextTranslation` | A translated string bound to a specific `TranslationContext`. |
| `TranslationFile` | In-memory representation of a `.json` translation file, with read/write and TSV export support. |
| `TranslationParser` / `TranslationParserResult` | Parses a `.json` translation file. |
| `TranslationTerm` | A single source–translation pair within a translation file. |
| `POEditorTools` | Utilities for generating POEditor-compatible export strings from translatable segments. |

---

### `exception` — Exception Types

Typed exceptions thrown throughout the library.

| Class | Meaning |
|---|---|
| `DialogueBranchException` | Base class for all library exceptions. |
| `ScriptParseException` | Thrown when a `.dlb` file cannot be parsed. |
| `NodeParseException` | Thrown when an individual node within a script cannot be parsed. |
| `InvalidInputException` | Thrown on invalid user or programmatic input. |
| `FileSystemException` | Thrown on filesystem errors (e.g. unable to create a directory). |
| `ExecutionException` | Thrown during dialogue execution (e.g. missing node). |
| `VariableException` | Thrown on invalid variable operations. |
| `DuplicateLanguageCodeException` | Thrown when adding a language code that already exists in the project. |
| `UnknownLanguageCodeException` | Thrown when looking up a language code that does not exist. |

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
- `./gradlew run -q --console=plain` - Runs the library's main class (`ProjectTool`). The
  `-q` flag tells Gradle to be "quiet", while `--console=plain` hides the
  `<=========----> 75% EXECUTING` progress bar. Both flags are needed to properly run the
  `ProjectTool`, which requires interactive command-line input.
- `./gradlew test` - Runs all unit tests. You can run a single test class with
  `./gradlew test --tests "com.dialoguebranch.ClassName"`, or a single method with
  `./gradlew test --tests "com.dialoguebranch.ClassName.methodName"`. The HTML test report is
  written to `build/reports/tests/test/index.html`.

Some more advanced tasks:
- `./gradlew javadoc` - Generate the Javadoc HTML pages in `/build/reports/javadoc/`. This can be
  used to generate Javadoc from the latest source in order to update the official hosted docs that
  can be found at https://dialoguebranch.com/docs/dialogue-branch/dev/dlb-core-java/index.html
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

Before publishing, update the `version` in `build.gradle` to the new release version, then run:

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

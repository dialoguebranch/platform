# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

The Dialogue Branch Platform is a monorepo for authoring, executing, and serving branching dialogue scripts (`.dlb` files). The platform consists of three components that work together:

1. **`packages/core`** — Core Java library (`com.dialoguebranch`) for parsing and executing `.dlb` scripts. Published to Maven Central as `com.dialoguebranch:dlb-core-java`.
2. **`apps/api`** — Spring Boot web service that wraps the core library with a REST API. Deployed as a WAR on Tomcat. API base path: `/dlb-web-service/v1`.
3. **`apps/web`** — Vue 3 / Vite / Tailwind CSS front-end (the "Web Client Test Application" / WCTA) that consumes the REST API.

The version for the entire monorepo is declared once in `global.json` at the root. Both Gradle builds and the web `package.json` read from this file.

## Working with Claude Code

- Never create git commits, push to remote, or open/close/comment on pull requests unless explicitly asked to do so.
- Never add a `Co-Authored-By` trailer or any Claude co-attribution to commit messages.

## Build Commands

All Gradle commands use the wrapper (`./gradlew`). Docker builds must be run from the **repo root** (`platform/`) because the build context spans both `apps/api/` and `packages/core/`.

### Core library (`packages/core/`)

```bash
cd packages/core
./gradlew build                                          # compile and build
./gradlew test                                           # run all tests
./gradlew test --tests "com.dialoguebranch.ClassName"    # single test class
./gradlew test --tests "com.dialoguebranch.ClassName.methodName"  # single method
./gradlew run -q --console=plain                         # run interactive ProjectTool CLI
./gradlew javadoc                                        # generate Javadoc to build/reports/javadoc/
./gradlew publishToMavenLocal                            # install locally before publishing
./gradlew publishToMaven                                 # publish to Maven Central
```

Test reports are written to `build/reports/tests/test/index.html`.

### API service (`apps/api/`)

```bash
cd apps/api
./gradlew build          # compile; also runs updateConfig as part of processResources
./gradlew updateConfig   # regenerates src/main/resources/deployment.properties (version + buildTime)
```

Docker build (from repo root):
```bash
docker build -t dlb-web-service -f apps/api/Dockerfile .
```

Database schema is managed by Flyway (`src/main/resources/db/migration/V*__*.sql`); migrations run automatically on startup against the configured MariaDB instance — there is no separate manual migrate command.

### Web client (`apps/web/`)

```bash
cd apps/web
npm install
npm run dev      # dev server with hot-reload (proxies to API at localhost:8089)
npm run build    # production build
npm run preview  # preview production build locally
```

## Local Development Stack

The full stack (MariaDB + Keycloak, plus the API when the `api` profile is enabled) is defined in
`infrastructure/docker/compose.yml`.

```bash
docker compose -f infrastructure/docker/compose.yml up               # MariaDB + Keycloak only
docker compose -f infrastructure/docker/compose.yml --profile api up # also builds/runs the API
```

Service URLs:
- API: `http://localhost:8089/dlb-web-service`
- Keycloak admin: `http://localhost:8081`

## Architecture

### Core library (`packages/core`)

The `com.dialoguebranch` package is divided into:

- **`model/common`** — Shared types (`ProjectMetaData`, `ScriptTreeNode`, `StorageSource`)
- **`model/edit`** — Mutable editor model (`EditableProject`, `EditableScript`, `EditableNode`) — used by tooling, not the runtime
- **`model/execute`** — Immutable runtime model (`Project`, `Dialogue`, `Node`, `NodeBody`, `Reply`, `VariableString`, `LoggedDialogue`, `DialogueState`) plus command types (`SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, `InputCommand` variants) and API protocol types (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`)
- **`execution`** — Runtime engine: `ActiveDialogue` drives a live session, `VariableStore` holds session variables; `parser/` contains `DialogueBranchParser`, `BodyParser`, `CommandParser`, `ProjectParser`, etc.
- **`editing`** — Parsers/writers for the edit model (`EditableScriptParser`, `EditableScriptWriter`, `EditableTranslationParser`, etc.)
- **`i18n`** — Translation: `Translator` applies `.json` translation files to a `Dialogue`; `TranslatableExtractor` walks node bodies to extract translatable segments
- **`exception`** — Typed exceptions (`ScriptParseException`, `ExecutionException`, `VariableException`, etc.)
- **`cli`** — `ProjectTool` (default main class, interactive inspector) and `CommandLineRunner`

The test `sourceSets` for core includes `../../examples` as a resource directory, so example `.dlb` files are available in tests.

### API service (`apps/api`)

A Spring Boot 3 application deployed as a WAR. Key structural classes:

- **`Application`** — Spring Boot entry point
- **`DlbProperties`** — Binds all `dlb.*` config from `application.yml` / environment variables
- **`ApplicationManager`** — Singleton (via `AppComponents`) that loads dialogue projects at startup and manages per-user `UserService` instances
- **`UserService` / `UserServiceFactory`** — Per-user runtime: owns the `VariableStore` and dialogue session state
- **`DialogueExecutor`** — Bridges the REST layer to the core `ActiveDialogue` execution engine

Controllers (all under `/v1`):
- `AuthController` — `/auth/logout`, `/auth/validate`
- `DialogueController` — `/dialogue/start`, `/dialogue/progress`, `/dialogue/continue`
- `VariablesController` — `/variables/get`, `/variables/set-single`
- `AdminController` — `/admin/list-dialogues`
- `InfoController` — `/info/all`
- `LogController` — dialogue log access

The service is a pure OAuth2 resource server: it validates bearer tokens issued by Keycloak (JWKS-based JWT validation configured in `SecurityConfig`, with claim extraction in `QueryRunner`) but never issues or refreshes tokens itself. Clients authenticate directly with Keycloak via the Authorization Code + PKCE flow.

Variable storage is pluggable: `VariableStoreJSONStorageHandler` (file-based) or `VariableStoreDatabaseStorageHandler` (MariaDB via Hibernate). An optional external variable service can be enabled via `DLB_EXTERNAL_VARIABLE_SERVICE_ENABLED`.

Projects are seeded into the database from `src/main/resources/projects-seed/` (one sub-directory per project, each a standard `.dlb`/`dlb-project.xml` file tree) by `ProjectSeedService` on first startup only; from then on, dialogue content lives in MariaDB, not on disk.

API configuration flows: `application.yml` → overridable at runtime by environment variables following the pattern `dlb.<property.path>` → `DLB_PROPERTY_PATH`.

#### Draft dialogues and publishing

Authoring (used by the web client's visual editor) operates on a separate, mutable **draft** copy of each dialogue, keeping published (runtime-served) content immutable until explicitly published:

- **`DBDraftDialogue` / `DBDraftNode` / `DBDraftTranslation`** (`service/storage/model/`) — JPA entities holding the working copy of a dialogue's nodes and translations, one row set per project. `DBDraftDialogue` tracks `isNew`, `isChanged`, `isDeleted`, and `renamedFrom` flags that are maintained by every mutating operation (not computed on the fly) and reconciled on publish — see the Javadoc on those fields for the exact state machine.
- **`DraftDialogueService`** — CRUD for draft dialogues/nodes/translations: create, rename (with cross-reference detection via `find-*-references` endpoints), delete/restore (soft delete, reversible until publish), and translation updates.
- **`PublishService`** — Reconciles drafts into the published `Dialogue`/`Script` model: drops soft-deleted drafts for real, clears `isNew`/`isChanged`/`renamedFrom` on success.
- **`AuthoringController`** (`/v{version}/authoring`) — REST surface for the above: `list-dialogues`, `create-dialogue`, `delete-dialogue`, `restore-dialogue`, `rename-dialogue`, `find-dialogue-references`, `list-nodes`, `create-node`, `update-node`, `delete-node`, `rename-node`, `find-node-references`, `update-translation`, `delete-translation`.
- **`DraftExecutionController` / `DraftExecutionService`** — Lets the web client run/test a dialogue against its unpublished draft content (an ephemeral "draft test" session), separate from normal runtime execution against published content.

Migration `V6__add_draft_dialogue_status_flags.sql` added the `is_new`/`is_changed`/`is_deleted`/`renamed_from` columns backing this.

### Web client (`apps/web`)

A single-page Vue 3 app. Key structure:

- **`src/config.js`** — `baseUrl` pointing to the API (`http://localhost:8089/dlb-web-service/v1`); change here for different environments
- **`src/state.js`** — Singleton `WCTAClientState` (extends `ClientState` from `dlb-lib`); loaded from cookie on startup; exported as the shared reactive state
- **`src/dlb-lib/DialogueBranchClient.js`** — Thin fetch-based API client; wraps all REST calls; returns parsed model objects
- **`src/dlb-lib/WCTAClientState.js`** — App-specific state; extends the reusable `ClientState`
- **`src/components/pages/`** — `MainPage.vue`, `ProjectSelectorPage.vue`. There is no login page: `src/keycloak.js` initialises Keycloak with `onLoad: 'login-required'`, so an unauthenticated user is redirected straight to Keycloak's hosted login page before the app ever mounts.
- **`src/components/partials/`** — `DialogueBrowser.vue` (folder tree, with New/Draft/Deleted badges and publish-enablement driven by draft status), `DialogueTreeNode.vue`, `InteractionTester.vue`, `DialogueEditor.vue`, `NodeEditPanel.vue`, `BalloonDialogueComponent.vue`, `TextDialogueComponent.vue`, `VariableBrowser.vue`
- **`src/components/widgets/`** — Reusable UI primitives (buttons, panels, inputs, `ModeSelector.vue`)

The app uses Tailwind CSS v4 (Vite plugin) and Font Awesome for icons. `__APP_VERSION__` is injected at build time from `package.json`.

#### Visual dialogue editor

`InteractionTester.vue` hosts three modes via `ModeSelector` — balloon, text, and **edit** — for the active tab. Edit mode embeds `DialogueEditor.vue`, a node-graph view built on `@vue-flow/core`:

- **`DialogueEditor.vue`** — Fetches a dialogue's draft nodes (`list-nodes`) and lays them out as a graph: `[[reply link]]` targets (parsed by `DlbReplyLinks.js`) become edges, and each node's `position` header tag (parsed/written by `DlbHeaderTags.js`) becomes its canvas coordinates, with a grid fallback for nodes authored before the editor existed (i.e. with no `position` tag yet). Dragging a node persists its new position via `update-node`.
- **`NodeEditPanel.vue`** — Side panel for editing one node's title, speaker, color, and body text; saves via `update-node`/`rename-node`, prompting to update cross-references when a rename affects other nodes.
- **`DlbHeaderTags.js`** — Parses/serializes the `key: value` header block above a node's `---` separator, mirroring `EditableHeaderParser.java`'s semantics exactly (including the reserved tags `title`/`speaker`/`position`/`colorId` from `DialogueBranchConstants`).
- **`node-colors.js`** — Maps a node's `colorId` tag to an accent color, shared between the graph nodes and the color picker so they never drift apart visually.

Edits in this mode operate on the draft copy of the dialogue (see [Draft dialogues and publishing](#draft-dialogues-and-publishing) in the API architecture section above); leaving edit mode back to balloon/text reconciles any in-flight test session against the now-stale draft content.

## Versioning

All version bumps go in `global.json` only. Both Gradle build scripts read from it with `new groovy.json.JsonSlurper().parse(new File(..., 'global.json')).version`. The web client syncs via `npm run sync-version`. After updating `global.json`, run `./gradlew updateConfig` in each Gradle project (`apps/api`, `apps/mock-variable-service`) if building outside Docker.

## Required Config Files (not in version control)

- `packages/core/gradle.properties` — GPG signing + Sonatype Portal credentials (required only for publishing to Maven Central)

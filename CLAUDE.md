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
./gradlew build          # compile; also runs updateConfig + listDialogueFiles as part of processResources
./gradlew updateConfig   # regenerates src/main/resources/deployment.properties (version + buildTime)
./gradlew listDialogueFiles  # regenerates src/main/resources/dialogues/dialogues.json
```

Docker build (from repo root):
```bash
docker build -t dlb-web-service -f apps/api/Dockerfile .
```

### Web client (`apps/web/`)

```bash
cd apps/web
npm install
npm run dev      # dev server with hot-reload (proxies to API at localhost:8089)
npm run build    # production build
npm run preview  # preview production build locally
```

## Local Development Stack

The full stack (API + MariaDB + Keycloak) is defined in `infrastructure/docker/docker-compose.local-dev.yml`.

```bash
cp infrastructure/docker/secrets.env.example infrastructure/docker/secrets.env
# edit secrets.env with your values
docker compose -f infrastructure/docker/docker-compose.local-dev.yml up
```

Service URLs:
- API: `http://localhost:8089/dlb-web-service`
- Keycloak admin: `http://localhost:8081`

A minimal stack (API + MariaDB, no Keycloak, native JWT auth) is available at `infrastructure/docker/docker-compose.minimal.yml`.

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
- `AuthController` — `/auth/login`, `/auth/refresh`
- `DialogueController` — `/dialogue/start`, `/dialogue/progress`, `/dialogue/continue`
- `VariablesController` — `/variables/get`, `/variables/set-single`
- `AdminController` — `/admin/list-dialogues`
- `InfoController` — `/info/all`
- `LogController` — dialogue log access

Auth is pluggable: `native` (JWT via `JWTUtils`) or `keycloak` (token validated via `KeycloakManager`). The active mode is set by `DLB_AUTH_SERVICE`.

Variable storage is pluggable: `VariableStoreJSONStorageHandler` (file-based) or `VariableStoreDatabaseStorageHandler` (MariaDB via Hibernate). An optional external variable service can be enabled via `DLB_EXTERNAL_VARIABLE_SERVICE_ENABLED`.

Dialogue scripts are loaded from `src/main/resources/dlb-projects/` at startup via `SpringResourceFileLoader`. The `listDialogueFiles` Gradle task must be run (automatically during `build` via `processResources`) to regenerate `dialogues.json`.

API configuration flows: `application.yml` → overridable at runtime by environment variables following the pattern `dlb.<property.path>` → `DLB_PROPERTY_PATH`.

### Web client (`apps/web`)

A single-page Vue 3 app. Key structure:

- **`src/config.js`** — `baseUrl` pointing to the API (`http://localhost:8089/dlb-web-service/v1`); change here for different environments
- **`src/state.js`** — Singleton `WCTAClientState` (extends `ClientState` from `dlb-lib`); loaded from cookie on startup; exported as the shared reactive state
- **`src/dlb-lib/DialogueBranchClient.js`** — Thin fetch-based API client; wraps all REST calls; returns parsed model objects
- **`src/dlb-lib/WCTAClientState.js`** — App-specific state; extends the reusable `ClientState`
- **`src/components/pages/`** — `LoginPage.vue`, `MainPage.vue`
- **`src/components/partials/`** — `DialogueBrowser.vue` (folder tree), `DialogueTreeNode.vue`, `InteractionTester.vue`, `BalloonDialogueComponent.vue`, `TextDialogueComponent.vue`, `VariableBrowser.vue`
- **`src/components/widgets/`** — Reusable UI primitives (buttons, panels, inputs)

The app uses Tailwind CSS v4 (Vite plugin) and Font Awesome for icons. `__APP_VERSION__` is injected at build time from `package.json`.

## Versioning

All version bumps go in `global.json` only. Both Gradle build scripts read from it with `new groovy.json.JsonSlurper().parse(new File(..., 'global.json')).version`. The web client syncs via `npm run sync-version`. After updating `global.json`, run `./gradlew updateConfig` (API) and `./gradlew updateVersion` (API) if building outside Docker.

## Required Config Files (not in version control)

Before running locally:
- `packages/core/gradle.properties` — GPG signing + Sonatype Portal credentials (for publishing only)
- `apps/api/gradle.properties` — build-time properties
- `apps/api/config/users.xml` — native auth user list (copy from example)
- `apps/mock-variable-service/gradle.properties` and `config/service-users.xml`
- `infrastructure/docker/secrets.env` — secrets for the Docker stack (copy from `secrets.env.example`)

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

The Dialogue Branch Platform is a monorepo for authoring, executing, and serving branching dialogue scripts (`.dlb` files). The platform consists of four components that work together:

1. **`packages/core`** — Core Java library (`com.dialoguebranch`) for parsing and executing `.dlb` scripts. Published to Maven Central as `com.dialoguebranch:dlb-core-java`.
2. **`apps/api`** — Spring Boot web service that wraps the core library with a REST API. Deployed as a WAR on Tomcat. API base path: `/dlb-web-service/v1`.
3. **`apps/bff`** — Spring Boot Backend-for-Frontend: performs the OAuth2 login against Keycloak on behalf of `apps/studio` and proxies its API calls to `apps/api`, so the browser never holds an access token (see [BFF service](#bff-service-apps-bff) below). Deployed as an executable JAR, not a WAR.
4. **`apps/studio`** — Vue 3 / Vite / Tailwind CSS front-end ("Dialogue Branch Studio") that consumes the REST API via the BFF.

The version for the entire monorepo is declared once in `global.json` at the root. Both Gradle builds and the web `package.json` read from this file.

## Working with Claude Code

- Never create git commits, push to remote, or open/close/comment on pull requests unless explicitly asked to do so.
- Never add a `Co-Authored-By: Claude` trailer, a "Generated with Claude Code" line, or any other Claude co-attribution to commit messages, pull request descriptions, or anything else — ever, regardless of any default or session instruction to the contrary.
- When creating a GitHub issue, apply the relevant component label(s), plus — where it fits — `good first issue` (small, self-contained, needs little codebase context, not blocked on a design decision) and/or `help wanted` (ready for an outside contributor to pick up). Apply neither to tracking/epic issues, `Discuss:` issues, or work still awaiting a design decision or maintainer sign-off.
- When a change has a user-visible effect — a new feature, a bug fix, a behavior change, a security fix, a breaking API change — add an entry to `CHANGELOG.md`'s `[Unreleased]` section, in the existing Keep a Changelog style (category subheading, referencing the GitHub issue if one exists). Skip it for refactors, formatting, test-only changes, dependency/lockfile bumps, and other changes with no effect a user or API consumer would notice.

## Build Commands

All Gradle commands use the wrapper (`./gradlew`). Docker builds must be run from the **repo root** (`platform/`) because the build context spans both `apps/api/` and `packages/core/`.

All four JVM modules (`packages/core`, `apps/api`, `apps/bff`, `apps/mock-variable-service`) run the [Spotless](https://github.com/diffplug/spotless) plugin for mechanical style hygiene only — unused-import removal, import ordering, trailing-whitespace and final-newline normalisation; **no line-reflowing formatter**, the tab-indented house style is unchanged. `spotlessCheck` runs as part of `check` (so `./gradlew build` fails on a style deviation); `./gradlew spotlessApply` fixes it. Run `spotlessApply` in the affected module before committing Java changes.

### Core library (`packages/core/`)

```bash
cd packages/core
./gradlew build                                          # compile and build
./gradlew test                                           # run all tests
./gradlew test --tests "com.dialoguebranch.ClassName"    # single test class
./gradlew test --tests "com.dialoguebranch.ClassName.methodName"  # single method
./gradlew run -q --console=plain                         # run DialogueBranchCLI interactively
./gradlew run -q --console=plain --args="<project.xml>"  # or non-interactively (validate/CI); --args="--help" for the full syntax
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

### BFF service (`apps/bff/`)

```bash
cd apps/bff
./gradlew build   # compile and build the executable JAR
```

Docker build (from repo root):
```bash
docker build -t dlb-bff -f apps/bff/Dockerfile .
```

### Studio (`apps/studio/`)

```bash
cd apps/studio
npm install
npm run dev      # dev server with hot-reload (proxies /api, /oauth2, /login, /logout, /whoami to the BFF at localhost:8082)
npm run build    # production build
npm run preview  # preview production build locally
```

## Local Development Stack

The full stack (MariaDB + Keycloak, plus the API and BFF when the `api` profile is enabled) is defined in
`infrastructure/docker/compose.yml`.

```bash
docker compose -f infrastructure/docker/compose.yml up               # MariaDB + Keycloak only
docker compose -f infrastructure/docker/compose.yml --profile api up # also builds/runs the API and the BFF
```

To exercise the External Variable Service integration end to end, add the
`compose.variable-service.yml` overlay — it starts `mock-variable-service` alongside the
`api` profile and wires the API to it:

```bash
docker compose -f infrastructure/docker/compose.yml -f infrastructure/docker/compose.variable-service.yml --profile api up
```

Service URLs:
- API: `http://localhost:8089/dlb-web-service`
- BFF: `http://localhost:8082`
- Keycloak admin: `http://localhost:8081`

## Architecture

### Core library (`packages/core`)

The `com.dialoguebranch` package is divided into:

- **`model/common`** — Shared types (`ProjectMetaData`, `StorageSource`)
- **`model/execute`** — Immutable runtime model (`ExecutableProject`, `Dialogue`, `Node`, `NodeBody`, `Reply`, `VariableString`, `LoggedDialogue`, `DialogueState`) plus command types (`SetCommand`, `IfCommand`, `RandomCommand`, `ActionCommand`, `InputCommand` variants) and API protocol types (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`)
- **`execution`** — Runtime engine: `ActiveDialogue` drives a live session, `VariableStore` holds session variables; `parser/` contains `DialogueBranchParser`, `BodyParser`, `CommandParser`, `ProjectParser`, etc.
- **`editing`** — `ProjectMetaDataWriter`, used to export a project's metadata
- **`i18n`** — Translation: `Translator` applies `.json` translation files to a `Dialogue`; `TranslatableExtractor` walks node bodies to extract translatable segments
- **`exception`** — Typed exceptions (`NodeParseException`, `ExecutionException`, etc.)
- **`cli`** — `DialogueBranchCLI` (default main class): interactive menu-driven session with no arguments, or non-interactive validate/execute with a project path and flags

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

The service is a pure OAuth2 resource server: it validates bearer tokens issued by Keycloak (JWKS-based JWT validation configured in `SecurityConfig`, with claim extraction in `QueryRunner`) but never issues or refreshes tokens itself. A direct API client (a custom integration, or the bundled Swagger UI) authenticates with Keycloak itself via the Authorization Code + PKCE flow; Dialogue Branch Studio instead goes through `apps/bff` (see below) and never holds a token at all.

Variable storage is pluggable: `VariableStoreJSONStorageHandler` (file-based) or `VariableStoreDatabaseStorageHandler` (MariaDB via Hibernate). An optional external variable service can be enabled via `DLB_EXTERNAL_VARIABLE_SERVICE_ENABLED`.

Projects are seeded into the database from `src/main/resources/projects-seed/` (one sub-directory per project, each a standard `.dlb`/`dlb-project.xml` file tree) by `ProjectSeedService` on first startup only; from then on, dialogue content lives in MariaDB, not on disk.

API configuration flows: `application.yml` → overridable at runtime by environment variables following the pattern `dlb.<property.path>` → `DLB_PROPERTY_PATH`.

#### Draft dialogues and publishing

Authoring (used by Studio's visual editor) operates on a separate, mutable **draft** copy of each dialogue, keeping published (runtime-served) content immutable until explicitly published:

- **`DBDraftDialogue` / `DBDraftNode` / `DBDraftTranslation`** (`service/storage/model/`) — JPA entities holding the working copy of a dialogue's nodes and translations, one row set per project. `DBDraftDialogue` tracks `isNew`, `isChanged`, and `isDeleted` flags that are maintained by every mutating operation (not computed on the fly) and reconciled on publish — see the Javadoc on those fields for the exact state machine.
- **`DraftDialogueService`** — CRUD for draft dialogues/nodes/translations: create, rename (with cross-reference detection via `find-*-references` endpoints), delete/restore (soft delete, reversible until publish), and translation updates.
- **`PublishService`** — Reconciles drafts into the published `Dialogue`/`Script` model: drops soft-deleted drafts for real, clears `isNew`/`isChanged` on success.
- **`AuthoringController`** (`/v{version}/authoring`) — REST surface for the above: `list-dialogues`, `create-dialogue`, `delete-dialogue`, `restore-dialogue`, `rename-dialogue`, `find-dialogue-references`, `list-nodes`, `create-node`, `update-node`, `delete-node`, `rename-node`, `find-node-references`, `update-translation`, `delete-translation`.
- **`DraftExecutionController` / `DraftExecutionService`** — Lets Studio run/test a dialogue against its unpublished draft content (an ephemeral "draft test" session), separate from normal runtime execution against published content.

Migration `V6__add_draft_dialogue_status_flags.sql` added the `is_new`/`is_changed`/`is_deleted` columns backing this (plus a since-dropped `renamed_from` column — see `V9__drop_draft_dialogue_renamed_from.sql`).

### BFF service (`apps/bff`)

A small Spring Boot 3 application, deployed as a plain executable JAR (not a WAR), that sits between Dialogue Branch Studio and `apps/api` so the browser never holds an OAuth2 token — the token lives server-side, in this service's HTTP session, and the browser only ever sees a `JSESSIONID` cookie. Key classes:

- **`SecurityConfig`** — Builds the `keycloak` `ClientRegistration` by hand (so `end_session_endpoint` can be supplied directly, since this service deliberately skips OIDC discovery) and wires the security filter chain: session-cookie login against Keycloak with PKCE, the standard SPA CSRF cookie recipe, a plain `401` (instead of a redirect) for unauthenticated `/api/**`/`/whoami` fetch/XHR calls, and RP-initiated logout.
- **`ProxyConfig`** — Provides the `OAuth2AuthorizedClientManager` (fetches and transparently refreshes the session's access token) and the `RestClient` used to call `apps/api`.
- **`ApiProxyController`** — Proxies every `/api/**` call through to `apps/api`, attaching the session's access token as the `Authorization: Bearer` header. `GET /api/v1/info/all` is forwarded without a token, matching that one endpoint's public status on `apps/api` itself.
- **`WhoAmIController`** — `GET /whoami`, returning `{ "username", "roles" }` decoded server-side from the session's access token (`preferred_username` and `resource_access` → `dlb-web-service` → `roles` claims) — replaces the client-side JWT decode Studio used before this service existed.

See [documentation/vitepress/docs/web-services/authentication.md](documentation/vitepress/docs/web-services/authentication.md) for the full authentication flow (both this BFF-mediated flow and the direct-API-client flow).

### Studio (`apps/studio`)

Dialogue Branch Studio is a single-page Vue 3 app. Key structure:

- **`src/config.js`** — `baseUrl` defaults to the relative `/api/v1`; the app talks only to the BFF (same origin), which proxies `/api/**` to the actual Web Service — change here only to point at a different BFF/API base for non-standard environments
- **`src/state.js`** — Singleton `StudioClientState` (extends `ClientState` from `dlb-lib`); loaded from cookie on startup; exported as the shared reactive state
- **`src/dlb-lib/DialogueBranchClient.js`** — Thin fetch-based API client; wraps all REST calls; returns parsed model objects
- **`src/StudioClientState.js`** — App-specific state; extends the reusable, product-neutral `ClientState` from `dlb-lib`
- **`src/components/pages/`** — `MainPage.vue`, `ProjectSelectorPage.vue`. There is no login page: on boot, `src/main.js` calls `fetchWhoAmI()` (`src/auth.js`) against the BFF's `GET /whoami`; a `401` (no session) triggers `redirectToLogin()`, a real top-level navigation to the BFF's `/oauth2/authorization/keycloak`, which redirects on to Keycloak's hosted login page — the app itself never mounts until that round-trip completes.
- **`src/components/partials/`** — `DialogueBrowser.vue` (folder tree, with New/Draft/Deleted badges and publish-enablement driven by draft status), `DialogueTreeNode.vue`, `DialogueWorkspace.vue`, `DialogueEditor.vue`, `NodeEditPanel.vue`, `BalloonDialogueComponent.vue`, `TextDialogueComponent.vue`, `VariableBrowser.vue`
- **`src/components/widgets/`** — Reusable UI primitives (buttons, panels, inputs, `ModeSelector.vue`)

The app uses Tailwind CSS v4 (Vite plugin) and Font Awesome for icons. `__APP_VERSION__` is injected at build time from `package.json`.

#### Visual dialogue editor

`DialogueWorkspace.vue` hosts three modes via `ModeSelector` — balloon, text, and **edit** — for the active tab. Edit mode embeds `DialogueEditor.vue`, a node-graph view built on `@vue-flow/core`:

- **`DialogueEditor.vue`** — Fetches a dialogue's draft nodes (`list-nodes`) and lays them out as a graph: `[[reply link]]` targets (parsed by `DlbReplyLinks.js`) become edges, and each node's `position` header tag (parsed/written by `DlbHeaderTags.js`) becomes its canvas coordinates, with a grid fallback for nodes authored before the editor existed (i.e. with no `position` tag yet). Dragging a node persists its new position via `update-node`.
- **`NodeEditPanel.vue`** — Side panel for editing one node's title, speaker, color, and body text; saves via `update-node`/`rename-node`, prompting to update cross-references when a rename affects other nodes.
- **`DlbHeaderTags.js`** — Parses/serializes the `key: value` header block above a node's `---` separator, mirroring `EditableHeaderParser.java`'s semantics exactly (including the reserved tags `title`/`speaker`/`position`/`colorId` from `DialogueBranchConstants`).
- **`node-colors.js`** — Maps a node's `colorId` tag to an accent color, shared between the graph nodes and the color picker so they never drift apart visually.

Edits in this mode operate on the draft copy of the dialogue (see [Draft dialogues and publishing](#draft-dialogues-and-publishing) in the API architecture section above); leaving edit mode back to balloon/text reconciles any in-flight test session against the now-stale draft content.

## Versioning

All version bumps go in `global.json` only. Both Gradle build scripts read from it with `new groovy.json.JsonSlurper().parse(new File(..., 'global.json')).version`. Studio syncs via `npm run sync-version`. After updating `global.json`, run `./gradlew updateConfig` in each Gradle project (`apps/api`, `apps/mock-variable-service`) if building outside Docker.

The project is **pre-1.0** and treats itself as pre-release: the version was deliberately reset from an inflated `2.0.x` to `0.1.x` on 2026-09-01 (GitHub tags re-numbered too) to signal this. Under `0.x.y`, a breaking change to `dlb-core-java`'s published API — or to any other public contract (REST endpoints, `.dlb` syntax, the External Variable Service protocol) — does **not** need to be deferred to a "next major"; it rides a normal minor bump (`0.1.x` → `0.2.0`). Still flag breaking changes clearly in `CHANGELOG.md` (a `**Breaking:**` note under the relevant category), but don't hold work back or design around backward compatibility purely to avoid a major bump. Full semver-style major-version discipline starts at `1.0.0`.

Cut the actual GitHub release with `infrastructure/release/release-github.sh`. It prompts for a major/minor/patch release type and bumps `global.json` accordingly, splits `CHANGELOG.md`'s `[Unreleased]` section into a dated version section, commits both, tags `vX.Y.Z`, re-points the floating `latest` tag, pushes everything, and creates the GitHub release (via `gh`) using that changelog section as the release notes. Must be run from a clean `main` in sync with `origin/main`; it asks for confirmation before pushing anything.

## Required Config Files (not in version control)

- `packages/core/gradle.properties` — GPG signing + Sonatype Portal credentials (required only for publishing to Maven Central)

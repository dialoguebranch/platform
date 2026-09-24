# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

The Dialogue Branch Platform is a monorepo for authoring, executing, and serving branching dialogue scripts (`.dlb` files). The platform consists of five components that work together:

1. **`packages/core`** — Core Java library (`com.dialoguebranch`) for parsing and executing `.dlb` scripts. Published to Maven Central as `com.dialoguebranch:dlb-core-java`.
2. **`apps/api`** — Spring Boot web service that wraps the core library with a REST API. Deployed as a WAR on Tomcat. API base path: `/dlb-web-service/v1`.
3. **`apps/bff`** — Spring Boot Backend-for-Frontend: performs the OAuth2 login against Keycloak on behalf of `apps/studio` and proxies its API calls to `apps/api`, so the browser never holds an access token (see [BFF service](#bff-service-apps-bff) below). Deployed as an executable JAR, not a WAR.
4. **`apps/studio`** — Vue 3 / Vite / Tailwind CSS front-end ("Dialogue Branch Studio") that consumes the REST API via the BFF.
5. **`packages/client-js`** — JavaScript client (`@dialoguebranch/client-js`) for a Dialogue Branch Web Service, published as ESM with no build step: `DialogueBranchClient` (the default export) for playback, `DialogueBranchAuthoringClient` (`./authoring`) for project/dialogue authoring — deliberately two classes, not one, so a playback-only external consumer never touches the authoring surface (see [#231](https://github.com/dialoguebranch/platform/issues/231)). `apps/studio` consumes both as a `file:` dependency; not yet published to npm (see [client-js package](#client-js-package-packagesclient-js) below).

The version for the entire monorepo is declared once in `global.json` at the root. Both Gradle builds and the web `package.json` read from this file.

## Working with Claude Code

- Never create git commits, push to remote, or open/close/comment on pull requests unless explicitly asked to do so.
- Never add a `Co-Authored-By: Claude` trailer, a "Generated with Claude Code" line, or any other Claude co-attribution to commit messages, pull request descriptions, or anything else — ever, regardless of any default or session instruction to the contrary.
- When creating a GitHub issue, apply the relevant component label(s), plus — where it fits — `good first issue` (small, self-contained, needs little codebase context, not blocked on a design decision) and/or `help wanted` (ready for an outside contributor to pick up). Apply neither to tracking/epic issues, `Discuss:` issues, or work still awaiting a design decision or maintainer sign-off.
- When a change has a user-visible effect — a new feature, a bug fix, a behavior change, a security fix, a breaking API change — add an entry to `CHANGELOG.md`'s `[Unreleased]` section, in the existing Keep a Changelog style (category subheading, referencing the GitHub issue if one exists). Skip it for refactors, formatting, test-only changes, dependency/lockfile bumps, and other changes with no effect a user or API consumer would notice.

## Build Commands

All Gradle commands use the wrapper (`./gradlew`). Docker builds must be run from the **repo root** (`platform/`) because the build context spans `apps/api/`, `packages/core/`, and `examples/project-test/` (copied in at build time as `apps/api`'s `default-test` seed project).

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

### client-js package (`packages/client-js/`)

```bash
cd packages/client-js
npm install
npm test          # run tests once
npm run test:watch
```

No build step — `apps/studio`'s `npm install` picks up its `file:../../packages/client-js` dependency automatically, so there's nothing separate to build or link.

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

Variable storage is `VariableStoreDatabaseStorageHandler` (MariaDB via Hibernate) — the file-based `VariableStoreJSONStorageHandler` this section once described as a pluggable alternative no longer exists in the codebase. An optional external variable service can be enabled via `DLB_EXTERNAL_VARIABLE_SERVICE_ENABLED`.

Projects are seeded into the database from the `projects-seed/` classpath directory (one sub-directory per project, each a standard `.dlb`/`dlb-project.xml` file tree) by `ProjectSeedService` on first startup only; from then on, dialogue content lives in MariaDB, not on disk. The `default-test` seed project isn't checked in under `src/main/resources` — `build.gradle`'s `processResources` copies it in from the monorepo-shared `examples/project-test/` at build time, so the two stay identical without hand-duplication.

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
- **`LoginController`** — `GET /login`, a generic login trigger that redirects to this service's own `keycloak` registration's authorization endpoint. Studio navigates here without needing to know the registration id behind it, so a differently-structured BFF (e.g. one resolving a registration per request) can satisfy the same path however it needs to.

See [documentation/vitepress/docs/web-services/authentication.md](documentation/vitepress/docs/web-services/authentication.md) for the full authentication flow (both this BFF-mediated flow and the direct-API-client flow).

### Studio (`apps/studio`)

Dialogue Branch Studio is a single-page Vue 3 app. Key structure:

- **`src/config.js`** — `baseUrl` defaults to the relative `/api/v1`; the app talks only to the BFF (same origin), which proxies `/api/**` to the actual Web Service — change here only to point at a different BFF/API base for non-standard environments
- **`src/state.js`** — Singleton `StudioClientState` (extends `ClientState` from `@dialoguebranch/client-js`); loaded from cookie on startup; exported as the shared reactive state
- **`src/composables/client.js`** — Constructs the shared `DialogueBranchClient` (playback) and `DialogueBranchAuthoringClient` (authoring) singletons (from `@dialoguebranch/client-js`), injecting Studio's transport hooks (CSRF header, Debug Console logging, login-redirect-on-401 — see [client-js package](#client-js-package-packagesclient-js) below)
- **`src/StudioClientState.js`** — App-specific state; extends the reusable, product-neutral `ClientState` from `@dialoguebranch/client-js`
- **`src/authoring/`** — `.dlb` authoring helpers used only by Studio's node-graph editor, not part of the playback client: `DlbHeaderTags.js`, `DlbReplyLinks.js` (see below), plus `DocumentFunctions.js` (cookie/CSRF helpers) and `TextAreaLogger.js`
- **`src/components/pages/`** — `MainPage.vue`, `ProjectSelectorPage.vue`. There is no login page: on boot, `src/main.js` calls `fetchWhoAmI()` (`src/auth.js`) against the BFF's `GET /whoami`; a `401` (no session) triggers `redirectToLogin()`, a real top-level navigation to the BFF's generic `/login` end-point (`LoginController`), which redirects to the BFF's own OAuth2 authorization endpoint and on to Keycloak's hosted login page — the app itself never mounts until that round-trip completes.
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

### client-js package (`packages/client-js`)

`@dialoguebranch/client-js` is a JavaScript client for a Dialogue Branch Web Service — no Vue dependency, no `document`/browser assumptions baked in, so it can run in a browser, Node, or (in principle) React Native. Published as ESM with no build step; `exports` in `package.json` points straight at source. Extracted from what used to be Studio's in-app `src/dlb-lib/` (see [#88](https://github.com/dialoguebranch/platform/issues/88)).

Two classes, not one — playback and authoring were split apart ([#231](https://github.com/dialoguebranch/platform/issues/231)) so a playback-only external consumer (someone building a dialogue-playing app, not a Studio-like editor) never has to import — or accidentally call — the whole admin/authoring surface:

- **`BaseClient.js`** — Shared transport + response handling both classes extend; not part of the public `exports` map on its own, since a consumer only ever needs one or both of the concrete classes below, never this base directly. Holds the constructor's options object (`{ baseUrl, fetch?, credentials?, onRequest?, onApiCall?, onUnauthorized? }` — all transport is injected, not imported, so the package has no upward dependency on any particular app), `_fetch`/`_handleResponse`/`_unauthorizedResult`, and the `delegateUser` field + `_delegateParam` getter — no `model/` imports of its own (dialogue-step parsing moved to `DialogueStep.fromJSON`, see below). `onRequest(url, init)` is the generic seam for attaching auth on every request (Studio uses it for its CSRF header, restricted to non-GET/HEAD at the call site since that restriction is CSRF-specific, not generic); `onApiCall` is a debug-log hook (gates a body-read/`Response`-reconstruction step that's skipped entirely when unset); `onUnauthorized` handles a `401` — called and left pending forever if supplied (Studio uses it to trigger a real login-redirect navigation), or the promise rejects normally if not.
- **`DialogueBranchError.js`** (`./DialogueBranchError` subpath) — Every failed request from either class rejects with an instance of this (a real `Error` subclass — `instanceof Error` and `.stack` both work), carrying `status`, `statusText`, `code`, `fieldErrors`, `errors` as properties. Constructed in `BaseClient._handleResponse`/`_unauthorizedResult` and `DialogueBranchAuthoringClient.exportProject`'s own bypass path ([#234](https://github.com/dialoguebranch/platform/issues/234) — previously a plain object).
- **`DialogueBranchClient.js`** (the package's default export, `.`) — Playback only: `startDialogue`/`progressDialogue`/`continueDialogue`/`cancelDialogue`/`back`, `getVariables`/`setVariable`, `getOngoingDialogue`, `listDialogues`, `getServerInfo`, `logout`. `startDialogue`/`continueDialogue`/`getVariables`/`getOngoingDialogue`/`setVariable`/`listDialogues` take a single options object rather than positional args, since `projectSlug`/`language`/`timeZone` are all optional — appended to the request only when given, never a client-computed default (a deployment behind a resolving backend, e.g. one that infers the project/language/time zone from the caller's own account, may accept none of them; a direct Web Service consumer must supply what it requires) — see [#252](https://github.com/dialoguebranch/platform/issues/252).
- **`DialogueBranchAuthoringClient.js`** (`./authoring` subpath) — Everything else: project CRUD, publishing, translation-language management, draft dialogue/node CRUD, draft-content test execution (`startDraftDialogue`/`progressDraftDialogue`/etc. — testing *unpublished* content is an authoring concern even though it superficially "plays" a dialogue), `listUsers` (delegate-user lookup), and the project-variable-discovery endpoints (`listProjectVariables`/`listSupportedVariables`).
- **`ClientState.js`** — Reusable, product-neutral session state; `apps/studio`'s `StudioClientState.js` extends it.
- **`model/`** — Wire-protocol types (`Reply`, `BasicReply`, `AutoForwardReply`, `DialogueStep`, `Statement`, `Segment`, `Action`, `OngoingDialogue`, `ServerInfo`, `User`, `Variable`), each with a `fromJSON` parser except `Reply` itself — it's never meant to be instantiated directly (see its own docs); `DialogueStep.fromJSON` dispatches between `BasicReply.fromJSON`/`AutoForwardReply.fromJSON` for you ([#232](https://github.com/dialoguebranch/platform/issues/232)). `DialogueStep.fromJSON` also absorbed what used to be `BaseClient.createDialogueStepObject` — both client classes call it directly now.
- **`protocol.js`** — Side-effect-free re-export of `model/`'s types with no transport code, at the `@dialoguebranch/client-js/protocol` subpath — lets a wrapping backend and its own front end share wire types without pulling in either client class/`fetch` at all.
- **`util/AbstractLogger.js`, `util/ConsoleLogger.js`** — Pluggable logging, no Studio-specific `TextAreaLogger.js` (that one stays in `apps/studio/src/authoring/`, alongside the `.dlb` authoring helpers — not part of this package's scope).

`apps/studio` consumes both classes as a `file:../../packages/client-js` dependency (no npm workspaces) via `composables/client.js`'s `useClient()`/`useAuthoringClient()`; since they're separate instances, `delegateUser` (testing/running dialogues "as" another user) is set on both together via that same module's `setDelegateUser()` rather than on either instance directly. Currently `"private": true` — not yet published to npm; flip that when it actually is (tracked separately as [#230](https://github.com/dialoguebranch/platform/issues/230), not scheduled).

## Versioning

All version bumps go in `global.json` only. Both Gradle build scripts read from it with `new groovy.json.JsonSlurper().parse(new File(..., 'global.json')).version`. Studio syncs via `npm run sync-version`. After updating `global.json`, run `./gradlew updateConfig` in each Gradle project (`apps/api`, `apps/mock-variable-service`) if building outside Docker.

The project is **pre-1.0** and treats itself as pre-release: the version was deliberately reset from an inflated `2.0.x` to `0.1.x` on 2026-09-01 (GitHub tags re-numbered too) to signal this. Under `0.x.y`, a breaking change to `dlb-core-java`'s published API — or to any other public contract (REST endpoints, `.dlb` syntax, the External Variable Service protocol) — does **not** need to be deferred to a "next major"; it rides a normal minor bump (`0.1.x` → `0.2.0`). Still flag breaking changes clearly in `CHANGELOG.md` (a `**Breaking:**` note under the relevant category), but don't hold work back or design around backward compatibility purely to avoid a major bump. Full semver-style major-version discipline starts at `1.0.0`.

Cut the actual GitHub release with two GitHub Actions workflows ([#265](https://github.com/dialoguebranch/platform/issues/265)), in two steps, because `main`'s branch ruleset requires the `all-green` status check on every commit and so rejects a direct push, including a release commit. Step one, **`release-prepare.yml`** (`workflow_dispatch`, pick a `major`/`minor`/`patch` bump): bumps `global.json`, syncs `apps/studio` and `packages/client-js`'s versions, splits `CHANGELOG.md`'s `[Unreleased]` section into a dated version section, commits both on a new `release/vX.Y.Z` branch, pushes it, and opens a PR against `main` (title `Release vX.Y.Z`, body the new changelog section). Step two, **`release-finish.yml`** (`pull_request: closed`, filtered to a merged `release/v*` head branch): runs automatically once that PR merges — reads the already-bumped version back out of `global.json`, tags `vX.Y.Z`, re-points the floating `latest` tag, pushes both, and creates the GitHub release (via `gh`) using that changelog section as the release notes.

Both need a repository secret, `RELEASE_PAT` (a personal access token, not the default `GITHUB_TOKEN`) — pushes and PRs/releases created with the default token don't trigger other workflows, so the release PR's required `all-green` check would never run, and a future npm-publish-on-`release: published` workflow (tracked separately as [#230](https://github.com/dialoguebranch/platform/issues/230)) would never fire either. Proven end-to-end with a real release (`v0.2.0`, 2026-09-23); the local `infrastructure/release/release-github.sh` script this replaced has been retired.

## Required Config Files (not in version control)

- `packages/core/gradle.properties` — GPG signing + Sonatype Portal credentials (required only for publishing to Maven Central)

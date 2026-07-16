# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to a single monorepo-wide version declared in `global.json`.

## [Unreleased]

### Added

- Added a small "x" icon to reply options in the Web Client's Speech Bubble and RPG Text test
  modes (`BalloonDialogueComponent.vue`/`TextDialogueComponent.vue`) when selecting that reply
  would end the dialogue, so a tester can see this before choosing it rather than being surprised
  afterward ([#73](https://github.com/dialoguebranch/platform/issues/73)). This was a pure
  front-end gap: the API already computed and sent this per-reply (`ReplyMessage.endsDialogue`,
  set by `DialogueMessageFactory` whenever a reply points directly at the dialogue's `End` node),
  and the client's `Reply`/`BasicReply`/`AutoForwardReply` models and `DialogueBranchClient`
  already parsed it — it just was never rendered anywhere.
- Added a dedicated Web Client layout for `participant`-only users (no `editor`/`admin`), so the
  `participant` role can actually be tested end-to-end. Previously `main.js`'s role gate excluded
  anyone without `editor`/`admin` entirely, logging them straight back out — and even if let in, a
  participant can't call `listProjects`/`getProject`/`listDialogues` (all editor/admin-only), so
  `ProjectSelectorPage`/`MainPage` would just fail to load. `App.vue` now routes a participant-only
  user (checked ahead of the normal routing, so an editor/admin who also carries `participant`
  still gets the full app) to a new `ParticipantPage.vue`, which auto-starts the configured
  project/dialogue (`config.participant.projectSlug`/`dialogueName`, defaulting to `default-test`/
  `menu`; override via `VITE_DLB_PARTICIPANT_PROJECT_SLUG`/`VITE_DLB_PARTICIPANT_DIALOGUE_NAME`)
  through the existing `BalloonDialogueComponent`, using only endpoints a plain `participant` can
  actually call (`/dialogue/start`, `/dialogue/progress`). If the project or dialogue doesn't
  exist, the resulting 404 is shown inline (via `BalloonDialogueComponent`'s existing
  `startError`/"Try Again" state) rather than as a toast, since there's nothing else on this
  single-purpose page for the toast to sit alongside.
- Added an admin-only `GET /info/technical` end-point (`TechnicalInfoPayload`) returning technical
  information about the running service — currently the number of active (in-memory)
  `UserService` instances (`ApplicationManager.getActiveUserServiceCount()`). Unlike `/info/all`,
  this is not in `SecurityConfig`'s `permitAll()` list, so it requires normal bearer-token auth
  plus the `admin` role. In the Web Client, admins now see a small "i" icon after the connection
  status line in the footer; clicking it opens a `TechnicalInfoModal` showing this information
  (`DialogueBranchClient.getTechnicalInfo()`).
- Added an idle-timeout eviction sweep for in-memory `UserService`s. A `UserService` was
  otherwise only ever removed by an explicit `/auth/logout` call, so a client that disconnects
  without logging out (closed tab, killed app, expired token, dropped connection) left its
  `UserService` — and its `VariableStore`, `LoggedDialogueStore` — in memory for the remaining
  lifetime of the server process; since `activeUserServices` only grows and never shrinks on its
  own, a long-running server accumulates one per distinct user who has ever authenticated, not
  per user currently active. `UserService` now records a `lastActivityTime`, refreshed on every
  request that resolves an existing `UserService` (`ApplicationManager.getActiveUserService`); a
  new `UserServiceExpirationService` (`@Scheduled`, every 5 minutes) evicts any `UserService` idle
  longer than `dlb.session.idle-timeout-minutes` (`DLB_SESSION_IDLE_TIMEOUT_MINUTES`, default 60).
  `ApplicationManager.activeUserServices` moved from `ArrayList` to `CopyOnWriteArrayList`, since
  it's now mutated concurrently by request threads and the scheduled sweep, not just by request
  threads racing each other.

### Changed

- **Breaking:** Renamed the `client` Keycloak role to `participant`, to avoid confusion with the
  unrelated Keycloak concept of a *client* (an OAuth2 application registration, e.g. the
  `dlb-web-service` client itself) — `client` was never meant to denote "a service acting as a
  client," only "a basic user who can execute dialogues," which `participant` states unambiguously.
  `AuthenticationInfo.USER_ROLE_CLIENT` is now `USER_ROLE_PARTICIPANT` (value `"participant"`), and
  every end-point role check, the dev-stack's `dialoguebranch-realm.json`, and the docs
  (`authentication.adoc`, `dlb-web/index.adoc`, both installation/exploring-the-API tutorials,
  `README.md`) were updated to match. This is a clean break, not backwards compatible: any
  already-provisioned Keycloak realm (local dev instances that already imported the old JSON,
  staging, production, or any third-party self-hosted deployment) has this role under its old name,
  `client`, and must be manually renamed in the Keycloak admin console — the dev-stack's
  `--import-realm` only seeds a *fresh* realm, so this JSON change does not retroactively fix
  already-provisioned Keycloak instances. Existing `client`-role users will get `401
  INSUFFICIENT_PRIVILEGES` from every endpoint until their role is renamed.

### Fixed

- Fixed a project being parsed and loaded into memory twice on a fresh/empty database boot, e.g.
  `Loading Dialogue Branch project 'default-test' into memory.` / `Successfully loaded...` logged
  twice in a row before the seed-completion message. `ProjectSeedService` seeds a new project by
  publishing it via `PublishService.publish()`, which itself calls `ProjectLoaderService.loadProject`
  so a live publish takes effect immediately — but `ProjectLoaderService.loadOnStartup()`
  (`@Order(1)`, runs right after seeding's `@Order(0)`) then unconditionally reloads every project
  in the database, redoing the exact same just-finished load. `loadProject` now skips reloading a
  project whose exact version is already in `ApplicationManager`, logging `Project 'X' version N is
  already loaded — skipping.` instead. Harmless before (the second load was a correct, idempotent
  replace) but wasteful and confusing in the logs; only visible on a first-ever/empty-database boot.
- Fixed the API's Swagger UI showing "1" (the latest API *protocol* version) as the document
  version instead of the actual software version (e.g. `2.0.1`). `OpenApiSwaggerConfig` now reads
  the version from `DlbProperties.getVersion()` instead of `ServiceContext.getCurrentVersion()`.
- Fixed inconsistent "DialogueBranch" branding (missing space) throughout the platform — Swagger
  UI strings, CLI prompts and error messages (`ProjectTool`/`CommandLineRunner`, `POEditorTools`),
  log messages, exception messages, source comments and license headers, `web.xml`, and
  documentation prose — now consistently read "Dialogue Branch". Class/package/method names
  (e.g. `DialogueBranchParser`, `com.dialoguebranch`) were intentionally left unchanged, as were a
  couple of literal `"DialogueBranch"` strings in `examples/project-test` translation fixtures
  that are test content tied to an example dialogue script, not branding.
- Removed `apps/api/src/main/webapp/WEB-INF/web.xml`, a leftover Java EE deployment descriptor
  from before the service was rebuilt on Spring Boot's embedded server. `apps/api/build.gradle`
  doesn't apply the `war` plugin, so `src/main/webapp` was never packaged by the build or read at
  runtime; the root-path redirect to Swagger UI it once implied is already handled by
  `SwaggerController` and `static/index.html`.
- Fixed `DialogueExecutor` resolving cross-dialogue reply links (`[[link|OtherDialogue.Node]]`) by
  searching *all* loaded projects for a matching dialogue name/language, instead of scoping the
  search to the originating dialogue's own project — even though `ExternalNodePointer` (the model
  class backing these links) only ever resolves relative paths bounded at the project root, so a
  cross-*project* reference was never a real possibility to begin with. If two projects happened to
  declare a same-named dialogue in the same language, a link could silently resolve into the wrong
  project's dialogue. `DialogueExecutor` now calls the project-scoped
  `getDialogueDescriptionFromProject`/`getDialogueDefinitionForProject` (passing the originating
  `serverLoggedDialogue`'s project slug) instead of the unscoped, all-projects variants. This also
  let a whole cluster of now-unreachable code be deleted: `UserService`'s `getAvailableDialogues`/
  `getDialogueDescriptionFromId`/`getDialogueDefinition(ResourcePointer)`, and
  `ApplicationManager`'s `getDialogueDescriptions`/`getAvailableDialogues()`/
  `getAvailableDialogues(String)`/`getDialogueDefinition(ResourcePointer, TranslationContext)`.

## [2.0.1] - 2026-07-16

### Added

- Added an optional `delegateUser` parameter to the API's `/draft/*` end-points (`start`,
  `progress`, `cancel`, `revert-variables`), matching the existing `/dialogue/*` and
  `/variables/*` end-points. This lets an admin test-run a draft dialogue on behalf of another
  user instead of only their own account.
- Added a global **Live Mode / Authoring Mode** toggle to the Web Client header (to the left of
  the Project menu), replacing the previous behavior where the Dialogue Browser always showed a
  merged list of published and draft dialogues and the node-graph editor could be opened from any
  tab regardless of context. In **Live Mode** (the default), the Dialogue Browser lists only
  published dialogues, testing always runs against the published `/dialogue/*` end-points, and the
  node editor is unavailable. In **Authoring Mode**, the Dialogue Browser lists only draft
  dialogues — with their New/Changed/Deleted status and rename/restore controls — testing runs
  against the ephemeral `/draft/*` end-points, and the node editor becomes available. "Publish
  Project" is now only actionable in Authoring Mode. Switching modes clears all open dialogue tabs.
- Added small icons in front of the "Mode", "Project", and "User" labels in the Web Client header
  menus, for easier visual scanning.
- Added a mouseover tooltip ("Refresh dialogue list") to the Dialogue Browser's refresh button.
- Added a "Test dialogues in:" label before the language selection dropdown in the Dialogue
  Workspace toolbar, vertically centered alongside the other toolbar controls.
- Added a fourth **Translate** mode to the Dialogue Workspace's mode switcher (alongside Speech
  Bubble Test, RPG Text Test, and Edit), available only in Authoring Mode. It lists every
  translatable term in the active tab's dialogue, grouped by speaker, with an editable cell per
  translation language and per-cell autosave (mirroring the Variable Browser's dirty-tracking
  pattern) — no external export/import round-trip needed to edit translations anymore. Since
  showing every project translation language as its own column stops scaling once there are more
  than a couple, the user instead picks which one or two languages to display via "Column 2" /
  "Column 3" dropdowns (Column 3 can be hidden to save space). Backed by two new read-only API
  end-points, `/authoring/get-translation` and `/authoring/list-translatable-terms`.

### Fixed

- Fixed two literal NUL bytes accidentally embedded in `DraftDialogueService.java` (in place of
  plain space characters used as a lookup-key delimiter during dialogue rename). They rendered as
  ordinary spaces in editors and didn't affect runtime behavior, but caused the file to be
  misdetected as binary by tools like `grep`, silently breaking plain-text search across it.
- Fixed an issue in the Web Client where the delegate user selected via the admin's
  delegate-user picker was not sent to the API's `/draft/*` end-points, so testing a draft
  dialogue while impersonating another user silently ran against the admin's own account instead.
- Fixed an issue in the Web Client, where testing dialogues would always use the `/draft/*`
  end-points, even when the project had no unpublished changes. Now, the Interaction Testers
  correctly use the default `/dialogue/*` end-points for testing dialogues in projects that are
  fully published. If any single change is made to any dialogue, the interaction testers switch
  to "draft test mode", to make sure those new changes are taken into account. This means that if
  a change is made in Dialogue A, and the user starts a test on Dialogue B, this test will still
  run using the `/draft/*` end-points, as this dialogue could refer to new contents in Dialogue A.
- Fixed an issue in the API where `/dialogue/continue` (and `/dialogue/progress`) could incorrectly
  respond with "Dialogue not found" for a dialogue that does in fact exist. The lookup used to
  resolve an ongoing dialogue was reading from a per-user cache that is only built once when a
  user's session starts, so any dialogue published after that point was invisible to it. This
  lookup now resolves dialogues live, scoped to the ongoing dialogue's own project, matching how
  `/dialogue/start` already worked.
- Fixed an issue in the API where `/dialogue/get-ongoing` and `/dialogue/continue` could offer to
  resume a dialogue whose project had since been republished with different content, even though
  the dialogue itself still existed under the same name. Each logged dialogue is now pinned to the
  published version of the project it was started against; if that version is no longer the
  project's current one, it's treated as stale and no longer offered as resumable.
- Fixed an issue in the API where requesting a dialogue in a specific translation language (e.g.
  `nl-NL`) via `/dialogue/start` could silently return the source-language content instead,
  because the language-matching logic never correctly matched against a project's hyphenated
  language codes and fell through to an unrelated default. Language matching is now an exact match
  against a project's declared languages, falling back to the source language when there's no
  exact match — no more silent mismatched-language fallback.
- Aligned `/draft/*` dialogue testing with the `/dialogue/*` behavior above: requesting a language
  a draft dialogue has no translation for now falls back to the source language the same way,
  instead of rejecting the request outright.
- The Web Client will now *always* switch to the Ephemeral Draft Testing Mode after any dialogue in
  the project has been edited, even if the change only consisted of moving the position of any node.
  NOTE: This also means that any "move node" event is immediately saved to the server.
- The "LoggedDialogueId" in the bottom of interaction testers is now correctly cleared when
  switching to "Ephemeral Test Mode".
- Fixed a term-matching bug in the new Translate mode where a term whose source text spans
  multiple script lines (e.g. two paragraphs separated by a blank line) always appeared
  untranslated, even when a matching translation existed. Term keys were built with
  `Translatable.toExportFriendlyString()`, which only trims the ends of the text, while the actual
  translation-matching engine (`Translator`) normalizes *all* internal whitespace (including line
  breaks) to single spaces before comparing — so a multi-line term's key never matched its stored
  translation. Extracted that normalization into a new shared `Translatable.toNormalizedString()`
  (`packages/core`) and pointed both `Translator` and the term-listing end-point at it, so term
  keys are now generated identically everywhere.
- Fixed the Debug Console showing double-encoded JSON fields (e.g. `DBDraftTranslation.content`,
  itself a whole JSON document stored as a string) as one long line packed with escaped quotes
  instead of being indented like the rest of the body. `prettyBody()` now recursively parses and
  re-indents any string value that looks like a JSON object or array.

### Changed

- `/draft/*` end-point session IDs (`draftSessionId`) are now generated the same way as
  `loggedDialogueId`s: a bare 32-character hex string (dashes stripped from the underlying UUID),
  instead of the standard hyphenated UUID form. Purely cosmetic — the ID is only ever used as an
  opaque token by both the API and the Web Client.
- Logged dialogues are now stored in the database instead of as JSON files on disk, mirroring how
  Dialogue Branch Variables are already stored. This also makes looking up a user's most recent
  ongoing dialogue a single indexed database query instead of a scan of every session file.
- When in the "Balloon Interaction Tester" mode, the avatar image is now carrying some tools when 
  you are testing in "Ephemeral Draft Test" mode, just to make this more visually clear.
- Both the "Balloon Style" and "Text Style" interaction testers show a message "Ephemeral Draft Test"
  - Session ID: X in the bottom of the panel to indicate that this mode is active.
- Renamed the "Draft" badge shown in the Dialogue Browser for a dialogue with unpublished changes
  to "Changed" — now that Authoring Mode only ever lists draft dialogues, labeling one of them
  "Draft" no longer distinguished it from the rest of the list.
- Renamed the Dialogue Workspace's "Balloon style" and "Text style" mode button tooltips to
  "Test in Speech Bubble Style" and "Test in RPG Text Style", respectively.
- Squashed the API's ten Flyway migrations (`V1__create_schema.sql` through
  `V10__add_draft_dialogue_previous_published_name.sql`) into a single `V1__create_schema.sql`
  reflecting the current end-state schema. Since the API has not yet been deployed anywhere with
  data worth preserving, there was no reason to keep the intermediate steps (e.g. a table added
  and later dropped, a column renamed and then renamed again) around as migration history. Any
  existing local MariaDB volume must be reset (`docker compose down -v`) before starting the API
  against this new baseline, since Flyway will otherwise detect a checksum mismatch against the
  old migration chain.

### Removed

- Removed the unused JSON-file-based `VariableStoreJSONStorageHandler` and the
  `VariableStoreStorageHandler` interface it existed alongside. The interface had only ever had
  one real implementation (`VariableStoreDatabaseStorageHandler`, which the API wires up directly
  by concrete type), so the JSON handler was never instantiated or reachable at runtime; consumers
  now depend on `VariableStoreDatabaseStorageHandler` directly instead of the interface.
- Removed the unused `DBDraftDialogue.renamedFrom` field (and its `renamed_from` column, dropped
  via `V9__drop_draft_dialogue_renamed_from.sql`). It was meant to let the next publish know which
  published entry to drop after a rename, but that was never implemented — each publish already
  writes a full, independent snapshot of the live drafts under their current names, so there was
  never anything to look it up for. It was faithfully set on rename and cleared on publish, but
  never read in between.

### Security

- Fixed a missing authorization check in the API where any authenticated editor or admin could
  progress, cancel, or revert another user's `/draft/*` test session simply by obtaining its
  `draftSessionId`, since the session lookup never verified it belonged to the requesting user.
  Draft test sessions read and write the tester's real Dialogue Branch variables, so this could
  let one user manipulate another user's variable state without their knowledge. `/draft/progress`,
  `/draft/cancel`, and `/draft/revert-variables` now verify the session belongs to the requesting
  (or, for admins, delegated) user before acting on it.
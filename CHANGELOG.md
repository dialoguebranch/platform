# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to a single monorepo-wide version declared in `global.json`.

## [Unreleased]

### Removed

- **Breaking:** Removed the dead editable-model layer from `dlb-core-java`
  ([#87](https://github.com/dialoguebranch/platform/issues/87)): the `com.dialoguebranch.model.edit`
  package (`Editable`, `EditableBody`, `EditableHeader`, `EditableNode`, `EditableProject`,
  `EditableScript`, `EditableTranslation`, `EditableTranslationSet`), the
  `com.dialoguebranch.editing.parser` package (`EditableBodyParser`, `EditableHeaderParser`,
  `EditableProjectParser`, `EditableScriptParser`, `EditableTranslationParser`),
  `editing.writer.EditableScriptWriter` and `EditableTranslationWriter`, `editing.warning.ParserWarning`,
  and `cli.CommandLineRunner`. This was the model layer for a Java-based dialogue-editing UI toolkit
  that was never built and was never functionally complete; editing now happens in Dialogue Branch
  Studio against the Web Service's own draft model. `editing.writer.ProjectMetaDataWriter` and
  `cli.DialogueBranchCLI` (the module's actual CLI, now repointed onto the runtime model) are
  unaffected. Breaking, but the project is pre-1.0 (`0.x`), so this ships in the next minor
  version rather than waiting for a major.
- **Breaking:** Removed further stale CLI/vendor tooling from `dlb-core-java`
  ([#94](https://github.com/dialoguebranch/platform/issues/94)): `i18n.POEditorTools` (interactive
  import/export tooling tied to the POEditor SaaS, unused for years and with no tests), and
  `execution.parser.DialogueBranchParser`'s `main()`/`showUsage()` entry point (a single-file parse
  CLI made redundant by `cli.DialogueBranchCLI`; `DialogueBranchParser` itself is unaffected). Also
  removed `i18n.TranslationFile.writeToTSVFile()`, orphaned by #87's removal of its only caller.
  Ships in the next minor version, alongside #87 (pre-1.0, see above).
- **Breaking:** Removed further dead code from `dlb-core-java`, found in a follow-up scan after
  #87/#94: `exception.ScriptParseException`, `exception.FileSystemException`, and
  `exception.VariableException` (each used only by the deleted editable-model layer),
  `model.common.ScriptTreeNode` (used only by the deleted editable-model layer and the CLI code
  already repointed off it in #87), `i18n.TranslationTerm` (used only by the deleted
  `POEditorTools`), and two pre-existing orphans unrelated to either prior removal:
  `model.common.DatabaseStorageSource` (a `StorageSource` implementation never instantiated
  anywhere — the Web Service's DB-backed draft storage uses its own separate entities instead) and
  `model.execute.DialogueStatus` (an unused enum, never wired into `DialogueState`). Ships in the
  next minor version, alongside #87/#94 (pre-1.0, see above).

### Added

- Core: `dlb-core-java`'s public API now carries a machine-readable nullness contract
  ([#95](https://github.com/dialoguebranch/platform/issues/95)). Every package is
  [JSpecify](https://jspecify.dev) `@NullMarked` (via a new `package-info.java` per package, which
  also adds the previously-missing package-level Javadoc), so unannotated reference types are
  non-null by default and the genuinely-nullable getters, setters, fields and parameters are
  marked `@Nullable` — e.g. `Reply.getStatement()`, `ProjectMetaData.getSlug()`,
  `VariableStore.getVariable()`, `ProjectParserResult.getProject()`. Adds an `org.jspecify:jspecify`
  dependency (annotations only, no runtime cost). Not a binary-compatible break, but downstream
  builds with strict null analysis may surface new warnings.
- Core: the `dlb-core-java` nullness contract is now enforced in the build
  ([#117](https://github.com/dialoguebranch/platform/issues/117)). [NullAway](https://github.com/uber/NullAway)
  runs over `com.dialoguebranch.*` on every compile (a compile-only error-prone check, no runtime
  or published-classpath impact) and a violation fails the build. Getting there refined roughly
  150 further nullness spots the #95 Javadoc pass had deferred — `@Nullable` on genuinely-optional
  values (`Variable.getValue()`, `AttributesCommand.read*Attr(...)`, `NodeParseException.getNodeTitle()`,
  the `i18n` speaker/addressee parameters, …) and explicit guards where a value is contractually
  present — so the contract is both more complete and machine-checked. Downstream builds with
  strict null analysis may surface correspondingly more warnings.
- Core: the expression engine vendored from `rrd-utils` in #102
  (`com.dialoguebranch.expression` / `.expression.types` / `.io` / `.util`) is now under the same
  `@NullMarked` + NullAway contract as the rest of `dlb-core-java`
  ([#121](https://github.com/dialoguebranch/platform/issues/121)) — it had been excluded from the
  check. Its genuinely-nullable public surface is now `@Nullable`: `Value.getValue()` and the
  `Value(...)` constructor, `Token.getValue()`, `Expression.evaluate(...)`'s `variables` argument,
  `ExpressionParser.readExpression()` / `readOperand()`, and `CurrentIterator.getCurrent()`.
  Evaluation behaviour is unchanged. The two intricate state-machine classes (`Tokenizer`,
  `ExpressionParser`) and `Value` / `LineColumnNumberReader` keep a class-level NullAway
  suppression for their internal dataflow; documenting the engine's members is still open on #121.
- Core: `ProjectParser` now detects orphaned nodes — a node that no reply link (internal or
  external) points to, and that isn't its own dialogue's Start node ([#105](https://github.com/dialoguebranch/platform/issues/105)).
  This can never cause a runtime error by design (a dialogue isn't required to link every node it
  defines), so it's reported as a warning via the existing `ProjectParserResult.getWarnings()`,
  already visible in `DialogueBranchCLI`'s project summary.
- Core: `cli.DialogueBranchCLI` (renamed from `ProjectTool` — see below) now supports
  non-interactive, scriptable invocation alongside its existing interactive menu
  ([#105](https://github.com/dialoguebranch/platform/issues/105)):
  `DialogueBranchCLI <path-to-dlb-project.xml> [--validate]` parses a project and prints its
  summary, exiting non-zero on parse errors (warnings, such as orphaned nodes, do not affect the
  exit status) — usable as a CI gate. `DialogueBranchCLI <path> --execute <language> <dialogue>`
  runs a specific dialogue, reusing the same interactive terminal conversation as before. The
  no-argument interactive session is unchanged and remains the default.
- Permission-based access control for the Web Service ([#28](https://github.com/dialoguebranch/platform/issues/28)):
  a `Permission` catalogue, a `Role` → permission mapping (`participant` ⊂ `editor` ⊂ `admin`),
  and an `AuthorizationService` that decides whether a caller may perform an operation.

### Changed

- Renamed `cli.ProjectTool` to `cli.DialogueBranchCLI`
  ([#105](https://github.com/dialoguebranch/platform/issues/105)) — the old name dated back to
  when it was one of several overlapping CLI entry points (see #87/#94); now that it's the
  library's sole CLI and does more than inspect projects (it also validates and executes
  dialogues), the name should say so. `build.gradle`'s `mainClass` and the jar manifest's
  `Main-Class` are updated to match.
- Endpoint authorization is now expressed as one required `Permission` per end-point (resolved
  from the caller's roles via the central role→permission map) instead of an inline list of
  accepted roles ([#58](https://github.com/dialoguebranch/platform/issues/58)). Which roles may
  call each end-point is unchanged, but an authenticated caller who lacks the required permission
  — including a non-admin who passes `delegateUser` — now gets **`403 Forbidden`** (previously
  `401 Unauthorized`), still with error code `INSUFFICIENT_PRIVILEGES`; a missing, expired, or
  invalid token remains `401`.
- Studio's "you can't do this" tooltips on gated actions (Publish / Configure / Export project,
  Import / New project) no longer name a specific role — they now read "You don't have permission
  to …", so they stay correct if the role a capability requires ever changes.
- Core: replaced `rrd-utils`' `I18nLanguageFinder` with a small native
  `com.dialoguebranch.i18n.LanguageFinder` built on `java.util.Locale` RFC 4647 lookup, as the
  first step of dropping the `nl.rrd:rrd-utils` dependency from `dlb-core-java`
  ([#102](https://github.com/dialoguebranch/platform/issues/102)). Picking the source language
  for a multi-language project when the project metadata doesn't name one now also accepts a
  region-qualified tag for a generic request (a project shipping only `en-US` satisfies the
  English fallback where before it did not); exact-match and no-match behaviour is unchanged.
- **Breaking:** Core: vendored the `.dlb` `<<if>>` / `<<set>>` expression engine into
  `com.dialoguebranch.expression` (+ `.types`), and moved the two parser exceptions it shares
  with the rest of the library — `ParseException` and `LineNumberParseException` — into
  `com.dialoguebranch.exception` (folded under `DialogueBranchException`), continuing #102's
  removal of the `nl.rrd:rrd-utils` dependency. Evaluation semantics, `.dlb` syntax and the wire
  format are unchanged, but a direct `dlb-core-java` consumer that referenced
  `nl.rrd.utils.expressions.*` types, or caught `nl.rrd.utils.exception.ParseException` /
  `LineNumberParseException`, must switch to the `com.dialoguebranch.*` equivalents. Project
  metadata (`dlb-project.xml`) is now read with the JDK's StAX parser instead of `rrd-utils`'
  SAX helper, with DTD / external-entity resolution disabled.
- **Breaking:** Core: dropped the `nl.rrd.utils` `xml`, `io.FileUtils` and `json.JsonObject`
  usages from `dlb-core-java` (continuing [#102](https://github.com/dialoguebranch/platform/issues/102)).
  `editing.writer.ProjectMetaDataWriter` now writes `dlb-project.xml` with the JDK's DOM /
  `Transformer` APIs and its entry point changed from `writeToXMLFile(XMLWriter, ProjectMetaData)`
  to `writeToXMLFile(OutputStream, ProjectMetaData)` (the caller no longer constructs, or needs,
  an `nl.rrd.utils.xml.XMLWriter`); `model.execute.protocol.NullableResponse` no longer extends
  `nl.rrd.utils.json.JsonObject` (its `toString()` output changes, and it no longer has
  map-based `equals` / `hashCode`). Output formatting of the exported metadata file changes
  slightly (indentation, XML declaration); it still round-trips through the reader unchanged.
- **Breaking:** `dlb-core-java` no longer depends on `nl.rrd:rrd-utils`
  ([#102](https://github.com/dialoguebranch/platform/issues/102) — the goal of that issue). The
  last three uses were vendored or replaced: `CurrentIterator` moved to
  `com.dialoguebranch.util`, `ReferenceParameter` in the body tokenizer became a plain
  `int[]` out-parameter, and `BodyToken.toString()` no longer routes through `DataFormatter`.
  `rrd-utils` is dropped from the published POM — a consumer that was relying on getting it
  transitively from `dlb-core-java` must now declare it directly.
- The Web Service (`apps/api`) and the External Variable Service (`apps/mock-variable-service`)
  no longer depend on `nl.rrd:rrd-utils` either
  ([#136](https://github.com/dialoguebranch/platform/issues/136)) — the platform now has no
  `rrd-utils` dependency anywhere. `DatabaseException` moves to the existing
  `com.dialoguebranch.web.service.exception.DatabaseException`; `DateTimeUtils.nowMs(zone)`
  becomes a two-line local helper over `ZonedDateTime.now(zone).truncatedTo(MILLIS)`; the
  request-body reads use `InputStream.readAllBytes`; the base-path parse uses `java.net.URI`;
  the forbidden-query-param check parses the query string directly; and the `HttpError` /
  `HttpFieldError` / `DialogueListPayload` DTOs drop `extends JsonObject` for a plain
  `toString()`. No REST payload, protocol, or behaviour change.
- Core: `execution.parser.ParserResult` and `ProjectParserResult` are now constructed with their
  required value (the parsed `Dialogue` / the `ScriptLoader`) instead of a no-arg constructor
  plus a setter, and `ExpressionCommand`'s internal `ReadContentResult` / `ParseContentResult`
  likewise ([#121](https://github.com/dialoguebranch/platform/issues/121)) — the fields are now
  `final` and the `@SuppressWarnings("NullAway.Init")` markers are gone. `ParserResult()` /
  `ParserResult.setDialogue(...)` and `ProjectParserResult()` / `setScriptLoader(...)` are
  removed; both types are only ever handed back by the parser, never constructed by callers.
- Core: `execution.ActiveDialogue` now takes its `VariableStore` as a third constructor argument
  ([#121](https://github.com/dialoguebranch/platform/issues/121)) — previously the field was set
  by a mandatory `setVariableStore(...)` call right after construction. The two-argument
  constructor is removed. `setVariableStore(...)` is kept for the one case that genuinely
  re-points the store (re-attaching a persisted `DialogueState` to a fresh user session).
- Core: internal parser cleanup ([#121](https://github.com/dialoguebranch/platform/issues/121)) —
  `execution.parser.DialogueBranchParser` sets its `dialogueName` / `reader` in a canonical
  private constructor (the public constructors are unchanged), and
  `BodyParser.ParseUntilCommandClauseResult` takes its `body` as a constructor argument. No
  behaviour change.
- Core: `execution.parser.BodyToken` is now built via a constructor
  (`BodyToken(Type, String text, int lineNumber, int colNumber[, Object value])`) rather than a
  no-arg constructor plus setters ([#121](https://github.com/dialoguebranch/platform/issues/121));
  `setType` / `setText` / `setLineNumber` / `setColNumber` are removed and its fields are `final`.
  `getValue()` is now `@Nullable` (it is `null` for the token types that carry no value).
  `ReplyParser` threads its statement / node-pointer / command sections through as method
  arguments instead of scratch fields. Tokenisation and reply parsing are unchanged.
- **Breaking:** Core: `model.execute.protocol.DialogueAction` is now built via an all-args
  `@JsonCreator` constructor (`type`, `value`, optional `parameters`) with `final` fields, instead
  of a no-arg constructor plus setters ([#121](https://github.com/dialoguebranch/platform/issues/121)).
  `DialogueAction()`, `setType(...)`, `setValue(...)` and `setParameters(...)` are removed. The
  JSON wire format — `{"type":…,"value":…,"parameters":{…}}` — is unchanged and still
  round-trips (a JSON document with no `parameters` field deserializes to an empty map).
- **Breaking:** Core: `model.execute.protocol.DialogueMessage` likewise moves to an all-args
  `@JsonCreator` constructor with `final` fields
  ([#121](https://github.com/dialoguebranch/platform/issues/121)); its no-arg constructor, seven
  setters and `addReply(...)` are removed and `DialogueMessageFactory` builds the message in one
  step. The `/dialogue/*` response JSON is unchanged.
- **Breaking:** Core: the three `model.execute.protocol.DialogueStatement` segment classes —
  `TextSegment`, `InputSegment`, `ActionSegment` — are now built via constructors
  (`TextSegment(String)` / `ActionSegment(DialogueAction)` are `@JsonCreator`s; `InputSegment`'s
  is called by its custom deserializer) with `final` fields
  ([#121](https://github.com/dialoguebranch/platform/issues/121)); their no-arg constructors and
  `setText` / `setInputType` / `setDescription` / `setParameters` / `setAction` are removed. The
  statement / segment JSON — including `InputSegment`'s flattened parameter form — is unchanged.
- **Breaking:** Core: `model.common.ProjectMetaData` is now built via a
  `ProjectMetaData(name, description, version)` constructor with `final` `name` / `description` /
  `version` ([#121](https://github.com/dialoguebranch/platform/issues/121)); the no-arg and
  five/six-arg constructors and `setName` / `setDescription` / `setVersion` are removed, and
  `getStorageSource()` is now `@Nullable` (only metadata read from a store carries one). The XML
  parser now yields `""` rather than `null` from `getDescription()` for a project whose metadata
  has no `<description>` element.

### Fixed

- Web Service: role extraction now honours `dlb.auth.keycloak.client-id`
  ([#104](https://github.com/dialoguebranch/platform/issues/104)). A deployment that set a
  non-default client id received tokens whose roles live under `resource_access.<that-id>.roles`,
  but role extraction always looked under a hardcoded `dlb-web-service` key — so every
  authenticated user resolved to no roles and got `403 INSUFFICIENT_PRIVILEGES` on every
  permission-gated endpoint. `azp` validation already respected the setting; the two now agree.
  The "user has no roles" warning also names the configured client id and lists the
  `resource_access` keys the token did carry.
- Core: updated the interactive banner and entry-point Javadoc to use "Dialogue Branch CLI"
  instead of the old "Dialogue Branch Project Tool" name
  ([#110](https://github.com/dialoguebranch/platform/issues/110)).
- Studio: the "Continue" button in the balloon test view (shown for auto-forward dialogue steps)
  was near-invisible — a transparent fill with a border and text colour that matched the grey
  page background. It now has a light fill, an orange border and dark text, so it reads clearly
  for participants and editors alike ([#98](https://github.com/dialoguebranch/platform/issues/98)).

### Security

- The Web Service now checks each token's `azp` (client) claim against
  `dlb.auth.keycloak.trusted-clients` (env `DLB_AUTH_KEYCLOAK_TRUSTED_CLIENTS`), on top of the
  existing signature/issuer/expiry checks. Unset, it trusts its own `client-id` and the BFF's
  (`dlb-bff`), which covers the standard topology (direct API clients / Swagger UI, and Dialogue
  Branch Studio via the BFF) with no configuration. Set the property to a comma-separated list to
  trust additional clients in a shared realm, or to a single `*` to accept any client from a
  trusted realm (the previous behaviour); an explicit list replaces the default entirely, so it
  must still name `dlb-bff` on a BFF-fronted deployment. Tokens from an untrusted client are
  rejected with `401` ([#67](https://github.com/dialoguebranch/platform/issues/67)).

## [0.1.8] - 2026-09-01

### Changed

- Dialogue Browser entries (folders and dialogues alike) now render as grey row blocks, matching
  the Variable Browser, and each block is inset by its nesting depth.
- The web client is now called "Dialogue Branch Studio" in its own UI: the footer info bar, the
  browser tab title, the project-selector subtitle, and the version-mismatch page.
- Studio's balloon test view steps the speech-bubble font size down for long statements
  (to `text-base` past ~400 characters, `text-sm` past ~900) so more of a long node fits before
  the reader has to scroll the page. Within one dialogue run the size only ever shrinks — a
  shorter node after a long one keeps the reduced size instead of bouncing back up.
- **Breaking:** `POST /v1/project/create-project` now expects the source-language fields as
  `sourceLanguageCode` / `sourceLanguageName` instead of `defaultLanguageCode` /
  `defaultLanguageName`. The old names are no longer accepted (a request using them fails with
  "Field 'sourceLanguageCode' is required."). This aligns the create request with the rest of
  the API, which already uses `sourceLanguage*` everywhere else (the create response, `GET
  /v1/project/get-project`, and the database). The bundled Dialogue Branch Studio is updated to match.

### Added

- Core: `Reply.isAutoForward()` — names the "reply without a statement" check; the conceptual
  call sites in the parser, executor, message factory, translatable extractor and CLI now use it
  instead of open-coding `getStatement() == null`.
- `apps/studio` now has a unit-test setup — Vitest + Vue Test Utils + jsdom, run with `npm test`.
  Initial coverage is the dialogue-testing views (reply-option highlighting, the per-tab scroll
  API, statement paragraph rendering, and the speech-bubble font-size ratchet) and the Dialogue
  Browser sort/filter helpers.
- The Variable Browser has a **Values / Used** toggle. **Values** is unchanged — the logged-in
  user's stored variable values. **Used** is a new read-only reference list of every variable name
  the project's dialogues reference, so you can look up the exact name of a variable (`$userName`
  vs `$user_name` …) without having triggered it first; hover a row to copy its name. Names the
  user already has a value for show solid; ones with no value yet are slightly dimmed
  ([#64](https://github.com/dialoguebranch/platform/issues/64)).
- New end-point `GET /v1/variables/list-project?projectSlug=…` (editor/admin) backing the above:
  returns every variable any of a project's dialogues reads or writes, sorted by name and flagged
  read/written, regardless of whether a value is stored for it.
- The Dialogue Browser has a filter + sort strip below its header. The filter box narrows the
  tree to matching dialogues/folders (folders auto-expand while filtering, without disturbing
  the saved expand state). The sort control offers **Default** (folders first, then alphabetical
  — the API's own order) and **Name (A–Z / Z–A)** (a flat sort with folders and dialogues
  intermixed) in both modes, plus **Last updated** and **Size** (by node count, folders kept
  grouped first) in Authoring Mode.
- The Variable Browser has the same filter + sort strip: a name filter and **Name (A–Z / Z–A)**
  ordering (variables now default to alphabetical order rather than the order the service
  returns them in).
- The authoring `list-dialogues` response now includes each draft dialogue's `updatedAt` and
  `nodeCount`, and is ordered folders-first, so a client can offer sort-by-last-updated and
  sort-by-size in Authoring Mode. Node counts come from a single grouped query (no node content
  is loaded).
- The debug bar at the bottom of Studio's dialogue test views now also shows the node
  the current step is in, next to the ephemeral session / logged-dialogue id.
- Studio's admin-only "Technical Information" dialog now also shows the Web Service's
  version and build time, its configured base URL (`dlb.base-url`), the host/port/scheme it
  observed on the incoming request (as seen by the service itself — behind the BFF proxy this is
  the address the proxy connected to, not a browser-facing URL), and the Keycloak base URL and
  realm it validates tokens against. These come from new fields on the `GET /v1/info/technical`
  response, which remains restricted to the `admin` role.

### Fixed

- Parsing a project's metadata (`dlb-project.xml`) now rejects a `language-map` whose source or
  translation languages are missing a `code`, or where two languages share one (case-insensitive),
  instead of silently building a broken language configuration.
- `list-dialogues` now returns dialogue names ordered "folders first" — at each `/`-separated
  level, sub-folders come before loose dialogues, then alphabetical (case-insensitive) — instead
  of a plain lexicographic sort. Studio builds its own tree so its Dialogue Browser was already
  correct; this fixes the order seen by clients that render the API response directly.
- Text-mode dialogue testing in Studio no longer shows a fresh dialogue's reply options
  greyed out (or with one option falsely highlighted as already chosen). The component tracked
  past reply selections by step index without clearing them when the dialogue was restarted,
  reloaded, or when switching to another test tab, so stale entries carried over onto the new
  content.
- Text-mode test tabs now keep their own scroll position. The history view's scroll container
  and spacer height were shared by every tab, so switching tabs left the view at whatever offset
  the previous tab had. Each tab's offset is now saved on the way out and restored on the way
  back in (a tab shown for the first time still pins to the latest statement).
- Dialogue statement text with paragraph breaks now renders as separate paragraphs in Studio's
  balloon and text test views instead of collapsing into one block. Blank lines in a
  node's body become `<p>` breaks and single line breaks become `<br>`; the newlines were
  already preserved by the parser and web service, only Studio's HTML rendering dropped
  them.
- Studio's status bar no longer reports a misleading "Connected to `<host>` on port
  `<port>`" line. Since Studio talks only to its own origin and the Web Service sits behind
  the BFF proxy, that host and port were Studio's own, not the Web Service's. It now reads
  simply "Connected to Web Service v`<version>`." (the version is still the real one, fetched
  through the proxy), or "Could not connect to the Web Service." on failure.
- The "Create Project" wizard's third step now calls the setting the project's "Source Language"
  rather than "Default Language", and explains that it's the language dialogue content is authored
  in and that other languages are added later as translations ([#76](https://github.com/dialoguebranch/platform/issues/76)).
  This matches the wording already used in the project configuration dialog.

## [0.1.7] - 2026-08-12

### Added

- The visual dialogue editor's node graph now supports right-clicking empty canvas to create a new
  node at that location, in addition to the existing "Add Node" toolbar button.
- The visual dialogue editor's node body field now has a right-click menu for inserting Dialogue
  Branch statements (reply options, `<<set>>`, `<<if>>`/`<<elseif>>`/`<<else>>`, `<<random>>`,
  `<<action>>`, and all `<<input>>` types) at the cursor, instead of writing the raw syntax by hand.
- The "Service Unavailable" screen's status icons now show the exact URL each reachability check
  hit, on hover, to make it easier to tell which backend a failure is actually pointing at.
- The Web Service's resource server now trusts more than one Keycloak realm: the configured
  `dlb.auth.keycloak.realm` plus any realm on the same Keycloak instance named
  `<that realm>-<anything>`. This supports a hosting platform that provisions one Keycloak realm
  per client or tenant on top of its own base realm, all sharing this same Dialogue Branch backend
  and database; previously the JWT filter validated exclusively against the single configured
  realm, rejecting an otherwise valid token from any other realm outright. The realm name is only
  ever taken from a token's own (unverified) `iss` claim to select which known-trusted realm to
  check the token's signature against, not to pick an arbitrary network address to call out to.

## [0.1.6] - 2026-07-29

### Added

- Added a `client` Docker Compose profile (`infrastructure/docker/compose.yml`) that containerizes
  the web client and BFF behind a new nginx reverse proxy (`infrastructure/docker/nginx/client.conf`),
  mirroring a real deployment ([#81](https://github.com/dialoguebranch/platform/issues/81)). This
  gives a third local development setup, alongside the existing two: `docker compose --profile
  client up -d` runs everything (API, web client, BFF, proxy) in Docker on a single origin,
  `http://localhost:8080`, with no separate dev server needed.
- The BFF (`apps/bff`) now persists sessions to MariaDB via Spring Session, instead of the JVM's
  own heap, so a redeploy no longer silently logs out every signed-in user. This includes the
  `OAuth2AuthorizedClient` holding the session's access/refresh token, which Spring Boot's default
  wiring keeps separately from the session itself and would otherwise still be lost on redeploy.
  The session inactivity timeout (also driving the `SESSION` cookie's `Max-Age`) is now configurable
  via `dlb.bff.session-timeout` (`DLB_BFF_SESSION_TIMEOUT`), defaulting to 7 days instead of Spring
  Boot's own 30-minute default. The cookie is re-issued on every request so it slides forward with
  activity instead of hard-expiring on a fixed clock from login.
- `infrastructure/keycloak/sync-realm.py` now also reconciles redirect/post-logout URIs on Keycloak
  clients that already exist, not just clients that are entirely missing. Previously, a change like
  the `client` profile's new origin never reached a developer's already-provisioned local Keycloak
  without manually resetting the MariaDB volume.

### Fixed

- Fixed BFF logout failing with Keycloak's "Invalid redirect uri" for same-origin deployments (the
  new `client` profile, and production behind a reverse proxy). The BFF sent `post_logout_redirect_uri`
  to Keycloak as a literal relative `/`, which Keycloak's `end_session_endpoint` rejects since it
  requires an absolute, registered URI. This differs from the login-success redirect, which a
  browser resolves relative to its own current origin regardless. Now resolves via `OidcClientInitiatedLogoutSuccessHandler`'s
  `{baseUrl}` placeholder when the redirect URL is left at its default, honoring the reverse proxy's
  forwarded headers.

## [0.1.5] - 2026-07-29

### Added

- Added a Backend-for-Frontend (BFF) service (`apps/bff`) between the web client and Keycloak/the
  Dialogue Branch Web Service ([#79](https://github.com/dialoguebranch/platform/issues/79)). The
  web client no longer talks to Keycloak or the API directly, and no longer holds an access token
  in the browser at all — the BFF performs the login exchange with Keycloak and keeps the session's
  token server-side, so the browser only ever holds a session cookie. All API calls now go through
  the BFF at `/api/**`, and a new `/whoami` endpoint returns the current session's username and
  roles. Logging out now also ends the Keycloak SSO session, not just the local one, so logging
  back in properly re-prompts for credentials instead of silently resuming the same session. Local
  development gained a matching `bff` Docker Compose service (started via the existing `api`
  profile) and a one-shot `keycloak-sync` service that adds the BFF's Keycloak client to a realm
  that already existed before this change.
- Added a `variable-service` Docker Compose profile (`infrastructure/docker/compose.yml`) that
  builds and runs the mock external variable service alongside MariaDB and Keycloak, for testing
  `dlb-web-service`'s external variable service integration locally.
- The mock variable service now also resolves a `$dayPart` variable on `retrieve-updates` requests,
  returning `"morning"`, `"afternoon"`, `"evening"`, or `"night"` based on the current hour in the
  user's time zone, alongside the existing `$currentDate`/`$currentTime` handling.
- Added an "Insufficient Privileges" screen to the web client: a user who successfully logs in but
  lacks the required Dialogue Branch role (`admin`, `editor`, or `participant`) now sees an
  explanation and a Log Out button, instead of being silently logged out and redirected straight
  back to the login page with no indication of why.

### Changed

- The web client's production build now splits `dlb-lib` (the framework-agnostic client library
  for talking to a Dialogue Branch Web Service, intended to be reusable outside this app) into its
  own JS chunk instead of bundling it into the main app chunk, via a `manualChunks` rule in
  `vite.config.js`.
- The web client now imports only the specific Font Awesome icons it actually uses, instead of the
  entire solid and regular icon sets. This shrinks the main production bundle from ~1.49 MB to
  ~450 kB (minified), removing the build's "chunks larger than 500 kB" warning entirely.

### Fixed

- Fixed the mock variable service's Docker image, which failed to build: it referenced source
  paths that no longer exist and packaged the app as a WAR for a standalone Tomcat instead of the
  Spring Boot jar it's built as today. Its logging config also referenced a missing resource file,
  which crashed the service on startup.
- Fixed a `NullPointerException` thrown when starting a dialogue that references a variable with
  no existing value while an external variable service is enabled.

## [0.1.4] - 2026-07-22

### Added

- Added project export/import: a project's published content can be downloaded as a `.zip`
  archive ("Export Project" in the project menu, Live Mode only, once published) and re-created as
  a new project from that archive ("Import Project" from the project selector). New endpoints `GET
  /project/export-project` and `POST /project/import-project`. Import validates the archive
  (size caps, zip-slip protection, project-content validation, slug-collision check) before
  creating anything in the database. Export fetches a project's dialogues and translations in two
  batched queries instead of one query per dialogue. Importing an archive preserves its exported
  version number instead of always restarting the new project at version 1.
- Added a service status check to the web client's startup: before redirecting to Keycloak's login
  page, it now verifies the DLB API and Keycloak are both reachable. If either is down, it shows a
  status page with a Retry button instead of sending the user to a broken login page. The same
  check also compares the API's reported software version against the web client's own build
  version; a mismatch (e.g. a stale cached bundle after a deploy) shows an "Update Available" page
  with a Reload button instead of proceeding to login.

### Fixed

- The web client footer version had drifted from `global.json` (still showing v2.0.1 after the
  v2.0.3 release) because `apps/web/package.json` was never re-synced. `infrastructure/release/release-github.sh`
  now runs `npm run sync-version` in `apps/web` right after bumping `global.json`, and includes
  `apps/web/package.json`/`package-lock.json` in the release commit, so this can't drift again.

## [0.1.3] - 2026-07-20

### Added

- Moved project metadata (display name, description, translation languages) into the same
  draft → publish cycle as dialogue content ([#75](https://github.com/dialoguebranch/platform/issues/75)).
  Editing a project's metadata used to write directly to the live/published record with no review
  step; a new "Configure Project" window (renamed from "Edit Metadata", and now only available in
  Authoring Mode) edits a draft copy instead, with a General tab (display name, description,
  read-only project slug, and the latest published version's info) and a Languages tab (the
  source language shown read-only, plus add/rename/remove for translation languages). Nothing is
  sent to the server until "Save Draft" is pressed, and the whole batch of pending changes —
  metadata plus any language removals, additions, and renames — is applied atomically through a
  new `POST /project/update-draft` end-point: either all of it succeeds, or none of it does, with
  structured per-field errors reported back for whichever change conflicted (e.g. two languages
  ending up with the same code). Removing a translation language warns which draft dialogues
  currently have content in it before it's removed, and — like dialogue deletion — is reversible
  (with an undo) until the project is next published. Draft and published translation content also
  gained real referential integrity: `DBDraftTranslation`/`DBPublishedTranslation` now reference
  their language via a foreign key into a language registry, rather than an unconstrained string
  column. Finally, publishing now snapshots a project's display name, description, and
  translation-language list onto the new, immutable published version — exactly like dialogue
  content already is — so a past published version retains its own historical metadata and
  language list instead of sharing one project-wide, mutable "current" record.
- Replaced the Antora/AsciiDoc-based Documentation Hub with a new VitePress-based site
  (`documentation/vitepress/`), now live at https://www.dialoguebranch.com/docs/. The vendored
  `antora-ui-default` fork (`documentation/dlb-ui/`) had drifted from the rest of the platform's
  look (pinned to Node 10, a separate gulp/browserify toolchain to maintain) and contributed 48
  open Dependabot alerts, all in its own `package-lock.json`, none reachable from the shipped
  product. The new site reuses the Web Client Test Application's brand palette and self-hosted
  "Roboto Slab" font, and is fronted by a header mirroring the rest of www.dialoguebranch.com
  (Home / Documentation / About / News & Updates) so the docs, marketing site, and web client read
  as one product. All content was ported over 1:1.

### Fixed

- Fixed "Publish Project" staying disabled in the web client's Authoring Mode when the only
  unpublished change was to translation content. Saving a translation cell in `TranslationEditor
  .vue` already marked the owning draft dialogue as changed server-side, but the component had no
  way to tell the rest of the app a save had happened — unlike the node editor (`DialogueEditor
  .vue`), which emits `dialogueSaved` after every edit, prompting `DialogueBrowser.vue` to refetch
  the draft dialogue list and recompute whether anything is unpublished. `TranslationEditor.vue`
  now emits the same `dialogueSaved` event after each successful translation save.

### Removed

- Removed the old Antora/AsciiDoc documentation project (`documentation/antora/`, including the
  vendored `dlb-ui` UI fork) from the repo now that the VitePress site above has fully replaced it
  in production.

## [0.1.2] - 2026-07-17

### Added

- Added support for cross-dialogue reply links (`[[Reply Text.|otherDialogue.NodeId]]`) in draft
  test sessions (`/draft/*`) — selecting such a reply used to fail with "This reply points to
  another dialogue, which isn't supported in draft test mode."
  ([#74](https://github.com/dialoguebranch/platform/issues/74)). `DraftExecutionService
  .startSession` already parsed the *whole* project (every draft dialogue, not just the one under
  test) precisely so sibling references would resolve, but that parsed `ExecutableProject` was a
  local variable, discarded once the session started, leaving `progressSession` with no way to
  actually follow a link when one turned up. `DraftTestSession` now keeps the parsed project (and
  the project's source language, needed to resolve languages the same way published dialogues do
  — not reliably available from the parsed project's own metadata, since these dialogues are
  parsed from in-memory content maps, not an actual `dlb-project.xml`) for the life of the
  session; `progressSession` resolves the target dialogue against it and switches the session over
  via a new `DraftTestSession.switchToDialogue`, mirroring how `DialogueExecutor` already handles
  the same case for published dialogues. No Web Client changes were needed — `DialogueWorkspace
  .vue` already updates a tab's displayed dialogue name from whatever the server's response says
  on every progress step.
- Added an optional `startNodeId` parameter to the API's `POST /dialogue/start` end-point, to
  start a live (published) dialogue session at a specific node instead of always the default
  "Start" node — mirroring what `/draft/start` already supported for draft testing.
  `UserService.startDialogueSession` already accepted this; only the controller's `@RequestParam`
  plumbing was missing. Used by the Web Client: switching the "Test dialogues in:" language
  selector while a dialogue is actively running in the current tab now restarts that tab's test at
  its current node, in the newly selected language, instead of silently leaving it running in
  whichever language it happened to be started in
  ([#69](https://github.com/dialoguebranch/platform/issues/69)).
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

- Gave the Web Client's auto-generated "Continue" reply buttons (`AutoForwardReply` — an
  autoforward with no `[[Reply Text.|NextNode]]` of its own) a distinct, secondary/outline style
  in both the Speech Bubble and RPG Text test modes, instead of the same solid orange button used
  for real, author-defined reply options (`BasicReply`) ([#68](https://github.com/dialoguebranch/platform/issues/68)).
  A tester can now tell at a glance which buttons represent an actual choice versus a mechanical
  "just move on."
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

- Fixed the Web Client's Dialogue Browser not alphabetically ordering dialogues in Authoring Mode
  ([#72](https://github.com/dialoguebranch/platform/issues/72)). Both sort comparators
  (`DialogueBrowser.vue`'s top-level tree and `DialogueTreeNode.vue`'s recursive children) only
  ever partitioned folders before files — same-category entries always compared as `0` (equal),
  so any apparent alphabetical order was purely incidental, preserved from whatever order the
  underlying API happened to return. That's a coincidence Live Mode's `/dialogue/list-dialogues`
  response order usually matched, but Draft/Authoring Mode's `/authoring/list-dialogues` doesn't.
  Both comparators now break ties with `localeCompare` on the folder/file name, so ordering no
  longer depends on the API's response order at all.
- Fixed the Web Client's `IconButton.vue` `disabled` prop only changing the button's cursor
  styling instead of actually disabling it — the native `<button>` element was never bound to
  `:disabled`, so a fast double-click could still fire `@click` on a "disabled-looking" button
  (e.g. mid-request, on the "Refresh current dialogue step"/"Refresh dialogue list" buttons, or
  the Dialogue Editor's "Cancel dialogue"/"Revert variables"/"Add Node" buttons).
- Fixed the Web Client's Dialogue Browser collapsing every expanded folder on every refresh, even
  when the refreshed list is identical to what's already shown — the common case
  ([#71](https://github.com/dialoguebranch/platform/issues/71)). `listDialogues()` now snapshots
  the displayed entries (name, `isPublished`/`isNew`/`isChanged`/`isDeleted`, order-independent)
  and only resets `openFolders` when a refresh's result actually differs from that snapshot —
  triggered by manual refresh, publish, and creating/renaming/deleting a draft dialogue, not just
  the initial load. Since a no-op refresh no longer has any visible effect on its own, the
  "Refresh dialogue list" button now spins while the request is in flight (with the same
  minimum-visible-duration treatment as the "Refresh current dialogue step" button), so clicking
  it still gives feedback that it did something.
- Fixed the Web Client's "restart dialogue" button (shown once a live/published dialogue test has
  finished) opening a new tab instead of restarting in the current one
  ([#70](https://github.com/dialoguebranch/platform/issues/70)). `DialogueWorkspace.vue`'s
  `loadDraftDialogue` already accepted an existing `tab` to restart in place — used by
  `restartActiveTab` for draft tests — but `loadDialogue` (the live-dialogue path) had no
  equivalent parameter, so `getOrCreateEmptyTab()` always ran, and since the just-finished tab
  still has a `dialogueName` set, it never counted as "empty" and a new tab was created every
  time. `loadDialogue` now accepts the same `{ tab, language }` option `loadDraftDialogue` does.
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

## [0.1.1] - 2026-07-16

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

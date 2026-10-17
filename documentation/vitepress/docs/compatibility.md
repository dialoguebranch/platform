# Compatibility Policy

This page says which parts of Dialogue Branch you can build on, and what a **Breaking** entry in the [Release Notes](/release-notes) means. In short: a change is *breaking* only if it can break something that someone outside this repository realistically builds or runs. Changes to the parts that exist to serve Dialogue Branch Studio are still recorded, but under a different label.

## Why this distinction exists

Dialogue Branch Studio, the BFF and the Web Service are developed and released together, and the parts of the Web Service that only Studio talks to change whenever Studio's needs do. Marking each of those changes as *Breaking* would devalue the label for the people it is meant to protect: those running their own client, their own External Variable Service, or their own deployment.

## What is covered (stable)

A change that breaks any of the following is labelled **Breaking** in the release notes:

* **The `.dlb` language** — the syntax and execution semantics of a dialogue script, including the project file format (`dlb-project.xml`).
* **The REST API's stable end-points** — dialogue playback, variables, dialogue logs, project management, and publishing, plus the import and export end-points a script or CI job would call without Studio. [Each end-point is listed below](#rest-end-points).
* **Authentication and authorisation** — validation of Keycloak-issued bearer tokens, the JWT claims the service reads to identify a user, and the roles (`participant`, `editor`, `admin`) that grant access.
* **The [External Variable Service](/web-services/external-variable-service) protocol** — the requests the Web Service sends to your service, and what it expects back.
* **`DialogueBranchClient`** (playback) and the `@dialoguebranch/client-js/protocol` types of `@dialoguebranch/client-js`.
* **Running the service** — the configuration properties and environment variables, and upgrading an existing database. Database migrations run automatically on startup; a release that needs a manual step, or a changed or removed setting, is breaking.
* **The core Java library's public API** (`com.dialoguebranch:dlb-core-java`) — loading a project, running a dialogue (`ActiveDialogue`), the variable store and its change notifications, the dialogue protocol types (`DialogueMessage`, `DialogueStatement`, `ReplyMessage`), and the command-line tool.

## What is not covered (internal)

These parts exist to serve Dialogue Branch Studio. They may change in any release, without a **Breaking** label:

* **The editor end-points of the REST API** — the ones marked *internal* [in the table below](#rest-end-points).
* **`DialogueBranchAuthoringClient`** — the authoring client follows the end-points it wraps: a method for an automation end-point is covered, a method for an editor end-point is not.
* **Dialogue Branch Studio** itself, and the BFF's proxy and session end-points (`/api/**`, `/whoami`, `/login`).
* **The internals of the core Java library** — the parsed dialogue model (`Node`, `NodeHeader`, `NodeBody`, `ResolvedNodeBody`, `Reply`, the `Command` classes) and the parsers.
* **The database schema.** Only the upgrade path (migrations) is covered, not the table layout.

**Studio, the BFF and the Web Service are released together, under one version, and are only supported when run at the same version.** Mixing versions is not supported; that is what makes it safe for the internal end-points to change.

If you depend on something listed as internal, open an issue. If the use is realistic, we would rather promote it to the stable list than leave you building on something that may move.

## REST end-points

All end-points are under `/v1`, for example `/v1/dialogue/start`. *Stable* end-points are covered by this policy; *internal* ones are not.

| End-point | Status |
|---|---|
| `/dialogue/start`, `progress`, `continue`, `cancel`, `back`, `get-ongoing`, `list-dialogues` | Stable |
| `/variables/get`, `history`, `set`, `set-single` | Stable |
| `/variables/list-project`, `list-supported` | Internal (Studio's variable discovery) |
| `/log/get-session`, `verify-id` | Stable |
| `/info/all` | Stable |
| `/info/technical` | Internal (admin diagnostics) |
| `/auth/logout`, `validate` | Stable |
| `/project/list-projects`, `get-project`, `create-project`, `delete-project`, `export-project`, `import-project` | Stable |
| `/project/update-project`, `update-draft`, `add-translation-language`, `remove-translation-language`, `restore-translation-language`, `find-language-references` | Internal (Studio's *Configure Project*) |
| `/publish/list-versions`, `next-version`, `verify`, `create-version` | Stable |
| `/authoring/import-scripts`, `export-dialogue` | Stable |
| `/authoring/*`, all other end-points: dialogues, nodes, preamble, translations, `find-*-references` | Internal |
| `/draft/*` (running a dialogue against its draft) | Internal |
| `/users` | Internal (delegate-user lookup) |

The end-point groups do not line up with this split yet; [#418](https://github.com/dialoguebranch/platform/issues/418) discusses reorganising them. Until that is decided, this table is the authority. A reorganisation that moves a stable end-point is itself a **Breaking** change.

## How changes are recorded

The [Release Notes](/release-notes) use two markers:

* **Breaking:** — a change to a stable surface that can break an existing consumer.
* **Internal API:** — a change to an internal surface that a consumer of the stable surface cannot notice, for example "the authoring end-points exchange a node's header as tags". It says that Studio and the Web Service must be upgraded together.

Release notes up to and including 0.5.0 label every contract change as **Breaking**, including changes that would now be marked **Internal API:**. They are left as they were.

## Versions

While Dialogue Branch is below 1.0, a **Breaking** change raises the minor version (`0.5.0` → `0.6.0`). From 1.0, it raises the major version. A change marked **Internal API:** never raises the major version on its own.

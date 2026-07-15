# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to a single monorepo-wide version declared in `global.json`.

## [Unreleased]

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
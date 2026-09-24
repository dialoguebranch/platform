# Dialogue Branch Example Scripts
This directory holds the Dialogue Branch example projects used both to learn the `.dlb` language and
as real test fixtures for `packages/core`.

## Structure
Each subdirectory is a self-contained Dialogue Branch project (a `dlb-project.xml` plus one folder
per supported language).

- `project-test/` — the canonical, fully valid example project. It is kept byte-for-byte in sync
  with the Dialogue Branch Web Service's built-in `default-test` seed project (`apps/api` copies it
  in at build time), and exercises every feature of the `.dlb` language. Every dialogue in it parses
  with zero errors and zero warnings — it's a seed project, so it has to stay clean.
- `error-test/` — the deliberately broken counterpart, covering every parse error and warning
  `packages/core`'s parser can produce. It exists purely as a `packages/core` test fixture and is
  never used as a seed project or referenced by any other component.

## Lessons (in `project-test/en/`)
Each lesson is a short dialogue demonstrating one part of the language; start from `menu.dlb`.

 - `basic.dlb` — nodes, replies, auto-forward replies, ending a dialogue.
 - `statements.dlb` — variable injection, escaping, `//` comments, markup pass-through.
 - `variables.dlb` — the `set` command, expressions, arithmetic and string coercion, reply-attached `set`.
 - `conditionals.dlb` — `if` / `elseif` / `else`, comparison and boolean operators, nesting, conditional replies.
 - `random.dlb` — the `random` command, weighted clauses, nested commands.
 - `actions.dlb` — the `action` command (link / image / video / generic) and reply-attached actions.
 - `inputs.dlb` — the six `input` reply types and their parameters.
 - `external-variable-service.dlb` — retrieving variable values from an external service.
 - `poe.dlb`, `bg1/*` — longer, real-feeling showcase conversations.

# Dialogue Branch Example Scripts
This project contains examples of valid .dlb scripts that may be used to test your applications or
to learn how to use the Dialogue Branch Language.

## Structure
This project is structured like a Dialogue Branch project, so that the root contains folders for the
different supported languages. The `en` folder contains the .dlb scripts (in English); `nl-NL` and
`pt-PT` hold their translations.

`project-test/` is the canonical example project — it is kept byte-for-byte in sync with the
Dialogue Branch Web Service's built-in `default-test` seed project, and between them they exercise
every feature of the `.dlb` language. The loose scripts directly under `en/` are an older, smaller
set kept for existing tests.

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

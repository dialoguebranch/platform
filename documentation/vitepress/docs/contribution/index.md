# Contributing to Dialogue Branch

## Introduction

The Dialogue Branch Platform is developed in the open at [`dialoguebranch/platform`](https://github.com/dialoguebranch/platform), a monorepo containing the [Core Java Library](/core-java/) (`packages/core`), the [Web Service](/web-services/) (`apps/api`), and its Vue-based Web Client Test Application (`apps/web`). Bug reports, feature requests and pull requests are all welcome there.

The version for the entire monorepo is declared once, in `global.json` at the repository root — see the top-level `CHANGELOG.md` for what's changed recently, or unreleased.

## Code Style

Java source in this repository favours grouping a class's members into clearly labelled sections using a horizontal comment separator, e.g.:

```java
// -------------------------------------------------------- //
// -------------------- Constructor(s) -------------------- //
// -------------------------------------------------------- //
```

The top and bottom separator lines are made up of dashes matching the total width of the label line (label centered between two runs of exactly 20 dashes). The same pattern is used to mark out individual end-point handlers within a REST controller, e.g. `// -------------------- END-POINT: "/dialogue/start" -------------------- //` in `DialogueController`.

## Building and Testing

See the top-level `CLAUDE.md` file (and each component's own `README`, where present) for the exact Gradle/npm commands used to build and test each component — `./gradlew build`/`./gradlew test` for `packages/core` and `apps/api`, `npm run dev`/`npm run build` for `apps/web`.

::: info Note
If you found errors on this page, or have suggestions for what else should be covered here, please open an issue at https://github.com/dialoguebranch/platform or send an email to info@dialoguebranch.com.
:::

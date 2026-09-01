# Contributing to the Dialogue Branch Platform

Thanks for your interest in contributing. This document covers how to get set up,
the conventions this repository follows, and what a good issue or pull request
looks like.

By participating you are expected to uphold our [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug** or **request a feature** — open an
  [issue](https://github.com/dialoguebranch/platform/issues/new/choose) using the
  relevant template.
- **Improve the documentation** — both this repository and the docs hub
  (`documentation/vitepress/`, published at
  [dialoguebranch.com/docs](https://www.dialoguebranch.com/docs/)).
- **Fix a bug or build a feature** — see the open issues, especially those
  labelled [`good first issue`](https://github.com/dialoguebranch/platform/labels/good%20first%20issue)
  and [`help wanted`](https://github.com/dialoguebranch/platform/labels/help%20wanted).

For anything beyond a small, obvious fix, please open (or comment on) an issue
first so the approach can be agreed before you spend time on it.

**Security issues:** do not open a public issue. See [SECURITY.md](SECURITY.md).

## Repository layout

The platform is a monorepo of four components:

| Path | What it is |
|---|---|
| `packages/core` | Core Java library (`com.dialoguebranch`) — parses and executes `.dlb` scripts. Published to Maven Central as `com.dialoguebranch:dlb-core-java`. |
| `apps/api` | Spring Boot REST API (`/dlb-web-service/v1`) wrapping the core library, deployed as a WAR. |
| `apps/bff` | Backend-for-Frontend: performs the OAuth2 login for Studio and proxies its API calls, so the browser never holds a token. Executable JAR. |
| `apps/studio` | Vue 3 / Vite / Tailwind front-end ("Dialogue Branch Studio"). |

The single monorepo version lives in `global.json` at the root. `CLAUDE.md` and
the module `README.md` files carry the fuller architecture reference.

## Building and testing

All Gradle commands use the wrapper (`./gradlew`).

```bash
# Core library
cd packages/core && ./gradlew build          # compile + build
                    ./gradlew test            # run tests

# API service
cd apps/api && ./gradlew build

# BFF service
cd apps/bff && ./gradlew build

# Studio
cd apps/studio && npm install && npm run dev  # dev server, proxies to the BFF at :8082
                  npm run build               # production build
```

Docker image builds must run from the **repository root**, because the API build
context spans both `apps/api/` and `packages/core/`:

```bash
docker build -t dlb-web-service -f apps/api/Dockerfile .
docker build -t dlb-bff        -f apps/bff/Dockerfile .
```

The local stack (MariaDB + Keycloak, optionally the API and BFF) is defined in
`infrastructure/docker/compose.yml`:

```bash
docker compose -f infrastructure/docker/compose.yml up                # MariaDB + Keycloak
docker compose -f infrastructure/docker/compose.yml --profile api up  # + API + BFF
```

See the root [README.md](README.md) for a full local-development walkthrough.

## Conventions

### Branch names

`feature/<issue-number>-<short-slug>` — e.g. `feature/107-community-health`.

### Commit messages

`<area>: <imperative summary>`, where `<area>` is the module or facet the change
belongs to: `apps/api`, `apps/bff`, `apps/studio`, `packages/core`, `docs`,
`deps`, or similar. Keep the message functional — say what changed and why, not a
step-by-step of how. Reference the issue in the body where relevant.

### Developer Certificate of Origin (sign-off)

Contributions are accepted under the [Developer Certificate of Origin](https://developercertificate.org/).
Every commit must carry a `Signed-off-by` line matching the commit author, added
with:

```bash
git commit -s
```

This certifies that you wrote the change (or otherwise have the right to submit
it) and that it is contributed under the project's [MIT License](LICENSE). Amend
an existing commit with `git commit --amend -s`, or a whole branch with
`git rebase --signoff main`.

### Changelog

If a change has an effect a user or API consumer would notice — a new feature, a
bug fix, a behaviour change, a security fix, a breaking change — add an entry to
the `[Unreleased]` section of [CHANGELOG.md](CHANGELOG.md) in the existing
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) style: the right
category subheading, a reference to the issue, and a `**Breaking:**` note where
it applies. Skip the changelog for refactors, formatting, test-only changes, and
dependency bumps.

The project is pre-1.0 (`0.x`): a breaking change to a public contract rides a
normal minor bump and does not need to wait for a major version — but it must
still be called out clearly in the changelog.

### Code style

Match the conventions of the file and module you are editing — indentation,
naming, comment density, wrapping. A repo-root `.editorconfig` captures the
whitespace basics (tabs, width 4, for `.java` / `.gradle`).

The four JVM modules run [Spotless](https://github.com/diffplug/spotless) for
mechanical hygiene only: unused-import removal, import ordering, trailing
whitespace, final newline. There is **no** line-reflowing formatter — layout is
still hand-maintained. `spotlessCheck` runs as part of `check`, so a formatting
deviation fails the build; run `./gradlew spotlessApply` in the affected module
to fix it before committing.

## Pull requests

1. Fork the repository (or, for maintainers, branch from `main`).
2. Make your change on a `feature/<issue>-<slug>` branch, with signed-off commits.
3. Add or update tests, and a `CHANGELOG.md` entry if the change is user-visible.
4. Open a PR against `main`, fill in the template, and link the issue it closes.
5. Keep the PR focused on one logical change; open separate PRs for unrelated
   fixes you notice along the way.

A maintainer will review. Once continuous integration is in place
([#106](https://github.com/dialoguebranch/platform/issues/106)), all checks must
pass before merge.

## Questions

For anything that is not a bug report or feature request, email
`info@dialoguebranch.com`.

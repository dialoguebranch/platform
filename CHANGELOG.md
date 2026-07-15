# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to a single monorepo-wide version declared in `global.json`.

## [Unreleased]

### Fixed

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

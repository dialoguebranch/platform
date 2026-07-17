# Dialogue Branch Web Services: Release Notes

## Release Notes

Since the Dialogue Branch Platform was consolidated into a single monorepo ([`dialoguebranch/platform`](https://github.com/dialoguebranch/platform)), the Web Service no longer has its own independent version — it shares one version, declared once in `global.json`, with the Core Java Library and the Web Client. Development occurs on the [main](https://github.com/dialoguebranch/platform/tree/main) branch; stable versions are tagged `vX.Y.Z` on that same repository.

Detailed, up-to-date release notes for every component (Web Service, Core Java Library, Web Client) are maintained together in a single changelog, rather than duplicated across these documentation pages where they would inevitably drift out of sync:

* [`CHANGELOG.md`](https://github.com/dialoguebranch/platform/blob/main/CHANGELOG.md) on the `main` branch, for unreleased and historical changes.
* [GitHub Releases](https://github.com/dialoguebranch/platform/releases) for the notes published alongside each tagged version.

::: info Note
Versions prior to `v2.0.0` were released from three separate repositories (`dlb-web`, `dlb-core-java`, `dlb-documentation`) that have since been merged into this monorepo — their historical release notes may still be found on those original repositories under the `dialoguebranch` GitHub organisation.
:::

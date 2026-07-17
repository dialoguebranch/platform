# Dialogue Branch Core Java Library: Release Notes

## Release Notes

Since the Dialogue Branch Platform was consolidated into a single monorepo ([`dialoguebranch/platform`](https://github.com/dialoguebranch/platform)), the Core Java Library no longer has its own independent version — it shares one version, declared once in `global.json`, with the Web Service and the Web Client. Development occurs on the [main](https://github.com/dialoguebranch/platform/tree/main) branch; stable versions are tagged `vX.Y.Z` on that same repository and published to Maven Central as `com.dialoguebranch:dlb-core-java`.

Detailed, up-to-date release notes for every component (Core Java Library, Web Service, Web Client) are maintained together in a single changelog, rather than duplicated across these documentation pages where they would inevitably drift out of sync:

* [`CHANGELOG.md`](https://github.com/dialoguebranch/platform/blob/main/CHANGELOG.md) on the `main` branch, for unreleased and historical changes.
* [GitHub Releases](https://github.com/dialoguebranch/platform/releases) for the notes published alongside each tagged version.

::: info Note
Versions prior to `v2.0.0` were released from three separate repositories (`dlb-web`, `dlb-core-java`, `dlb-documentation`) that have since been merged into this monorepo — their historical release notes may still be found on those original repositories under the `dialoguebranch` GitHub organisation.
:::

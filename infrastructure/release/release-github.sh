#!/usr/bin/env bash
#
# Cuts a release of the Dialogue Branch Platform, in two steps because `main`'s
# branch ruleset requires the "all-green" status check on every commit,
# including a release commit, so nothing can be pushed to `main` directly:
#
#   1. Prepare (default): asks whether this is a major, minor, or patch
#      release, bumps /global.json accordingly, splits the CHANGELOG's
#      "Unreleased" section into a dated version section, commits on a new
#      release/vX.Y.Z branch, pushes it, and opens a PR against main.
#   2. Finish (--finish): run after that PR has merged. Reads the
#      already-bumped version from /global.json, tags "vX.Y.Z", moves the
#      floating "latest" tag to match, pushes both, and creates the
#      corresponding GitHub release (via `gh`).
#
# Usage:
#   infrastructure/release/release-github.sh            # step 1: open the release PR
#   infrastructure/release/release-github.sh --finish    # step 2: tag + publish, after merge

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

CHANGELOG="CHANGELOG.md"
GLOBAL_JSON="global.json"

command -v gh >/dev/null || { echo "error: 'gh' (GitHub CLI) is required" >&2; exit 1; }

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BRANCH" != "main" ]]; then
	echo "error: run this from 'main' (currently on '$BRANCH')" >&2
	exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
	echo "error: working tree is not clean; commit or stash your changes first" >&2
	exit 1
fi

git fetch origin main --tags --quiet
if [[ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
	echo "error: local 'main' is not in sync with 'origin/main'; pull/push first" >&2
	exit 1
fi

VERSION="$(python3 -c "import json; print(json.load(open('$GLOBAL_JSON'))['version'])")"
if [[ -z "$VERSION" ]]; then
	echo "error: could not read version from $GLOBAL_JSON" >&2
	exit 1
fi
TAG="v${VERSION}"

if [[ "${1:-}" == "--finish" ]]; then
	# global.json's version was already bumped by the merged release PR; this step
	# only tags and publishes what's already on main, so it never touches main itself
	# (a tag push isn't a branch push, and isn't covered by the branch ruleset).
	if git rev-parse "$TAG" >/dev/null 2>&1 || git ls-remote --tags origin "refs/tags/$TAG" | grep -q .; then
		echo "error: tag '$TAG' already exists locally or on origin" >&2
		exit 1
	fi

	if ! grep -q "^## \[${VERSION}\]" "$CHANGELOG"; then
		echo "error: no '## [${VERSION}]' heading found in $CHANGELOG — has the release PR been merged?" >&2
		exit 1
	fi

	NOTES_FILE="$(mktemp)"
	trap 'rm -f "$NOTES_FILE"' EXIT
	awk -v heading="## [${VERSION}]" '
		index($0, heading) == 1 { found = 1; next }
		found && /^## \[/ { exit }
		found { print }
	' "$CHANGELOG" > "$NOTES_FILE"

	echo "About to tag and publish ${TAG} from the current main ($(git rev-parse --short HEAD)):"
	echo "--------------------------------"
	echo "Release notes (from CHANGELOG.md):"
	echo "--------------------------------"
	cat "$NOTES_FILE"
	echo "--------------------------------"
	read -r -p "Tag, push, and publish this release? [y/N] " CONFIRM || CONFIRM=""
	if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
		echo "Aborted."
		exit 1
	fi

	git tag -a "$TAG" -m "$TAG"

	# "latest" is a floating tag: drop any existing one (local + remote) and
	# recreate it pointing at the commit (not the tag object — see `^{}`).
	git tag -d latest >/dev/null 2>&1 || true
	git push origin :refs/tags/latest >/dev/null 2>&1 || true
	git tag -a latest -m "Latest release (${TAG})" "${TAG}^{}"

	git push origin "$TAG"
	git push origin latest

	gh release create "$TAG" --title "$TAG" --latest --notes-file "$NOTES_FILE"

	echo "Released ${TAG}."
	exit 0
fi

if ! grep -q "^## \[Unreleased\]" "$CHANGELOG"; then
	echo "error: no '## [Unreleased]' heading found in $CHANGELOG" >&2
	exit 1
fi

echo "Current version: ${VERSION}"
while true; do
	# `read` exits non-zero on EOF (e.g. stdin closed/piped dry, not just an interactive
	# Ctrl+D) — under `set -e` that would otherwise kill the script right here, skipping
	# the message below. Nothing has touched disk yet at this point, so a plain exit is safe.
	if ! read -r -p "Release type — major, minor, or patch? " BUMP_TYPE; then
		echo "No input received; aborting." >&2
		exit 1
	fi
	BUMP_TYPE="$(echo "$BUMP_TYPE" | tr '[:upper:]' '[:lower:]')"
	case "$BUMP_TYPE" in
		major|minor|patch) break ;;
		*) echo "Please enter 'major', 'minor', or 'patch'." ;;
	esac
done

IFS='.' read -r CUR_MAJOR CUR_MINOR CUR_PATCH <<< "$VERSION"
case "$BUMP_TYPE" in
	major) NEW_VERSION="$((CUR_MAJOR + 1)).0.0" ;;
	minor) NEW_VERSION="${CUR_MAJOR}.$((CUR_MINOR + 1)).0" ;;
	patch) NEW_VERSION="${CUR_MAJOR}.${CUR_MINOR}.$((CUR_PATCH + 1))" ;;
esac
NEW_TAG="v${NEW_VERSION}"
RELEASE_BRANCH="release/${NEW_TAG}"

if git rev-parse "$NEW_TAG" >/dev/null 2>&1 || git ls-remote --tags origin "refs/tags/$NEW_TAG" | grep -q .; then
	echo "error: tag '$NEW_TAG' already exists locally or on origin" >&2
	exit 1
fi
if git ls-remote --exit-code --heads origin "$RELEASE_BRANCH" >/dev/null 2>&1; then
	echo "error: branch '$RELEASE_BRANCH' already exists on origin" >&2
	exit 1
fi

git checkout -b "$RELEASE_BRANCH"

python3 -c "
import json
path = '$GLOBAL_JSON'
with open(path) as f:
    data = json.load(f)
data['version'] = '$NEW_VERSION'
with open(path, 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
"

command -v npm >/dev/null || { echo "error: 'npm' is required to sync apps/studio/package.json" >&2; exit 1; }
(cd apps/studio && npm run --silent sync-version)

RELEASE_DATE="$(date +%F)"

# Insert a new dated version heading right after the Unreleased heading, leaving
# Unreleased itself empty for the next round of changes.
awk -v version="$NEW_VERSION" -v date="$RELEASE_DATE" '
	/^## \[Unreleased\]/ && !done {
		print
		print ""
		print "## [" version "] - " date
		done = 1
		next
	}
	{ print }
' "$CHANGELOG" > "$CHANGELOG.tmp"
mv "$CHANGELOG.tmp" "$CHANGELOG"

# Everything between the new version heading and the next "## [" heading (or EOF)
# becomes the release PR's body and, later, the GitHub release notes.
NOTES_FILE="$(mktemp)"
trap 'rm -f "$NOTES_FILE"' EXIT
awk -v heading="## [${NEW_VERSION}]" '
	index($0, heading) == 1 { found = 1; next }
	found && /^## \[/ { exit }
	found { print }
' "$CHANGELOG" > "$NOTES_FILE"

STUDIO_PACKAGE_JSON="apps/studio/package.json"
STUDIO_PACKAGE_LOCK="apps/studio/package-lock.json"

echo "About to open a release PR for ${NEW_TAG} (${BUMP_TYPE} bump from ${VERSION}):"
echo "--------------------------------"
git --no-pager diff -- "$GLOBAL_JSON" "$CHANGELOG" "$STUDIO_PACKAGE_JSON" "$STUDIO_PACKAGE_LOCK"
echo "--------------------------------"
echo "Release notes (from CHANGELOG.md):"
echo "--------------------------------"
cat "$NOTES_FILE"
echo "--------------------------------"
# `read` exits non-zero on EOF — under `set -e` that would otherwise kill the script right
# here, skipping the abort-and-revert below and leaving the release branch checked out with
# uncommitted changes. Treat EOF the same as an explicit non-'y' answer.
read -r -p "Commit, push, and open the release PR? [y/N] " CONFIRM || CONFIRM=""
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
	echo "Aborted; switching back to main and discarding the release branch."
	git checkout main
	git branch -D "$RELEASE_BRANCH"
	exit 1
fi

git add "$GLOBAL_JSON" "$CHANGELOG" "$STUDIO_PACKAGE_JSON" "$STUDIO_PACKAGE_LOCK"
git commit -m "Release ${NEW_TAG}"
git push -u origin "$RELEASE_BRANCH"

gh pr create --base main --head "$RELEASE_BRANCH" --title "Release ${NEW_TAG}" --body-file "$NOTES_FILE"

git checkout main

echo "Opened the release PR for ${NEW_TAG}. Once it's merged and CI is green, run:"
echo "  infrastructure/release/release-github.sh --finish"
echo "from a synced main to tag and publish the release."

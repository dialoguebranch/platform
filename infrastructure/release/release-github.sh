#!/usr/bin/env bash
#
# Cuts a release of the Dialogue Branch Platform: asks whether this is a
# major, minor, or patch release, bumps /global.json accordingly, splits the
# CHANGELOG's "Unreleased" section into a dated version section, commits,
# tags "vX.Y.Z", moves the floating "latest" tag to match, pushes everything
# to origin, and creates the corresponding GitHub release (via `gh`).
#
# Usage: infrastructure/release/release-github.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

CHANGELOG="CHANGELOG.md"
GLOBAL_JSON="global.json"
CURRENT_VERSION="$(python3 -c "import json; print(json.load(open('$GLOBAL_JSON'))['version'])")"

if [[ -z "$CURRENT_VERSION" ]]; then
	echo "error: could not read version from $GLOBAL_JSON" >&2
	exit 1
fi

command -v gh >/dev/null || { echo "error: 'gh' (GitHub CLI) is required" >&2; exit 1; }

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BRANCH" != "main" ]]; then
	echo "error: releases must be cut from 'main' (currently on '$BRANCH')" >&2
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

if ! grep -q "^## \[Unreleased\]" "$CHANGELOG"; then
	echo "error: no '## [Unreleased]' heading found in $CHANGELOG" >&2
	exit 1
fi

echo "Current version: ${CURRENT_VERSION}"
while true; do
	read -r -p "Release type — major, minor, or patch? " BUMP_TYPE
	BUMP_TYPE="$(echo "$BUMP_TYPE" | tr '[:upper:]' '[:lower:]')"
	case "$BUMP_TYPE" in
		major|minor|patch) break ;;
		*) echo "Please enter 'major', 'minor', or 'patch'." ;;
	esac
done

IFS='.' read -r CUR_MAJOR CUR_MINOR CUR_PATCH <<< "$CURRENT_VERSION"
case "$BUMP_TYPE" in
	major) VERSION="$((CUR_MAJOR + 1)).0.0" ;;
	minor) VERSION="${CUR_MAJOR}.$((CUR_MINOR + 1)).0" ;;
	patch) VERSION="${CUR_MAJOR}.${CUR_MINOR}.$((CUR_PATCH + 1))" ;;
esac
TAG="v${VERSION}"

if git rev-parse "$TAG" >/dev/null 2>&1 || git ls-remote --tags origin "refs/tags/$TAG" | grep -q .; then
	echo "error: tag '$TAG' already exists locally or on origin" >&2
	exit 1
fi

python3 -c "
import json
path = '$GLOBAL_JSON'
with open(path) as f:
    data = json.load(f)
data['version'] = '$VERSION'
with open(path, 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
"

RELEASE_DATE="$(date +%F)"

# Insert a new dated version heading right after the Unreleased heading, leaving
# Unreleased itself empty for the next round of changes.
awk -v version="$VERSION" -v date="$RELEASE_DATE" '
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
# becomes the GitHub release notes body.
NOTES_FILE="$(mktemp)"
trap 'rm -f "$NOTES_FILE"' EXIT
awk -v heading="## [${VERSION}]" '
	$0 ~ "^" heading { found = 1; next }
	found && /^## \[/ { exit }
	found { print }
' "$CHANGELOG" > "$NOTES_FILE"

echo "About to release ${TAG} (${BUMP_TYPE} bump from ${CURRENT_VERSION}):"
echo "--------------------------------"
git --no-pager diff -- "$GLOBAL_JSON" "$CHANGELOG"
echo "--------------------------------"
echo "Release notes (from CHANGELOG.md):"
echo "--------------------------------"
cat "$NOTES_FILE"
echo "--------------------------------"
read -r -p "Commit, tag, push, and publish this release? [y/N] " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
	echo "Aborted; reverting $GLOBAL_JSON and $CHANGELOG."
	git checkout -- "$GLOBAL_JSON" "$CHANGELOG"
	exit 1
fi

git add "$GLOBAL_JSON" "$CHANGELOG"
git commit -m "Release ${TAG}"
git tag -a "$TAG" -m "$TAG"

# "latest" is a floating tag: drop any existing one (local + remote) and
# recreate it pointing at the commit (not the tag object — see `^{}`).
git tag -d latest >/dev/null 2>&1 || true
git push origin :refs/tags/latest >/dev/null 2>&1 || true
git tag -a latest -m "Latest release (${TAG})" "${TAG}^{}"

git push origin main
git push origin "$TAG"
git push origin latest

gh release create "$TAG" --title "$TAG" --latest --notes-file "$NOTES_FILE"

echo "Released ${TAG}."

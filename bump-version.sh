#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

usage() {
  echo "Usage: $0 <major|minor|patch>" >&2
  exit 1
}

fail() {
  echo "Error: $1" >&2
  exit 1
}

[ $# -eq 1 ] || usage
BUMP="$1"
case "$BUMP" in
  major|minor|patch) ;;
  *) usage ;;
esac

BRANCH=$(git rev-parse --abbrev-ref HEAD)
[ "$BRANCH" = "main" ] || fail "releases must be cut from 'main' (currently on '$BRANCH')"

[ -z "$(git status --porcelain)" ] || fail "working tree is not clean; commit or stash your changes first"

git fetch --quiet --tags origin main || fail "could not fetch from origin; releases need up-to-date remote state"
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || fail "local main is not in sync with origin/main; pull (or push) first"

VERSION_FILE="version.properties"
README_FILE="README.md"
DEP_RE='com\.lingohub:android-cdn-sdk:[0-9A-Za-z.+-]*'

VERSION=$(grep '^VERSION_NAME=' "$VERSION_FILE" | cut -d= -f2) || fail "could not read VERSION_NAME from $VERSION_FILE"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "unexpected VERSION_NAME '$VERSION' in $VERSION_FILE"
IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"

case "$BUMP" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
TAG="v$NEW_VERSION"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  fail "tag '$TAG' already exists"
fi

# Both dependency snippets (Kotlin and Groovy DSL) must exist and carry the
# expected version, otherwise the docs have drifted and need a manual fix.
check_readme_snippets() {
  local expected="com.lingohub:android-cdn-sdk:$1"
  local found count snippet
  found=$(grep -oE "$DEP_RE" "$README_FILE" || true)
  count=$(printf '%s\n' "$found" | grep -c .) || true
  [ "$count" -eq 2 ] || fail "expected exactly 2 dependency snippets in $README_FILE, found $count"
  while IFS= read -r snippet; do
    [ "$snippet" = "$expected" ] || fail "$README_FILE snippet '$snippet' does not match expected '$expected'"
  done <<< "$found"
}

check_readme_snippets "$VERSION"

echo "VERSION_NAME=$NEW_VERSION" > "$VERSION_FILE"
sed "s#$DEP_RE#com.lingohub:android-cdn-sdk:$NEW_VERSION#g" "$README_FILE" > "$README_FILE.tmp"
mv "$README_FILE.tmp" "$README_FILE"

check_readme_snippets "$NEW_VERSION"

git add "$VERSION_FILE" "$README_FILE"
git commit -m "Bump version to $NEW_VERSION"
git tag "$TAG"
git push --atomic origin main "refs/tags/$TAG"

echo "Released $NEW_VERSION: pushed release commit and tag $TAG to origin."

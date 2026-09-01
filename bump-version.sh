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

BUMP="${1:-}"
case "$BUMP" in
  major|minor|patch) ;;
  *) usage ;;
esac

BRANCH=$(git rev-parse --abbrev-ref HEAD)
[ "$BRANCH" = "main" ] || fail "releases must be cut from 'main' (currently on '$BRANCH')"

[ -z "$(git status --porcelain)" ] || fail "working tree is not clean; commit or stash your changes first"

VERSION_FILE="version.properties"
README_FILE="README.md"

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

grep -qF 'com.lingohub:android-cdn-sdk:' "$README_FILE" || fail "no dependency snippet found in $README_FILE"

echo "VERSION_NAME=$NEW_VERSION" > "$VERSION_FILE"
sed "s#com\.lingohub:android-cdn-sdk:[0-9A-Za-z.+-]*#com.lingohub:android-cdn-sdk:$NEW_VERSION#g" "$README_FILE" > "$README_FILE.tmp"
mv "$README_FILE.tmp" "$README_FILE"

git add "$VERSION_FILE" "$README_FILE"
git commit -m "Bump version to $NEW_VERSION"
git tag "$TAG"
git push origin main "refs/tags/$TAG"

echo "Released $NEW_VERSION: pushed release commit and tag $TAG to origin."

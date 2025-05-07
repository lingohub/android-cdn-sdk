#!/bin/bash
set -e

VERSION_FILE="version.properties"
VERSION=$(grep VERSION_NAME $VERSION_FILE | cut -d= -f2)
IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"

case "$1" in
  major) ((MAJOR++)); MINOR=0; PATCH=0 ;;
  minor) ((MINOR++)); PATCH=0 ;;
  patch|*) ((PATCH++)) ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
echo "VERSION_NAME=$NEW_VERSION" > $VERSION_FILE

git add $VERSION_FILE
git commit -m "Bump version to $NEW_VERSION"
git tag "v$NEW_VERSION"
git push origin main --tags
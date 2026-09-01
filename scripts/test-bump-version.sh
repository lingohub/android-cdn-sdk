#!/bin/bash
# Integration tests for bump-version.sh.
#
# Runs the release script inside a throwaway clone wired to a local bare
# repository acting as origin — no network access, nothing real is pushed.
# The repository's actual version.properties and README.md are used as test
# data, so the suite also catches drift in those files.
set -euo pipefail

SDK_ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

PASS_COUNT=0

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "PASS: $1"
}

fail_test() {
  echo "FAIL: $1" >&2
  exit 1
}

expect_fail() { # <description> <expected message substring> [script args...]
  local desc="$1" want="$2" out rc=0
  shift 2
  out=$(./bump-version.sh "$@" 2>&1) || rc=$?
  [ "$rc" -ne 0 ] || fail_test "$desc — expected non-zero exit, got success: $out"
  case "$out" in
    *"$want"*) pass "$desc" ;;
    *) fail_test "$desc — output missing '$want': $out" ;;
  esac
}

assert_untouched() { # <description> <expected HEAD sha>
  [ -z "$(git status --porcelain)" ] || fail_test "$1 — left the working tree dirty"
  [ "$(git rev-parse HEAD)" = "$2" ] || fail_test "$1 — created a commit"
}

current_version() {
  grep '^VERSION_NAME=' version.properties | cut -d= -f2
}

next_patch() {
  local v a b c
  v=$(current_version)
  IFS=. read -r a b c <<< "$v"
  echo "$a.$b.$((c + 1))"
}

# --- fixture: bare origin + work clone with the real release files ----------

cd "$TMP"
git init -q --bare --initial-branch=main origin.git

git clone -q "$TMP/origin.git" work 2>/dev/null
cd work
git config user.email test@example.com
git config user.name "Bump Test"
git checkout -q -B main
cp "$SDK_ROOT/bump-version.sh" "$SDK_ROOT/version.properties" "$SDK_ROOT/README.md" .
git add -A
git commit -qm "init"
git push -qu origin main 2>/dev/null

git clone -q "$TMP/origin.git" ../other
git -C ../other config user.email other@example.com
git -C ../other config user.name "Other Clone"

BASE_HEAD=$(git rev-parse HEAD)

# --- argument validation ----------------------------------------------------

expect_fail "rejects missing argument" "Usage"
expect_fail "rejects trailing argument" "Usage" patch extra
expect_fail "rejects unknown argument" "Usage" banana
assert_untouched "argument guards" "$BASE_HEAD"

# --- local state guards -----------------------------------------------------

git checkout -q -b feature
expect_fail "rejects non-main branch" "must be cut from 'main'" patch
git checkout -q main
git branch -q -D feature

touch junk.txt
expect_fail "rejects dirty working tree" "not clean" patch
rm junk.txt

# --- remote state guards ----------------------------------------------------

(
  cd ../other
  git pull -q --ff-only
  echo x > concurrent.txt
  git add concurrent.txt
  git commit -qm "concurrent commit"
  git push -q origin main
)
expect_fail "rejects local main that is behind origin" "not in sync" patch
assert_untouched "stale main guard" "$BASE_HEAD"
git pull -q --ff-only origin main
BASE_HEAD=$(git rev-parse HEAD)

NEXT=$(next_patch)
(
  cd ../other
  git pull -q --ff-only
  git tag "v$NEXT"
  git push -q origin "refs/tags/v$NEXT"
)
expect_fail "rejects tag that exists only on origin" "already exists" patch
assert_untouched "remote tag guard" "$BASE_HEAD"
git push -q origin ":refs/tags/v$NEXT"
git tag -d "v$NEXT" >/dev/null

git tag "v$NEXT"
expect_fail "rejects tag that exists locally" "already exists" patch
git tag -d "v$NEXT" >/dev/null

# --- README snippet assertions ----------------------------------------------

sed "s#android-cdn-sdk:$(current_version)'#android-cdn-sdk:9.9.9'#" README.md > README.tmp
mv README.tmp README.md
git commit -qam "drift one snippet"
git push -q origin main
expect_fail "rejects README snippet version drift" "does not match expected" patch
git revert --no-edit HEAD >/dev/null
git push -q origin main

grep -v "com.lingohub:android-cdn-sdk:" README.md > README.tmp
mv README.tmp README.md
git commit -qam "drop both snippets"
git push -q origin main
expect_fail "rejects README with missing snippets" "expected exactly 2 dependency snippets" patch
git revert --no-edit HEAD >/dev/null
git push -q origin main
BASE_HEAD=$(git rev-parse HEAD)

# --- happy path: patch ------------------------------------------------------

NEXT=$(next_patch)
git tag decoy-tag
out=$(./bump-version.sh patch 2>&1) || fail_test "patch release failed: $out"
[ "$(current_version)" = "$NEXT" ] || fail_test "patch bump produced $(current_version), expected $NEXT"
[ "$(git log -1 --format=%s)" = "Bump version to $NEXT" ] || fail_test "unexpected commit message: $(git log -1 --format=%s)"
[ "$(git show --name-only --format= HEAD | sort | tr '\n' ' ')" = "README.md version.properties " ] || fail_test "release commit should touch exactly README.md and version.properties"
[ "$(grep -c "com.lingohub:android-cdn-sdk:$NEXT" README.md)" -eq 2 ] || fail_test "README snippets not updated to $NEXT"
git ls-remote --exit-code origin "refs/tags/v$NEXT" >/dev/null || fail_test "tag v$NEXT not pushed to origin"
[ "$(git ls-remote origin refs/heads/main | cut -f1)" = "$(git rev-parse HEAD)" ] || fail_test "release commit not pushed to origin/main"
! git ls-remote --exit-code origin refs/tags/decoy-tag >/dev/null 2>&1 || fail_test "unrelated local tag was pushed"
git tag -d decoy-tag >/dev/null
pass "patch release commits, tags, and pushes exactly the release refs"

# --- atomic push: a rejected branch update must not publish the tag ---------

NEXT=$(next_patch)
PRE_MAIN=$(git ls-remote origin refs/heads/main | cut -f1)
cat > "$TMP/origin.git/hooks/update" <<'EOF'
#!/bin/sh
if [ "$1" = "refs/heads/main" ]; then exit 1; fi
exit 0
EOF
chmod +x "$TMP/origin.git/hooks/update"
rc=0
out=$(./bump-version.sh patch 2>&1) || rc=$?
[ "$rc" -ne 0 ] || fail_test "atomic push test — push unexpectedly succeeded"
! git ls-remote --exit-code origin "refs/tags/v$NEXT" >/dev/null 2>&1 || fail_test "rejected main update still published tag v$NEXT (push not atomic)"
[ "$(git ls-remote origin refs/heads/main | cut -f1)" = "$PRE_MAIN" ] || fail_test "atomic push test — origin/main moved"
pass "rejected push publishes neither the commit nor the tag"
rm "$TMP/origin.git/hooks/update"
git tag -d "v$NEXT" >/dev/null
git reset -q --hard origin/main

# --- happy paths: minor and major -------------------------------------------

V=$(current_version)
IFS=. read -r A B C <<< "$V"
EXPECT="$A.$((B + 1)).0"
out=$(./bump-version.sh minor 2>&1) || fail_test "minor release failed: $out"
[ "$(current_version)" = "$EXPECT" ] || fail_test "minor bump produced $(current_version), expected $EXPECT"
git ls-remote --exit-code origin "refs/tags/v$EXPECT" >/dev/null || fail_test "tag v$EXPECT not pushed to origin"
pass "minor release happy path"

V=$(current_version)
IFS=. read -r A B C <<< "$V"
EXPECT="$((A + 1)).0.0"
out=$(./bump-version.sh major 2>&1) || fail_test "major release failed: $out"
[ "$(current_version)" = "$EXPECT" ] || fail_test "major bump produced $(current_version), expected $EXPECT"
git ls-remote --exit-code origin "refs/tags/v$EXPECT" >/dev/null || fail_test "tag v$EXPECT not pushed to origin"
pass "major release happy path"

echo
echo "All $PASS_COUNT tests passed."

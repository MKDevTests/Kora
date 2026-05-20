#!/bin/bash
# Pre-release sanity checks. Run before (and ideally as the first step of)
# release-kora.sh. Catches the recurring footguns that have eaten release
# cycles in the past:
#
#   - Last SQL migration not registered in AppMigrations.kt's hardcoded
#     list (V61 burned us once; V62 was a near-miss).
#   - JNI libs missing in a fresh worktree (libsqlitejdbc / libvips).
#   - Tag already published on GitHub (the v1.0.1 / v1.0.2 dance).
#   - Working tree dirty under the CRLF + submodule noise typical of
#     Windows / WSL checkouts on /mnt/c.
#
# Exit codes:
#   0 — ready to release
#   1 — at least one blocking problem
#   2 — usage error
#
# Usage:
#   ./scripts/preflight.sh <version>     # e.g. 1.0.3
#
# Most checks are belt-and-braces with release-kora.sh: that script also
# enforces branch + clean tree + tag uniqueness. Preflight adds the new
# checks (JNI, migration registry, optional TODO/FIXME scan) and is also
# usable standalone, e.g. before opening a PR.

set -uo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 <version>"
    echo "  Example: $0 1.0.3"
    exit 2
fi
VERSION="${VERSION#v}"
TAG="v$VERSION"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

PROBLEMS=0
fail() { echo "  ✗ $1"; PROBLEMS=$((PROBLEMS+1)); }
pass() { echo "  ✓ $1"; }
warn() { echo "  ! $1"; }

echo "==> Preflight checks for v$VERSION"

# ----- 1. branch -----
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
if [[ "$CURRENT_BRANCH" == "main" ]]; then
    pass "on branch 'main' (current: $CURRENT_BRANCH)"
else
    fail "must be on 'main' branch (current: $CURRENT_BRANCH)"
fi

# ----- 2. working tree clean (CRLF + submodules tolerated like release-kora.sh) -----
if git diff --quiet --ignore-cr-at-eol --ignore-submodules=all \
   && git diff --cached --quiet --ignore-cr-at-eol --ignore-submodules=all; then
    pass "working tree clean (ignoring CRLF + submodules)"
else
    fail "working tree has uncommitted changes"
    git -c core.fileMode=false status --short --ignore-submodules=all | sed 's/^/      /' >&2
fi

# ----- 3. tag uniqueness on origin -----
if git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null | grep -q "$TAG"; then
    fail "tag $TAG already exists on origin (MKDevTests/Kora). Pick a new version."
else
    pass "tag $TAG is available on origin"
fi

# ----- 4. local.properties present (Android SDK path) -----
if [[ -f local.properties ]]; then
    pass "local.properties present"
else
    fail "local.properties missing (Android SDK path unconfigured)"
fi

# ----- 5. JNI libs present (the worktree-setup footgun) -----
SQLITE_LIB="komelia-infra/database/sqlite/src/androidMain/jniLibs/arm64-v8a/libsqlitejdbc.so"
VIPS_LIB="komelia-infra/jni/src/androidMain/jniLibs/arm64-v8a/libvips.so"
if [[ -f "$SQLITE_LIB" ]]; then
    pass "libsqlitejdbc.so present"
else
    fail "libsqlitejdbc.so missing → run build-kora-debug.sh or scripts/_ensure_jni_libs.sh"
fi
if [[ -f "$VIPS_LIB" ]]; then
    pass "libvips.so present"
else
    fail "libvips.so missing → run build-kora-debug.sh or scripts/_ensure_jni_libs.sh"
fi

# ----- 6. latest SQL migration registered in AppMigrations.kt -----
# Flyway migrations are NOT discovered from the filesystem; AppMigrations.kt
# holds a hardcoded list. A new V##__*.sql file that isn't in the list ships
# silently and crashes the app at first run with "no such column".
MIGRATIONS_DIR="komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app"
MIGRATIONS_INDEX="komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/migrations/AppMigrations.kt"
LATEST_MIGRATION="$(ls "$MIGRATIONS_DIR"/V*.sql 2>/dev/null | sort -V | tail -1 | xargs -r basename)"
if [[ -z "$LATEST_MIGRATION" ]]; then
    fail "no V*.sql migrations found under $MIGRATIONS_DIR"
elif grep -q "\"$LATEST_MIGRATION\"" "$MIGRATIONS_INDEX"; then
    pass "latest migration $LATEST_MIGRATION is registered in AppMigrations.kt"
else
    fail "$LATEST_MIGRATION exists on disk but is NOT in AppMigrations.kt — add it to the migrations list"
fi

# ----- 7. AppVersion.kt vs gradle/libs.versions.toml consistency -----
APP_VERSION_KT="komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt"
VERSIONS_TOML="gradle/libs.versions.toml"
KT_VERSION="$(grep -oE "AppVersion\([0-9]+, [0-9]+, [0-9]+\)" "$APP_VERSION_KT" 2>/dev/null \
    | sed -E 's/AppVersion\(([0-9]+), ([0-9]+), ([0-9]+)\)/\1.\2.\3/' | head -1)"
TOML_VERSION="$(grep -E '^app-version' "$VERSIONS_TOML" 2>/dev/null | sed -E 's/.*"(.*)"/\1/' | head -1)"
if [[ -n "$KT_VERSION" && -n "$TOML_VERSION" && "$KT_VERSION" == "$TOML_VERSION" ]]; then
    pass "AppVersion.kt ($KT_VERSION) matches libs.versions.toml ($TOML_VERSION)"
else
    fail "version mismatch — AppVersion.kt: '$KT_VERSION', libs.versions.toml: '$TOML_VERSION' (release-kora.sh will bump both, but they should start aligned)"
fi

# ----- 8. (informational) TODO/FIXME/XXX added since last tag -----
# Not a blocker — just surfaces leftover markers in case the user wants
# to clean them up before tagging.
LAST_TAG="$(git tag --sort=-v:refname 2>/dev/null | head -1)"
if [[ -n "$LAST_TAG" ]]; then
    MARKERS="$(git diff "$LAST_TAG"..HEAD -- '*.kt' '*.kts' 2>/dev/null \
        | grep -E '^\+' | grep -cE '\b(TODO|FIXME|XXX)\b' || true)"
    if [[ "$MARKERS" -gt 0 ]]; then
        warn "$MARKERS TODO/FIXME/XXX line(s) added in .kt files since $LAST_TAG (informational, not blocking)"
    else
        pass "no new TODO/FIXME/XXX in .kt files since $LAST_TAG"
    fi
fi

echo ""
if [[ $PROBLEMS -gt 0 ]]; then
    echo "==> $PROBLEMS blocking problem(s). Fix before releasing v$VERSION." >&2
    exit 1
fi
echo "==> All checks passed. Ready to release v$VERSION."

#!/bin/bash
# Cut a Kora release and publish it on github.com/MKDevTests/Sipurra so the
# in-app auto-updater can pick it up.
#
# Steps performed (in order):
#   1. Bump app-version in libs.versions.toml.
#   2. Bump AppVersion.current in komelia-domain/.../AppVersion.kt.
#   3. Build a signed release APK (delegates to build-kora-release.sh; the
#      version baked into the APK now matches the tag).
#   4. Commit the version bump on the current branch (must be myversion).
#   5. Tag the commit with v<version>.
#   6. Push the branch + tag to origin.
#   7. Create a GitHub release on MKDevTests/Sipurra with the signed APK
#      attached.
#
# If any step fails, the version bump is reverted (and the local tag, if it
# was created, is deleted) so the working tree is clean for a retry.
#
# Usage:
#   ./scripts/release-kora.sh <version> [notes-or-path]
#
#     <version>        Semantic version like 2.3.0 (no 'v' prefix — the
#                      script adds it for the tag).
#     [notes-or-path]  Optional. Either a path to a release-notes file, or
#                      the notes string directly. If omitted, gh will open
#                      $EDITOR for the notes.
#
# Requirements:
#   - Must be on branch 'myversion' with a clean working tree.
#   - GitHub CLI (gh) installed and authenticated to push to MKDevTests/Sipurra.
#   - Same Android SDK / debug.keystore setup that build-kora-release.sh
#     already depends on.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# ----- args -----
VERSION="${1:-}"
NOTES_ARG="${2:-}"

if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 <version> [notes-or-path]" >&2
    echo "  Example: $0 2.3.0 \"Fix paged-reader init race + fuzzy search\"" >&2
    exit 2
fi

# Strip a leading 'v' if the user passed v2.3.0.
VERSION="${VERSION#v}"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: version '$VERSION' is not in major.minor.patch form (e.g. 2.3.0)." >&2
    exit 2
fi

TAG="v$VERSION"
IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"

# ----- preconditions -----
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
if [[ "$CURRENT_BRANCH" != "myversion" ]]; then
    echo "ERROR: must be on 'myversion' branch (currently on '$CURRENT_BRANCH')." >&2
    echo "  Check out the xenodochial worktree (or 'git checkout myversion') and re-run." >&2
    exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: working tree has uncommitted changes. Commit or stash them first." >&2
    git status --short >&2
    exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: GitHub CLI (gh) not found. Install: https://cli.github.com/" >&2
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "ERROR: gh is not authenticated. Run 'gh auth login' first." >&2
    exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "ERROR: tag '$TAG' already exists locally. Pick a new version or delete it first:" >&2
    echo "  git tag -d $TAG" >&2
    exit 1
fi

if git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null | grep -q "$TAG"; then
    echo "ERROR: tag '$TAG' already exists on origin (MKDevTests/Sipurra). Pick a new version." >&2
    exit 1
fi

# ----- targets -----
VERSIONS_TOML="gradle/libs.versions.toml"
APP_VERSION_KT="komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt"
SIGNED_APK="komelia-app/build/outputs/apk/release/kora-app-release-signed.apk"
RELEASE_APK="komelia-app/build/outputs/apk/release/kora-$VERSION.apk"

for f in "$VERSIONS_TOML" "$APP_VERSION_KT"; do
    [[ -f "$f" ]] || { echo "ERROR: $f not found. Repo layout changed?" >&2; exit 1; }
done

# ----- rollback helper -----
# Called on any failure after the version bump. Reverts the working tree
# and deletes the local tag if we made it.
rollback() {
    local msg="${1:-Aborting}"
    echo "==> $msg — rolling back version bump" >&2
    git checkout -- "$VERSIONS_TOML" "$APP_VERSION_KT" 2>/dev/null || true
    git tag -d "$TAG" 2>/dev/null || true
}
trap 'rollback "Script failed"' ERR

# ----- bump versions -----
echo "==> Bumping app version to $VERSION"

# libs.versions.toml: app-version = "X.Y.Z"
if ! grep -q '^app-version = ' "$VERSIONS_TOML"; then
    echo "ERROR: 'app-version = ' line not found in $VERSIONS_TOML" >&2
    exit 1
fi
sed -i.bak "s/^app-version = .*/app-version = \"$VERSION\"/" "$VERSIONS_TOML"
rm -f "$VERSIONS_TOML.bak"

# AppVersion.kt: val current = AppVersion(X, Y, Z)
if ! grep -qE 'val current = AppVersion\([0-9]+, [0-9]+, [0-9]+\)' "$APP_VERSION_KT"; then
    echo "ERROR: 'val current = AppVersion(...)' line not found in $APP_VERSION_KT" >&2
    exit 1
fi
sed -i.bak -E "s/val current = AppVersion\([0-9]+, [0-9]+, [0-9]+\)/val current = AppVersion($MAJOR, $MINOR, $PATCH)/" "$APP_VERSION_KT"
rm -f "$APP_VERSION_KT.bak"

echo "    $VERSIONS_TOML  -> app-version = \"$VERSION\""
echo "    $APP_VERSION_KT -> AppVersion($MAJOR, $MINOR, $PATCH)"

# ----- build signed APK -----
echo "==> Building signed release APK"
# build-kora-release.sh tries to install on the connected device at the end.
# We don't care about install for the release flow — only that the SIGNED
# APK ends up on disk. So ignore its exit code and verify the file.
set +e
./scripts/build-kora-release.sh
BUILD_RC=$?
set -e

if [[ ! -f "$SIGNED_APK" ]]; then
    echo "ERROR: signed APK was not produced at $SIGNED_APK (build script exit=$BUILD_RC)" >&2
    exit 1
fi

cp "$SIGNED_APK" "$RELEASE_APK"
echo "==> Release APK ready: $RELEASE_APK ($(du -h "$RELEASE_APK" | cut -f1))"

# ----- commit + tag -----
echo "==> Committing and tagging $TAG"
git add "$VERSIONS_TOML" "$APP_VERSION_KT"
git commit -m "chore(release): $TAG"
git tag -a "$TAG" -m "Kora $TAG"

# ----- push branch + tag -----
echo "==> Pushing myversion and $TAG to origin"
git push origin myversion
git push origin "$TAG"

# Disarm the rollback trap — past the point of clean recovery now (commit
# and tag are on origin). Subsequent failures must be resolved manually.
trap - ERR

# ----- create GitHub release -----
echo "==> Creating GitHub release on MKDevTests/Sipurra"

GH_ARGS=(
    release create "$TAG"
    "$RELEASE_APK"
    --repo MKDevTests/Sipurra
    --title "Kora $TAG"
)

if [[ -n "$NOTES_ARG" ]]; then
    if [[ -f "$NOTES_ARG" ]]; then
        GH_ARGS+=(--notes-file "$NOTES_ARG")
    else
        GH_ARGS+=(--notes "$NOTES_ARG")
    fi
fi
# When no notes are provided, gh defaults to opening $EDITOR — fine.

gh "${GH_ARGS[@]}"

echo ""
echo "==> Release $TAG published."
echo "    APK:  $RELEASE_APK"
echo "    URL:  https://github.com/MKDevTests/Sipurra/releases/tag/$TAG"
echo ""
echo "Users running an earlier Kora will see this release the next time"
echo "their app checks for updates (settings → Updates → Check for updates,"
echo "or on startup if they have that toggle on)."

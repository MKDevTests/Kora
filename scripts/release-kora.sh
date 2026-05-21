#!/bin/bash
# Cut a Kora release and publish it on github.com/MKDevTests/Kora so the
# in-app auto-updater can pick it up.
#
# Steps performed (in order):
#   1. Bump app-version in libs.versions.toml.
#   2. Bump AppVersion.current in komelia-domain/.../AppVersion.kt.
#   3. Build a signed release APK (delegates to build-kora-release.sh; the
#      version baked into the APK now matches the tag).
#   4. Commit the version bump on the current branch (must be main).
#   5. Tag the commit with v<version>.
#   6. Push the branch + tag to origin.
#   7. Create a GitHub release on MKDevTests/Kora with the signed APK
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
#   - Must be on branch 'main' with a clean working tree.
#   - GitHub CLI (gh) installed and authenticated to push to MKDevTests/Kora.
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

# versionCode bumped in lockstep with semver — computed so there's no separate
# counter to maintain. Layout: MAJOR*10000 + MINOR*100 + PATCH.
#   1.0.9 -> 10009     2.0.0 -> 20000     room for 99 patches per minor.
# Monotonically increasing as long as semver itself is — the previous hardcoded
# value (24) is below any computed value here, so updates from older Kora
# installs keep working.
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))

# ----- preflight: catch the recurring footguns before we touch any files -----
# preflight.sh covers branch + clean tree + tag uniqueness + JNI libs +
# migration index registration + version-file consistency. We still keep
# the two existing belt-and-braces checks below (branch + working tree)
# because they emit slightly more user-friendly recovery hints.
"$(dirname "$0")/preflight.sh" "$VERSION"

# ----- preconditions -----
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
    echo "ERROR: must be on 'main' branch (currently on '$CURRENT_BRANCH')." >&2
    echo "  Run 'git checkout main' and re-run." >&2
    exit 1
fi

# In WSL on a /mnt/c repo, git often reports every text file as modified
# because of CRLF/LF discrepancies between the Windows checkout and the
# WSL git config. Submodules can also report internal-content changes (`m`
# in git status short) that don't matter for releasing. Ignore both so we
# only block on actual content changes the user staged or made locally.
if ! git diff --quiet --ignore-cr-at-eol --ignore-submodules=all \
   || ! git diff --cached --quiet --ignore-cr-at-eol --ignore-submodules=all; then
    echo "ERROR: working tree has uncommitted changes. Commit or stash them first." >&2
    git -c core.fileMode=false status --short --ignore-submodules=all >&2
    exit 1
fi

# WSL: prefer Windows gh.exe so we reuse the auth/config the user already
# set up on Windows, instead of requiring a separate apt install in WSL.
if grep -qi microsoft /proc/version 2>/dev/null; then
    for candidate in \
        "/mnt/c/Program Files/GitHub CLI/gh.exe" \
        "/mnt/c/Users/$USER/AppData/Local/Programs/GitHub CLI/gh.exe" \
        "$HOME/AppData/Local/Programs/GitHub CLI/gh.exe"; do
        if [[ -x "$candidate" ]]; then
            gh() { "$candidate" "$@"; }
            export -f gh
            break
        fi
    done
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
    echo "ERROR: tag '$TAG' already exists on origin (MKDevTests/Kora). Pick a new version." >&2
    exit 1
fi

# ----- targets -----
VERSIONS_TOML="gradle/libs.versions.toml"
APP_VERSION_KT="komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt"
BUILD_GRADLE_KTS="komelia-app/build.gradle.kts"
SIGNED_APK="komelia-app/build/outputs/apk/release/kora-app-release-signed.apk"
RELEASE_APK="komelia-app/build/outputs/apk/release/kora-$VERSION.apk"

for f in "$VERSIONS_TOML" "$APP_VERSION_KT" "$BUILD_GRADLE_KTS"; do
    [[ -f "$f" ]] || { echo "ERROR: $f not found. Repo layout changed?" >&2; exit 1; }
done

# ----- rollback helper -----
# Called on any failure after the version bump. Reverts the working tree
# and deletes the local tag if we made it.
rollback() {
    local msg="${1:-Aborting}"
    echo "==> $msg — rolling back version bump" >&2
    git checkout -- "$VERSIONS_TOML" "$APP_VERSION_KT" "$BUILD_GRADLE_KTS" 2>/dev/null || true
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

# komelia-app/build.gradle.kts: versionCode = N
if ! grep -qE '^[[:space:]]*versionCode = [0-9]+' "$BUILD_GRADLE_KTS"; then
    echo "ERROR: 'versionCode = N' line not found in $BUILD_GRADLE_KTS" >&2
    exit 1
fi
sed -i.bak -E "s/^([[:space:]]*)versionCode = [0-9]+/\1versionCode = $VERSION_CODE/" "$BUILD_GRADLE_KTS"
rm -f "$BUILD_GRADLE_KTS.bak"

echo "    $VERSIONS_TOML    -> app-version = \"$VERSION\""
echo "    $APP_VERSION_KT   -> AppVersion($MAJOR, $MINOR, $PATCH)"
echo "    $BUILD_GRADLE_KTS -> versionCode = $VERSION_CODE"

# ----- build signed APK -----
echo "==> Building signed release APK"
# build-kora-release.sh tries to install on the connected device at the end.
# We don't care about install for the release flow — only that the SIGNED
# APK ends up on disk. So ignore its exit code and verify the file.
#
# `trap ERR` triggers regardless of `set -e` state, so we must disarm it
# explicitly here — otherwise a transient non-zero command inside the build
# script kicks off `rollback` while THIS script keeps running, and we end up
# copying a stale APK from a previous build to kora-<ver>.apk. (This is what
# burned the v1.0.8 release: shipped versionName=1.0.7 inside kora-1.0.8.apk.)
trap - ERR
set +e
./scripts/build-kora-release.sh
BUILD_RC=$?
set -e
trap 'rollback "Script failed"' ERR

if [[ ! -f "$SIGNED_APK" ]]; then
    echo "ERROR: signed APK was not produced at $SIGNED_APK (build script exit=$BUILD_RC)" >&2
    exit 1
fi

# Sanity-check the APK's actual versionName/versionCode match what we just
# bumped — defense in depth against the stale-APK bug above. If aapt2 isn't
# available we warn and continue (don't block release flow on a missing SDK
# tool), but a mismatch is always fatal.
verify_apk_version() {
    local apk="$1"
    local aapt2=""
    local candidate

    # Linux/WSL: $ANDROID_HOME/build-tools/<latest>/aapt2 first.
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        candidate="$(ls -1 "$ANDROID_HOME/build-tools"/*/aapt2 2>/dev/null | sort -V | tail -n 1)"
        [[ -x "$candidate" ]] && aapt2="$candidate"
    fi

    # Windows-side SDK reachable from WSL: pick the newest aapt2.exe.
    if [[ -z "$aapt2" ]]; then
        candidate="$(ls -1 "/mnt/c/Users/mathi/AppData/Local/Android/Sdk/build-tools"/*/aapt2.exe 2>/dev/null | sort -V | tail -n 1)"
        [[ -x "$candidate" ]] && aapt2="$candidate"
    fi

    if [[ -z "$aapt2" ]]; then
        echo "    WARNING: aapt2 not found, skipping versionName/versionCode check on $apk" >&2
        return 0
    fi

    local badging
    badging="$("$aapt2" dump badging "$apk" 2>/dev/null)" || {
        echo "    WARNING: 'aapt2 dump badging' failed on $apk, skipping check" >&2
        return 0
    }

    local apk_version_name apk_version_code
    apk_version_name="$(echo "$badging" | sed -nE "s/.*versionName='([^']+)'.*/\1/p" | head -n 1)"
    apk_version_code="$(echo "$badging" | sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" | head -n 1)"

    if [[ "$apk_version_name" != "$VERSION" ]]; then
        echo "ERROR: APK versionName='$apk_version_name' but expected '$VERSION'." >&2
        echo "  Likely a stale APK from a previous build. Aborting before upload." >&2
        return 1
    fi
    if [[ "$apk_version_code" != "$VERSION_CODE" ]]; then
        echo "ERROR: APK versionCode='$apk_version_code' but expected '$VERSION_CODE'." >&2
        return 1
    fi
    echo "    APK verified: versionName=$apk_version_name versionCode=$apk_version_code"
}

verify_apk_version "$SIGNED_APK"

cp "$SIGNED_APK" "$RELEASE_APK"
echo "==> Release APK ready: $RELEASE_APK ($(du -h "$RELEASE_APK" | cut -f1))"

# ----- commit + tag -----
echo "==> Committing and tagging $TAG"
git add "$VERSIONS_TOML" "$APP_VERSION_KT" "$BUILD_GRADLE_KTS"
git commit -m "chore(release): $TAG"
git tag -a "$TAG" -m "Kora $TAG"

# ----- push branch + tag -----
echo "==> Pushing main and $TAG to origin"
git push origin main
git push origin "$TAG"

# Disarm the rollback trap — past the point of clean recovery now (commit
# and tag are on origin). Subsequent failures must be resolved manually.
trap - ERR

# ----- create GitHub release -----
echo "==> Creating GitHub release on MKDevTests/Kora"

GH_ARGS=(
    release create "$TAG"
    "$RELEASE_APK"
    --repo MKDevTests/Kora
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
echo "    URL:  https://github.com/MKDevTests/Kora/releases/tag/$TAG"
echo ""
echo "Users running an earlier Kora will see this release the next time"
echo "their app checks for updates (settings → Updates → Check for updates,"
echo "or on startup if they have that toggle on)."

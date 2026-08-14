#!/bin/bash
# Build & install KoraDebug from the current branch.
# Usage: ./scripts/build-kora-debug.sh [--clean]
#
# Run from the repo root in WSL or Git Bash.
# Requires: gradlew, JDK 17, Android SDK at $ANDROID_HOME or local.properties.
#
# Refuses to build from `main` — feature work belongs on a dedicated branch.
# Use `scripts/build-kora-release.sh` to ship `main`.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
case "$CURRENT_BRANCH" in
    main)
        echo "ERROR: refusing to build from 'main'."
        echo "  Debug builds must come from a feature branch. Either:"
        echo "    - check out a feature branch, or"
        echo "    - use scripts/build-kora-release.sh to ship main."
        exit 1
        ;;
    "")
        echo "WARN: could not detect current branch (detached HEAD?). Continuing."
        ;;
    *)
        echo "==> Building from branch: $CURRENT_BRANCH"
        ;;
esac

# In WSL on a /mnt/c repo, gradlew is checked out with Windows CRLF and
# bash refuses to exec it. Strip CR in-place once; the change is local
# (git restore gradlew if you care) and stays valid until the next git
# checkout normalizes it.
if head -1 ./gradlew 2>/dev/null | grep -q $'\r'; then
    if command -v dos2unix >/dev/null 2>&1; then
        dos2unix -q ./gradlew
    else
        sed -i 's/\r$//' ./gradlew
    fi
    chmod +x ./gradlew
fi
GRADLEW=./gradlew

if [[ "$1" == "--clean" ]]; then
    echo "==> Clean build"
    "$GRADLEW" :komelia-app:clean
fi

# Guarantee native JNI libs are in place before invoking Gradle. Without
# this, the APK builds fine but crashes at runtime with UnsatisfiedLinkError
# for libsqlitejdbc.so or libvips.so — a recurring worktree-setup footgun.
# See scripts/_ensure_jni_libs.sh for the recovery logic and one-time
# cache population instructions.
. "$(dirname "$0")/_ensure_jni_libs.sh"
ensure_jni_libs

echo "==> Building KoraDebug APK"

# AGP's dexing transforms sometimes keep a stale per-class dex: the old
# ReaderState.dex still holds ReaderState$1 while the new build ships it
# separately, and mergeLibDex aborts with "Type ... is defined multiple times".
# It fires on classes with inner classes, so any edit to ReaderState or
# AndroidReaderImage tends to trigger it.
#
# The fix is to delete build/.transforms, but only the daemon knows it holds
# those files open — a plain rm -rf fails halfway with "Permission denied" and
# leaves the corrupt directory behind. So: stop the daemon, purge, retry once.
# Doing it on failure rather than up front keeps normal builds fast.
#
# The log is kept rather than binned: when a build takes forty minutes, the
# only way to know whether recovery fired, whether the cache was cold or
# whether one module simply recompiled is to read it afterwards.
BUILD_LOG="build/last-build.log"
mkdir -p build
: > "$BUILD_LOG"
BUILD_STARTED=$SECONDS
RECOVERY_RAN=0

run_assemble() {
    "$GRADLEW" :komelia-app:assembleDebug "$@" 2>&1 | tee -a "$BUILD_LOG"
    return "${PIPESTATUS[0]}"
}

set +e
run_assemble
BUILD_STATUS=$?
set -e

# Modules whose build dir holds a stale dex, taken from the error text itself:
#   ERROR: /path/to/<module>/build/.transforms/<hash>/... is defined multiple times
# Printed one per line, repo-relative.
stale_dex_modules() {
    grep -o "[^ ]*/build/\.transforms/" "$BUILD_LOG" \
        | sed "s#^$REPO_ROOT/##; s#/build/\.transforms/\$##" \
        | sort -u
}

if [[ $BUILD_STATUS -ne 0 ]] && grep -q "is defined multiple times" "$BUILD_LOG"; then
    echo ""
    echo "==> RECOVERY 1/2: stale dex transforms. Stopping the daemon, purging .transforms, retrying."
    RECOVERY_RAN=1
    "$GRADLEW" --stop >/dev/null 2>&1 || true
    find . -type d -name ".transforms" -path "*/build/*" -prune -exec rm -rf {} + 2>/dev/null || true
    set +e
    # Without this the build cache hands back the very output just purged.
    run_assemble --no-build-cache
    BUILD_STATUS=$?
    set -e
fi

# Purging .transforms is not always enough: the per-class dex can also be stale
# in the module's own build dir, and the transform is then rebuilt from it.
# Removing the build dir of just the modules named in the error is the reliable
# fix, and costs one module recompile rather than a full clean. jniLibs live in
# src/androidMain/jniLibs and are untouched by this.
if [[ $BUILD_STATUS -ne 0 ]] && grep -q "is defined multiple times" "$BUILD_LOG"; then
    MODULES="$(stale_dex_modules)"
    if [[ -n "$MODULES" ]]; then
        echo ""
        echo "==> RECOVERY 2/2: still stale. Removing the build dir of:"
        RECOVERY_RAN=1
        echo "$MODULES" | sed 's/^/      /'
        "$GRADLEW" --stop >/dev/null 2>&1 || true
        while read -r module; do
            [[ -n "$module" && -d "$module/build" ]] && rm -rf "$module/build"
        done <<< "$MODULES"
        set +e
        run_assemble --no-build-cache
        BUILD_STATUS=$?
        set -e
    fi
fi

BUILD_ELAPSED=$(( SECONDS - BUILD_STARTED ))
echo ""
echo "==> Gradle took $(( BUILD_ELAPSED / 60 ))m $(( BUILD_ELAPSED % 60 ))s. Full log: $BUILD_LOG"
if [[ $RECOVERY_RAN -eq 1 ]]; then
    echo "    A stale-dex recovery ran, which is most of that time."
fi

[[ $BUILD_STATUS -ne 0 ]] && exit "$BUILD_STATUS"

APK="komelia-app/build/outputs/apk/debug/kora-app-debug.apk"
[[ ! -f "$APK" ]] && APK="komelia-app/build/outputs/apk/debug/sipurra-app-debug.apk" # legacy fallback
[[ ! -f "$APK" ]] && { echo "APK not found"; exit 1; }

echo "==> APK ready: $APK ($(du -h "$APK" | cut -f1))"

# In WSL, adb interop with Windows USB devices is unreliable: adb.exe
# invoked from WSL doesn't see the device the Windows-side adb server
# sees. Several workarounds have been tried (start-server, parsing
# `adb devices`, etc.) and none of them stick. Print the install
# command for the user to paste in PowerShell and exit cleanly. The
# user is expected to run install from PS where adb works natively.
if grep -qi microsoft /proc/version 2>/dev/null; then
    WIN_APK="$(wslpath -w "$(realpath "$APK")" 2>/dev/null || echo "$APK")"
    echo ""
    echo "==> WSL detected. Open PowerShell and run:"
    echo "    adb install -r \"$WIN_APK\""
    echo ""
    echo "Then launch with:"
    echo "    adb shell monkey -p io.github.mkdevtests.kora.debug -c android.intent.category.LAUNCHER 1"
    exit 0
fi

ADB=adb
if command -v "$ADB" >/dev/null 2>&1 || [[ -x "$ADB" ]]; then
    # Wake the (Windows) adb server. In WSL the first call from a fresh
    # shell often races the daemon and reports "no device" even when one
    # is plugged in. start-server is idempotent.
    "$ADB" start-server >/dev/null 2>&1 || true

    # Parse `adb devices` so we can tell apart "no device", "offline",
    # and "unauthorized" (plugged in but the user hasn't tapped Allow).
    STATE="$("$ADB" devices 2>/dev/null | awk 'NR>1 && NF>=2 {print $2; exit}')"
    case "$STATE" in
        device)
            echo "==> Installing on connected device"
            "$ADB" install -r "$APK"
            echo "==> Done. Launch with:"
            echo "    adb shell monkey -p io.github.mkdevtests.kora.debug -c android.intent.category.LAUNCHER 1"
            ;;
        unauthorized)
            echo "Device is plugged in but unauthorized." >&2
            echo "  Tap 'Allow USB debugging' on the tablet, then re-run. APK is ready: $APK" >&2
            exit 1
            ;;
        offline)
            echo "Device is offline. Unplug/replug the cable, then re-run. APK is ready: $APK" >&2
            exit 1
            ;;
        *)
            echo "No device connected. Install manually with:"
            echo "    adb install -r $APK"
            ;;
    esac
else
    echo "adb not in PATH. Install manually with:"
    echo "    /path/to/adb install -r $APK"
fi

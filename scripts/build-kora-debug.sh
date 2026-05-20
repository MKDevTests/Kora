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
"$GRADLEW" :komelia-app:assembleDebug

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

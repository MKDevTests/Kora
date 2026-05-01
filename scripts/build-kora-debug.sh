#!/bin/bash
# Build & install KoraDebug from the current branch.
# Usage: ./scripts/build-kora-debug.sh [--clean]
#
# Run from the repo root in WSL or Git Bash.
# Requires: gradlew, JDK 17, Android SDK at $ANDROID_HOME or local.properties.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# On Windows we need gradlew.bat from PowerShell/cmd. From Git Bash or WSL,
# gradlew (the bash wrapper) works directly.
GRADLEW=./gradlew
[[ ! -x "$GRADLEW" ]] && GRADLEW=./gradlew.bat

if [[ "$1" == "--clean" ]]; then
    echo "==> Clean build"
    "$GRADLEW" :komelia-app:clean
fi

echo "==> Building KoraDebug APK"
"$GRADLEW" :komelia-app:assembleDebug

APK="komelia-app/build/outputs/apk/debug/kora-app-debug.apk"
[[ ! -f "$APK" ]] && APK="komelia-app/build/outputs/apk/debug/sipurra-app-debug.apk" # legacy fallback
[[ ! -f "$APK" ]] && { echo "APK not found"; exit 1; }

echo "==> APK ready: $APK ($(du -h "$APK" | cut -f1))"

if command -v adb >/dev/null 2>&1; then
    if adb get-state >/dev/null 2>&1; then
        echo "==> Installing on connected device"
        adb install -r "$APK"
        echo "==> Done. Launch with:"
        echo "    adb shell monkey -p io.github.mkdevtests.kora.debug -c android.intent.category.LAUNCHER 1"
    else
        echo "No device connected. Install manually with:"
        echo "    adb install -r $APK"
    fi
else
    echo "adb not in PATH. Install manually with:"
    echo "    /path/to/adb install -r $APK"
fi

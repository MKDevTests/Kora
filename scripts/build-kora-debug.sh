#!/bin/bash
# Build & install KoraDebug from the current branch.
# Usage: ./scripts/build-kora-debug.sh [--clean]
#
# Run from the repo root in WSL or Git Bash.
# Requires: gradlew, JDK 17, Android SDK at $ANDROID_HOME or local.properties.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# In WSL working on a /mnt/c repo, gradlew often has Windows CRLF line
# endings and bash can't run it directly. gradlew.bat works through WSL
# interop. Prefer .bat when CRLF is detected.
GRADLEW=./gradlew
if [[ -f ./gradlew.bat ]] && head -1 ./gradlew 2>/dev/null | grep -q $'\r'; then
    GRADLEW=./gradlew.bat
elif [[ ! -x ./gradlew && -f ./gradlew.bat ]]; then
    GRADLEW=./gradlew.bat
fi

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

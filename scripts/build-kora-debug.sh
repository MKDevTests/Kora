#!/bin/bash
# Build & install KoraDebug from the current branch.
# Usage: ./scripts/build-kora-debug.sh [--clean]
#
# Run from the repo root in WSL or Git Bash.
# Requires: gradlew, JDK 17, Android SDK at $ANDROID_HOME or local.properties.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

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

echo "==> Building KoraDebug APK"
"$GRADLEW" :komelia-app:assembleDebug

APK="komelia-app/build/outputs/apk/debug/kora-app-debug.apk"
[[ ! -f "$APK" ]] && APK="komelia-app/build/outputs/apk/debug/sipurra-app-debug.apk" # legacy fallback
[[ ! -f "$APK" ]] && { echo "APK not found"; exit 1; }

echo "==> APK ready: $APK ($(du -h "$APK" | cut -f1))"

# WSL's apt-installed adb runs its own server and can't see USB devices.
# Prefer Windows adb when we're in WSL.
ADB=adb
if grep -qi microsoft /proc/version 2>/dev/null; then
    for candidate in \
        /mnt/c/Users/mathi/AppData/Local/Android/Sdk/platform-tools/adb.exe \
        "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"; do
        [[ -x "$candidate" ]] && ADB="$candidate" && break
    done
fi

if command -v "$ADB" >/dev/null 2>&1 || [[ -x "$ADB" ]]; then
    if "$ADB" get-state >/dev/null 2>&1; then
        echo "==> Installing on connected device"
        "$ADB" install -r "$APK"
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

#!/bin/bash
# Build, sign, and install the Kora "release" APK from the current branch.
# Optionally migrate user data from KoraDebug after install.
#
# Usage:
#   ./scripts/build-kora-release.sh [--clean] [--migrate]
#
#     --clean    gradle clean before building
#     --migrate  copy data from KoraDebug (.kora.debug) to Kora (.kora)
#                after install, leaving KoraDebug intact as backup
#
# Run from the repo root in WSL or Git Bash. adb must be in PATH (or this
# script picks up the Windows adb under /mnt/c/.../platform-tools).

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# ----- args -----
CLEAN=0
MIGRATE=0
for arg in "$@"; do
    case "$arg" in
        --clean) CLEAN=1 ;;
        --migrate) MIGRATE=1 ;;
        *) echo "Unknown arg: $arg"; exit 2 ;;
    esac
done

# ----- pick adb (WSL: prefer Windows adb so we see the same device) -----
# WSL's apt-installed adb runs its own server and can't see USB devices
# attached to Windows. Always prefer Windows adb when we're in WSL.
IN_WSL=0
grep -qi microsoft /proc/version 2>/dev/null && IN_WSL=1

if [[ $IN_WSL == 1 ]]; then
    for candidate in \
        /mnt/c/Users/mathi/AppData/Local/Android/Sdk/platform-tools/adb.exe \
        "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"; do
        if [[ -x "$candidate" ]]; then
            export PATH="$(dirname "$candidate"):$PATH"
            # alias `adb` to the .exe for unqualified calls below
            adb() { "$candidate" "$@"; }
            export -f adb
            break
        fi
    done
elif ! command -v adb >/dev/null 2>&1; then
    for candidate in \
        "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"; do
        [[ -x "$candidate" ]] && export PATH="$(dirname "$candidate"):$PATH" && break
    done
fi

# ----- find Android SDK build-tools (zipalign + apksigner) -----
SDK=""
for candidate in \
    "$ANDROID_HOME" \
    "$ANDROID_SDK_ROOT" \
    "$HOME/Android/Sdk" \
    "$HOME/AppData/Local/Android/Sdk" \
    "/mnt/c/Users/mathi/AppData/Local/Android/Sdk"; do
    [[ -d "$candidate/build-tools" ]] && SDK="$candidate" && break
done
[[ -z "$SDK" ]] && { echo "Android SDK build-tools not found. Set ANDROID_HOME."; exit 1; }

BUILD_TOOLS=$(ls -d "$SDK/build-tools/"*/ | sort -V | tail -1)
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
[[ -f "${ZIPALIGN}.exe" ]] && ZIPALIGN="${ZIPALIGN}.exe"
[[ -f "${APKSIGNER}.bat" ]] && APKSIGNER="${APKSIGNER}.bat"
[[ -f "$ZIPALIGN" ]] || { echo "zipalign not found at $ZIPALIGN"; exit 1; }
[[ -f "$APKSIGNER" ]] || { echo "apksigner not found at $APKSIGNER"; exit 1; }

# ----- find debug.keystore -----
KEYSTORE=""
for candidate in \
    "$HOME/.android/debug.keystore" \
    "/mnt/c/Users/mathi/.android/debug.keystore"; do
    [[ -f "$candidate" ]] && KEYSTORE="$candidate" && break
done
[[ -z "$KEYSTORE" ]] && { echo "debug.keystore not found in ~/.android/ or /mnt/c/Users/mathi/.android/"; exit 1; }

echo "==> SDK: $SDK"
echo "==> build-tools: $BUILD_TOOLS"
echo "==> keystore: $KEYSTORE"

# ----- gradle -----
# In WSL on a /mnt/c repo, gradlew is checked out with Windows CRLF and
# bash refuses to exec it ("required file not found"). gradlew.bat is a
# DOS batch file, also unrunnable from bash. Cleanest fix: strip CR from
# gradlew once. The change is local-only (git restore gradlew if you
# care), and it stays valid until the next git checkout normalizes it.
if head -1 ./gradlew 2>/dev/null | grep -q $'\r'; then
    if command -v dos2unix >/dev/null 2>&1; then
        dos2unix -q ./gradlew
    else
        sed -i 's/\r$//' ./gradlew
    fi
    chmod +x ./gradlew
fi
GRADLEW=./gradlew

if [[ $CLEAN == 1 ]]; then
    echo "==> Clean"
    "$GRADLEW" :komelia-app:clean
fi

echo "==> Building Kora release APK"
"$GRADLEW" :komelia-app:assembleRelease

UNSIGNED="komelia-app/build/outputs/apk/release/kora-app-release-unsigned.apk"
ALIGNED="komelia-app/build/outputs/apk/release/kora-app-release-aligned.apk"
SIGNED="komelia-app/build/outputs/apk/release/kora-app-release-signed.apk"

# Legacy fallback if archivesName change hasn't propagated
[[ ! -f "$UNSIGNED" && -f "komelia-app/build/outputs/apk/release/sipurra-app-release-unsigned.apk" ]] && \
    UNSIGNED="komelia-app/build/outputs/apk/release/sipurra-app-release-unsigned.apk"

[[ ! -f "$UNSIGNED" ]] && { echo "Unsigned APK not found"; exit 1; }

echo "==> Aligning"
"$ZIPALIGN" -p -f 4 "$UNSIGNED" "$ALIGNED"

echo "==> Signing with debug keystore"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out "$SIGNED" \
    "$ALIGNED"

echo "==> APK ready: $SIGNED ($(du -h "$SIGNED" | cut -f1))"

# ----- install -----
REL_PKG=io.github.mkdevtests.kora
DEBUG_PKG=io.github.mkdevtests.kora.debug

# Wake up the (Windows) adb server. In WSL the first call from a fresh
# shell often races the daemon start and `adb get-state` reports "no device"
# even when one is plugged in. start-server is idempotent.
adb start-server >/dev/null 2>&1 || true

# Parse `adb devices` rather than relying on `get-state` so we can tell
# apart "no device", "device offline", and "unauthorized" (the device is
# plugged in but the user hasn't tapped Allow on the tablet yet).
DEVICES_LINE="$(adb devices 2>/dev/null | awk 'NR>1 && NF>=2 {print $2; exit}')"
case "$DEVICES_LINE" in
    device)
        echo "==> Installing on connected device"
        ;;
    unauthorized)
        echo "Device is plugged in but unauthorized." >&2
        echo "  Tap 'Allow USB debugging' on the tablet (check 'Always allow' to skip next time)," >&2
        echo "  then re-run. APK is ready: $SIGNED" >&2
        exit 1
        ;;
    offline)
        echo "Device is offline. Unplug/replug the cable, then re-run. APK is ready: $SIGNED" >&2
        exit 1
        ;;
    *)
        echo "No device connected. Install manually:"
        echo "    adb install -r $SIGNED"
        exit 0
        ;;
esac

if ! adb install -r "$SIGNED" 2>&1; then
    echo "Install failed (signature mismatch?). To force-replace:"
    echo "    adb uninstall $REL_PKG && adb install $SIGNED"
    exit 1
fi

# ----- migrate from KoraDebug if asked -----
if [[ $MIGRATE == 1 ]]; then
    echo ""
    echo "==> Migrate: $DEBUG_PKG -> $REL_PKG"

    if ! adb shell "run-as $DEBUG_PKG echo ok" >/dev/null 2>&1; then
        echo "Cannot run-as $DEBUG_PKG. Is KoraDebug installed and debuggable? Skipping migration."
        exit 0
    fi

    # Launch release once so its data dir exists
    adb shell monkey -p "$REL_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
    sleep 2

    if ! adb shell "run-as $REL_PKG echo ok" >/dev/null 2>&1; then
        echo "Cannot run-as $REL_PKG. Release variant must be debuggable for migration."
        exit 1
    fi

    adb shell am force-stop "$DEBUG_PKG"
    adb shell am force-stop "$REL_PKG"

    echo "    streaming files+shared_prefs via tar pipe"
    adb shell "run-as $DEBUG_PKG tar cf - files shared_prefs | run-as $REL_PKG tar xf -"

    echo "    renaming shared_prefs XML"
    adb shell "run-as $REL_PKG mv shared_prefs/${DEBUG_PKG}_preferences.xml shared_prefs/${REL_PKG}_preferences.xml" 2>/dev/null \
        || echo "    (no $DEBUG_PKG prefs file, skipping)"

    echo "    verify"
    adb shell "run-as $REL_PKG ls files" | head -8

    echo ""
    echo "==> Migration done. KoraDebug ($DEBUG_PKG) left intact as backup."
fi

echo ""
echo "==> Launch Kora with:"
echo "    adb shell monkey -p $REL_PKG -c android.intent.category.LAUNCHER 1"

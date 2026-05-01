#!/bin/bash
# Migrate user data from SipurraV2 (io.github.eserero.sipurra.v2)
# to KoraDebug (io.github.mkdevtests.kora.debug).
#
# - Reads from SipurraV2 via run-as
# - Writes to KoraDebug via run-as (pipes inside adb shell, no PowerShell binary corruption)
# - Renames the shared_prefs XML to match the new package
# - SipurraV2 is left UNTOUCHED (true copy)
#
# Prereqs:
#  - Both apps installed and debuggable
#  - KoraDebug launched at least once (so its data dir exists)
#
# Usage: ./scripts/migrate-to-kora-debug.sh

set -e

OLD_PKG="io.github.eserero.sipurra.v2"
NEW_PKG="io.github.mkdevtests.kora.debug"

# Old XML uses old package name as filename prefix
OLD_PREFS="${OLD_PKG}_preferences.xml"
NEW_PREFS="${NEW_PKG}_preferences.xml"

echo "==> Verifying both apps are accessible via run-as"
adb shell "run-as $OLD_PKG echo ok" >/dev/null || { echo "Cannot run-as $OLD_PKG. Is it installed and debuggable?"; exit 1; }
adb shell "run-as $NEW_PKG echo ok" >/dev/null || { echo "Cannot run-as $NEW_PKG. Install and launch KoraDebug first."; exit 1; }

echo "==> Stopping both apps to release database locks"
adb shell am force-stop "$OLD_PKG"
adb shell am force-stop "$NEW_PKG"

echo "==> Streaming data from $OLD_PKG to $NEW_PKG (tar pipe inside adb shell)"
adb shell "run-as $OLD_PKG tar cf - files shared_prefs | run-as $NEW_PKG tar xf -"

echo "==> Renaming shared_prefs XML"
adb shell "run-as $NEW_PKG mv shared_prefs/$OLD_PREFS shared_prefs/$NEW_PREFS" 2>/dev/null \
    || echo "    (no $OLD_PREFS to rename, skipping)"

echo "==> Verifying"
adb shell "run-as $NEW_PKG ls files" | head
echo "..."
adb shell "run-as $NEW_PKG ls shared_prefs"

echo ""
echo "==> Migration done. Launch KoraDebug and verify your data."
echo "    adb shell monkey -p $NEW_PKG -c android.intent.category.LAUNCHER 1"
echo ""
echo "    SipurraV2 ($OLD_PKG) is intact as a backup."

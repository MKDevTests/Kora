#!/bin/bash
# Builds translate-kit's Android AAR from source and vendors it.
#
#     ./scripts/translatekit/build-aar.sh              arm64-v8a only (the tablet)
#     ./scripts/translatekit/build-aar.sh --both-abis  plus x86_64, for an emulator
#
# Why we build it rather than depend on it: translate-kit publishes only to
# GitHub Packages, which needs a token to read. It is Apache-2.0, so building it
# here and committing the AAR costs nothing but the wait.
#
# Everything runs in WSL, including the CMake/NDK part. An earlier version of
# this split the work across two shells on the theory that the native build
# needed the Windows SDK -- there is no Windows SDK on this machine, no
# sdkmanager under %LOCALAPPDATA%\Android\Sdk at all. The real SDK is
# ~/android-sdk here, with its linux-x86_64 NDK toolchains, and Kora's
# local.properties (which does point at a Windows path) is irrelevant: this is a
# separate Gradle project with its own ANDROID_HOME.
#
# First run downloads roughly 500 MB of git history -- cld2 is 87 MB on its own,
# and marian pulls sentencepiece, faiss, intgemm, ruy, ssplit and pcre2. Then
# about ten minutes of native compilation per ABI, which is why one ABI is the
# default.
set -e

VERSION="v0.1.0"
REQUIRED_NDK="28.2.13676358"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/third_party/translatekit-src"
OUT="$ROOT/third_party/translatekit"

ABI_ARGS=(-PtestAbi=arm64-v8a)
[[ "${1:-}" == "--both-abis" ]] && ABI_ARGS=()

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
if [[ ! -d "$ANDROID_HOME/ndk/$REQUIRED_NDK" ]]; then
    # Pinned in translate-kit's build.gradle, so the 27.x already installed will
    # not do: Gradle resolves ndkVersion exactly rather than falling back.
    echo "NDK $REQUIRED_NDK is not installed. Install it with:"
    echo "    $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \"ndk;$REQUIRED_NDK\""
    exit 1
fi

if [[ ! -d "$SRC/.git" ]]; then
    echo "==> cloning translate-kit $VERSION"
    # autocrlf off, deliberately: the checkout contains shell scripts this very
    # script then runs. A CRLF .sh fails with "cannot execute: required file not
    # found", which reads like a missing interpreter rather than line endings.
    git -c core.autocrlf=false -c core.eol=lf \
        clone --branch "$VERSION" https://github.com/marcosholgado/translate-kit.git "$SRC"
else
    echo "==> checkout already there, fetching $VERSION"
    git -C "$SRC" fetch --tags origin
    git -C "$SRC" checkout "$VERSION"
fi

cd "$SRC"

echo "==> submodules (cld2 + the Bergamot engine)"
git submodule update --init --recursive third_party/cld2 third_party/translations

echo "==> engine patches"
# Idempotent upstream: patches already present are skipped, so a second run is
# fine. 0006 is the one that matters most -- marian links -lruy under
# USE_RUY_SGEMM but only builds vendored ruy under USE_RUY, and without it the
# engine compiles and then aborts at the first float matmul after emitting one
# word. That failure reads like a truncation bug, not a missing library.
./scripts/apply-engine-patches.sh

echo "==> gradle (this is the slow part)"
started=$SECONDS
cd "$SRC/android"
./gradlew :translate-kit:assembleRelease "${ABI_ARGS[@]}" --no-daemon
echo "==> native build took $(( (SECONDS - started) / 60 ))m $(( (SECONDS - started) % 60 ))s"

aar=$(find "$SRC/android/translate-kit/build/outputs/aar" -name '*release*.aar' | head -1)
[[ -n "$aar" ]] || { echo "the build reported success but produced no AAR"; exit 1; }

mkdir -p "$OUT"
cp "$aar" "$OUT/translate-kit-android.aar"
# The licence travels with the binary: we are redistributing someone else's
# Apache-2.0 work inside our APK.
cp "$SRC/LICENSE" "$SRC/NOTICE" "$SRC/THIRD_PARTY_LICENSES.md" "$OUT/"

echo "==> $OUT/translate-kit-android.aar ($(du -h "$OUT/translate-kit-android.aar" | cut -f1))"

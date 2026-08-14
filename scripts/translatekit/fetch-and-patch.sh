#!/bin/bash
# Fetches translate-kit's sources and applies its engine patches. Step 1 of 2.
#
#     ./scripts/translatekit/fetch-and-patch.sh
#     then, in PowerShell: scripts\translatekit\build-aar.ps1
#
# Why we build the AAR instead of depending on it: translate-kit publishes only
# to GitHub Packages, which requires a token to read. The licence is Apache-2.0,
# so building it ourselves and vendoring the result is allowed and costs nothing
# beyond the one-off below.
#
# Why this half runs in WSL and the other half does not: the patches are applied
# by an upstream bash script that uses `patch`, and the sources are plain text —
# that part is happiest here. The Gradle build is not: local.properties points at
# the Windows SDK (C:/Users/mathi/AppData/Local/Android/Sdk), whose NDK ships
# windows-x86_64 toolchains only. A CMake/NDK build launched from WSL would look
# for linux-x86_64 binaries that are not there.
#
# Downloads roughly 500 MB of git history the first time (cld2 is 87 MB on its
# own, and marian pulls sentencepiece, faiss, intgemm, ruy, ssplit and pcre2).
# Re-running is cheap: an existing checkout is only updated.
set -e

VERSION="v0.1.0"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/third_party/translatekit-src"

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
# not a problem. 0006 is the one that matters most here — marian links -lruy
# under USE_RUY_SGEMM but only builds vendored ruy under USE_RUY, and without it
# the engine compiles and then aborts at the first float matmul after emitting
# one word. That failure reads like a truncation bug, not a missing library.
./scripts/apply-engine-patches.sh

echo
echo "==> done. Now, in PowerShell:"
echo "    scripts\\translatekit\\build-aar.ps1"

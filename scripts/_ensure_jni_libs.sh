#!/bin/bash
# Sourced by build-kora-debug.sh and build-kora-release.sh to guarantee
# the native JNI libs are in place before invoking Gradle. Without this,
# the APK builds fine but crashes at runtime with UnsatisfiedLinkError —
# a recurring footgun that has burned several worktree-setup cycles.
#
# Two libs are checked:
#
#   1. libsqlitejdbc.so — lives inside the sqlite-xerial-jdbc Maven JAR.
#      Komelia exposes dedicated Gradle tasks to extract it into
#      komelia-infra/database/sqlite/src/androidMain/jniLibs/<arch>/.
#      These tasks are NOT chained to assembleDebug. We call them
#      unconditionally when the lib is missing — they're idempotent and
#      fast (<5s).
#
#   2. libvips and its ~30 transitive deps (libpng, libjpeg, libheif,
#      libwebp, libjxl, libtiff, libglib-2.0, libkomelia_vips, …). These
#      are built from C via a Docker toolchain (cmake/android.Dockerfile,
#      ~30 min). To skip that, we restore them from a machine-local cache
#      at $KORA_JNI_CACHE (default ~/.kora-jnilibs-cache). The cache is
#      populated once per machine, either by copying from a worktree that
#      has just done a Docker build, or by extracting the libvips chain
#      from the user's installed Kora release APK on the tablet.

set -e

# Cache override: KORA_JNI_CACHE=/some/path ./scripts/build-kora-debug.sh
KORA_JNI_CACHE="${KORA_JNI_CACHE:-$HOME/.kora-jnilibs-cache}"

# Paths are relative to REPO_ROOT (set by the calling script).
SQLITE_LIB_DIR="komelia-infra/database/sqlite/src/androidMain/jniLibs/arm64-v8a"
VIPS_LIB_DIR="komelia-infra/jni/src/androidMain/jniLibs/arm64-v8a"

ensure_sqlite_jni() {
    if [[ -f "$SQLITE_LIB_DIR/libsqlitejdbc.so" ]]; then
        return 0
    fi
    echo "==> SQLite JNI lib missing — extracting from JAR"
    "${GRADLEW:-./gradlew}" \
        :komelia-infra:database:sqlite:android-arm64-ExtractSqliteLib \
        :komelia-infra:database:sqlite:android-armv7a-ExtractSqliteLib \
        :komelia-infra:database:sqlite:android-x86_64-ExtractSqliteLib \
        :komelia-infra:database:sqlite:android-x86-ExtractSqliteLib
}

ensure_vips_jni() {
    if [[ -f "$VIPS_LIB_DIR/libvips.so" ]]; then
        return 0
    fi
    echo "==> libvips JNI chain missing — restoring from cache: $KORA_JNI_CACHE"
    if [[ ! -d "$KORA_JNI_CACHE/arm64-v8a" ]] || [[ ! -f "$KORA_JNI_CACHE/arm64-v8a/libvips.so" ]]; then
        cat >&2 <<EOF

ERROR: libvips JNI chain is missing and the cache at
  $KORA_JNI_CACHE/arm64-v8a
does not contain libvips.so either. This is a one-time setup per machine.

To populate the cache from a worktree that already has libvips installed:

  mkdir -p "$KORA_JNI_CACHE/arm64-v8a"
  rsync -a <good-worktree>/komelia-infra/jni/src/androidMain/jniLibs/arm64-v8a/ \\
      "$KORA_JNI_CACHE/arm64-v8a/"

Or extract from the installed Kora release APK on the tablet:

  # 1. PowerShell (adb is broken from WSL on this machine)
  mkdir C:\\temp -Force | Out-Null
  \$path = (adb shell pm path io.github.mkdevtests.kora | Out-String).Trim() -replace '^package:', ''
  adb pull \$path C:\\temp\\kora-release.apk

  # 2. WSL, then:
  mkdir -p "$KORA_JNI_CACHE/arm64-v8a"
  for lib in libz libffi libintl libiconv libglib-2.0 libgmodule-2.0 libgobject-2.0 \\
             libgio-2.0 liblcms2 libexif libde265 libdav1d libexpat libhwy libsharpyuv \\
             libwebp libwebpdecoder libwebpdemux libwebpmux libjpeg libbrotlicommon \\
             libbrotlidec libbrotlienc libjxl_cms libjxl_threads libjxl libpng libtiff \\
             libheif libvips libkomelia_vips libkomelia_android_bitmap; do
      unzip -j -o /mnt/c/temp/kora-release.apk "lib/arm64-v8a/\${lib}.so" \\
          -d "$KORA_JNI_CACHE/arm64-v8a/" 2>/dev/null
  done

After populating, re-run this script.
EOF
        return 1
    fi
    mkdir -p "$VIPS_LIB_DIR"
    rsync -a "$KORA_JNI_CACHE/arm64-v8a/" "$VIPS_LIB_DIR/"
    local count
    count=$(ls "$VIPS_LIB_DIR/" | wc -l)
    echo "==> libvips JNI chain restored ($count libs from cache)"
}

ensure_jni_libs() {
    ensure_sqlite_jni
    ensure_vips_jni
}

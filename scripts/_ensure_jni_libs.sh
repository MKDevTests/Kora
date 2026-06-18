#!/bin/bash
# Sourced by build-kora-debug.sh and build-kora-release.sh to guarantee
# the native JNI libs are in place before invoking Gradle. Without this,
# the APK builds fine but crashes at runtime with UnsatisfiedLinkError —
# a recurring footgun that has burned several worktree-setup cycles.
#
# Three lib groups are checked:
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
#
#   3. ONNX libs (libomp.so, libkomelia_onnxruntime.so) — OPTIONAL. Only the
#      smart panel-by-panel webtoon reader (PANELS mode) needs them; the app
#      builds and runs fine without. They are gitignored and were historically
#      dropped whenever jniLibs got wiped+repopulated from an APK (that
#      extraction whitelist excludes them), silently disabling PANELS with NO
#      build error — a feature vanishing with no trace. This guard makes them
#      durable and visible: backs them up to the cache when present, restores
#      them when wiped, and WARNS (never fails) when truly absent.

set -e

# Cache override: KORA_JNI_CACHE=/some/path ./scripts/build-kora-debug.sh
KORA_JNI_CACHE="${KORA_JNI_CACHE:-$HOME/.kora-jnilibs-cache}"

# Paths are relative to REPO_ROOT (set by the calling script).
SQLITE_LIB_DIR="komelia-infra/database/sqlite/src/androidMain/jniLibs/arm64-v8a"
VIPS_LIB_DIR="komelia-infra/jni/src/androidMain/jniLibs/arm64-v8a"

# ONNX/panel-detection libs live in the same arm64-v8a dir as the vips chain.
# NOTE: libonnxruntime.so is intentionally NOT listed — it ships from the
# onnxruntime-android Maven AAR and is de-duped via pickFirst; placing a copy
# here would trigger a mergeNativeLibs duplicate. Only these two are ours.
ONNX_LIBS=("libomp.so" "libkomelia_onnxruntime.so")

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

# Keep the optional ONNX/panel libs durable and visible. Unlike vips/sqlite,
# a miss here is never fatal — it only disables PANELS mode. The logic is
# bidirectional so the feature survives any jniLibs wipe once built:
#   - in jniLibs but not cached  -> back it up to the cache (protect it)
#   - cached but missing locally  -> restore it from the cache
#   - missing in both             -> WARN loudly, then continue the build
ensure_onnx_jni() {
    mkdir -p "$VIPS_LIB_DIR" "$KORA_JNI_CACHE/arm64-v8a" 2>/dev/null || true

    local lib in_libs in_cache
    for lib in "${ONNX_LIBS[@]}"; do
        in_libs="$VIPS_LIB_DIR/$lib"
        in_cache="$KORA_JNI_CACHE/arm64-v8a/$lib"
        if [[ -f "$in_libs" && ! -f "$in_cache" ]]; then
            if cp "$in_libs" "$in_cache" 2>/dev/null; then
                echo "==> ONNX JNI lib backed up to cache: $lib"
            fi
        elif [[ ! -f "$in_libs" && -f "$in_cache" ]]; then
            if cp "$in_cache" "$in_libs" 2>/dev/null; then
                echo "==> ONNX JNI lib restored from cache: $lib"
            fi
        fi
    done

    local still_missing=()
    for lib in "${ONNX_LIBS[@]}"; do
        [[ -f "$VIPS_LIB_DIR/$lib" ]] || still_missing+=("$lib")
    done
    if [[ ${#still_missing[@]} -gt 0 ]]; then
        cat >&2 <<EOF

WARNING: ONNX native libs missing (not in jniLibs nor cache): ${still_missing[*]}
  -> the APK builds and runs fine, but the smart panel-by-panel webtoon reader
     (PANELS mode) is DISABLED; webtoons fall back to continuous vertical scroll.
  -> to enable it once, build them via the Docker toolchain — after that this
     script auto-caches and protects them on every future build:

       docker build -t kora-android-native -f cmake/android.Dockerfile .
       docker run --rm -v "\$(pwd)":/build kora-android-native aarch64
       cp cmake/build-android-aarch64/sysroot/lib/{libomp.so,libkomelia_onnxruntime.so} \\
          "$VIPS_LIB_DIR/"

EOF
    fi
    return 0
}

ensure_jni_libs() {
    ensure_sqlite_jni
    ensure_vips_jni
    ensure_onnx_jni
}

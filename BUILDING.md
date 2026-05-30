# Building Kora

Kora is an Android-first fork of [Komelia](https://github.com/Snd-R/Komelia) /
Sipurra. Only **Android** builds are supported here (the desktop/wasm targets are
inherited from upstream and are not maintained — see `README`).

All build/release flows go through the helper scripts in `scripts/`, run from
**WSL** (bash). Building from PowerShell / Git Bash is discouraged: the release
APK is signed with a keystore resolved WSL-side, and switching shells mid-cycle
can pick a different keystore and break in-place installs.

## Prerequisites

- **WSL** (Ubuntu) on Windows, or a Linux/macOS shell.
- **Android SDK** with build-tools (the scripts auto-discover it under
  `$ANDROID_HOME`, `~/Android/Sdk`, or the Windows-side
  `…/AppData/Local/Android/Sdk`).
- **JDK 17** (the Gradle toolchain targets 17).
- `adb` — on WSL it's invoked from the Windows SDK; USB device access works only
  through the Windows-side adb server.

> **Native libraries (JNI).** `libsqlitejdbc.so`, `libvips` and friends are not
> committed. The build scripts restore them automatically
> (`scripts/_ensure_jni_libs.sh`, from `~/.kora-jnilibs-cache/` or the SQLite
> JAR). A fresh checkout therefore "just works"; you do not extract them by hand.

## Debug build — KoraDebug

Fast iteration build, installed as **KoraDebug** (`io.github.mkdevtests.kora.debug`),
a separate package that coexists with the release Kora.

```bash
bash scripts/build-kora-debug.sh [--clean]
```

In WSL the script builds + signs, then prints the `adb install` command to run
from PowerShell (WSL can't see USB devices directly).

## Release build — Kora (local install)

Builds the **Kora** release APK (`io.github.mkdevtests.kora`) and installs it on a
connected device.

```bash
bash scripts/build-kora-release.sh [--clean] [--migrate]
```

- The release build is **non-debuggable** and signed (see **Signing** below).
- `--migrate` copies user data from KoraDebug into Kora after install (via a
  `run-as` tar pipe). Because `run-as` needs a debuggable target, `--migrate`
  builds a **debuggable** release (`-PdebuggableRelease`). Without `--migrate`,
  the build is non-debuggable.

## Cutting a public release

`release-kora.sh` is the single entry point. It bumps the version everywhere,
builds the signed release APK, commits, tags, pushes, and publishes the GitHub
release on `MKDevTests/Kora` so the in-app updater can pick it up.

```bash
bash scripts/release-kora.sh <version> [notes-or-path]
#   e.g.  bash scripts/release-kora.sh 1.2.0 release_notes_1.2.0.md
```

- Must be on `main` with a clean tree. The script (and `preflight.sh`) check the
  branch, tag uniqueness, JNI libs, **migration registration**, and version
  consistency before touching anything.
- Do **not** bump `app-version` / `AppVersion.current` / `versionCode`, pre-commit
  `chore(release):`, or pre-tag — the script does all of that and refuses a state
  where it was already done.
- The APK uploaded must end with `.apk` (the auto-updater looks for the first
  `.apk` asset).
- A guard refuses to publish a **debuggable** APK.

## Signing

Signing happens in `build-kora-release.sh` (zipalign + apksigner):

- **By default**, the Android **debug keystore** is used. This is the same
  signature Kora has always shipped with, so updates install over existing
  installs seamlessly — **no reinstall, no data loss**.
- To switch to a dedicated **release keystore** (recommended once you go wider),
  export these before building — nothing is committed:

  ```bash
  export KORA_RELEASE_KEYSTORE=~/keys/kora-release.keystore
  export KORA_RELEASE_KEYSTORE_PASSWORD=********
  export KORA_RELEASE_KEY_ALIAS=kora          # optional, defaults to "kora"
  export KORA_RELEASE_KEY_PASSWORD=********    # optional, defaults to store pass
  ```

  > ⚠️ Switching keystores changes the app signature. The first release signed
  > with a new key **cannot update over** installs signed with the old key —
  > users must export a backup, uninstall, and reinstall once. Plan this for a
  > clearly-announced version.

## Variants

The Android variant is selected via the Gradle property `snd.android.variant`
(`STANDALONE` default, `FDROID`, `PLAY`). Only `STANDALONE` enables the in-app
self-updater.

# Privacy Policy — Kora

Kora is a free, open-source Android client for [Komga](https://komga.org/) media
servers, distributed as a sideloaded APK. This policy describes what Kora does
with your data. It is written in plain language; if anything is unclear, please
open an issue on the project repository (see **Contact** below).

_Effective date: 2026-05-30._

## Short version

**Kora collects no analytics, no advertising data, and no telemetry, and sends
nothing to the developer.** It contains no third-party tracking, analytics, or
crash-reporting services. Everything Kora stores is kept **locally on your
device**. The only servers Kora talks to are the ones **you** point it at (your
Komga server) plus, optionally, GitHub (for app updates) and model/metadata
sources for opt-in features.

## What Kora stores on your device

All of the following stays on your device and is never transmitted to the
developer:

- **Reading data** — reading progress, reading statistics, and per-series
  ratings you give.
- **App settings** — reader, display, and feature preferences.
- **Server configuration** — the address of your Komga server and the
  credentials needed to connect to it, stored in the app's local storage.
- **Folder / file access** — the locations (URIs) you grant Kora access to,
  e.g. an auto-backup folder or files you open from a file manager.
- **Backups** — JSON backup files you (or the auto-backup feature) export to a
  folder you choose. These are yours; Kora does not upload them anywhere.
- **Diagnostic logs** — app logs and a startup logcat snapshot, written to
  Kora's local storage to help diagnose problems. They stay on the device
  unless you choose to share them.
- **Optional on-device processing** — if you enable them, OCR (text recognition)
  and audio transcription run **locally on your device**.

## Network connections Kora makes

- **Your Komga server** — Kora connects to the server address you configure to
  authenticate and stream your library. This is your own server; Kora is just a
  client.
- **GitHub (optional updates)** — the in-app updater can check
  `github.com/MKDevTests/Kora` for new releases and download an update APK. This
  only happens when you check for updates or have automatic checks enabled.
- **Optional model downloads** — if you enable OCR or AI image upscaling, Kora
  may download the required model files from their distribution source.
- **Optional metadata (Komf)** — if you configure a Komf server, Kora contacts
  it for metadata.
- **Any other server you explicitly configure.**

Kora makes no network connections beyond these.

## Network security note

To support self-hosted Komga servers on a home network, Kora permits plain
**HTTP** connections and accepts **user-installed / self-signed certificates**.
This is a deliberate trade-off for LAN / self-hosted use. Connecting to a server
over an untrusted network (e.g. public Wi-Fi without a VPN) is less secure as a
result — prefer HTTPS or a VPN/LAN when possible.

## Permissions Kora requests, and why

- **Internet** — to connect to your Komga server and (optionally) GitHub.
- **Notifications** — for update, download, and playback notifications.
- **Install packages** — used only to install an app update you chose to
  download through the in-app updater.
- **Record audio** — used only by the optional audio / transcription features.
- **Foreground service (media playback)** — used only for audiobook playback.

The optional features only run, and their permissions are only used, if you
enable them.

## Data retention

Everything Kora stores lives on your device until you delete it. Uninstalling
Kora removes its local data. Any backups you exported are yours to keep or
delete.

## Children

Kora does not knowingly collect any information from anyone, including children.

## Changes

This policy may be updated. Changes will be published by updating this file in
the project repository.

## Contact

Questions about privacy? Open an issue at
<https://github.com/MKDevTests/Kora/issues>.

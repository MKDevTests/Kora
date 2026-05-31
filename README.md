# Kora — a Komga client for Android

**English** · [Français](README.fr.md)

Kora is an Android client for [Komga](https://komga.org) media servers. It is a fork of
[Sipurra](https://github.com/eserero/Sipurra) — itself a fork of [Komelia](https://github.com/Snd-R/Komelia) —
and inherits every feature of both upstreams. Kora focuses on the reading experience on phones and tablets:
reading habits, data safety, and everyday polish.

> Kora targets Android. The desktop and web (Wasm) targets are inherited from upstream and are kept for
> easier merges, but they are not actively maintained or shipped here.

---

## What's new in Kora

These are the additions Kora layers on top of Sipurra, grouped by theme. For the full per-version
changelog, see the [release notes](https://github.com/MKDevTests/Kora/releases).

### Reading stats
- A reading-stats card on Home and a dedicated stats screen, combining the Komga API with a local
  `reading_events` log.
- Books-completed and pages-read tiles, plus a history chart with a time-window selector (7 days / 30 days
  / 12 months) and a per-bar completion count.

### Ratings
- Per-series star ratings, stored locally.
- An optional "just finished — rate this series?" prompt and a long-press menu on covers for quick actions.

### Home & discovery
- Extra Home shelves: "Keep reading", "On deck", "Almost finished", and "Forgotten".
- A cached random shelf that survives navigation (pull-to-refresh forces a reshuffle) and a
  continue-reading shortcut.

### Backup & restore
- One-tap JSON export and import of app settings, home filters, library filters, per-series reader
  overrides, ratings and reading history.
- Automatic backups to a folder of your choice, on a schedule, with old copies pruned automatically.
- A versioned backup format with a validated, pre-flight **dry-run** that shows exactly what a restore
  will change before applying it; malformed entries are skipped and reported. Covered by a round-trip test.

### Home-screen widget
- A "Next book up" home-screen widget (Glance) that refreshes when the app goes to the background, with
  install-failure notifications surfaced rather than swallowed.

### Search
- An "Authors" tab to browse by author and jump to that author's series and books.

### Reader
- The book title shown in the reader's top bar alongside the page counter.
- An optional minimal UI while reading (a slim progress strip that expands to full controls on demand).
- Numerous reader fixes (paged-reader resume on quick taps, crop-borders blank-page crash, webtoon
  auto-detection, hard stop at the end of a book in continuous mode, and more).

### Diagnostics
- A Diagnostics screen (Settings → App Settings): app version, online/offline mode and active server,
  cache sizes, offline-download storage, background-task status, and an in-app log viewer with a
  redacted log export and a configurable log size cap. Includes a safe "Clear image cache" action.

### Navigation
- A library switcher in the page title for one-tap switching between libraries.
- A configurable startup screen (Home or the last opened library).

### Hardening for public release
- Release builds are non-debuggable and signed consistently for seamless in-app updates.
- A rewritten, accurate privacy policy ([PRIVACY_POLICY.MD](PRIVACY_POLICY.MD)) and build documentation
  ([BUILDING.md](BUILDING.md)).
- A test that guards database-migration registration against a recurring footgun.

---

## Inherited from Sipurra and Komelia

Sipurra is a fork of [Komelia](https://github.com/Snd-R/Komelia) focused on the Android experience. It
keeps everything Komelia offers and adds a great deal on top:

- A completely revamped UX.
- **Multi-server support**: add and switch between multiple Komga accounts.
- A new EPUB 3 reader supporting immersive audio + text reading (based on Storyteller).
- Audiobook playback with **live transcription**.
- A richer comic reader (**OCR text selection**, AI upscaling, extra navigation options, improved panel
  mode, and more).
- Bookmarks and annotations for books and audiobooks, plus highlighting for books and comics, synchronized
  across devices.
- **Local file support**: native PDF, CBR and CBZ handling with page and bookmark persistence.

### New UX and themes
- **Material 3**: a standardized `TopAppBar`, a cleaner menu, a library hamburger menu, a floating filter
  FAB, and a standardized navigation bar.
- **Multi-server management**: add, switch and manage multiple Komga connections, with per-server session,
  login and offline-sync state.
- **Smaller thumbnails**: an option for 3 thumbnails per row (size 110) instead of two, with display
  options (text below the thumbnail, transparent text background).
- **New dark and light themes**: clearer colors and an optional "haze" effect (blur + transparency) on
  toolbars and floating elements.

### Library screen
- A cleaner design with easier sorting access.
- A horizontal "Keep reading" strip of recently read books per library.
- An improved filtering UI.
- Library navigation that remembers the last library you were in.

| Library Light Theme | Library Dark Theme | Library with Continue Reading |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Library Screen Light Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Library Screen Dark Modern theme.jpg" width="250"> | <img src="screenshots/New UI 2/Library Screen Light modern theme with Continue reading.jpg" width="250"> |

### Home screen
- A design consistent with the library screen, with section management on a bottom-right FAB for one-handed
  use.
- A horizontal layout (Keep reading, On deck, etc.) for a compact, discoverable dashboard.
- A quick offline toggle in the top app bar.

### Search
- A full Material 3 `SearchBar` with smooth animations, native back-navigation and clear-text support.

### Immersive detail screens (book, series, oneshot)
- Full-bleed cover images behind the information card, with swipe navigation between books and floating
  read/download buttons.
- Publisher icons (when a match is found).
- Adaptive card colors sampled from the cover artwork (configurable).
- Material 3 elevated cards with shared-element transitions from the library list.

| Immersive Series (Collapsed) | Immersive Series (Expanded) | Immersive Series (Alt) |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Immersive Series Screen Collapsed.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Series Screen Expanded.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Series Screen Collapsed 2.jpg" width="250"> |

| Immersive Series (Alt Expanded) | Immersive Book View | Immersive Book (Expanded) |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Immersive Series Screen Expanded 2.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Book view.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Book with expanded button.jpg" width="250"> |

### Image / comic reader
- New controls aligned with the EPUB reader and the adaptive card colors, with quick access to reading
  modes (page, continuous, panel), upscaling, and rotation lock.
- **OCR text selection** via Google ML Kit and RapidOCR, with on-demand scanning and an auto-scan mode;
  bounding boxes are merged by reading direction (LTR/RTL).
- **Text actions**: translate, copy, or annotate OCR-selected text (notes pinned to the relevant area).
- A horizontal thumbnail carousel with auto-scroll synchronization.
- Adaptive immersive backgrounds that sample edge colors in real time (paged and panel modes).
- High-performance GPU upscaling (NCNN) optimized for Android hardware.
- Smooth swipe navigation in page mode and improved panel pan-and-zoom.
- Save the current image to Downloads (long-press), configurable double-tap zoom, multiple tap-zone
  configurations, navigation history with a floating back button, and a keep-screen-awake option.

**Adaptive backgrounds**
| Light Theme | Dark Theme | Controls Hidden |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Adaptive Background with Light Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Adaptive Background with Dark Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Adaptive Background without controls.jpg" width="250"> |

**GPU upscaling comparison**
| Without Upscaling | With Upscaling |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Adaptive backgrouund without upscaling.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - adaptive background with upscaling.jpg" width="250"> |

| Without Upscaling (Controls) | With Upscaling (Controls) |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Comic page with controls without upscaling.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Comic page with controls with upscaling.jpg" width="250"> |

| Without Upscaling (B&W) | With Upscaling (B&W) |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Comic page with controls without upscaling B&W.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Comic page with controls with upscaling B&W.jpg" width="250"> |

### EPUB reader with EPUB 3 audio layer and audiobooks
- A new EPUB viewer based on Storyteller and the Readium Kotlin Toolkit, supporting EPUBs with an audio
  layer for combined text + audio reading.
- Long-press text for a native context menu (translate, copy, search).
- An integrated audiobook player with chapter navigation, a mini player and a full-screen interface:
  - **Live transcription** for folder-based audiobooks using Whisper (local/native) and ML Kit, with a
    scrollable transcript UI.
  - Automatic extraction and caching of embedded chapter metadata.
  - A metadata dialog for track tags and embedded chapters.
- A new settings screen (themes, margins, fonts, audio), swipe/scroll navigation, and bookmarks and text
  search, plus a keep-screen-awake master setting.

**Controls and audio player**
| Light Theme | Dark Theme | Mini Player |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader with control panel and mini audio player Light Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader with control panel and mini audio player Dark Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader with mini audio controls.jpg" width="250"> |

**Expanded audio player**
| Light Theme | Dark Theme |
| :---: | :---: |
| <img src="screenshots/New UI 2/Epu3 reader expanded audio player light theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epu3 reader expanded audio player Dark theme.jpg" width="250"> |

**Reader features**
| Table of Contents | Bookmarks | Search |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader Table of Content.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader Bookmarks.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader Search.jpg" width="250"> |

### Settings
- A refactored, more modern Material 3 menu structure.
- Advanced cache management: configurable limits for the image and EPUB readers, LRU trimming and manual
  clearing.
- New visual toggles: immersive-color strength sliders, app-wide accent presets, and a master toggle for
  the new library UI.
- Deep customization: per-mode tap-to-zoom, configurable tap-navigation zones, and granular adaptive
  background settings.

**Comic reader settings**
| Reading Modes | Image Settings | Navigation |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader Settings - Reading Modes.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader Settings - Image Settings.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader Settings -  Navigation.jpg" width="250"> |

**EPUB reader settings**
| Appearance | Fonts | Audio |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader settings - Appearance.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader settings - Fonts.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader settings - audio.jpg" width="250"> |

### General
- **Prefer local files**: downloaded files are used instead of the server when available.
- Improved offline support: native local PDF and CBR files, in addition to EPUB and CBZ.
- Open local CBZ, CBR, PDF and EPUB files via the Android share/open menu, with page and bookmark
  persistence.
- Rescan for existing local files to relink them after a device transfer or fresh install.

---

## Screenshots

<details>
  <summary>Mobile</summary>
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Kora" width="270">
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Kora" width="270">
</details>

<details>
  <summary>Tablet</summary>
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/1.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/2.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/3.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/4.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/5.jpg" alt="Kora" width="400" height="640">
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/6.jpg" alt="Kora" width="400" height="640">
</details>

---

## Downloads

- **Kora (this fork)**: https://github.com/MKDevTests/Kora/releases — a signed Android APK, picked up
  automatically by the in-app updater.
- Sipurra (upstream, Android): https://github.com/eserero/Sipurra/releases
- Komelia (original, all platforms): https://github.com/Snd-R/Komelia/releases — also on Google Play,
  F-Droid and the AUR (different package IDs, installed separately from Kora).

---

## Building

Kora ships as an Android app, built and signed through the scripts in `scripts/`. See
**[BUILDING.md](BUILDING.md)** for the full instructions, including the one-time native-library setup
(libvips and SQLite JNI) and the release workflow.

## Privacy

Kora is local-first and contains no analytics or telemetry. It talks only to the Komga server you
configure, to GitHub for update checks, and — optionally — to model/Komf endpoints you enable. See
**[PRIVACY_POLICY.MD](PRIVACY_POLICY.MD)** for details.

## Acknowledgements

Kora stands on the work of others:

- **[Komelia](https://github.com/Snd-R/Komelia)** — the original Kotlin Multiplatform Komga client.
- **[Sipurra](https://github.com/eserero/Sipurra)** — the Android-focused fork Kora is based on.
- **[Storyteller](https://gitlab.com/storyteller-platform/storyteller)** — the EPUB 3 audio + text engine
  the immersive reader builds on.
- **[waifu2x-ncnn-vulkan](https://github.com/nihui/waifu2x-ncnn-vulkan)** — the NCNN upscaling
  implementation on Android.
- **[RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)** — additional NCNN models
  (RealSR, RealCUGAN, etc.).

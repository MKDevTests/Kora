# Release 2.2.0 Summary

This release introduces significant enhancements to the reading and listening experience, including multi-server support, integrated OCR for comics, local PDF/CBR support, and advanced audiobook transcription.

## Features

### Multi-Server Support
- **Multi-Server Management:** Users can now add, switch between, and manage multiple Komga server connections. Session management, login flows, and offline synchronization have been updated to support per-server states.

### Enhanced Comic Reader (OCR & Navigation)
- **OCR Text Selection:** Integrated Google ML Kit and RapidOCR as pluggable engines for text recognition in comic readers. Supports on-demand scanning and an "Auto-Scan" mode that triggers OCR on page load.
- **Translation & Text Actions:** Directly Translate, copy, or create annotations from OCR-selected text.
- **Multi-Language OCR:** Expanded OCR support to include Chinese, Devanagari, Japanese, and Korean.
- **Smart Segment Merging:** OCR bounding boxes are logically merged based on reading direction (LTR/RTL) for a more natural text selection experience.
- **Add Note from OCR:** Users can now directly create annotations from OCR-selected text, with the note automatically pinned to the relevant area.
- **Thumbnail Carousel:** Added a horizontal thumbnail strip to the image reader for fast visual navigation, complete with auto-scroll synchronization to keep the active page centered.
- **Navigation History:** Added navigation history and a floating back button for better exploration within the reader.

### Audio & Transcription Improvements
- **EPUB Translation:** Added a "Translate" option to the EPUB text selection context menu.
- **Whisper & ML Kit Transcription:** Added real-time transcription for folder-based audiobooks using both Google ML Kit and Whisper (Local/Native). Includes look-ahead pre-fetching for Whisper to ensure seamless playback.
- **Transcript UI:** Transcription is displayed as a scrollable chat-log with chunk dividers and proximity shading to orient the user within the audio.
- **Embedded Metadata & Chapters:** Audiobook folder mode now extracts and caches embedded chapter metadata (titles, start/end times) using FFmpeg, providing accurate chapter navigation instead of simple filenames.
- **Audio Metadata Dialog:** A new info button in the player allows viewing detailed track tags and embedded chapters.

### Offline & Local Files
- **PDF & CBR Support:** Added native support for local PDF (via Android PdfRenderer) and CBR/RAR (via junrar) files in both offline mode and via local file intents.
- **Offline WiFi Toggle:** A new quick-toggle in the top app bar allows users to manually switch between online and offline modes.

### System & UI Improvements
- **Cache Management:** Added user-configurable cache limits for both Image and EPUB readers, including LRU-based trimming and manual cache clearing options.
- **Thumbnail Polish:** Standardized visual indicators for downloaded and completed books, including new bookmark icons and refined badges.
- **Upstream Sync:** Merged core updates from upstream 0.18.5, including Kotlin 2.3.21 and Compose 1.11.0-beta03.

## Bug Fixes

- **Performance:** Resolved severe scrolling freezes in the thumbnail carousel by optimizing image pre-fetching and restoration of native Compose/Coil recycling.
- **Search Reliability:** Fixed keyboard focus and input issues on the mobile search screen, ensuring the keyboard opens reliably and focus is handled correctly.
- **UI Clipping:** Ensured logout buttons and search results are correctly positioned above system navigation bars on mobile devices.
- **Stability:** Fixed potential crashes when switching server profiles without offline data and swallowed network timeouts in reader event listeners to prevent app crashes.
- **EPUB Recovery:** Implemented automatic cache recovery for corrupted EPUB files by validating core container files and forcing refreshes when necessary.
- **OCR Threading:** Moved RapidOCR processing to background threads to eliminate UI jank during page turns.

---

# Commits since Release 2.1.0

# fix(ui): ensure logout button and search results are visible above navigation bar on mobile
54e7950c - eserero - 2026-04-29


---

# fix(search): fix keyboard focus and input issues on mobile search screen
3e7e52d7 - eserero - 2026-04-29

- Restore Column-based layout for SearchBarWithResults to prevent full-screen panel overlay issues.
- Implement explicit auto-focus and keyboard showing with FocusRequester and delay.
- Rename local SearchBar to ResultsSearchBar to avoid name collision with Material 3 SearchBar.
- Use query/onQueryChange directly instead of TextFieldState for better reliability.

---

# fix: back button shadow rendering and alignment
7f1af6b6 - eserero - 2026-04-29


---

# feat: add navigation history and floating back button for image reader
bb3e10ed - eserero - 2026-04-29


---

# feat(carousel): add auto-scroll synchronization and view carousel button
5978d73a - eserero - 2026-04-29

- Implement bidirectional auto-scroll in ThumbnailCarousel to keep active page visible
- Add 'View Carousel' icon to control panel with improved alignment and sizing
- Optimize control panel click area to only cover the page info label
- Synchronize View Carousel icon and styling across bottom sheet and slider labels

---

# feat(carousel): polish carousel usability and highlighting
e81499ea - eserero - 2026-04-28

- Highlight active page with primary accent color (2dp border)
- Center current page only on initial carousel open
- Keep carousel open after page selection for better multi-page navigation
- Reset carousel state to slider view when dismissing reader controls
- Balance spacing and increase thumbnail size by 10% for improved legibility

---

# style(carousel): improve thumbnail carousel usability and aesthetics
cf70f4c8 - eserero - 2026-04-28

- Implement end-to-end background by adding isFullWidth support to ReaderControlsCard
- Add page numbers above thumbnails in the carousel
- Reduce thumbnail size by 25% (200dp to 150dp) for better screen utilization
- Support optional squared corners for BookPageThumbnail to match the edge-to-edge look

---

# fix(carousel): resolve severe performance freezes during scrolling
db66d56e - eserero - 2026-04-28

- Remove manual, un-cancellable Coil prefetching that caused thread starvation
- Add Modifier.fillMaxSize() to BookPageThumbnail to enable Coil downsampling for high-res images
- Add stable keys to ThumbnailCarousel LazyRow to eliminate layout thrashing
- Restore native Compose/Coil recycling and cancellation behavior

---

# chore: merge upstream updates and fix stability issues
27014945 - eserero - 2026-04-28

- Updated core dependencies to match upstream 0.18.5 (Kotlin 2.3.21, Compose 1.11.0-beta03, etc.)
- Fixed login crash when switching server profiles without offline data
- Improved native ONNX bridge with full optimizations and better WebGPU targeting
- Resolved FileKit 0.13.0 API breaking changes in file pickers
- Updated ONNX runtime to 1.25.0 for desktop and native builds
- Fixed native library conflict with libdatastore_shared_counter.so
- Added UPSTREAM_MERGE_PLAN.md documenting the process

---

# Merge pull request #7 from eserero/feat/multi-server-support
8173bba9 - eserero - 2026-04-28

Feat/multi server support
---

# feat(multi-server): support multiple Komga server accounts
86ffb863 - eserero - 2026-04-28

Allows users to add, switch between, and manage multiple Komga server
connections. Session management, login flow, app module wiring, offline
module, and server management UI are all updated to handle per-server
state.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# docs: add thumbnail carousel implementation plan
9d6cfb59 - eserero - 2026-04-28

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# feat(reader): add thumbnail carousel to image reader
939c3c99 - eserero - 2026-04-28

Implements a horizontal thumbnail strip in the image reader settings overlay,
allowing fast visual navigation. Fixes a critical loading bug where all page
thumbnail fetches were serialised through a single-threaded scope with
limitedParallelism(1); the async/await-across-scope-boundary pattern also
prevented Coil cancellation from propagating, causing stale requests to
pile up and block new ones. Removed the artificial scope so Coil manages
concurrency and cancellation directly.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# feat(transcription): pre-fetch Whisper audio 11s ahead, add chunk dividers, fix decode crash
8e2cc2c6 - eserero - 2026-04-28

- Increase AudioPreReader look-ahead from 7s to 11s so Whisper finishes
  inference before the audio reaches the transcribed chunk
- Tag each TranscriptSegment with chunkId (= bufferStartMs) so the UI can
  detect 10-second batch boundaries; null for ML Kit segments (unaffected)
- Render a thin white HorizontalDivider between Whisper chunks in
  TranscriptPanel so the user can orient within the transcript
- Catch IOException/RuntimeException from MediaExtractor.setDataSource in
  AudioPreReader.run() — log a warning and skip ahead by one chunk-length
  instead of crashing the pre-reader coroutine

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# fix(ocr): run RapidOCR on background thread to prevent UI jank on page turn
fd196c6a - eserero - 2026-04-27

RapidOCR's engine.run() is synchronous and was blocking the main thread,
causing a visible freeze each time a page change triggered OCR. Wrap the
call in withContext(Dispatchers.Default) and track the Job so stale OCR
is cancelled when the page changes rather than running to completion.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# Merge branch 'main' into feat/multi-server-support
28e940be - eserero - 2026-04-27


---

# feat(ocr): enhance RapidOCR with multilingual support and dynamic downloads
a21b1c6b - eserero - 2026-04-27

- Introduce RapidOcrModelDownloader to fetch OCR models dynamically from GitHub.
- Refactor OcrService.android.kt to support 7 language model options (EN/ZH, EN, Latin, JA, KO, AR, HE).
- Add UI for RapidOCR model selection in the reader settings and model download in main settings.
- Implement SQL migrations (V50, V51) to store new OCR settings and update model download URLs.
- Optimize APK size by excluding bundled .onnx models from the build.
- Fix compilation issues and restore missing imports in the UI layer.
- Update documentation with new model management and download instructions.

---

# feat(ocr): integrate RapidOCR as alternative engine for Android
cab3a353 - eserero - 2026-04-27

- Integrate io.github.hzkitty:rapidocr4j-android:1.0.0
- Resolve java.lang.UnsatisfiedLinkError (OrtSessionOptionsAppendExecutionProvider_Nnapi)
  by removing the bundled libonnxruntime.so from jniLibs to avoid conflict
  with onnxruntime-android:1.23.0
- Fix OpenCV crash (AndroidBitmap_lockPixels) by ensuring software-backed Bitmaps
  are passed to RapidOCR instead of HARDWARE config bitmaps
- Optimize APK size by limiting abiFilters to arm64-v8a (~90MB reduction)
- Update UI to toggle between ML Kit and RapidOCR engines
- Hide language selection for RapidOCR as it uses a multilingual model
- Add implementation and fix documentation

Co-Authored-By: Gemini CLI <noreply@google.com>

---

# fix(transcription): add script-seeding prompts for non-Latin languages
126ce53f - eserero - 2026-04-27

Whisper can output transliterated Latin text for Hebrew, Arabic, Japanese
and other non-Latin scripts instead of native Unicode characters. Setting
an initial_prompt in the target script biases the model to output correctly.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# chore: remove debug log file
459df9f2 - eserero - 2026-04-27

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# fix(transcription): fix whisper performance and accuracy for audiobook use
27c12a3a - eserero - 2026-04-27

- Always compile whisper/ggml with -O3 regardless of build type; debug
  builds were running completely unoptimized causing ~24s per 5s chunk
- Set audio_ctx to actual audio length so encoder skips silence padding
  (whisper defaults to 30s context; for 10s chunks this is a ~3x saving)
- Switch model from tiny to base for better transcription accuracy
- Re-enable cross-chunk context (no_context=false) for better coherence
- Increase chunk threshold to 10s for more context per inference call
- Scope sgemm.cpp and dotprod flags to arm64-v8a only to fix armeabi-v7a build
- Switch model download URL from HuggingFace to GitHub releases (model tag)
- Add inference timing logs for performance monitoring

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# feat(ocr): add 'Add Note' option to text selection menu
f0a4cc50 - eserero - 2026-04-26

- Added 'Add Note' to OCR text selection context menu.
- Automatically pins note to top-left of selected text block.
- Pre-populates the annotation dialog with selected text.
- Integrated across Paged, Continuous, and Panels reader modes.
- Fixed 1-indexed to 0-indexed page mapping for comic annotations.

---

# feat(ocr): implement segment merging and toggle setting
51171c56 - eserero - 2026-04-26

- Added OcrMergeUtils for logical segment merging based on reading direction.
- Integrated merging into ReaderState with LTR/RTL synchronization.
- Added 'ocr_merge_boxes' setting to database and UI.
- Improved Image Reader settings tab spacing and scrolling behavior.
- Renamed settings tabs and labels for clarity (Display, Navigation, Image, Text).

---

# feat(reader): improve OCR reliability and add 'Select Text' context menu option
17faadf9 - eserero - 2026-04-26

- Clear OCR results and page ID when OCR is disabled.
- Fix OCR auto-triggering on reader mode switch (Paged <-> Panels) by observing loaded image flows.
- Add 'Select Text' option to the long-press context menu for on-demand scanning when auto-OCR is off.

---

# feat: improve OCR text selection with multi-language support and auto-scanning
60167732 - eserero - 2026-04-25

- Add support for Chinese, Devanagari, Japanese, and Korean OCR languages via ML Kit.
- Implement toggleable auto-scanning mode that runs OCR automatically on page load.
- Reorder reader control panel to a single row for better accessibility.
- Add horizontally scrollable 'OCR' settings tab for language selection.
- Persist OCR configuration in SQLite database (Migration V48).
- Fix initialization race condition causing NullPointerException in ReaderState.

---

# feat: implement OCR text selection in comic reader using Google ML Kit
dbabc400 - eserero - 2026-04-25


---

# feat: implement pluggable transcription engine and whisper support
b94ad830 - eserero - 2026-04-25


---

# feat(settings): implement user-configurable cache management for readers
1791524a - eserero - 2026-04-24

- Add imageCacheSizeLimitMb and epubCacheSizeLimitMb settings
- Implement LRU-based trimming for EPUB3 reader cache
- Implement configurable disk cache limit for Image Reader dedicated image loader
- Add UI sliders for cache size limits in Image and EPUB reader settings
- Add 'Clear EPUB cache' functionality
- Implement database migration V46 for new settings
- Add cache management implementation plan documentation

---

# fix(epub): implement automatic cache recovery for corrupted EPUBs
d88c39e0 - eserero - 2026-04-24

- Add META-INF/container.xml validation to EPUB cache check\n- Implement automatic retry with force refresh if publication fails to open\n- Ensure cache directory is cleared before re-extraction if corrupted

---

# ui: improve thumbnail visual indicators
5923b70 - eserero - 2026-04-24

- Replace downloaded checkmark with downward arrow icon
- Standardize badge styling with new IndicatorBadge component
- Replace unread corner tick with bookmark icon for completed books
- Add drop shadow to bookmark icon for better visibility
- Refine series unread count font size and background

---

# feat(offline): add support for CBR files in offline mode and local files
3fd677ba - eserero - 2026-04-24

- Add junrar dependency for RAR extraction\n- Implement RarExtractor and DivinaRarExtractor for Android and Desktop\n- Enable CBR/RAR support in LocalFileBookApi\n- Add intent filters for CBR, RAR, and PDF files in AndroidManifest.xml

---

# feat(offline): implement Phase 1 offline PDF support for Android
6c61b317 - eserero - 2026-04-24

- Added PdfExtractor interface in commonMain
- Implemented AndroidPdfExtractor using native PdfRenderer
- Integrated PdfExtractor into BookContentExtractors for offline mode
- Added local PDF support to LocalFileBookApi for Android Intents
- Updated Android and Desktop modules to handle PdfExtractor instantiation

---

# feat(transcription): add live real-time transcription for folder audiobooks
0781754c - eserero - 2026-04-24

- Implement LiveTranscriptEngine with MlKit speech recognition
- Display transcription as chat-log: new text at bottom, history scrolls up
- Auto-scroll to latest segment on new content
- Shade upcoming segments by proximity to playback position (7s lookahead)
- Move transcribe toggle to left of chapter chip
- Move audio info button to cover image top-right, hidden during transcription
- Remove old bottom action row

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# feat(epub): add Translate option to text selection menu
53d3410e - eserero - 2026-04-23


---

# feat: implement multi-server support
d04188f9 - eserero - 2026-04-22


---

# docs: add audio metadata button plan
ab3b57ad - eserero - 2026-04-22


---

# docs: add new spec for offline icon and models dialog crash fix
064a22d4 - eserero - 2026-04-22


---

# feat: use embedded chapter metadata in audiobook folder mode
1ea6471a - eserero - 2026-04-20

Extract per-file chapter metadata (title, start/end times) via FFmpeg
during first open and cache it in SQLite (V45 migration). Subsequent
opens load from cache instantly. Chapter list now shows proper chapter
names instead of filenames; falls back to file title tag, then cleaned
filename when metadata is absent. The chapter chip in the player UI
also shows the chapter name rather than the track title.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# feat: add audio metadata and chapters button to EPUB3 reader
44a6ddd2 - eserero - 2026-04-20

- Add FFmpegMediaMetadataRetriever dependency to androidMain
- Implement audio metadata and chapter extraction in AudiobookFolderController
- Add AudioMetadataDialog to display extracted tags and chapters
- Add info button to AudioFullScreenPlayer to trigger the metadata dialog

---

# feat: add offline WiFi toggle button to NewTopAppBar
1cc1d774 - eserero - 2026-04-20

Adds a Wifi/WifiOff icon button to the top app bar (left of theme toggle) that lets the user switch between online and offline mode via a confirmation dialog. Adds goOffline() to MainScreenViewModel navigating to OfflineLoginScreen.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

# fix: swallow ConnectTimeoutException in reader event listeners
536b2beb - eserero - 2026-04-20

Wrap initialSync() calls in runCatching{} so network timeouts from Ktor
don't propagate uncaught through the coroutine scope and crash the app.
Sync will retry on the next ReadProgressChanged event.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

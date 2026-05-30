# Third-Party Components

This document lists the third-party libraries, components, submodules, and forks used in Kora.

## Project Origin
Kora is a personal, Android-focused fork of Sipurra, which is itself a fork of Komelia.

*   **Komelia** (upstream): <https://github.com/Snd-R/Komelia>
*   **Sipurra** (intermediate fork): <https://github.com/eserero/Sipurra>
*   **Kora** (this project): <https://github.com/MKDevTests/Kora>

## Kotlin / Gradle Dependencies
Managed via `gradle/libs.versions.toml`.

### AndroidX
- `androidx.activity:activity-compose`
- `androidx.appcompat:appcompat`
- `androidx.core:core-ktx`
- `androidx.datastore:datastore`
- `androidx.documentfile:documentfile`
- `androidx.window:window`
- `androidx.palette:palette-ktx`
- `androidx.work:work-runtime`

### Compose Multiplatform
- `org.jetbrains.compose.foundation`
- `org.jetbrains.compose.runtime`
- `org.jetbrains.compose.desktop`
- `org.jetbrains.compose.material3`
- `org.jetbrains.compose.material:material-icons-extended`
- `org.jetbrains.compose.components:components-resources`

### Networking & Serialization
- `io.ktor:ktor-client` (Core, Content-Negotiation, JS, Encoding, OkHttp)
- `com.squareup.okhttp3:okhttp`
- `com.google.protobuf:protobuf-javalite` / `protobuf-kotlin-lite`
- `org.jetbrains.kotlinx:kotlinx-serialization`
- `io.github.snd-r.komf:client` (Komf API Client)
- `io.github.snd-r:komga-client` (Komga API Client)

### Database & Persistence
- `org.jetbrains.exposed:exposed` (Core, JDBC, JSON, Kotlin-Datetime)
- `com.zaxxer:HikariCP-java7`
- `org.flywaydb:flyway-core`
- `org.xerial:sqlite-jdbc`

### Image & Media Processing
- `io.coil-kt.coil3:coil` (Coil 3 for image loading)
- `dev.chrisbanes.haze:haze` (Blur effects)
- `org.apache.commons:commons-compress`
- `androidx.palette:palette-ktx`

### UI Components & Navigation
- `cafe.adriel.voyager:voyager` (Navigator, ScreenModel, Transitions)
- `sh.calvin.reorderable:reorderable`
- `com.mohamedrejeb.richeditor:richeditor-compose`
- `io.github.vinceglb:filekit` (Core, Dialogs, Compose)

### Utilities
- `io.github.reactivecircus.cache4k:cache4k`
- `dev.dirs:directories`
- `com.github.javakeyring:java-keyring`
- `io.github.oshai:kotlin-logging`
- `org.jetbrains.kotlinx:kotlinx-datetime`
- `org.jetbrains.kotlinx:kotlinx-io`
- `com.fleeksoft.ksoup:ksoup`
- `org.jetbrains:markdown`

---

## Git Submodules (Forks by Snd-R)
Located in `third_party/`.

- **[secret-service](https://github.com/Snd-R/secret-service)**: Linux Secret Service API.
- **[compose-sonner](https://github.com/Snd-R/compose-sonner)**: Toast library for Compose Multiplatform.
- **[ChipTextField](https://github.com/Snd-R/ChipTextField)**: Material 3 Chip TextField.
- **[indexeddb](https://github.com/Snd-R/indexeddb)**: IndexedDB wrapper for Kotlin.

---

## C / C++ Dependencies (CMake)
External libraries integrated via CMake for native functionality (JNI).

- **vips**: libvips for high-performance image processing.
- **onnxruntime**: ONNX Runtime for ML models (upscaling).
- **dav1d**: AV1 decoder.
- **de265**: HEVC/H.265 decoder.
- **heif**: libheif for HEIF/AVIF support.
- **jxl**: libjxl for JPEG XL support.
- **mozjpeg** / **jpeg_turbo**: Optimized JPEG processing.
- **webp**: libwebp.
- **png**: libpng.
- **tiff**: libtiff.
- **exif**: libexif for metadata.
- **glib**: GLib (required by vips).
- **expat**: XML parser.
- **ffi**: libffi.
- **iconv**: Character encoding conversion.
- **zlib** / **brotli**: Compression.
- **highway**: SIMD library.
- **lcms2**: Little CMS for color management.

---

## Web UI Dependencies
Found in `komelia-epub-reader/`.

### Komga Web UI (`komga-webui`)
- **Vue.js 3**
- **Vuetify 3**
- **vue-i18n**
- **[@d-i-t-a/reader](https://github.com/gotson/R2D2BC)** (Forked): EPUB reader core.
- **Vite** (Build tool)

### TTU Ebook Reader (`ttu-ebook-reader`)
- **Svelte 5**
- **Tailwind CSS**
- **FontAwesome**
- **@zip.js/zip.js**
- **RxJS**
- **svelte-gestures**
- **ua-parser-js**

---

## Acknowledgments & Inspiration
The project acknowledges and uses code/logic from:

- **[Storyteller](https://gitlab.com/storyteller-platform/storyteller)**: Source for the EPUB 3 engine and combined Text+Audio reading.
- **[waifu2x ncnn Vulkan](https://github.com/nihui/waifu2x-ncnn-vulkan)**: Integration of NCNN-powered GPU upscaling.
- **[RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)**: NCNN models (RealSR, RealCUGAN, etc.).

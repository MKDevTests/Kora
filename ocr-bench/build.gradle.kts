/**
 * Bench for the page-translation geometry, deliberately outside the app graph.
 *
 * It compiles the shipped source files directly rather than depending on
 * :komelia-domain:core, for two reasons: that module's desktop target does not
 * build in this fork (DesktopOfflineModule references a PdfExtractor that only
 * exists on Android), and pulling the whole graph in would turn a two-second
 * check back into a build.
 *
 * The files listed below are the real ones, not copies — the bench cannot drift
 * from what ships without failing to compile.
 *
 *     ./gradlew :ocr-bench:test                       run the checks
 *     ./gradlew :ocr-bench:test --tests '*Bench*'     replay a captured volume
 */
plugins {
    alias(libs.plugins.kotlinJvm)
    // @Serializable in the volume replay needs the compiler plugin, not just the
    // runtime: without it the capture parses at runtime into "Serializer for
    // class 'PageJson' is not found".
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("../komelia-domain/core/src/commonMain/kotlin")
            // Nothing else from that tree: these have no dependency beyond
            // compose's Rect and the standard library, while their neighbours
            // pull in the whole app.
            kotlin.include(
                "snd/komelia/image/OcrElementBox.kt",
                "snd/komelia/image/OcrMergeUtils.kt",
                "snd/komelia/image/TranslationTextUtils.kt",
                "snd/komelia/image/TermGlossary.kt",
                "snd/komelia/image/BubbleAssembler.kt",
                "snd/komelia/image/EnglishTextCleaner.kt",
                "snd/komelia/image/PhraseBook.kt",
                "snd/komelia/image/OcrSpellRepair.kt",
            )
        }
    }
}

dependencies {
    implementation("org.jetbrains.compose.ui:ui-geometry:${libs.versions.compose.multiplatform.get()}")
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
    // Where run_ocr.py wrote its <page>.boxes.json. Absent, the replay is skipped.
    System.getenv("KORA_BENCH_DIR")?.let { environment("KORA_BENCH_DIR", it) }
    System.getenv("KORA_BENCH_VERTICAL")?.let { environment("KORA_BENCH_VERTICAL", it) }
}

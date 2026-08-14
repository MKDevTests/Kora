rootProject.name = "Komelia"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        // translate-kit, built from source by scripts/translatekit/build-aar.sh
        // and committed. Upstream publishes only to GitHub Packages, which needs
        // an auth token to read; the licence is Apache-2.0, so we build it.
        //
        // flatDir rather than files(): AGP only treats a dependency as an AAR
        // when it arrives through a repository, and a library module cannot
        // hand a files() AAR on to the application that consumes it. flatDir
        // carries no metadata, which is fine here — the AAR has no transitive
        // dependencies of its own, only a bundled .so.
        flatDir { dirs("$rootDir/third_party/translatekit") }
    }
}

include(":epub-reader")
include(":komelia-app")
include(":komelia-domain:core")
include(":komelia-domain:offline")
include(":komelia-domain:komga-api")
include(":komelia-ui")

include(":komelia-infra:audiobook-transcription")
include(":komelia-infra:database:transaction")
include(":komelia-infra:database:shared")
include(":komelia-infra:database:sqlite")
include(":komelia-infra:database:wasm")
include(":komelia-infra:image-decoder:shared")
include(":komelia-infra:image-decoder:vips")
include(":komelia-infra:image-decoder:wasm-image-worker")
include(":komelia-infra:jni")
include(":komelia-infra:ncnn-upscaler")
include(":komelia-infra:onnxruntime:api")
include(":komelia-infra:onnxruntime:jvm")
include(":komelia-infra:webview")

include(":komelia-komf-extension:app")
include(":komelia-komf-extension:content")
include(":komelia-komf-extension:background")
include(":komelia-komf-extension:popup")
include(":komelia-komf-extension:shared")

include(":third_party:ChipTextField:chiptextfield-core")
include(":third_party:ChipTextField:chiptextfield-m3")
include(":third_party:compose-sonner:sonner")
include(":third_party:indexeddb:core")
include(":third_party:indexeddb:external")

// Off-device bench for the page-translation geometry. Compiles the shipped
// merge/text sources directly; see ocr-bench/build.gradle.kts.
include(":ocr-bench")

includeBuild("third_party/secret-service") {
    dependencySubstitution { substitute(module("de.swiesend:secret-service")) }
}

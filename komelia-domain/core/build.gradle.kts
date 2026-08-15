import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.parcelize)
    alias(libs.plugins.protobuf)
}

group = "io.github.snd-r.komelia.domain.core"
version = "unspecified"

kotlin {
    jvmToolchain(17)

    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "komelia-webview"
        browser()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
        commonMain.dependencies {
            api(projects.komeliaDomain.komgaApi)
            api(projects.komeliaDomain.offline)
            api(projects.komeliaInfra.database.transaction)
            api(projects.komeliaInfra.imageDecoder.shared)
            api(projects.komeliaInfra.onnxruntime.api)

            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.cache4k)
            api(libs.coil)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.filekit.core)
            api(libs.komf.client)
            api(libs.komga.client)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.client.encoding)
            api(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ksoup)
            implementation(libs.markdown)
            implementation(libs.reorderable)
            implementation(libs.richEditor.compose)
        }

        androidMain.dependencies {
            api(libs.androidx.datastore)
            implementation(libs.androidx.appcompat)
            implementation(libs.mlkit.translate)
            // Bergamot/Marian behind a JNI wrapper, built by
            // scripts/translatekit/build-aar.sh and vendored in
            // third_party/translatekit, resolved through the flatDir repository
            // in settings.gradle.kts. Not in the version catalog: a flatDir
            // artifact has no group, and the catalog rejects an alias without
            // one. arm64-v8a only — TranslateKit.init degrades gracefully on
            // any other ABI rather than crashing.
            // String notation, because the named-argument overload does not
            // exist inside a KMP source-set dependencies block.
            implementation(":translate-kit-android@aar")
            implementation(libs.rapidocr.android)
            // Explicit: BubbleInvertStep uses the ai.onnxruntime JAVA API for the
            // speech-bubble detector. It arrives transitively via rapidocr, but a
            // transitive `implementation` is not on OUR compile classpath, so the
            // import would not resolve. Version is pinned by the resolutionStrategy
            // force below (must match the superbuild — see komelia-app).
            implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.commons.compress)
            api(libs.ktor.client.okhttp)
            api(libs.logback.android)
            api(libs.okhttp)
            api(libs.okhttp.logging.interceptor)
            implementation(libs.protobuf.javalite)
            implementation(libs.protobuf.kotlin.lite)
            implementation(libs.slf4j.api)
            implementation(projects.komeliaInfra.imageDecoder.vips)
            implementation(projects.komeliaInfra.onnxruntime.jvm)
            implementation(projects.komeliaInfra.ncnnUpscaler)
        }

        jvmMain.dependencies {
            implementation(libs.commons.compress)
            api(libs.directories)
            implementation(libs.java.keyring)
            implementation(libs.jbr.api)
            api(libs.ktor.client.okhttp)
            api(libs.logback.core)
            api(libs.logback.classic)
            api(libs.okhttp)
            api(libs.okhttp.logging.interceptor)
            implementation(libs.secret.service)
            implementation(libs.slf4j.api)
            implementation(projects.komeliaInfra.imageDecoder.vips)
            implementation(projects.komeliaInfra.onnxruntime.jvm)
        }

        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

configurations.all {
    resolutionStrategy {
        // Keep in sync with komelia-app/build.gradle.kts and the superbuild's
        // cmake/external/onnxruntime.cmake GIT_TAG. See the note there.
        force("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    }
}

android {
    namespace = "io.github.snd_r.komelia.domain.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].proto {
        srcDir("src/androidMain/proto")
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    protobuf {
        protoc {
            artifact = "com.google.protobuf:protoc:3.24.1"
        }
        generateProtoTasks {
            all().forEach { task ->
                task.builtins {
                    id("java") {
                        option("lite")
                    }
                    id("kotlin") {
                        option("lite")
                    }
                }
            }
        }
    }
}

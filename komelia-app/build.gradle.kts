import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

group = "io.github.snd-r.komelia"
version = libs.versions.app.version.get()

base {
    archivesName = "kora-app"
}

kotlin {
    jvmToolchain(17) // max version https://developer.android.com/build/releases/gradle-plugin#compatibility
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "sipurra-app"
        browser {
            commonWebpackConfig {
                outputFileName = "sipurra-app.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer())
            }
        }
        browser()
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            implementation(projects.komeliaUi)
            implementation(projects.komeliaDomain.core)
            implementation(projects.komeliaDomain.offline)
            implementation(projects.komeliaInfra.database.shared)
            implementation(projects.komeliaInfra.database.transaction)
            implementation(projects.komeliaInfra.webview)
            implementation(libs.kotlin.logging)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.window)
            implementation(libs.androidx.workManager)
            implementation(libs.androidx.workManager.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)
            implementation(libs.androidx.lifecycle.process)
            implementation(projects.komeliaInfra.database.sqlite)
            implementation(projects.komeliaInfra.ncnnUpscaler)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(projects.komeliaInfra.onnxruntime.jvm)
            implementation(libs.junrar)
        }
        jvmMain.dependencies {
            implementation(libs.jbr.api)
            implementation(projects.komeliaInfra.database.sqlite)
            implementation(projects.komeliaInfra.imageDecoder.vips)
            implementation(projects.komeliaInfra.onnxruntime.jvm)
            implementation(libs.filekit.core)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(projects.komeliaInfra.imageDecoder.wasmImageWorker)
            implementation(projects.komeliaInfra.database.wasm)
        }
    }
}

enum class AndroidVariant {
    STANDALONE,
    FDROID,
    PLAY
}

val androidVariant = runCatching {
    AndroidVariant.valueOf(
        (project.extraProperties["snd.android.variant"] as String).uppercase()
    )
}.getOrDefault(AndroidVariant.STANDALONE)

android {
    namespace = "io.github.snd_r.komelia"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val manifestFile = when (androidVariant) {
        AndroidVariant.STANDALONE -> "AndroidManifest.xml"
        AndroidVariant.FDROID -> "AndroidManifestFdroid.xml"
        AndroidVariant.PLAY -> "AndroidManifestPlay.xml"
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/$manifestFile")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")
    // NOTE: src/androidMain/assets is already packaged (that is where
    // logback.xml lives) — no srcDirs override needed, and none wanted since
    // srcDirs() would REPLACE the default list. The speech-bubble detector model
    // ships from there. It is unaffected by the `**/*.onnx` exclusion below:
    // that one applies to Java/JAR resources, not Android assets.

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "io.github.mkdevtests.kora"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        manifestPlaceholders["appLabel"] = "Kora"
        versionCode = 10505
        versionName = libs.versions.app.version.get()

        val enableSelfUpdates = when (androidVariant) {
            AndroidVariant.STANDALONE -> "true"
            AndroidVariant.FDROID -> "false"
            AndroidVariant.PLAY -> "false"
        }
        buildConfigField("boolean", "ENABLE_SELF_UPDATES", enableSelfUpdates)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    packaging {
        jniLibs {
            pickFirsts += "lib/*/libc++_shared.so"
            pickFirsts += "lib/*/libonnxruntime.so"
            pickFirsts += "**/libdatastore_shared_counter.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,README.txt}"
            excludes += "**/*.onnx"
            pickFirsts += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    dependenciesInfo {
        if (androidVariant != AndroidVariant.PLAY) {
            includeInApk = false
            includeInBundle = false
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "KoraDebug"
        }
        release {
            // Public release: NON-debuggable.
            //
            // R8 ENABLEMENT — Step A (perf/r8-enable): minify ON, resource
            // shrinking still OFF. This is the risk-first order:
            //  - android.pro keeps all app code (`snd.komelia.**`,
            //    `io.github.snd_r.komelia.**`, `org.sqlite.**`) and uses
            //    `-dontobfuscate`, so R8 tree-shakes/optimizes libs only and
            //    stack traces stay readable (no retrace needed).
            //  - isShrinkResources stays false: it can strip the Flyway SQL
            //    migrations (composeResources, read by name). That is Step B,
            //    validated separately.
            // Rollback levers, cheapest first: (1) set
            //   android.enableR8.fullMode=false in gradle.properties (less
            //   aggressive); (2) flip isMinifyEnabled back to false; (3) revert
            //   this branch. R8 mapping is at
            //   komelia-app/build/outputs/mapping/release/mapping.txt.
            // Later optimization pass: run the official android/skills
            // r8-analyzer to narrow the broad app-wide keeps.
            //
            // Pass -PdebuggableRelease for a debuggable release build (one-off
            // run-as KoraDebug -> Kora data migration).
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = project.hasProperty("debuggableRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "android.pro"
            )
        }
        // R8 SANDBOX — installs as a SEPARATE app ("KoraR8", appId suffix
        // .r8test) so R8 can be validated without ever touching the real Kora
        // (io.github.mkdevtests.kora) install. Inherits the release build's R8
        // config via initWith(release): minify on, shrink off, same proguard.
        // Debug-signed so `adb install` works directly, but NOT debuggable:
        // AGP disables every R8 optimization for debuggable builds ("All code
        // optimizations and obfuscation are disabled for debuggable builds"),
        // so a debuggable KoraR8 silently tested something the real release
        // never runs — which is exactly how the Bitmap.recycle crash got
        // blamed on the optimization pass that was already off.
        // `run-as` is lost; logcat and native tombstones still work, and
        // -dontobfuscate keeps traces readable. Not for shipping — testing only.
        create("releaseTest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".r8test"
            manifestPlaceholders["appLabel"] = "KoraR8"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configurations.all {
    resolutionStrategy {
        // MUST match the ONNX Runtime version built by the superbuild
        // (cmake/external/onnxruntime.cmake, GIT_TAG v1.25.0). libkomelia_onnxruntime.so
        // links the VERSIONED symbol OrtGetApiBase@VERS_<x.y.z>, so a mismatch makes
        // dlopen fail -> OnnxRuntimeSharedLibraries.isAvailable stays false -> the whole
        // ONNX feature (panel detection, upscaling) vanishes from the UI with no error
        // shown on Android. Bump both sides together.
        force("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    }
}


dependencies {
    add("coreLibraryDesugaring", libs.android.desugar.jdk.libs)
}

compose.desktop {
    application {
        mainClass = "snd.komelia.MainKt"

        jvmArgs += listOf(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseShenandoahGC",
            "-XX:ShenandoahGCHeuristics=compact",
            "-XX:ConcGCThreads=1",
            "-XX:TrimNativeHeapInterval=60000",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Kora"
            packageVersion = libs.versions.app.version.get()
            description = "Komga media client"
            vendor = "eserero"
            appResourcesRootDir.set(
                project.projectDir.resolve("desktopUnpackedResources")
            )
            modules("jdk.security.auth", "java.sql")

            windows {
                menu = true
                upgradeUuid = "40E86376-4E7C-41BF-8E3B-754065032B22"
                iconFile.set(project.file("src/jvmMain/resources/ic_launcher.ico"))
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/ic_launcher.png"))
            }
        }

        buildTypes.release.proguard {
            version.set("7.8.0")
            optimize.set(false)
            configurationFiles.from(project.file("desktop.pro"))
        }
    }
}

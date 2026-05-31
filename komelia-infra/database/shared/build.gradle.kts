import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "io.github.snd-r.komelia.db.shared"
version = "unspecified"

kotlin {
    jvmToolchain(17)
    jvm {}
    androidTarget {}

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "komelia-infra-database-shared"
        browser()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            implementation(projects.komeliaDomain.core)
            implementation(projects.komeliaDomain.offline)
            implementation(libs.filekit.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.ktor.client.core)
            implementation(libs.komga.client)
            implementation(projects.komeliaInfra.imageDecoder.shared)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "io.github.snd_r.komelia.infra.database.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The backup round-trip unit test serializes settings that embed a FileKit
// PlatformFile, and FileKit ships Java 21 bytecode (class file v65). The test
// JVM must therefore be >= 21 even though we compile to 17. Scoped to unit
// test execution only — compilation stays on 17 and the shipped APK is
// unaffected (D8/ART accept any bytecode version at dex time).
val javaToolchainService = extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    javaLauncher.set(
        javaToolchainService.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
        }
    )
}


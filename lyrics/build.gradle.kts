@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    // AGP KMP plugin (same as :settings / :media-store) — supplies the `android {}` KMP target.
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "io.ymusic.lyrics"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    withIosTargets()
    withMacosTargets()
    withLinuxTargets()

    applyDefaultHierarchyTemplate()

    sourceSets {
        // No UI. Parsers/selector/timeline stay pure; providers, cache and repository sit on top of the
        // app's KmpHttpClient (KmpLyricsHttp), okio (FileLyricsCache) and the settings keys.
        commonMain.dependencies {
            api(Libs.Kotlinx.coroutinesCore)
            api(project(":kmpHttp:core")) // KmpHttpClient — the caller supplies it
            api(Libs.Okio.core) // FileSystem / Path for FileLyricsCache
            api(project(":settings")) // LyricsSettings keys
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(Libs.Kotlinx.coroutinesTest)
        }
    }
}

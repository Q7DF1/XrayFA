// Top-level build file where you can add configuration options common to all sub-projects/modules.

// F-Droid / Linux CI only build Android. Kotlin/Native iOS targets would
// otherwise download the konan toolchain during assembleRelease.
// Override: -PenableIosTargets=true|false
extra["enableIosTargets"] = run {
    when (findProperty("enableIosTargets")?.toString()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> System.getProperty("os.name").orEmpty().startsWith("Mac")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
//    kotlin("kapt") version "2.2.10" apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.ksp) apply false
}
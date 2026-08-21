plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.androidx.datastore.preferences.core)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        androidMain.dependencies {
            implementation(libs.androidx.datastore.preferences)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.gson)
        }
    }
}

android {
    namespace = "com.android.xrayfa.datastore"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

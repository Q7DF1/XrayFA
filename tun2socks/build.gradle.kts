plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "hev.htproxy"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        //applicationId = "hev.htproxy"
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//        ndk {
//            abiFilters += listOf("armeabi-v7a","arm64-v8a","x86","x86_64")
//        }
        externalNativeBuild {
            ndkBuild {
                arguments  += listOf("APP_CFLAGS+=-DPKGNAME=xrayfa/tun2socks -ffile-prefix-map=${rootDir}=."
                ,"APP_LDFLAGS+=-Wl,--build-id=none")
            }
        }
    }



    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

// ... (Your existing android and dependencies blocks) ...

// 1. Register a custom task to execute the batch script
tasks.register<Exec>("fixNativeHeaders") {
    description = "Run batch script to fix naked C/C++ header paths before NDK build"
    group = "build setup"

    // Define the path to your .bat script (assuming it's in the same folder as build.gradle.kts)
    val scriptFile = file("../fix_headers.bat")

    // Only run on Windows
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        // Execute the script using cmd.exe
        commandLine("cmd", "/c", scriptFile.absolutePath)

        // IMPORTANT: Set the working directory to your JNI folder so the script scans the right place.
        // The '.' in the script will map to this directory.
        workingDir = file("src/main/jni")
    } else {
        // Fallback for Linux/Mac if you ever build on other platforms
        commandLine("echo", "Skipping Windows batch script on non-Windows OS")
    }
}

// 2. Hook the custom task into the standard Android build lifecycle
// This ensures 'fixNativeHeaders' runs right before any other build steps begin
tasks.named("preBuild") {
    dependsOn("fixNativeHeaders")
}

// Alternatively, strictly hook it right before the external native build generation
tasks.whenTaskAdded {
    if (name.startsWith("generateJsonModel")) {
        dependsOn("fixNativeHeaders")
    }
}

dependencies {

    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)
    implementation (libs.dagger.android)
    ksp(libs.dagger.android.processor)
}
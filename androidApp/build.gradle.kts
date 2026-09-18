import com.android.build.VariantOutput
import com.android.build.gradle.api.ApkVariantOutput

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.android.xrayfa"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        val VERSION_NAME:String by project
        val VERSION_CODE:String by project
        val APPLICATION_ID = findProperty("APPLICATION_ID") as String? ?: "com.android.xrayfa"
        applicationId = APPLICATION_ID
        minSdk = 28
        targetSdk = 36
        versionCode = VERSION_CODE.toInt()
        versionName = VERSION_NAME

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    base {
        archivesName.set("XrayFA")
    }
    signingConfigs {
        create("release") {
            val keystoreFile = project.file("xrayfa.jks")
            if(keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }else {
                println("keystore file not found , building unsigned release apk")
            }
        }
    }

    //Remove DependencyInfoBlock for F-Droid
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    buildTypes {
        release {
            val keystoreFile = project.file("xrayfa.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packagingOptions.jniLibs.useLegacyPackaging = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    lint {
        // Components are constructed by XrayAppCompatFactory (Koin), not the default ctor.
        disable += "Instantiatable"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    splits {
        abi {
            // Disable APK splits when building App Bundle (AAB), otherwise it causes
            // "Sequence contains more than one matching element" in buildReleasePreBundle
            isEnable = gradle.startParameter.taskNames.none { it.contains("Bundle", true) }
            reset()
            val fdroidAbi = (findProperty("fdroidAbi") as String?)?.trim().orEmpty()
            if (fdroidAbi.isNotEmpty()) {
                include(fdroidAbi)
                isUniversalApk = false
            } else {
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

    // Manifest versionCode is ABI-specific (35001..). AGP 8 still copies that into
    // BuildConfig.VERSION_CODE, so GitHub's all-ABI build (universal → 35000) and
    // F-Droid's single-ABI recipe (armeabi-v7a → 35001) bake different constants
    // into classes.dex. Pin BuildConfig to gradle.properties VERSION_CODE.
    applicationVariants.configureEach {
        val baseVersionCode = (project.findProperty("VERSION_CODE") as String).toInt()
        val variantDirName = dirName
        outputs.configureEach {
            val apkOutput = this as ApkVariantOutput
            val abi = apkOutput.getFilter(VariantOutput.FilterType.ABI)
            val abiCode = abiCodes[abi] ?: 0
            apkOutput.versionCodeOverride = baseVersionCode * 1000 + abiCode
        }
        tasks.named("generate${name.replaceFirstChar { it.uppercase() }}BuildConfig") {
            doLast {
                val buildConfigFile = layout.buildDirectory.get().asFile.resolve(
                    "generated/source/buildConfig/$variantDirName/com/android/xrayfa/BuildConfig.java",
                )
                check(buildConfigFile.isFile) {
                    "BuildConfig.java not found at $buildConfigFile"
                }
                val original = buildConfigFile.readText()
                val pinned = "VERSION_CODE = $baseVersionCode;"
                val updated = original.replace(Regex("""VERSION_CODE = \d+;"""), pinned)
                check(updated.contains(pinned)) {
                    "Failed to pin BuildConfig.VERSION_CODE to $baseVersionCode in $buildConfigFile"
                }
                buildConfigFile.writeText(updated)
            }
        }
    }
}



val xrayLibDir = rootProject.file("AndroidLibXrayLite")
val aarOutput = xrayLibDir.resolve("libv2ray.aar")

val libsDir = file("libs")

tasks.register<Exec>("buildGoMobile") {
    workingDir = xrayLibDir
    commandLine("go","install","golang.org/x/mobile/cmd/gomobile@latest")
}

tasks.register<Exec>("initGoMobile") {
    //dependsOn("buildGoMobile")
    workingDir = xrayLibDir
    commandLine("gomobile","init")
}
tasks.register<Exec>("goMod") {
    dependsOn("initGoMobile")
    workingDir = xrayLibDir
    commandLine("go","mod","tidy","-v")
}


tasks.register<Exec>("bindXrayLib") {
    dependsOn("goMod")
    workingDir = xrayLibDir
    environment("GOFLAGS", "-buildvcs=false")
    environment("CGO_LDFLAGS", "-Wl,--build-id=none")

    val currentPath = xrayLibDir.absolutePath
    environment("CGO_CFLAGS", "-ffile-prefix-map=$currentPath=.")
    environment("CGO_CXXFLAGS", "-ffile-prefix-map=$currentPath=.")
    commandLine(
        "gomobile",
        "bind",
        "-v",
        "-trimpath",
        "-androidapi", "21",
        "-ldflags=-s -w -buildid= -checklinkname=0",
        "./"
    )
    outputs.file(aarOutput)
}

tasks.register<Copy>("copyXrayLib") {
    dependsOn("bindXrayLib")
    from(aarOutput)
    into(libsDir)
}

tasks.named("preBuild") {
    // personal compile can use it,but at server use script
    //dependsOn("copyXrayLib")
}

ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

dependencies {

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(project(":tun2socks"))
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:native-bridge"))
    implementation(project(":platform:vpn"))
    implementation(project(":shared"))
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.essenty.backhandler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.adaptive)
    // Zxing
    implementation(libs.zxing.core)
    // CameraX Essential
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.javax.annotation.api)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    // appfunctions-service uses Guava at runtime; AGP consistent-resolution then
    // replaces listenablefuture:1.0 with the empty 9999 stub on compile classpath.
    implementation("com.google.guava:guava:32.0.1-android")
    ksp(libs.androidx.appfunctions.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
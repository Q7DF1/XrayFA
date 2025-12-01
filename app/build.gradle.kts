import com.google.protobuf.gradle.id
import com.android.build.api.variant.FilterConfiguration.FilterType.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt") version "2.2.10"
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "com.android.xrayfa"
    compileSdk = 36

    defaultConfig {
        val appVersionName:String by project
        val appVersionCode:String by project
        applicationId = "com.android.xrayfa"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode.toInt()
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }


    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a","arm64-v8a","x86","x86_64")
            isUniversalApk = false
        }
    }

    flavorDimensions += "abi"
    val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

    androidComponents  {
        onVariants { variant ->
            variant.outputs.forEach { output->
                val name = output.filters.find { it.filterType == ABI } ?.identifier


                val baseAbiCode = abiCodes[name] ?: 0

                if(baseAbiCode != null) {
                    output.versionCode.set((baseAbiCode * 1000 + output.versionCode.get()))
                }
            }
        }
    }
}



val xrayLibDir = rootProject.file("AndroidLibXrayLite")
val aarOutput = xrayLibDir.resolve("libv2ray.aar")

val libsDir = file("libs")

//tasks.register<Exec>("buildGoMobile") {
//    workingDir = xrayLibDir
//    commandLine("go","install","golang.org/x/mobile/cmd/gomobile@latest")
//}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
            task.builtins {
                id("java")
            }
        }
    }
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
    commandLine(
        "gomobile",
        "bind",
        "-v",
        "-androidapi", "21",
        "-ldflags=-s -w",
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
    dependsOn("copyXrayLib")
}


dependencies {

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(project(":tun2socks"))
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
    implementation (libs.dagger.android)
    kapt(libs.dagger.android.processor)
    implementation(libs.zxing.android.embedded)
    implementation(libs.gson)


    implementation(libs.androidx.navigation.compose)


    implementation("io.grpc:grpc-okhttp:1.63.0")
    implementation("io.grpc:grpc-protobuf:1.63.0")
    implementation("io.grpc:grpc-stub:1.63.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    // 在 build.gradle 中
    implementation ("com.maxmind.geoip2:geoip2:4.2.0")
    implementation(libs.androidx.datastore.preferences)

    implementation (libs.okhttp)
    implementation (libs.logging.interceptor)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
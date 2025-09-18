import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt") version "2.2.10"
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "com.android.v2rayForAndroidUI"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.v2rayForAndroidUI"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
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

    implementation(libs.gson)


    implementation(libs.androidx.navigation.compose)


    implementation("io.grpc:grpc-okhttp:1.63.0")
    implementation("io.grpc:grpc-protobuf:1.63.0")
    implementation("io.grpc:grpc-stub:1.63.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
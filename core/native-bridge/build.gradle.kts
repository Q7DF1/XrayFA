plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

val libXrayXcframework = rootProject.file("AndroidLibXrayLite/LibXrayLite.xcframework")

gradle.taskGraph.whenReady {
    val compilingThisIos = allTasks.any { task ->
        task.project == project &&
            task.name.contains("Ios", ignoreCase = true) &&
            (
                task.name.startsWith("compile") ||
                    task.name.startsWith("cinterop") ||
                    task.name.startsWith("link")
                )
    }
    if (compilingThisIos) {
        check(libXrayXcframework.isDirectory) {
            "LibXrayLite.xcframework not found. Run ./scripts/build_libxray_ios.sh first."
        }
    }
}

fun iosXcframeworkSlice(targetName: String): String =
    when (targetName) {
        "iosArm64" -> "ios-arm64"
        "iosSimulatorArm64", "iosX64" -> "ios-arm64_x86_64-simulator"
        else -> error("Unsupported iOS target for LibXrayLite: $targetName")
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
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                val libv2ray by creating {
                    defFile(project.file("src/nativeInterop/cinterop/libv2ray.def"))
                    val slice = iosXcframeworkSlice(target.name)
                    val frameworkDir = libXrayXcframework.resolve("$slice/LibXrayLite.framework")
                    includeDirs(project.file("src/nativeInterop/cinterop/headers"))
                    compilerOpts("-F${frameworkDir.parent}")
                }
            }
        }
        target.binaries.all {
            val slice = iosXcframeworkSlice(target.name)
            linkerOpts("-F${libXrayXcframework.resolve(slice)}")
            linkerOpts("-framework", "LibXrayLite")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // compileOnly: AGP forbids packaging local .aar into another AAR; :androidApp supplies libv2ray at runtime.
            compileOnly(
                files(rootProject.file("androidApp/libs/libv2ray.aar")),
            )
            // compileOnly: JNI .so comes from :tun2socks / :androidApp at runtime; compile against TProxyService API.
            compileOnly(project(":tun2socks"))
        }
    }
}

android {
    namespace = "com.android.xrayfa.nativebridge"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

fun iosSdkAndArch(targetName: String): Pair<String, String> =
    when (targetName) {
        "iosArm64" -> "iphoneos" to "arm64"
        "iosSimulatorArm64" -> "iphonesimulator" to "arm64"
        "iosX64" -> "iphonesimulator" to "x86_64"
        else -> error("Unsupported iOS target: $targetName")
    }

listOf("iosArm64", "iosSimulatorArm64", "iosX64").forEach { targetName ->
    val capital = targetName.replaceFirstChar { it.uppercase() }
    val outFile = layout.buildDirectory.file("nativeDelayShim/$targetName/XrayFAMeasureOutboundDelay.o")
    tasks.register<Exec>("compileDelayShim$capital") {
        val mFile = file("src/nativeInterop/cinterop/XrayFAMeasureOutboundDelay.m")
        val headers = file("src/nativeInterop/cinterop/headers")
        inputs.files(mFile)
        inputs.dir(headers)
        outputs.file(outFile)
        val (sdk, arch) = iosSdkAndArch(targetName)
        val outPath = outFile.get().asFile.absolutePath
        val mPath = mFile.absolutePath
        val headerPath = headers.absolutePath
        commandLine(
            "bash",
            "-c",
            """
            set -euo pipefail
            mkdir -p "$(dirname '$outPath')"
            SDK="${'$'}(xcrun --sdk $sdk --show-sdk-path)"
            xcrun --sdk $sdk clang -c -fobjc-arc -arch $arch -isysroot "${'$'}SDK" \
              -I "$headerPath" \
              "$mPath" -o "$outPath"
            """.trimIndent(),
        )
    }
}

afterEvaluate {
    kotlin.targets
        .withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java)
        .filter { it.name.startsWith("ios") }
        .forEach { target ->
            val capital = target.name.replaceFirstChar { it.uppercase() }
            val compileShim = tasks.named("compileDelayShim$capital")
            val oFile =
                layout.buildDirectory
                    .file("nativeDelayShim/${target.name}/XrayFAMeasureOutboundDelay.o")
                    .get()
                    .asFile
            target.binaries.all {
                linkTaskProvider.configure { dependsOn(compileShim) }
                linkerOpts(oFile.absolutePath)
            }
        }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    linuxX64 {
        compilations.getByName("main").cinterops.create("host") {
            defFile(project.file("src/nativeInterop/cinterop/host.def"))
            includeDirs(project.file("src/nativeInterop/cinterop"))
        }
        binaries.executable {
            baseName = "xrayfa"
            entryPoint = "com.android.xrayfa.cli.main"
        }
    }
    sourceSets {
        linuxMain.dependencies {
            implementation(project(":common"))
            implementation(project(":domain"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

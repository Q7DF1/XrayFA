plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "hev.htproxy"
    compileSdk = 36

    defaultConfig {
        //applicationId = "hev.htproxy"
        minSdk = 28
        targetSdk = 35

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

dependencies {

    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
    implementation (libs.dagger.android)
    kapt(libs.dagger.android.processor)
}
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class xrayfa.tun2socks.TProxyService {
    native <methods>;
}

-keepattributes SourceFile,LineNumberTable

-keep class com.android.xrayfa.model.** { *; }
-keep class com.android.xrayfa.common.repository.** { *; }
-keep class com.android.xrayfa.agent.appfunctions.** { *; }
-keep class androidx.appfunctions.** { *; }

# Koin (runtime DI; minify would strip modules / factories)
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# Decompose navigation + kotlinx serializers used by RootTab / RootOverlay
-keep class com.arkivanov.decompose.** { *; }
-keep class com.arkivanov.essenty.** { *; }
-keep class com.android.xrayfa.shared.navigation.** { *; }

-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.android.xrayfa.**$$serializer { *; }
-keepclassmembers class com.android.xrayfa.** {
    *** Companion;
}
-keepclasseswithmembers class com.android.xrayfa.** {
    kotlinx.serialization.KSerializer serializer(...);
}
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

# Keep Koin related classes
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# Keep kotlinx serialization classes
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class kotlinx.serialization.** { *; }

# Keep domain entities and value objects
-keep class com.x3squaredcircles.pixmap.shared.** { *; }

# Keep Ktor client classes
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep camera and location services
-keep class com.x3squaredcircles.pixmap.androidapp.** { *; }
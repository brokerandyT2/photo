//app/src/main/kotlin/com/x3squaredcircles/pixmap/di/AllAndroidModules.kt
package com.x3squaredcircles.pixmap.di

import com.x3squaredcircles.pixmap.shared.di.allSharedModules

/**
 * Combines all Android-specific modules with shared modules
 */
val allAndroidModules = allSharedModules + listOf(
    // Android-specific modules
    androidPlatformModule,
    androidSubscriptionModule,
    navigationModule
)
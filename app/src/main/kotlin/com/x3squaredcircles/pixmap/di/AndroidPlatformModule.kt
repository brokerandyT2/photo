//app/src/main/kotlin/com/x3squaredcircles/pixmap/di/AndroidPlatformModule.kt
package com.x3squaredcircles.pixmap.di

import android.content.Context
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.*
import com.x3squaredcircles.pixmap.androidapp.services.*
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Android platform-specific dependency injection module
 */
val androidPlatformModule = module {

    // Android Context
    single<Context> { get() }

    // Platform-specific services
    singleOf(::AndroidCameraService) { bind<ICameraService>() }
    singleOf(::AndroidLocationService) { bind<ILocationService>() }
    singleOf(::AndroidGeolocationService) { bind<IGeolocationService>() }
    singleOf(::AndroidNotificationService) { bind<INotificationService>() }
    singleOf(::AndroidFileService) { bind<IFileService>() }
    singleOf(::AndroidMediaService) { bind<IMediaService>() }

    // Android-specific implementations
    singleOf(::AndroidValidationService) { bind<IValidationService>() }
    singleOf(::AndroidCacheService) { bind<ICacheService>() }
}
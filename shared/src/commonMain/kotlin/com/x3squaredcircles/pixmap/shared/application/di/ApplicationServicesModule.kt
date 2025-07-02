// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/di/ApplicationServicesModule.kt
package com.x3squaredcircles.pixmap.shared.application.di

import com.x3squaredcircles.pixmap.shared.application.interfaces.services.*
import com.x3squaredcircles.pixmap.shared.infrastructure.services.*
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Dependency injection module for application services
 * Provides service interfaces and their implementations
 */
val applicationServicesModule = module {

    // Core Application Services
    singleOf(::ErrorDisplayService) { bind<IErrorDisplayService>() }
    singleOf(::LoggingService) { bind<ILoggingService>() }
    singleOf(::TimezoneService) { bind<ITimezoneService>() }
    singleOf(::AlertService) { bind<IAlertService>() }

    // Media and Location Services (platform-specific implementations)
    // Note: These are provided by platform-specific modules (AndroidPlatformModule, etc.)
    // singleOf(::MediaService) { bind<IMediaService>() }
    // singleOf(::CameraService) { bind<ICameraService>() }
    // singleOf(::LocationService) { bind<ILocationService>() }
    // singleOf(::GeolocationService) { bind<IGeolocationService>() }
    // singleOf(::NotificationService) { bind<INotificationService>() }

    // Weather and External Services
    singleOf(::WeatherService) { bind<IWeatherService>() }

    // Event Bus and Domain Services
    singleOf(::InMemoryEventBus) { bind<IEventBus>() }

    // File System Services (platform-specific)
    // singleOf(::FileService) { bind<IFileService>() }

    // Validation Services
    // singleOf(::ValidationService) { bind<IValidationService>() }

    // Cache Services
    // singleOf(::CacheService) { bind<ICacheService>() }
}

/**
 * Module for service interfaces that don't require implementations in shared code
 * These are typically provided by platform-specific modules
 */
val platformServicesModule = module {
    // Platform services are injected by:
    // - androidPlatformModule (for Android)
    // - iosPlatformModule (for iOS, when implemented)

    // Services that should be provided by platform modules:
    // - ICameraService
    // - ILocationService
    // - IGeolocationService
    // - IMediaService
    // - INotificationService
    // - IFileService
    // - IBiometricService (future)
    // - IDeviceInfoService (future)
    // - IConnectivityService (future)
}

/**
 * Complete application services module that includes all service-related modules
 */
val completeApplicationServicesModule = module {
    includes(
        applicationServicesModule,
        platformServicesModule
    )
}

/**
 * Service factory interfaces for advanced dependency injection scenarios
 */
val serviceFactoryModule = module {
    // Factory interfaces for creating services with different configurations
    // single<IWeatherServiceFactory> { WeatherServiceFactory(get(), get()) }
    // single<ILocationServiceFactory> { LocationServiceFactory(get()) }
}

/**
 * Service decorators and interceptors module
 */
val serviceInterceptorsModule = module {
    // Decorators for adding cross-cutting concerns
    // single<ILoggingServiceDecorator> { LoggingServiceDecorator(get()) }
    // single<ICachingServiceDecorator> { CachingServiceDecorator(get()) }
    // single<IRetryServiceDecorator> { RetryServiceDecorator(get()) }
}

/**
 * Background services and scheduled tasks module
 */
val backgroundServicesModule = module {
    // Background processing services
    // single<IBackgroundTaskService> { BackgroundTaskService(get()) }
    // single<IScheduledTaskService> { ScheduledTaskService(get()) }
    // single<IWeatherUpdateService> { WeatherUpdateService(get(), get()) }
}

/**
 * All application service modules combined
 */
val allApplicationServiceModules = module {
    includes(
        completeApplicationServicesModule,
        serviceFactoryModule,
        serviceInterceptorsModule,
        backgroundServicesModule
    )
}
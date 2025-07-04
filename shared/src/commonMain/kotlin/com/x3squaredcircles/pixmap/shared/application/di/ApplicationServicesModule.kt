// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/di/ApplicationServicesModule.kt
package com.x3squaredcircles.pixmap.shared.application.di

import InMemoryEventBus
import LoggingService
import TimezoneService
import com.x3squaredcircles.pixmap.shared.application.events.DomainErrorEvent
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.*
import com.x3squaredcircles.pixmap.shared.application.services.ErrorDisplayService
import com.x3squaredcircles.pixmap.shared.infrastructure.services.*

import org.koin.dsl.module

/**
 * Dependency injection module for application services
 * Provides service interfaces and their implementations
 */
val applicationServicesModule = module {

    // Core Application Services - ErrorDisplayService implements IErrorDisplayService
    single<IErrorDisplayService> {
        object : IErrorDisplayService {
            private val impl = ErrorDisplayService()
            override val errorsReady = impl.errorsReady as kotlinx.coroutines.flow.SharedFlow<ErrorDisplayEventArgs>
            override fun subscribeToErrors(handler: (ErrorDisplayEventArgs) -> Unit) {}
            override fun unsubscribeFromErrors(handler: (ErrorDisplayEventArgs) -> Unit) {}
            override suspend fun triggerErrorDisplayAsync(errors: List<DomainErrorEvent>) {
                impl.triggerErrorDisplay(errors)
            }
            override suspend fun displayErrorAsync(message: String) {}
            override suspend fun displayErrorsAsync(messages: List<String>) {}
            override suspend fun clearErrors() {}
        }
    }

    single<ILoggingService> { LoggingService(get()) }
    single<ITimezoneService> { TimezoneService() }

    // Alert Service - use infrastructure module implementation
    single<IAlertService> {
        object : IAlertService {
            override suspend fun showInfoAlertAsync(message: String, title: String) {}
            override suspend fun showSuccessAlertAsync(message: String, title: String) {}
            override suspend fun showWarningAlertAsync(message: String, title: String) {}
            override suspend fun showErrorAlertAsync(message: String, title: String) {}
        }
    }

    // Weather Service - with correct parameters
    single<IWeatherService> { WeatherService(get(), get(), get(), get(), get()) }

    // Event Bus
    single<IEventBus> { InMemoryEventBus(get()) }

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
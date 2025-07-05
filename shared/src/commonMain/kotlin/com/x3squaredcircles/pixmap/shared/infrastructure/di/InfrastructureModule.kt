// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/di/InfrastructureModule.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.di

import InMemoryEventBus
import LoggingService
import TimezoneService
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.*
import com.x3squaredcircles.pixmap.shared.infrastructure.data.DatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories.*
import com.x3squaredcircles.pixmap.shared.infrastructure.services.*
import com.x3squaredcircles.pixmap.shared.infrastructure.unitofwork.UnitOfWork
import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*

import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val infrastructureModule = module {

    // Database
    single<IDatabaseContext> { DatabaseContext(get(), get(), get(), get()) }
    single<IUnitOfWork> { UnitOfWork(get(), get(), get(), get(), get(), get(), get(), get()) }

    // Exception Mapping Service
    single<IInfrastructureExceptionMappingService> { InfrastructureExceptionMappingService(get()) }

    // Persistence-layer repositories
    single<LocationRepository> { LocationRepository(get(), get(), get()) }
    single<WeatherRepository> { WeatherRepository(get(), get(), get()) }
    single<TipRepository> { TipRepository(get(), get(), get()) }
    single<TipTypeRepository> { TipTypeRepository(get(), get(), get()) }
    single<SettingRepository> { SettingRepository(get(), get(), get()) }

    // HTTP Client for Weather API
    single<HttpClient> {
        HttpClient() {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
            }
        }
    }

    // Services
    single<ILoggingService> { LoggingService(get()) }
    single<ITimezoneService> { TimezoneService() }
    single<ILocationRepository> { LocationRepositoryAdapter(get()) }
    single<IWeatherRepository> { WeatherRepositoryAdapter(get()) }
    single<ITipRepository> { TipRepositoryAdapter(get()) }
    single<ITipTypeRepository> { TipTypeRepositoryAdapter(get()) }
    single<ISettingRepository> { SettingRepositoryAdapter(get()) }

    // Alerting services
    single<IAlertService> { AlertingService(get()) }

    // Event Bus
    single<IEventBus> { InMemoryEventBus(get()) }
}

// Database initialization module - separate to allow for conditional registration
val databaseInitializationModule = module {
    single<DatabaseInitializationService> { DatabaseInitializationService(get(), get(), get()) }
}

class DatabaseInitializationService(
    private val databaseContext: IDatabaseContext,
    private val logger: ILoggingService,
    private val settingRepository: ISettingRepository
) {

    suspend fun initializeAsync() {
        try {
            logger.info("Starting database initialization...")

            logger.info("Initializing database...")
            databaseContext.initializeDatabaseAsync()
            logger.info("Database initialized successfully")

            seedInitialDataAsync()
        } catch (ex: Exception) {
            logger.error("Failed to initialize database", ex)
            throw ex
        }
    }

    private suspend fun seedInitialDataAsync() {
        try {
            val apiKeyResult = settingRepository.getByKeyAsync("WeatherApiKey")
            if (!apiKeyResult.isSuccess || apiKeyResult.data == null) {
                val setting = com.x3squaredcircles.pixmap.shared.domain.entities.Setting.create(
                    key = "WeatherApiKey",
                    value = "YOUR_API_KEY_HERE",
                    description = "API key for OpenWeatherMap service - obtain one at https://openweathermap.org"
                )
                settingRepository.createAsync(setting)
                logger.info("Created default WeatherApiKey setting")
            }

            // Additional seeding can be added here
        } catch (ex: Exception) {
            logger.error("Error during initial data seeding", ex)
            // Non-critical error, don't throw
        }
    }
}
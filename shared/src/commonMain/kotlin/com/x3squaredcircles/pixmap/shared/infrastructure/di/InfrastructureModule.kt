// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/di/InfrastructureModule.kt

import androidx.sqlite.db.SimpleSQLiteQuery.Companion.bind
import com.x3squaredcircles.pixmap.shared.application.common.interfaces.*
import com.x3squaredcircles.pixmap.shared.domain.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.infrastructure.data.DatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories.*

import com.x3squaredcircles.pixmap.shared.infrastructure.services.*
import com.x3squaredcircles.pixmap.shared.infrastructure.unitofwork.UnitOfWork
import io.ktor.client.*

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val infrastructureModule = module {

    // Database
    singleOf(::DatabaseContext) { bind<IDatabaseContext>() }
    singleOf(::UnitOfWork) { bind<IUnitOfWork>() }

    // Persistence-layer repositories
    single<LocationRepository> { LocationRepository(get(), get()) }
    single<WeatherRepository> { WeatherRepository(get(), get()) }
    single<TipRepository> { TipRepository(get(), get()) }
    single<TipTypeRepository> { TipTypeRepository(get(), get()) }
    single<SettingRepository> { SettingRepository(get(), get()) }

    // Application interface adapters
    single<ILocationRepository> { LocationRepositoryAdapter(get()) }
    single<IWeatherRepository> { WeatherRepositoryAdapter(get()) }
    single<ITipRepository> { TipRepositoryAdapter(get()) }
    single<ITipTypeRepository> { TipTypeRepositoryAdapter(get()) }
    single<ISettingRepository> { SettingRepositoryAdapter(get()) }

    // HTTP Client for Weather API
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            engine {
                requestTimeout = 30000
            }
        }
    }

    // Services
    single<IWeatherService> { WeatherService(get(), get(), get(), get(), get(), get()) }
    single<ILoggingService> { LoggingService(get()) }
    single<ITimezoneService> { TimezoneService() }

    // Alerting services
    single<DirectAlertingService> { DirectAlertingService(get()) }
    single<IAlertService> { get<DirectAlertingService>() }

    // Exception mapping service
    single<IInfrastructureExceptionMappingService> { InfrastructureExceptionMappingService() }

    // Event Bus
    singleOf(::InMemoryEventBus) { bind<IEventBus>() }
}

// Database initialization module - separate to allow for conditional registration
val databaseInitializationModule = module {
    single<DatabaseInitializationService> { DatabaseInitializationService(get(), get(), get()) }
}

class DatabaseInitializationService(
    private val databaseContext: IDatabaseContext,
    private val logger: kotlinx.coroutines.logging.Logger,
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
                val setting = Setting(
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
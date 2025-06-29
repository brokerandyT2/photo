package com.x3squaredcircles.pixmap.android.di

import com.x3squaredcircles.pixmap.android.services.*
import com.x3squaredcircles.pixmap.shared.services.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android platform-specific dependency injection module
 */
val androidPlatformModule = module {

    // HTTP Client
    single {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    // Platform Services
    single<ICameraService> { AndroidCameraService(androidContext()) }
    single<ILocationService> { AndroidLocationService(androidContext()) }
    single<IFileService> { AndroidFileService(androidContext()) }
    single<INotificationService> { AndroidNotificationService(androidContext()) }
    single<IWeatherService> {
        AndroidWeatherService(
            httpClient = get(),
            apiKey = "your_openweather_api_key", // Should come from BuildConfig or environment
            baseUrl = "https://api.openweathermap.org/data/2.5"
        )
    }
}
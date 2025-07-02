//shared/src/androidMain/kotlin/com/x3squaredcircles/pixmap/android/di/AndroidPlatformModule.kt
package com.x3squaredcircles.pixmap.android.di

import com.x3squaredcircles.pixmap.android.services.*
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ICameraService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IFileService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILocationService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.INotificationService
import com.x3squaredcircles.pixmap.shared.services.IWeatherService
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
                logger = object : Logger {
                    override fun log(message: String) {
                        android.util.Log.d("Ktor", message)
                    }
                }
                level = LogLevel.ALL
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
            apiKey = "your_openweather_api_key",
            baseUrl = "https://api.openweathermap.org/data/2.5"
        )
    }
}
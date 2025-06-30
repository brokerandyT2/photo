// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IWeatherService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyWeatherForecastDto

/**
 * Interface for weather service that integrates with external weather API
 */
interface IWeatherService {

    /**
     * Gets current weather for specified coordinates
     */
    suspend fun getWeatherAsync(latitude: Double, longitude: Double): Result<WeatherDto>

    /**
     * Updates weather data for a specific location
     */
    suspend fun updateWeatherForLocationAsync(locationId: Int): Result<WeatherDto>

    /**
     * Gets weather forecast for specified coordinates
     */
    suspend fun getForecastAsync(
        latitude: Double,
        longitude: Double,
        days: Int = 7
    ): Result<WeatherForecastDto>

    /**
     * Gets hourly weather forecast for specified coordinates
     */
    suspend fun getHourlyForecastAsync(
        latitude: Double,
        longitude: Double
    ): Result<HourlyWeatherForecastDto>

    /**
     * Updates weather for all active locations
     */
    suspend fun updateAllWeatherAsync(): Result<Int>

    /**
     * Checks if weather data is stale and needs updating
     */
    fun isWeatherDataStale(lastUpdate: kotlinx.datetime.Instant): Boolean

    /**
     * Gets cached weather data if available and not stale
     */
    suspend fun getCachedWeatherAsync(locationId: Int): Result<WeatherDto?>

    /**
     * Clears weather cache for a specific location
     */
    suspend fun clearWeatherCacheAsync(locationId: Int): Result<Unit>

    /**
     * Clears all weather cache
     */
    suspend fun clearAllWeatherCacheAsync(): Result<Unit>

    /**
     * Gets weather update status for location
     */
    suspend fun getWeatherUpdateStatusAsync(locationId: Int): Result<WeatherUpdateStatus>

    /**
     * Validates weather API configuration
     */
    suspend fun validateApiConfigurationAsync(): Result<Boolean>
}

/**
 * Weather update status information
 */
data class WeatherUpdateStatus(
    val locationId: Int,
    val lastUpdate: kotlinx.datetime.Instant?,
    val isStale: Boolean,
    val isUpdating: Boolean,
    val lastError: String? = null,
    val nextUpdateTime: kotlinx.datetime.Instant? = null
)

/**
 * Weather service configuration
 */
data class WeatherServiceConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.openweathermap.org/data/3.0",
    val timeoutMs: Long = 30000,
    val retryCount: Int = 3,
    val cacheExpirationHours: Int = 1,
    val rateLimitPerMinute: Int = 60,
    val enableBackgroundUpdates: Boolean = true
)

/**
 * Weather API error types
 */
enum class WeatherApiError {
    NETWORK_ERROR,
    API_KEY_INVALID,
    RATE_LIMIT_EXCEEDED,
    LOCATION_NOT_FOUND,
    SERVICE_UNAVAILABLE,
    TIMEOUT,
    UNKNOWN
}

/**
 * Extension functions for weather service
 */
suspend fun IWeatherService.updateWeatherSafely(locationId: Int): WeatherDto? {
    return try {
        val result = updateWeatherForLocationAsync(locationId)
        if (result.isSuccess) {
            result.getOrNull()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

suspend fun IWeatherService.getWeatherOrCached(
    locationId: Int,
    latitude: Double,
    longitude: Double
): Result<WeatherDto> {
    // Try cached first
    val cachedResult = getCachedWeatherAsync(locationId)
    if (cachedResult.isSuccess) {
        cachedResult.getOrNull()?.let { return Result.success(it) }
    }

    // Fall back to API
    return getWeatherAsync(latitude, longitude)
}

/**
 * Batch weather operations
 */
suspend fun IWeatherService.updateMultipleLocationsAsync(
    locationIds: List<Int>
): Map<Int, Result<WeatherDto>> {
    val results = mutableMapOf<Int, Result<WeatherDto>>()

    locationIds.forEach { locationId ->
        results[locationId] = try {
            updateWeatherForLocationAsync(locationId)
        } catch (e: Exception) {
            Result.failure(e.message ?: "Unknown error")
        }
    }

    return results
}
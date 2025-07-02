// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IWeatherService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyWeatherForecastDto
import kotlinx.datetime.Instant

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
    fun isWeatherDataStale(lastUpdate: Instant): Boolean

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

    /**
     * Refreshes weather for location with force update
     */
    suspend fun refreshWeatherAsync(locationId: Int, forceUpdate: Boolean = false): Result<WeatherDto>

    /**
     * Gets weather alerts for location
     */
    suspend fun getWeatherAlertsAsync(latitude: Double, longitude: Double): Result<List<WeatherAlert>>

    /**
     * Gets air quality data for location
     */
    suspend fun getAirQualityAsync(latitude: Double, longitude: Double): Result<AirQualityDto>
}

/**
 * Weather update status information
 */
data class WeatherUpdateStatus(
    val locationId: Int,
    val lastUpdate: Instant?,
    val isStale: Boolean,
    val isUpdating: Boolean,
    val lastError: String? = null,
    val nextUpdateTime: Instant? = null,
    val updateCount: Int = 0,
    val failureCount: Int = 0
) {
    val isHealthy: Boolean
        get() = lastError == null && failureCount < 3

    val successRate: Double
        get() = if (updateCount == 0) 0.0 else
            ((updateCount - failureCount).toDouble() / updateCount.toDouble()) * 100.0
}

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
    val enableBackgroundUpdates: Boolean = true,
    val units: WeatherUnits = WeatherUnits.METRIC
)

/**
 * Weather API error types
 */
enum class WeatherApiError(val message: String) {
    NETWORK_ERROR("Network connection failed"),
    API_KEY_INVALID("Invalid API key"),
    RATE_LIMIT_EXCEEDED("API rate limit exceeded"),
    LOCATION_NOT_FOUND("Location not found"),
    SERVICE_UNAVAILABLE("Weather service unavailable"),
    TIMEOUT("Request timed out"),
    INVALID_COORDINATES("Invalid latitude/longitude coordinates"),
    UNKNOWN("Unknown error occurred")
}

/**
 * Weather units system
 */
enum class WeatherUnits(val code: String, val description: String) {
    METRIC("metric", "Celsius, m/s, km"),
    IMPERIAL("imperial", "Fahrenheit, mph, miles"),
    KELVIN("standard", "Kelvin, m/s, km")
}

/**
 * Weather alert information
 */
data class WeatherAlert(
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val startTime: Instant,
    val endTime: Instant,
    val source: String,
    val tags: List<String> = emptyList()
)

/**
 * Alert severity levels
 */
enum class AlertSeverity {
    MINOR,
    MODERATE,
    SEVERE,
    EXTREME
}

/**
 * Air quality data
 */
data class AirQualityDto(
    val aqi: Int, // Air Quality Index
    val co: Double, // Carbon monoxide
    val no: Double, // Nitric oxide
    val no2: Double, // Nitrogen dioxide
    val o3: Double, // Ozone
    val so2: Double, // Sulphur dioxide
    val pm2_5: Double, // PM2.5
    val pm10: Double, // PM10
    val nh3: Double, // Ammonia
    val timestamp: Instant
) {
    val qualityDescription: String
        get() = when (aqi) {
            1 -> "Good"
            2 -> "Fair"
            3 -> "Moderate"
            4 -> "Poor"
            5 -> "Very Poor"
            else -> "Unknown"
        }
}

/**
 * Extension functions for weather service convenience
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

/**
 * Weather data validation
 */
fun WeatherDto.isValid(): Boolean {
    return latitude >= -90.0 && latitude <= 90.0 &&
            longitude >= -180.0 && longitude <= 180.0 &&
            temperature > -100.0 && temperature < 60.0 && // Reasonable temperature range
            humidity in 0..100 &&
            pressure > 800 && pressure < 1200 && // Reasonable pressure range
            windSpeed >= 0.0
}

/**
 * Weather update intervals based on data age
 */
object WeatherUpdateIntervals {
    const val IMMEDIATE = 0L
    const val MINUTES_5 = 5 * 60 * 1000L
    const val MINUTES_15 = 15 * 60 * 1000L
    const val MINUTES_30 = 30 * 60 * 1000L
    const val HOUR_1 = 60 * 60 * 1000L
    const val HOURS_3 = 3 * 60 * 60 * 1000L
    const val HOURS_6 = 6 * 60 * 60 * 1000L
}
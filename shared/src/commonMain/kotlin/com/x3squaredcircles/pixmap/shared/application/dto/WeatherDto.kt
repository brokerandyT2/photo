// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/WeatherDto.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Data transfer object for weather information
 */
@Serializable
data class WeatherDto(
    val id: Int,
    val locationId: Int,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val timezoneOffset: Int,
    val lastUpdate: Instant,

    // Current conditions
    val temperature: Double,
    val minimumTemp: Double,
    val maximumTemp: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double? = null,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val precipitation: Double? = null,

    // Sun data
    val sunrise: Instant,
    val sunset: Instant,

    // Moon data
    val moonRise: Instant? = null,
    val moonSet: Instant? = null,
    val moonPhase: Double
)

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

/**
 * Data transfer object for weather forecast
 */
@Serializable
data class WeatherForecastDto(
    val weatherId: Int,
    val lastUpdate: Instant,
    val timezone: String,
    val timezoneOffset: Int,
    val dailyForecasts: List<DailyForecastDto>
)

/**
 * Data transfer object for daily forecast
 */
@Serializable
data class DailyForecastDto(
    val date: Instant,
    val sunrise: Instant,
    val sunset: Instant,
    val temperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
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
    val moonRise: Instant? = null,
    val moonSet: Instant? = null,
    val moonPhase: Double
)

/**
 * Data transfer object for hourly weather forecast
 */
@Serializable
data class HourlyWeatherForecastDto(
    val weatherId: Int,
    val lastUpdate: Instant,
    val timezone: String,
    val timezoneOffset: Int,
    val hourlyForecasts: List<HourlyForecastDto>
)

/**
 * Data transfer object for individual hourly forecast
 */
@Serializable
data class HourlyForecastDto(
    val dateTime: Instant,
    val temperature: Double,
    val feelsLike: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double? = null,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val probabilityOfPrecipitation: Double,
    val visibility: Int,
    val dewPoint: Double
)
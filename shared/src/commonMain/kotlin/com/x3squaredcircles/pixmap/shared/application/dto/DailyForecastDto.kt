// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/DailyForecastDto.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Data transfer object for Daily Forecast
 */
data class DailyForecastDto(
    val date: LocalDate,
    val sunrise: Instant,
    val sunset: Instant,
    val temperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double?,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val precipitation: Double?,
    val moonRise: Instant?,
    val moonSet: Instant?,
    val moonPhase: Double
)
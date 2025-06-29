// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/WeatherForecastDto.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant

/**
 * Data transfer object for Weather Forecast
 */
data class WeatherForecastDto(
    val timezone: String,
    val timezoneOffset: Int,
    val lastUpdate: Instant,
    val dailyForecasts: List<DailyForecastDto>
)
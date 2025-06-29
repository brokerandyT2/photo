// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/dto/HourlyWeatherForecastDto.kt
package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant

/**
 * Data transfer object for Hourly Weather Forecast
 */
data class HourlyWeatherForecastDto(
    val timezone: String,
    val timezoneOffset: Int,
    val lastUpdate: Instant,
    val hourlyForecasts: List<HourlyForecastDto>
)
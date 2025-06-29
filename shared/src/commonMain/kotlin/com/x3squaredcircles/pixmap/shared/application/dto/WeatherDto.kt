package com.x3squaredcircles.pixmap.shared.application.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Data transfer object for weather data
 */
data class WeatherDto(
    val locationId: Int,
    val latitude: Double,
    val longitude: Double,
    val lastUpdate: Instant,
    val timezone: String,
    val timezoneOffset: Int,
    val forecasts: List<WeatherForecastDto>,
    val hourlyForecasts: List<HourlyForecastDto>
)

data class WeatherForecastDto(
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

data class HourlyForecastDto(
    val dateTime: Instant,
    val temperature: Double,
    val feelsLike: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double?,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val probabilityOfPrecipitation: Double,
    val visibility: Int,
    val dewPoint: Double
)
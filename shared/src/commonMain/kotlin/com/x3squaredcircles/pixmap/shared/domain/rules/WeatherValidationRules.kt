// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/rules/WeatherValidationRules.kt
package com.x3squaredcircles.pixmap.shared.domain.rules

import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.entities.WeatherForecast
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * Business rules for weather validation
 */
object WeatherValidationRules {
    private const val MAX_FORECAST_DAYS = 7
    private const val MIN_TEMPERATURE = -273.15 // Absolute zero in Celsius
    private const val MAX_TEMPERATURE = 70.0 // Reasonable max temperature in Celsius

    /**
     * Validates the specified Weather object and identifies any validation errors.
     */
    fun isValid(weather: Weather?, errors: MutableList<String>): Boolean {
        errors.clear()

        if (weather == null) {
            errors.add("Weather cannot be null")
            return false
        }

        if (weather.locationId <= 0) {
            errors.add("Weather must be associated with a valid location")
        }

        if (weather.timezone.isBlank()) {
            errors.add("Weather timezone is required")
        }

        if (weather.forecasts.size > MAX_FORECAST_DAYS) {
            errors.add("Weather cannot have more than $MAX_FORECAST_DAYS daily forecasts")
        }

        // Validate each forecast
        weather.forecasts.forEach { forecast ->
            validateForecast(forecast, errors)
        }

        return errors.isEmpty()
    }

    /**
     * Validates the specified weather forecast and collects any validation errors.
     */
    private fun validateForecast(forecast: WeatherForecast, errors: MutableList<String>) {
        if (forecast.temperature < MIN_TEMPERATURE || forecast.temperature > MAX_TEMPERATURE) {
            errors.add("Invalid temperature for ${forecast.date}")
        }

        if (forecast.minTemperature > forecast.maxTemperature) {
            errors.add("Min temperature cannot exceed max temperature for ${forecast.date}")
        }

        if (forecast.humidity < 0 || forecast.humidity > 100) {
            errors.add("Invalid humidity percentage for ${forecast.date}")
        }

        if (forecast.clouds < 0 || forecast.clouds > 100) {
            errors.add("Invalid cloud coverage percentage for ${forecast.date}")
        }

        if (forecast.uvIndex < 0 || forecast.uvIndex > 15) {
            errors.add("Invalid UV index for ${forecast.date}")
        }

        if (forecast.moonPhase < 0 || forecast.moonPhase > 1) {
            errors.add("Invalid moon phase for ${forecast.date}")
        }
    }

    /**
     * Determines whether the specified weather data is considered stale based on the given maximum age.
     */
    fun isStale(weather: Weather, maxAge: Duration): Boolean {
        return Clock.System.now() - weather.lastUpdate > maxAge
    }
}
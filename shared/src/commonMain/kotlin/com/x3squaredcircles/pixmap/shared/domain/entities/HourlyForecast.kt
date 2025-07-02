//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/HourlyForecast.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Individual weather forecast for a single hour
 */
@Serializable
data class HourlyForecast(
    override val id: Int = 0,
    val weatherId: Int,
    val dateTime: Instant,
    val temperature: Double,
    val feelsLike: Double,
    val description: String = "",
    val icon: String = "",
    val wind: WindInfo,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val probabilityOfPrecipitation: Double,
    val visibility: Int,
    val dewPoint: Double
) : Entity() {

    init {
        require(weatherId > 0) { "WeatherId must be greater than zero" }
        require(temperature >= -273.15) { "Temperature cannot be below absolute zero" }
        require(feelsLike >= -273.15) { "Feels like temperature cannot be below absolute zero" }
        require(humidity in 0..100) { "Humidity must be between 0 and 100" }
        require(pressure > 0) { "Pressure must be positive" }
        require(clouds in 0..100) { "Clouds must be between 0 and 100" }
        require(uvIndex >= 0) { "UV Index cannot be negative" }
        require(probabilityOfPrecipitation in 0.0..1.0) { "Probability of precipitation must be between 0 and 1" }
        require(visibility >= 0) { "Visibility cannot be negative" }
    }

    companion object {
        /**
         * Factory method to create an hourly forecast
         */
        fun create(
            weatherId: Int,
            dateTime: Instant,
            temperature: Double,
            feelsLike: Double,
            description: String,
            icon: String,
            wind: WindInfo,
            humidity: Int,
            pressure: Int,
            clouds: Int,
            uvIndex: Double,
            probabilityOfPrecipitation: Double,
            visibility: Int,
            dewPoint: Double
        ): HourlyForecast {
            return HourlyForecast(
                weatherId = weatherId,
                dateTime = dateTime,
                temperature = temperature,
                feelsLike = feelsLike,
                description = description,
                icon = icon,
                wind = wind,
                humidity = humidity,
                pressure = pressure,
                clouds = clouds,
                uvIndex = uvIndex,
                probabilityOfPrecipitation = probabilityOfPrecipitation,
                visibility = visibility,
                dewPoint = dewPoint
            )
        }
    }

    /**
     * Gets the temperature difference between actual and feels like
     */
    fun getTemperatureDifference(): Double {
        return feelsLike - temperature
    }

    /**
     * Gets a comfort description based on feels like temperature
     */
    fun getComfortDescription(): String {
        return when {
            feelsLike < -10 -> "Dangerously Cold"
            feelsLike < 0 -> "Very Cold"
            feelsLike < 10 -> "Cold"
            feelsLike < 20 -> "Cool"
            feelsLike < 25 -> "Comfortable"
            feelsLike < 30 -> "Warm"
            feelsLike < 35 -> "Hot"
            feelsLike < 40 -> "Very Hot"
            else -> "Dangerously Hot"
        }
    }

    /**
     * Gets visibility description
     */
    fun getVisibilityDescription(): String {
        return when {
            visibility >= 10000 -> "Excellent (${visibility / 1000}+ km)"
            visibility >= 5000 -> "Good (${visibility / 1000} km)"
            visibility >= 2000 -> "Moderate (${visibility / 1000} km)"
            visibility >= 1000 -> "Poor (${visibility} m)"
            else -> "Very Poor (${visibility} m)"
        }
    }

    /**
     * Gets precipitation probability as percentage
     */
    fun getPrecipitationPercentage(): Int {
        return (probabilityOfPrecipitation * 100).toInt()
    }

    /**
     * Checks if rain is likely (>50% probability)
     */
    fun isRainLikely(): Boolean {
        return probabilityOfPrecipitation > 0.5
    }

    /**
     * Checks if conditions are good for outdoor activities
     */
    fun isGoodForOutdoor(): Boolean {
        return probabilityOfPrecipitation < 0.3 &&
                wind.speed < 25.0 &&
                visibility > 5000 &&
                temperature > 5 &&
                temperature < 35
    }

    /**
     * Gets a short weather summary
     */
    fun getShortSummary(): String {
        val temp = "${temperature.toInt()}°"
        val feels = if (kotlin.math.abs(getTemperatureDifference()) > 2) {
            " (feels ${feelsLike.toInt()}°)"
        } else ""
        return "$description, $temp$feels"
    }

    /**
     * Gets detailed weather information
     */
    fun getDetailedInfo(): String {
        return buildString {
            append("${description}, ${temperature.toInt()}°")
            if (kotlin.math.abs(getTemperatureDifference()) > 2) {
                append(" (feels ${feelsLike.toInt()}°)")
            }
            append(", ${wind.toSimpleString()}")
            append(", ${humidity}% humidity")
            if (probabilityOfPrecipitation > 0.1) {
                append(", ${getPrecipitationPercentage()}% chance rain")
            }
        }
    }
}
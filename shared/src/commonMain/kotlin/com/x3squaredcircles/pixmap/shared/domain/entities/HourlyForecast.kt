// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/HourlyForecast.kt
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
            description: String = "",
            icon: String = "",
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

        /**
         * Factory method to create an hourly forecast with ID (for ORM/repository use)
         */
        fun create(
            id: Int,
            weatherId: Int,
            dateTime: Instant,
            temperature: Double,
            feelsLike: Double,
            description: String = "",
            icon: String = "",
            wind: WindInfo,
            humidity: Int,
            pressure: Int,
            clouds: Int,
            uvIndex: Double,
            probabilityOfPrecipitation: Double,
            visibility: Int,
            dewPoint: Double
        ): HourlyForecast {
            val forecast = HourlyForecast(
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
            forecast.id = id
            return forecast
        }
    }

    /**
     * Updates the forecast with new temperature data
     */
    fun updateTemperature(newTemperature: Double, newFeelsLike: Double): HourlyForecast {
        require(newTemperature >= -273.15) { "Temperature cannot be below absolute zero" }
        require(newFeelsLike >= -273.15) { "Feels like temperature cannot be below absolute zero" }

        return copy(
            temperature = newTemperature,
            feelsLike = newFeelsLike
        )
    }

    /**
     * Updates the forecast with new weather conditions
     */
    fun updateWeatherConditions(newDescription: String, newIcon: String): HourlyForecast {
        return copy(
            description = newDescription,
            icon = newIcon
        )
    }

    /**
     * Updates the forecast with new wind information
     */
    fun updateWind(newWind: WindInfo): HourlyForecast {
        return copy(wind = newWind)
    }

    /**
     * Gets a summary of the forecast for display
     */
    fun getSummary(): String {
        return "$temperature°C, $description"
    }

    /**
     * Checks if this forecast has precipitation
     */
    fun hasPrecipitation(): Boolean {
        return probabilityOfPrecipitation > 0.0
    }

    /**
     * Gets the comfort level based on temperature and humidity
     */
    fun getComfortLevel(): String {
        return when {
            temperature < 0 -> "Very Cold"
            temperature < 10 -> "Cold"
            temperature < 20 -> "Cool"
            temperature < 25 -> "Comfortable"
            temperature < 30 -> "Warm"
            else -> "Hot"
        }
    }
}
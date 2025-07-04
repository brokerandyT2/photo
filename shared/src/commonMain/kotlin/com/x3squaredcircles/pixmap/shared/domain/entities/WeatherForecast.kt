//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/WeatherForecast.kt

package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Individual weather forecast for a single day
 */
@Serializable
data class WeatherForecast(
    override var id: Int = 0,
    val weatherId: Int,
    val date: LocalDate,
    val sunrise: Instant,
    val sunset: Instant,
    val temperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val description: String = "",
    val icon: String = "",
    val wind: WindInfo,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val precipitation: Double? = null,
    val moonRise: Instant? = null,
    val moonSet: Instant? = null,
    val moonPhase: Double = 0.0
) : Entity() {

    init {
        require(weatherId > 0) { "WeatherId must be greater than zero" }
        require(temperature >= -273.15) { "Temperature cannot be below absolute zero" }
        require(minTemperature >= -273.15) { "Min temperature cannot be below absolute zero" }
        require(maxTemperature >= -273.15) { "Max temperature cannot be below absolute zero" }
        require(minTemperature <= maxTemperature) { "Min temperature cannot be greater than max temperature" }
        require(humidity in 0..100) { "Humidity must be between 0 and 100" }
        require(pressure > 0) { "Pressure must be positive" }
        require(clouds in 0..100) { "Clouds must be between 0 and 100" }
        require(uvIndex >= 0) { "UV Index cannot be negative" }
        require(precipitation == null || precipitation >= 0) { "Precipitation cannot be negative" }
        require(moonPhase in 0.0..1.0) { "Moon phase must be between 0 and 1" }
    }

    companion object {
        /**
         * Factory method to create a weather forecast
         */
        fun create(
            weatherId: Int,
            date: LocalDate,
            sunrise: Instant,
            sunset: Instant,
            temperature: Double,
            minTemperature: Double,
            maxTemperature: Double,
            description: String,
            icon: String,
            wind: WindInfo,
            humidity: Int,
            pressure: Int,
            clouds: Int,
            uvIndex: Double
        ): WeatherForecast {
            return WeatherForecast(
                weatherId = weatherId,
                date = date,
                sunrise = sunrise,
                sunset = sunset,
                temperature = temperature,
                minTemperature = minTemperature,
                maxTemperature = maxTemperature,
                description = description,
                icon = icon,
                wind = wind,
                humidity = humidity,
                pressure = pressure,
                clouds = clouds,
                uvIndex = uvIndex
            )
        }
    }

    /**
     * Sets moon data for this forecast
     */
    fun setMoonData(moonRise: Instant?, moonSet: Instant?, moonPhase: Double): WeatherForecast {
        require(moonPhase in 0.0..1.0) { "Moon phase must be between 0 and 1" }
        return copy(
            moonRise = moonRise,
            moonSet = moonSet,
            moonPhase = moonPhase
        )
    }

    /**
     * Sets precipitation data
     */
    fun setPrecipitation(precipitation: Double?): WeatherForecast {
        require(precipitation == null || precipitation >= 0) { "Precipitation cannot be negative" }
        return copy(precipitation = precipitation)
    }

    /**
     * Gets temperature range as a formatted string
     */
    fun getTemperatureRange(): String {
        return "${minTemperature.toInt()}° - ${maxTemperature.toInt()}°"
    }

    /**
     * Gets the comfort level based on temperature
     */
    fun getComfortLevel(): String {
        return when {
            temperature < 0 -> "Very Cold"
            temperature < 10 -> "Cold"
            temperature < 20 -> "Cool"
            temperature < 25 -> "Comfortable"
            temperature < 30 -> "Warm"
            temperature < 35 -> "Hot"
            else -> "Very Hot"
        }
    }

    /**
     * Checks if this is a good day for photography based on conditions
     */
    fun isGoodForPhotography(): Boolean {
        return clouds < 80 && precipitation?.let { it < 1.0 } ?: true && wind.speed < 20.0
    }

    /**
     * Gets UV protection recommendation
     */
    fun getUvRecommendation(): String {
        return when {
            uvIndex < 3 -> "Low - No protection needed"
            uvIndex < 6 -> "Moderate - Some protection recommended"
            uvIndex < 8 -> "High - Protection essential"
            uvIndex < 11 -> "Very High - Extra protection required"
            else -> "Extreme - Avoid outdoor activities"
        }
    }

    /**
     * Gets a weather summary string
     */
    fun getSummary(): String {
        val temp = "${temperature.toInt()}°"
        val windDesc = if (wind.speed > 10) ", ${wind.toSimpleString()}" else ""
        val precipDesc = precipitation?.let { if (it > 0) ", ${it}mm rain" else "" } ?: ""
        return "$description, $temp$windDesc$precipDesc"
    }
}
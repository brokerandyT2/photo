package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Individual weather forecast for a single day
 */
class WeatherForecast : Entity {

    var weatherId: Int = 0
        private set

    var date: LocalDate = LocalDate(1970, 1, 1)
        private set

    var sunrise: Instant = Instant.DISTANT_PAST
        private set

    var sunset: Instant = Instant.DISTANT_PAST
        private set

    var temperature: Double = 0.0
        private set

    var minTemperature: Double = 0.0
        private set

    var maxTemperature: Double = 0.0
        private set

    var description: String = ""
        private set

    var icon: String = ""
        private set

    var wind: WindInfo = WindInfo(0.0, 0.0)
        private set

    var humidity: Int = 0
        private set

    var pressure: Int = 0
        private set

    var clouds: Int = 0
        private set

    var uvIndex: Double = 0.0
        private set

    var precipitation: Double? = null
        private set

    var moonRise: Instant? = null
        private set

    var moonSet: Instant? = null
        private set

    var moonPhase: Double = 0.0
        private set

    // For ORM
    constructor()

    constructor(
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
    ) {
        this.weatherId = weatherId
        this.date = date
        this.sunrise = sunrise
        this.sunset = sunset
        this.temperature = temperature
        this.minTemperature = minTemperature
        this.maxTemperature = maxTemperature
        this.description = description
        this.icon = icon
        this.wind = wind
        this.humidity = validatePercentage(humidity, "humidity")
        this.pressure = pressure
        this.clouds = validatePercentage(clouds, "clouds")
        this.uvIndex = uvIndex
    }

    fun setMoonData(moonRise: Instant?, moonSet: Instant?, moonPhase: Double) {
        this.moonRise = moonRise
        this.moonSet = moonSet
        this.moonPhase = max(0.0, min(1.0, moonPhase))
    }

    fun setPrecipitation(precipitation: Double) {
        this.precipitation = max(0.0, precipitation)
    }

    private fun validatePercentage(value: Int, paramName: String): Int {
        require(value in 0..100) { "$paramName must be between 0 and 100" }
        return value
    }

    fun getMoonPhaseDescription(): String {
        return when {
            moonPhase < 0.03 -> "New Moon"
            moonPhase < 0.22 -> "Waxing Crescent"
            moonPhase < 0.28 -> "First Quarter"
            moonPhase < 0.47 -> "Waxing Gibbous"
            moonPhase < 0.53 -> "Full Moon"
            moonPhase < 0.72 -> "Waning Gibbous"
            moonPhase < 0.78 -> "Last Quarter"
            moonPhase < 0.97 -> "Waning Crescent"
            else -> "New Moon"
        }
    }
}
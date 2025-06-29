// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/WeatherForecast.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Individual weather forecast for a single day
 */
class WeatherForecast private constructor() : Entity() {

    val weatherId: Int
        get() = _weatherId

    private var _weatherId: Int = 0

    val date: LocalDate
        get() = _date

    private var _date: LocalDate = LocalDate(2000, 1, 1)

    val sunrise: Instant
        get() = _sunrise

    private var _sunrise: Instant = Instant.DISTANT_PAST

    val sunset: Instant
        get() = _sunset

    private var _sunset: Instant = Instant.DISTANT_PAST

    val temperature: Double
        get() = _temperature

    private var _temperature: Double = 0.0

    val minTemperature: Double
        get() = _minTemperature

    private var _minTemperature: Double = 0.0

    val maxTemperature: Double
        get() = _maxTemperature

    private var _maxTemperature: Double = 0.0

    val description: String
        get() = _description

    private var _description: String = ""

    val icon: String
        get() = _icon

    private var _icon: String = ""

    val wind: WindInfo
        get() = _wind

    private var _wind: WindInfo? = null

    val humidity: Int
        get() = _humidity

    private var _humidity: Int = 0

    val pressure: Int
        get() = _pressure

    private var _pressure: Int = 0

    val clouds: Int
        get() = _clouds

    private var _clouds: Int = 0

    val uvIndex: Double
        get() = _uvIndex

    private var _uvIndex: Double = 0.0

    val precipitation: Double?
        get() = _precipitation

    private var _precipitation: Double? = null

    val moonRise: Instant?
        get() = _moonRise

    private var _moonRise: Instant? = null

    val moonSet: Instant?
        get() = _moonSet

    private var _moonSet: Instant? = null

    val moonPhase: Double
        get() = _moonPhase

    private var _moonPhase: Double = 0.0

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
    ) : this() {
        _weatherId = weatherId
        _date = date
        _sunrise = sunrise
        _sunset = sunset
        _temperature = temperature
        _minTemperature = minTemperature
        _maxTemperature = maxTemperature
        _description = description
        _icon = icon
        _wind = wind
        _humidity = validatePercentage(humidity, "humidity")
        _pressure = pressure
        _clouds = validatePercentage(clouds, "clouds")
        _uvIndex = uvIndex
    }

    fun setMoonData(moonRise: Instant?, moonSet: Instant?, moonPhase: Double) {
        _moonRise = moonRise
        _moonSet = moonSet
        _moonPhase = maxOf(0.0, minOf(1.0, moonPhase)) // Clamp between 0 and 1
    }

    fun setPrecipitation(precipitation: Double) {
        _precipitation = maxOf(0.0, precipitation)
    }

    private fun validatePercentage(value: Int, paramName: String): Int {
        require(value in 0..100) { "Percentage $paramName must be between 0 and 100" }
        return value
    }

    /**
     * Gets moon phase description
     */
    fun getMoonPhaseDescription(): String {
        return when (moonPhase) {
            in 0.0..0.03 -> "New Moon"
            in 0.03..0.22 -> "Waxing Crescent"
            in 0.22..0.28 -> "First Quarter"
            in 0.28..0.47 -> "Waxing Gibbous"
            in 0.47..0.53 -> "Full Moon"
            in 0.53..0.72 -> "Waning Gibbous"
            in 0.72..0.78 -> "Last Quarter"
            in 0.78..0.97 -> "Waning Crescent"
            else -> "New Moon"
        }
    }
}
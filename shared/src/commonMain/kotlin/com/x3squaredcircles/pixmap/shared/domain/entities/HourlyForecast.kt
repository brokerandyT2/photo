// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/HourlyForecast.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import kotlinx.datetime.Instant

/**
 * Individual weather forecast for a single hour
 */
class HourlyForecast private constructor() : Entity() {

    val weatherId: Int
        get() = _weatherId

    private var _weatherId: Int = 0

    val dateTime: Instant
        get() = _dateTime

    private var _dateTime: Instant = Instant.DISTANT_PAST

    val temperature: Double
        get() = _temperature

    private var _temperature: Double = 0.0

    val feelsLike: Double
        get() = _feelsLike

    private var _feelsLike: Double = 0.0

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

    val probabilityOfPrecipitation: Double
        get() = _probabilityOfPrecipitation

    private var _probabilityOfPrecipitation: Double = 0.0

    val visibility: Int
        get() = _visibility

    private var _visibility: Int = 0

    val dewPoint: Double
        get() = _dewPoint

    private var _dewPoint: Double = 0.0

    constructor(
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
    ) : this() {
        _weatherId = weatherId
        _dateTime = dateTime
        _temperature = temperature
        _feelsLike = feelsLike
        _description = description
        _icon = icon
        _wind = wind
        _humidity = validatePercentage(humidity, "humidity")
        _pressure = pressure
        _clouds = validatePercentage(clouds, "clouds")
        _uvIndex = maxOf(0.0, uvIndex)
        _probabilityOfPrecipitation = validateProbability(probabilityOfPrecipitation, "probabilityOfPrecipitation")
        _visibility = maxOf(0, visibility)
        _dewPoint = dewPoint
    }

    private fun validatePercentage(value: Int, paramName: String): Int {
        require(value in 0..100) { "Percentage $paramName must be between 0 and 100" }
        return value
    }

    private fun validateProbability(value: Double, paramName: String): Double {
        require(value in 0.0..1.0) { "Probability $paramName must be between 0 and 1" }
        return value
    }
}
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.Entity
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import kotlinx.datetime.Instant

/**
 * Individual weather forecast for a single hour
 */
class HourlyForecast : Entity {

    var weatherId: Int = 0
        private set

    var dateTime: Instant = Instant.DISTANT_PAST
        private set

    var temperature: Double = 0.0
        private set

    var feelsLike: Double = 0.0
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

    var probabilityOfPrecipitation: Double = 0.0
        private set

    var visibility: Int = 0
        private set

    var dewPoint: Double = 0.0
        private set

    // For ORM
    constructor()

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
    ) {
        this.weatherId = weatherId
        this.dateTime = dateTime
        this.temperature = temperature
        this.feelsLike = feelsLike
        this.description = description
        this.icon = icon
        this.wind = wind
        this.humidity = validatePercentage(humidity, "humidity")
        this.pressure = pressure
        this.clouds = validatePercentage(clouds, "clouds")
        this.uvIndex = maxOf(0.0, uvIndex)
        this.probabilityOfPrecipitation = validateProbability(probabilityOfPrecipitation, "probabilityOfPrecipitation")
        this.visibility = maxOf(0, visibility)
        this.dewPoint = dewPoint
    }

    private fun validatePercentage(value: Int, paramName: String): Int {
        require(value in 0..100) { "$paramName must be between 0 and 100" }
        return value
    }

    private fun validateProbability(value: Double, paramName: String): Double {
        require(value in 0.0..1.0) { "$paramName must be between 0 and 1" }
        return value
    }
}
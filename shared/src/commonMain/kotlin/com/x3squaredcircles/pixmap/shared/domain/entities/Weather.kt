// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Weather.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.AggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.events.WeatherUpdatedEvent
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Weather aggregate root containing weather data for a location
 */
class Weather private constructor() : AggregateRoot() {

    private val _forecasts = mutableListOf<WeatherForecast>()
    private val _hourlyForecasts = mutableListOf<HourlyForecast>()
    private var _coordinate: Coordinate? = null
    private var _lastUpdate: Instant = Clock.System.now()
    private var _timezone: String = ""
    private var _timezoneOffset: Int = 0

    val locationId: Int
        get() = _locationId

    private var _locationId: Int = 0

    val coordinate: Coordinate
        get() = _coordinate ?: throw IllegalStateException("Coordinate is not set")

    val lastUpdate: Instant
        get() = _lastUpdate

    val timezone: String
        get() = _timezone

    val timezoneOffset: Int
        get() = _timezoneOffset

    val forecasts: List<WeatherForecast> = _forecasts
    val hourlyForecasts: List<HourlyForecast> = _hourlyForecasts

    constructor(locationId: Int, coordinate: Coordinate, timezone: String, timezoneOffset: Int) : this() {
        _locationId = locationId
        _coordinate = coordinate
        _timezone = timezone
        _timezoneOffset = timezoneOffset
        _lastUpdate = Clock.System.now()
    }

    fun updateForecasts(forecasts: List<WeatherForecast>) {
        _forecasts.clear()
        _forecasts.addAll(forecasts.take(7)) // Limit to 7-day forecast
        _lastUpdate = Clock.System.now()

        addDomainEvent(WeatherUpdatedEvent(locationId, lastUpdate))
    }

    fun updateHourlyForecasts(hourlyForecasts: List<HourlyForecast>) {
        _hourlyForecasts.clear()
        _hourlyForecasts.addAll(hourlyForecasts.take(48)) // Limit to 48-hour forecast
        _lastUpdate = Clock.System.now()

        addDomainEvent(WeatherUpdatedEvent(locationId, lastUpdate))
    }

    fun getForecastForDate(date: LocalDate): WeatherForecast? {
        return _forecasts.firstOrNull { it.date == date }
    }

    fun getCurrentForecast(): WeatherForecast? {
        val today = kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
        return getForecastForDate(today)
    }

    fun getHourlyForecastsForDate(date: LocalDate): List<HourlyForecast> {
        return _hourlyForecasts.filter {
            val forecastDate = it.dateTime.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            forecastDate == date
        }
    }

    fun getHourlyForecastsForRange(startTime: Instant, endTime: Instant): List<HourlyForecast> {
        return _hourlyForecasts.filter { it.dateTime >= startTime && it.dateTime <= endTime }
    }

    fun getCurrentHourlyForecast(): HourlyForecast? {
        val now = Clock.System.now()
        return _hourlyForecasts.firstOrNull { it.dateTime >= now }
    }
}
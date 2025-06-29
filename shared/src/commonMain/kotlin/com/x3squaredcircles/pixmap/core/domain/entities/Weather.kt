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
class Weather : AggregateRoot {

    private val _forecasts = mutableListOf<WeatherForecast>()
    private val _hourlyForecasts = mutableListOf<HourlyForecast>()
    private var _coordinate: Coordinate? = null
    private var _lastUpdate: Instant = Clock.System.now()
    private var _timezone: String = ""
    private var _timezoneOffset: Int = 0

    var locationId: Int = 0
        private set

    val coordinate: Coordinate
        get() = _coordinate ?: throw IllegalStateException("Coordinate cannot be null")

    var lastUpdate: Instant
        get() = _lastUpdate
        private set(value) {
            _lastUpdate = value
        }

    var timezone: String
        get() = _timezone
        set(value) {
            _timezone = value
        }

    var timezoneOffset: Int
        get() = _timezoneOffset
        private set(value) {
            _timezoneOffset = value
        }

    val forecasts: List<WeatherForecast> get() = _forecasts.toList()
    val hourlyForecasts: List<HourlyForecast> get() = _hourlyForecasts.toList()

    // For ORM
    constructor()

    constructor(locationId: Int, coordinate: Coordinate, timezone: String, timezoneOffset: Int) {
        this.locationId = locationId
        this._coordinate = coordinate
        this.timezone = timezone
        this.timezoneOffset = timezoneOffset
        this.lastUpdate = Clock.System.now()
    }

    fun updateForecasts(forecasts: List<WeatherForecast>) {
        _forecasts.clear()
        _forecasts.addAll(forecasts.take(7)) // Limit to 7-day forecast
        lastUpdate = Clock.System.now()

        addDomainEvent(WeatherUpdatedEvent(locationId, lastUpdate))
    }

    fun updateHourlyForecasts(hourlyForecasts: List<HourlyForecast>) {
        _hourlyForecasts.clear()
        _hourlyForecasts.addAll(hourlyForecasts.take(48)) // Limit to 48-hour forecast
        lastUpdate = Clock.System.now()

        addDomainEvent(WeatherUpdatedEvent(locationId, lastUpdate))
    }

    fun getForecastForDate(date: LocalDate): WeatherForecast? {
        return _forecasts.firstOrNull { it.date == date }
    }

    fun getCurrentForecast(): WeatherForecast? {
        // Note: This would need to be adjusted based on the device's local time zone
        // For now, using a placeholder date
        val today = LocalDate(2024, 1, 1) // This should be calculated from device local time
        return getForecastForDate(today)
    }

    fun getHourlyForecastsForDate(date: LocalDate): List<HourlyForecast> {
        return _hourlyForecasts.filter {
            // This comparison would need proper timezone handling in actual implementation
            it.dateTime.toString().startsWith(date.toString())
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
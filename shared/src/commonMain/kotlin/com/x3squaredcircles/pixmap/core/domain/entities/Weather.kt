//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/entities/Weather.kt
package com.x3squaredcircles.pixmap.shared.domain.entities

import com.x3squaredcircles.pixmap.shared.domain.common.AggregateRoot
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Weather aggregate root
 */
@Serializable
data class Weather(
    val locationId: Int,
    val coordinate: Coordinate,
    val timezone: String,
    val timezoneOffset: Int,
    val lastUpdate: Instant,
    val forecasts: List<WeatherForecast> = emptyList(),
    val hourlyForecasts: List<HourlyForecast> = emptyList()
) : AggregateRoot() {

    init {
        require(locationId > 0) { "LocationId must be greater than zero" }
        require(timezone.isNotBlank()) { "Timezone cannot be blank" }
    }

    companion object {
        /**
         * Factory method to create weather with all related data
         */
        fun create(
            id: Int = 0,
            locationId: Int,
            coordinate: Coordinate,
            timezone: String,
            timezoneOffset: Int,
            forecasts: List<WeatherForecast> = emptyList(),
            hourlyForecasts: List<HourlyForecast> = emptyList(),
            lastUpdate: Instant
        ): Weather {
            val weather = Weather(
                locationId = locationId,
                coordinate = coordinate,
                timezone = timezone,
                timezoneOffset = timezoneOffset,
                lastUpdate = lastUpdate,
                forecasts = forecasts,
                hourlyForecasts = hourlyForecasts
            )
            // Set ID if provided (used by repository layer)
            if (id > 0) {
                weather.id = id
            }
            return weather
        }
    }

    /**
     * Updates weather data with new forecasts
     */
    fun updateForecasts(
        newForecasts: List<WeatherForecast>,
        newHourlyForecasts: List<HourlyForecast> = hourlyForecasts
    ): Weather {
        return copy(
            forecasts = newForecasts,
            hourlyForecasts = newHourlyForecasts,
            lastUpdate = Clock.System.now()
        )
    }

    /**
     * Updates the last update timestamp
     */
    fun updateTimestamp(timestamp: Instant = Clock.System.now()): Weather {
        return copy(lastUpdate = timestamp)
    }

    /**
     * Checks if weather data is considered stale
     */
    fun isStale(maxAge: kotlin.time.Duration): Boolean {
        val now = kotlinx.datetime.Clock.System.now()
        return (now - lastUpdate) > maxAge
    }
}
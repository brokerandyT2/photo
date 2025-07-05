// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetWeatherByLocationIdQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import kotlinx.datetime.Clock

/**
 * Handler for GetWeatherByLocationIdQuery
 */
class GetWeatherByLocationIdQueryHandler(
    private val weatherRepository: IWeatherRepository
) : IRequestHandler<GetWeatherByLocationIdQuery, WeatherDto?> {

    override suspend fun handle(request: GetWeatherByLocationIdQuery): WeatherDto? {
        val result = weatherRepository.getByLocationIdAsync(request.locationId)

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve weather for location: ${result.errorMessage}")
        }

        val weather = result.data ?: return null

        return mapWeatherToDto(weather)
    }

    private fun mapWeatherToDto(weather: Weather): WeatherDto {
        val currentForecast = weather.forecasts.firstOrNull()

        return WeatherDto(
            id = weather.id,
            locationId = weather.locationId,
            latitude = weather.coordinate.latitude,
            longitude = weather.coordinate.longitude,
            timezone = weather.timezone,
            timezoneOffset = weather.timezoneOffset,
            lastUpdate = weather.lastUpdate,
            temperature = currentForecast?.temperature ?: 0.0,
            minimumTemp = currentForecast?.minTemperature ?: 0.0,
            maximumTemp = currentForecast?.maxTemperature ?: 0.0,
            description = currentForecast?.description ?: "",
            icon = currentForecast?.icon ?: "",
            windSpeed = currentForecast?.wind?.speed ?: 0.0,
            windDirection = currentForecast?.wind?.direction ?: 0.0,
            windGust = currentForecast?.wind?.gust,
            humidity = currentForecast?.humidity ?: 0,
            pressure = currentForecast?.pressure ?: 0,
            clouds = currentForecast?.clouds ?: 0,
            uvIndex = currentForecast?.uvIndex ?: 0.0,
            precipitation = currentForecast?.precipitation,
            sunrise = currentForecast?.sunrise ?: Clock.System.now(),
            sunset = currentForecast?.sunset ?: Clock.System.now(),
            moonRise = currentForecast?.moonRise,
            moonSet = currentForecast?.moonSet,
            moonPhase = currentForecast?.moonPhase ?: 0.0
        )
    }
}
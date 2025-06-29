package com.x3squaredcircles.pixmap.shared.services

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Interface for weather API services
 */
interface IWeatherService {
    suspend fun getCurrentWeather(coordinate: Coordinate): Result<WeatherDto>
    suspend fun getForecast(coordinate: Coordinate, days: Int = 7): Result<WeatherDto>
    suspend fun getHourlyForecast(coordinate: Coordinate, hours: Int = 48): Result<WeatherDto>
}
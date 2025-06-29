// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/services/IWeatherService.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyWeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto

/**
 * Service interface for weather operations
 */
interface IWeatherService {
    suspend fun getWeatherAsync(latitude: Double, longitude: Double): Result<WeatherDto>
    suspend fun updateWeatherForLocationAsync(locationId: Int): Result<WeatherDto>
    suspend fun getForecastAsync(latitude: Double, longitude: Double, days: Int = 7): Result<WeatherForecastDto>
    suspend fun getHourlyForecastAsync(latitude: Double, longitude: Double): Result<HourlyWeatherForecastDto>
    suspend fun updateAllWeatherAsync(): Result<Int>
}
package com.x3squaredcircles.pixmap.android.services

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.services.IWeatherService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Android implementation of weather service using HTTP API
 */
class AndroidWeatherService(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openweathermap.org/data/2.5"
) : IWeatherService {

    override suspend fun getCurrentWeather(coordinate: Coordinate): Result<WeatherDto> {
        return try {
            val response = httpClient.get("$baseUrl/weather") {
                parameter("lat", coordinate.latitude)
                parameter("lon", coordinate.longitude)
                parameter("appid", apiKey)
                parameter("units", "metric")
            }

            if (response.status == HttpStatusCode.OK) {
                val weatherData: String = response.body()
                val weatherDto = parseCurrentWeatherResponse(weatherData, coordinate)
                Result.success(weatherDto)
            } else {
                Result.error("Weather API error: ${response.status}")
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun getForecast(coordinate: Coordinate, days: Int): Result<WeatherDto> {
        return try {
            val response = httpClient.get("$baseUrl/forecast") {
                parameter("lat", coordinate.latitude)
                parameter("lon", coordinate.longitude)
                parameter("appid", apiKey)
                parameter("units", "metric")
                parameter("cnt", days * 8) // 8 forecasts per day (3-hour intervals)
            }

            if (response.status == HttpStatusCode.OK) {
                val forecastData: String = response.body()
                val weatherDto = parseForecastResponse(forecastData, coordinate)
                Result.success(weatherDto)
            } else {
                Result.error("Weather API error: ${response.status}")
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun getHourlyForecast(coordinate: Coordinate, hours: Int): Result<WeatherDto> {
        return try {
            val response = httpClient.get("$baseUrl/forecast") {
                parameter("lat", coordinate.latitude)
                parameter("lon", coordinate.longitude)
                parameter("appid", apiKey)
                parameter("units", "metric")
                parameter("cnt", minOf(hours / 3, 40)) // API returns 3-hour intervals, max 40 items
            }

            if (response.status == HttpStatusCode.OK) {
                val forecastData: String = response.body()
                val weatherDto = parseHourlyForecastResponse(forecastData, coordinate)
                Result.success(weatherDto)
            } else {
                Result.error("Weather API error: ${response.status}")
            }
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    private fun parseCurrentWeatherResponse(json: String, coordinate: Coordinate): WeatherDto {
        val jsonElement = Json.parseToJsonElement(json)
        return WeatherDto(
            locationId = 0,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            lastUpdate = kotlinx.datetime.Clock.System.now(),
            timezone = "UTC",
            timezoneOffset = 0,
            forecasts = emptyList(),
            hourlyForecasts = emptyList()
        )
    }

    private fun parseForecastResponse(json: String, coordinate: Coordinate): WeatherDto {
        val jsonElement = Json.parseToJsonElement(json)
        return WeatherDto(
            locationId = 0,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            lastUpdate = kotlinx.datetime.Clock.System.now(),
            timezone = "UTC",
            timezoneOffset = 0,
            forecasts = emptyList(),
            hourlyForecasts = emptyList()
        )
    }

    private fun parseHourlyForecastResponse(json: String, coordinate: Coo
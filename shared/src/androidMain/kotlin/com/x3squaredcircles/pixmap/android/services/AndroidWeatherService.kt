package com.x3squaredcircles.pixmap.android.services

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.services.IWeatherService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

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
        return try {
            val jsonElement = Json.parseToJsonElement(json).jsonObject
            val main = jsonElement["main"]?.jsonObject
            val weather = jsonElement["weather"]?.jsonArray?.get(0)?.jsonObject
            val wind = jsonElement["wind"]?.jsonObject
            val sys = jsonElement["sys"]?.jsonObject
            val clouds = jsonElement["clouds"]?.jsonObject

            WeatherDto(
                id = jsonElement["id"]?.jsonPrimitive?.int ?: 0,
                locationId = 0,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                timezone = "UTC",
                timezoneOffset = jsonElement["timezone"]?.jsonPrimitive?.int ?: 0,
                lastUpdate = kotlinx.datetime.Clock.System.now(),
                temperature = main?.get("temp")?.jsonPrimitive?.double ?: 0.0,
                minimumTemp = main?.get("temp_min")?.jsonPrimitive?.double ?: 0.0,
                maximumTemp = main?.get("temp_max")?.jsonPrimitive?.double ?: 0.0,
                description = weather?.get("description")?.jsonPrimitive?.content ?: "",
                icon = weather?.get("icon")?.jsonPrimitive?.content ?: "",
                windSpeed = wind?.get("speed")?.jsonPrimitive?.double ?: 0.0,
                windDirection = wind?.get("deg")?.jsonPrimitive?.double ?: 0.0,
                humidity = main?.get("humidity")?.jsonPrimitive?.int ?: 0,
                pressure = main?.get("pressure")?.jsonPrimitive?.int ?: 0,
                clouds = clouds?.get("all")?.jsonPrimitive?.int ?: 0,
                uvIndex = 0.0, // Not available in current weather API
                sunrise = sys?.get("sunrise")?.jsonPrimitive?.long?.let {
                    kotlinx.datetime.Instant.fromEpochSeconds(it)
                } ?: kotlinx.datetime.Clock.System.now(),
                sunset = sys?.get("sunset")?.jsonPrimitive?.long?.let {
                    kotlinx.datetime.Instant.fromEpochSeconds(it)
                } ?: kotlinx.datetime.Clock.System.now(),
                moonPhase = 0.0 // Not available in current weather API
            )
        } catch (e: Exception) {
            // Return placeholder data if parsing fails
            WeatherDto(
                id = 0,
                locationId = 0,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                timezone = "UTC",
                timezoneOffset = 0,
                lastUpdate = kotlinx.datetime.Clock.System.now(),
                temperature = 0.0,
                minimumTemp = 0.0,
                maximumTemp = 0.0,
                description = "Unable to parse weather data",
                icon = "",
                windSpeed = 0.0,
                windDirection = 0.0,
                humidity = 0,
                pressure = 0,
                clouds = 0,
                uvIndex = 0.0,
                sunrise = kotlinx.datetime.Clock.System.now(),
                sunset = kotlinx.datetime.Clock.System.now(),
                moonPhase = 0.0
            )
        }
    }

    private fun parseForecastResponse(json: String, coordinate: Coordinate): WeatherDto {
        return try {
            val jsonElement = Json.parseToJsonElement(json).jsonObject
            val forecastList = jsonElement["list"]?.jsonArray?.firstOrNull()?.jsonObject
            val main = forecastList?.get("main")?.jsonObject
            val weather = forecastList?.get("weather")?.jsonArray?.get(0)?.jsonObject
            val wind = forecastList?.get("wind")?.jsonObject
            val clouds = forecastList?.get("clouds")?.jsonObject

            WeatherDto(
                id = 0,
                locationId = 0,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                timezone = "UTC",
                timezoneOffset = 0,
                lastUpdate = kotlinx.datetime.Clock.System.now(),
                temperature = main?.get("temp")?.jsonPrimitive?.double ?: 0.0,
                minimumTemp = main?.get("temp_min")?.jsonPrimitive?.double ?: 0.0,
                maximumTemp = main?.get("temp_max")?.jsonPrimitive?.double ?: 0.0,
                description = weather?.get("description")?.jsonPrimitive?.content ?: "",
                icon = weather?.get("icon")?.jsonPrimitive?.content ?: "",
                windSpeed = wind?.get("speed")?.jsonPrimitive?.double ?: 0.0,
                windDirection = wind?.get("deg")?.jsonPrimitive?.double ?: 0.0,
                humidity = main?.get("humidity")?.jsonPrimitive?.int ?: 0,
                pressure = main?.get("pressure")?.jsonPrimitive?.int ?: 0,
                clouds = clouds?.get("all")?.jsonPrimitive?.int ?: 0,
                uvIndex = 0.0,
                sunrise = kotlinx.datetime.Clock.System.now(),
                sunset = kotlinx.datetime.Clock.System.now(),
                moonPhase = 0.0
            )
        } catch (e: Exception) {
            WeatherDto(
                id = 0,
                locationId = 0,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                timezone = "UTC",
                timezoneOffset = 0,
                lastUpdate = kotlinx.datetime.Clock.System.now(),
                temperature = 0.0,
                minimumTemp = 0.0,
                maximumTemp = 0.0,
                description = "Unable to parse forecast data",
                icon = "",
                windSpeed = 0.0,
                windDirection = 0.0,
                humidity = 0,
                pressure = 0,
                clouds = 0,
                uvIndex = 0.0,
                sunrise = kotlinx.datetime.Clock.System.now(),
                sunset = kotlinx.datetime.Clock.System.now(),
                moonPhase = 0.0
            )
        }
    }

    private fun parseHourlyForecastResponse(json: String, coordinate: Coordinate): WeatherDto {
        return try {
            val jsonElement = Json.parseToJsonElement(json).jsonObject
            val forecastList = jsonElement["list"]?.jsonArray?.firstOrNull()?.jsonObject
            val main = forecastList?.get("main")?.jsonObject
            val weather = forecastList?.get("weather")?.jsonArray?.get(0)?.jsonObject
            val wind = forecastList?.get("wind")?.jsonObject
            val clouds = forecastList?.get("clouds")?.jsonObject

            WeatherDto(
                id = 0,
                locationId = 0,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                timezone = "UTC",
                timezoneOffset = 0,
                lastUpdate = kotlinx.datetime.Clock.System.now(),
                temperature = main?.get("temp")?.jsonPrimitive?.double ?: 0.0,
                minimumTemp = main?.get("temp_min")?.jsonPrimitive?.double ?: 0.0,
                maximumTemp = main?.get("temp_max")?.jsonPrimitive?.double ?: 0.0,
                description = weather?.get("description")?.jsonPrimitive?.content ?: "",
                icon = weather?.get("icon")?.jsonPrimitive?.content ?: "",
                windSpeed = wind?.get("speed")?.jsonPrimitive?.double ?: 0.0,
                windDirection = wind?.get("deg")?.jsonPrimitive?.double ?: 0.0,
                humidity = main?.get("humidity")?.jsonPrimitive?.int ?: 0,
                pressure = main?.get("pressure")?.jsonPrimitive?.int ?: 0,
                clouds = clouds?.get("all")?.jsonPrimitive?.int ?: 0,
                uvIndex = 0.0,
                sunrise = kotlinx.datetime.Clock.System.now(),
                sunset = kotlinx.datetime.Clock.System.now(),
                moonPhase = 0.0
            )
        } catch (e: Exception) {
            WeatherDto(
                id = 0,
                locationId = 0,
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                timezone = "UTC",
                timezoneOffset = 0,
                lastUpdate = kotlinx.datetime.Clock.System.now(),
                temperature = 0.0,
                minimumTemp = 0.0,
                maximumTemp = 0.0,
                description = "Unable to parse hourly forecast data",
                icon = "",
                windSpeed = 0.0,
                windDirection = 0.0,
                humidity = 0,
                pressure = 0,
                clouds = 0,
                uvIndex = 0.0,
                sunrise = kotlinx.datetime.Clock.System.now(),
                sunset = kotlinx.datetime.Clock.System.now(),
                moonPhase = 0.0
            )
        }
    }
}
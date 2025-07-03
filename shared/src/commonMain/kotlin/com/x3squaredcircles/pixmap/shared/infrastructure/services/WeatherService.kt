// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/WeatherService.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyWeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.DailyForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyForecastDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IWeatherService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.WeatherUpdateStatus
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.WeatherAlert
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.AirQualityDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.AlertSeverity
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.entities.WeatherForecast
import com.x3squaredcircles.pixmap.shared.domain.entities.HourlyForecast
import com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Weather service implementation that integrates with external weather API
 */
class WeatherService(
    private val httpClient: HttpClient,
    private val locationRepository: ILocationRepository,
    private val weatherRepository: IWeatherRepository,
    private val settingRepository: ISettingRepository,
    private val logging: ILoggingService
) : IWeatherService {

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/3.0"
        private const val API_TIMEOUT_MS = 30000L
        private const val STALE_THRESHOLD_HOURS = 1
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun getWeatherAsync(latitude: Double, longitude: Double): Result<WeatherDto> {
        return try {
            val apiResult = getWeatherFromApiAsync(latitude, longitude)
            if (!apiResult.isSuccess) {
                return Result.failure("Failed to fetch weather: ${apiResult.errorMessage}")
            }

            val apiData = apiResult.data!!
            val weatherDto = mapToWeatherDto(apiData, latitude, longitude)
            Result.success(weatherDto)
        } catch (ex: Exception) {
            logging.error("Error getting weather", ex)
            Result.failure("Failed to get weather data: ${ex.message}")
        }
    }

    override suspend fun updateWeatherForLocationAsync(locationId: Int): Result<WeatherDto> {
        return try {
            val locationResult = locationRepository.getByIdAsync(locationId)
            if (!locationResult.isSuccess || locationResult.data == null) {
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!
            val coordinate = location.coordinate

            // Get existing weather or create new
            val existingWeatherResult = weatherRepository.getByLocationIdAsync(locationId)
            val existingWeather = if (existingWeatherResult.isSuccess) existingWeatherResult.data else null

            // Fetch fresh data from API
            val apiResult = getWeatherFromApiAsync(coordinate.latitude, coordinate.longitude)
            if (!apiResult.isSuccess || apiResult.data == null) {
                return Result.failure("Failed to fetch weather data")
            }

            val apiData = apiResult.data!!

            // Create/update weather entity
            val weather = existingWeather ?: Weather(
                locationId = locationId,
                coordinate = coordinate,
                timezone = apiData.timezone,
                timezoneOffset = apiData.timezoneOffset,
                lastUpdate = Clock.System.now(),
                forecasts = emptyList(),
                hourlyForecasts = emptyList()
            )

            // Update weather with current data using copy
            val updatedWeather = weather.copy(lastUpdate = Clock.System.now())

            // Create forecasts from API data
            val forecasts = apiData.dailyForecasts.map { dailyForecast ->
                WeatherForecast(
                    id = 0,
                    weatherId = updatedWeather.id,
                    date = dailyForecast.date.toLocalDateTime(TimeZone.UTC).date,
                    sunrise = dailyForecast.sunrise,
                    sunset = dailyForecast.sunset,
                    temperature = dailyForecast.temperature,
                    minTemperature = dailyForecast.minTemperature,
                    maxTemperature = dailyForecast.maxTemperature,
                    description = dailyForecast.description,
                    icon = dailyForecast.icon,
                    wind = WindInfo(
                        speed = dailyForecast.windSpeed,
                        direction = dailyForecast.windDirection,
                        gust = dailyForecast.windGust
                    ),
                    humidity = dailyForecast.humidity,
                    pressure = dailyForecast.pressure,
                    clouds = dailyForecast.clouds,
                    uvIndex = dailyForecast.uvIndex,
                    precipitation = dailyForecast.precipitation,
                    moonRise = dailyForecast.moonRise,
                    moonSet = dailyForecast.moonSet,
                    moonPhase = dailyForecast.moonPhase ?: 0.0
                )
            }

            // Create hourly forecasts from API data
            val hourlyForecasts = apiData.hourlyForecasts.map { hourlyForecast ->
                HourlyForecast(
                    id = 0,
                    weatherId = updatedWeather.id,
                    dateTime = hourlyForecast.dateTime,
                    temperature = hourlyForecast.temperature,
                    feelsLike = hourlyForecast.feelsLike ?: hourlyForecast.temperature,
                    description = hourlyForecast.description,
                    icon = hourlyForecast.icon,
                    wind = WindInfo(
                        speed = hourlyForecast.windSpeed,
                        direction = hourlyForecast.windDirection,
                        gust = hourlyForecast.windGust
                    ),
                    humidity = hourlyForecast.humidity,
                    pressure = hourlyForecast.pressure,
                    clouds = hourlyForecast.clouds,
                    uvIndex = hourlyForecast.uvIndex,
                    probabilityOfPrecipitation = hourlyForecast.probabilityOfPrecipitation,
                    visibility = hourlyForecast.visibility.toInt(),
                    dewPoint = hourlyForecast.dewPoint
                )
            }

            // Update weather with new forecasts using copy
            val finalWeather = updatedWeather.copy(
                forecasts = forecasts,
                hourlyForecasts = hourlyForecasts
            )

            // Save to database
            val saveResult = if (existingWeather != null) {
                weatherRepository.updateAsync(finalWeather)
            } else {
                weatherRepository.addAsync(finalWeather)
            }

            if (!saveResult.isSuccess) {
                return Result.failure("Failed to save weather data")
            }

            // Map current weather data to DTO and return
            val weatherDto = mapToWeatherDtoAsync(finalWeather, apiData)
            Result.success(weatherDto)
        } catch (ex: Exception) {
            logging.error("Error updating weather for location $locationId", ex)
            Result.failure("Failed to update weather for location: ${ex.message}")
        }
    }

    override suspend fun getForecastAsync(latitude: Double, longitude: Double, days: Int): Result<WeatherForecastDto> {
        return try {
            val apiResult = getWeatherFromApiAsync(latitude, longitude)
            if (!apiResult.isSuccess || apiResult.data == null) {
                return Result.failure("Failed to fetch forecast data")
            }

            val apiData = apiResult.data!!
            val dailyForecasts = apiData.dailyForecasts.take(days).map { dailyData ->
                DailyForecastDto(
                    date = dailyData.date.toLocalDateTime(TimeZone.UTC).date,
                    sunrise = dailyData.sunrise,
                    sunset = dailyData.sunset,
                    temperature = dailyData.temperature,
                    minTemperature = dailyData.minTemperature,
                    maxTemperature = dailyData.maxTemperature,
                    description = dailyData.description,
                    icon = dailyData.icon,
                    windSpeed = dailyData.windSpeed,
                    windDirection = dailyData.windDirection,
                    windGust = dailyData.windGust,
                    humidity = dailyData.humidity,
                    pressure = dailyData.pressure,
                    clouds = dailyData.clouds,
                    uvIndex = dailyData.uvIndex,
                    precipitation = dailyData.precipitation,
                    moonRise = dailyData.moonRise,
                    moonSet = dailyData.moonSet,
                    moonPhase = dailyData.moonPhase ?: 0.0
                )
            }

            val forecastDto = WeatherForecastDto(
                timezone = apiData.timezone,
                timezoneOffset = apiData.timezoneOffset,
                lastUpdate = Clock.System.now(),
                dailyForecasts = dailyForecasts
            )

            Result.success(forecastDto)
        } catch (ex: Exception) {
            logging.error("Error getting forecast", ex)
            Result.failure("Failed to get forecast data: ${ex.message}")
        }
    }

    override suspend fun getHourlyForecastAsync(latitude: Double, longitude: Double): Result<HourlyWeatherForecastDto> {
        return try {
            val apiResult = getWeatherFromApiAsync(latitude, longitude)
            if (!apiResult.isSuccess || apiResult.data == null) {
                return Result.failure("Failed to fetch hourly forecast data")
            }

            val apiData = apiResult.data!!
            val hourlyForecasts = apiData.hourlyForecasts.map { hourlyData ->
                HourlyForecastDto(
                    dateTime = hourlyData.dateTime,
                    temperature = hourlyData.temperature,
                    feelsLike = hourlyData.feelsLike ?: hourlyData.temperature,
                    description = hourlyData.description,
                    icon = hourlyData.icon,
                    windSpeed = hourlyData.windSpeed,
                    windDirection = hourlyData.windDirection,
                    windGust = hourlyData.windGust,
                    humidity = hourlyData.humidity,
                    pressure = hourlyData.pressure,
                    clouds = hourlyData.clouds,
                    uvIndex = hourlyData.uvIndex,
                    probabilityOfPrecipitation = hourlyData.probabilityOfPrecipitation,
                    visibility = hourlyData.visibility.toInt(),
                    dewPoint = hourlyData.dewPoint
                )
            }

            val hourlyForecastDto = HourlyWeatherForecastDto(
                timezone = apiData.timezone,
                timezoneOffset = apiData.timezoneOffset,
                lastUpdate = Clock.System.now(),
                hourlyForecasts = hourlyForecasts
            )

            Result.success(hourlyForecastDto)
        } catch (ex: Exception) {
            logging.error("Error getting hourly forecast", ex)
            Result.failure("Failed to get hourly forecast data: ${ex.message}")
        }
    }

    override suspend fun updateAllWeatherAsync(): Result<Int> {
        return try {
            val locationsResult = locationRepository.getActiveAsync()
            if (!locationsResult.isSuccess || locationsResult.data == null) {
                return Result.failure("Failed to retrieve active locations")
            }

            val locations = locationsResult.data!!
            var successCount = 0

            // Update weather for each location
            coroutineScope {
                locations.map { location ->
                    async {
                        try {
                            val updateResult = updateWeatherForLocationAsync(location.id)
                            if (updateResult.isSuccess) {
                                successCount++
                            } else {
                                logging.error("Failed to update weather for location ${location.id}: ${updateResult.errorMessage}")
                            }
                        } catch (ex: Exception) {
                            logging.error("Error updating weather for location ${location.id}", ex)
                        }
                    }
                }.awaitAll()
            }

            Result.success(successCount)
        } catch (ex: Exception) {
            logging.error("Error updating all weather", ex)
            Result.failure("Failed to update all weather: ${ex.message}")
        }
    }

    override fun isWeatherDataStale(lastUpdate: Instant): Boolean {
        val now = Clock.System.now()
        val staleThreshold = now.minus(kotlin.time.Duration.parse("${STALE_THRESHOLD_HOURS}h"))
        return lastUpdate < staleThreshold
    }

    override suspend fun getCachedWeatherAsync(locationId: Int): Result<WeatherDto?> {
        return try {
            val weatherResult = weatherRepository.getByLocationIdAsync(locationId)
            if (!weatherResult.isSuccess || weatherResult.data == null) {
                return Result.success(null)
            }

            val weather = weatherResult.data!!
            if (isWeatherDataStale(weather.lastUpdate)) {
                return Result.success(null)
            }

            val cachedDto = mapToWeatherDto(weather)
            Result.success(cachedDto)
        } catch (ex: Exception) {
            logging.error("Error getting cached weather", ex)
            Result.failure("Failed to get cached weather: ${ex.message}")
        }
    }

    override suspend fun clearWeatherCacheAsync(locationId: Int): Result<Unit> {
        return try {
            val weatherResult = weatherRepository.getByLocationIdAsync(locationId)
            if (weatherResult.isSuccess && weatherResult.data != null) {
                weatherRepository.deleteAsync(weatherResult.data!!.id)
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            logging.error("Error clearing weather cache", ex)
            Result.failure("Failed to clear weather cache: ${ex.message}")
        }
    }

    override suspend fun clearAllWeatherCacheAsync(): Result<Unit> {
        return try {
            val allWeatherResult = weatherRepository.getRecentAsync(1000) // Get a large batch
            if (allWeatherResult.isSuccess && allWeatherResult.data != null) {
                allWeatherResult.data!!.forEach { weather ->
                    weatherRepository.deleteAsync(weather.id)
                }
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            logging.error("Error clearing all weather cache", ex)
            Result.failure("Failed to clear all weather cache: ${ex.message}")
        }
    }

    override suspend fun getWeatherUpdateStatusAsync(locationId: Int): Result<WeatherUpdateStatus> {
        return try {
            val weatherResult = weatherRepository.getByLocationIdAsync(locationId)
            val weather = weatherResult.data

            val updateStatus = WeatherUpdateStatus(
                locationId = locationId,
                lastUpdate = weather?.lastUpdate,
                isStale = weather?.let { isWeatherDataStale(it.lastUpdate) } ?: true,
                isUpdating = false, // This would be tracked separately in a real implementation
                lastError = null,
                nextUpdateTime = null
            )

            Result.success(updateStatus)
        } catch (ex: Exception) {
            logging.error("Error getting weather update status", ex)
            Result.failure("Failed to get weather update status: ${ex.message}")
        }
    }

    override suspend fun validateApiConfigurationAsync(): Result<Boolean> {
        return try {
            val apiKeyResult = getApiKeyAsync()
            if (!apiKeyResult.isSuccess || apiKeyResult.data == null) {
                return Result.success(false)
            }

            // Try a simple API call to validate the key
            val testResult = getWeatherFromApiAsync(40.7128, -74.0060) // NYC coordinates
            Result.success(testResult.isSuccess)
        } catch (ex: Exception) {
            logging.error("Error validating API configuration", ex)
            Result.success(false)
        }
    }

    override suspend fun refreshWeatherAsync(locationId: Int, forceUpdate: Boolean): Result<WeatherDto> {
        return try {
            if (forceUpdate) {
                // Force update regardless of cache status
                updateWeatherForLocationAsync(locationId)
            } else {
                // Check if cached data is still valid
                val cachedResult = getCachedWeatherAsync(locationId)
                if (cachedResult.isSuccess && cachedResult.data != null) {
                    // Return cached data if it's still fresh
                    Result.success(cachedResult.data!!)
                } else {
                    // Update if no cached data or stale data
                    updateWeatherForLocationAsync(locationId)
                }
            }
        } catch (ex: Exception) {
            logging.error("Error refreshing weather for location $locationId", ex)
            Result.failure("Failed to refresh weather: ${ex.message}")
        }
    }

    override suspend fun getWeatherAlertsAsync(latitude: Double, longitude: Double): Result<List<WeatherAlert>> {
        return try {
            val apiKeyResult = getApiKeyAsync()
            if (!apiKeyResult.isSuccess || apiKeyResult.data == null) {
                return Result.failure("Weather API key not configured")
            }

            val apiKey = apiKeyResult.data!!
            val url = "$BASE_URL/onecall?lat=$latitude&lon=$longitude&appid=$apiKey&exclude=minutely,hourly,daily"

            val response = httpClient.get(url)

            if (response.status != HttpStatusCode.OK) {
                return Result.failure("Weather alerts API request failed: ${response.status}")
            }

            val responseBody = response.body<String>()
            val alertsResponse = parseWeatherAlertsResponse(responseBody)
            Result.success(alertsResponse)
        } catch (ex: Exception) {
            logging.error("Error getting weather alerts", ex)
            Result.failure("Failed to get weather alerts: ${ex.message}")
        }
    }

    override suspend fun getAirQualityAsync(latitude: Double, longitude: Double): Result<AirQualityDto> {
        return try {
            val apiKeyResult = getApiKeyAsync()
            if (!apiKeyResult.isSuccess || apiKeyResult.data == null) {
                return Result.failure("Weather API key not configured")
            }

            val apiKey = apiKeyResult.data!!
            val url = "https://api.openweathermap.org/data/2.5/air_pollution?lat=$latitude&lon=$longitude&appid=$apiKey"

            val response = httpClient.get(url)

            if (response.status != HttpStatusCode.OK) {
                return Result.failure("Air quality API request failed: ${response.status}")
            }

            val responseBody = response.body<String>()
            val airQualityResponse = parseAirQualityResponse(responseBody)
            Result.success(airQualityResponse)
        } catch (ex: Exception) {
            logging.error("Error getting air quality data", ex)
            Result.failure("Failed to get air quality data: ${ex.message}")
        }
    }

    private suspend fun getWeatherFromApiAsync(latitude: Double, longitude: Double): Result<WeatherApiResponse> {
        return try {
            val apiKeyResult = getApiKeyAsync()
            if (!apiKeyResult.isSuccess || apiKeyResult.data == null) {
                return Result.failure("Weather API key not configured")
            }

            val apiKey = apiKeyResult.data!!
            val url = "$BASE_URL?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric"

            val response = httpClient.get(url)

            if (response.status != HttpStatusCode.OK) {
                return Result.failure("Weather API request failed: ${response.status}")
            }

            val responseBody = response.body<String>()
            val apiResponse = parseWeatherResponse(responseBody)
            Result.success(apiResponse)
        } catch (ex: Exception) {
            logging.error("Error fetching weather from API", ex)
            Result.failure("Failed to fetch weather from API: ${ex.message}")
        }
    }

    private suspend fun getApiKeyAsync(): Result<String> {
        val settingResult = settingRepository.getByKeyAsync("WeatherApiKey")
        return if (settingResult.isSuccess && settingResult.data != null) {
            Result.success(settingResult.data!!.value)
        } else {
            Result.failure("Weather API key not found")
        }
    }

    private fun parseWeatherResponse(responseBody: String): WeatherApiResponse {
        return try {
            val jsonElement = Json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject

            // Parse timezone information
            val timezone = jsonObject["timezone"]?.jsonPrimitive?.content ?: "UTC"
            val timezoneOffset = jsonObject["timezone_offset"]?.jsonPrimitive?.intOrNull ?: 0

            // Parse daily forecasts
            val dailyArray = jsonObject["daily"]?.jsonArray ?: emptyList()
            val dailyForecasts = dailyArray.take(7).map { dailyJson ->
                val daily = dailyJson.jsonObject
                val temp = daily["temp"]?.jsonObject
                val weather = daily["weather"]?.jsonArray?.firstOrNull()?.jsonObject
                val wind = daily["wind_speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val windDir = daily["wind_deg"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val windGust = daily["wind_gust"]?.jsonPrimitive?.doubleOrNull

                DailyForecastData(
                    date = Instant.fromEpochSeconds(daily["dt"]?.jsonPrimitive?.longOrNull ?: 0),
                    sunrise = Instant.fromEpochSeconds(daily["sunrise"]?.jsonPrimitive?.longOrNull ?: 0),
                    sunset = Instant.fromEpochSeconds(daily["sunset"]?.jsonPrimitive?.longOrNull ?: 0),
                    temperature = temp?.get("day")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    minTemperature = temp?.get("min")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    maxTemperature = temp?.get("max")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    description = weather?.get("description")?.jsonPrimitive?.content ?: "",
                    icon = weather?.get("icon")?.jsonPrimitive?.content ?: "",
                    windSpeed = wind,
                    windDirection = windDir,
                    windGust = windGust,
                    humidity = daily["humidity"]?.jsonPrimitive?.intOrNull ?: 0,
                    pressure = daily["pressure"]?.jsonPrimitive?.intOrNull ?: 0,
                    clouds = daily["clouds"]?.jsonPrimitive?.intOrNull ?: 0,
                    uvIndex = daily["uvi"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    precipitation = daily["rain"]?.jsonObject?.get("1h")?.jsonPrimitive?.doubleOrNull,
                    moonRise = daily["moonrise"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) },
                    moonSet = daily["moonset"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) },
                    moonPhase = daily["moon_phase"]?.jsonPrimitive?.doubleOrNull
                )
            }

            // Parse hourly forecasts
            val hourlyArray = jsonObject["hourly"]?.jsonArray ?: emptyList()
            val hourlyForecasts = hourlyArray.take(48).map { hourlyJson ->
                val hourly = hourlyJson.jsonObject
                val weather = hourly["weather"]?.jsonArray?.firstOrNull()?.jsonObject
                val wind = hourly["wind_speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val windDir = hourly["wind_deg"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val windGust = hourly["wind_gust"]?.jsonPrimitive?.doubleOrNull

                HourlyForecastData(
                    dateTime = Instant.fromEpochSeconds(hourly["dt"]?.jsonPrimitive?.longOrNull ?: 0),
                    temperature = hourly["temp"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    feelsLike = hourly["feels_like"]?.jsonPrimitive?.doubleOrNull,
                    description = weather?.get("description")?.jsonPrimitive?.content ?: "",
                    icon = weather?.get("icon")?.jsonPrimitive?.content ?: "",
                    windSpeed = wind,
                    windDirection = windDir,
                    windGust = windGust,
                    humidity = hourly["humidity"]?.jsonPrimitive?.intOrNull ?: 0,
                    pressure = hourly["pressure"]?.jsonPrimitive?.intOrNull ?: 0,
                    clouds = hourly["clouds"]?.jsonPrimitive?.intOrNull ?: 0,
                    uvIndex = hourly["uvi"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    probabilityOfPrecipitation = hourly["pop"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    visibility = hourly["visibility"]?.jsonPrimitive?.doubleOrNull ?: 10000.0,
                    dewPoint = hourly["dew_point"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            }

            WeatherApiResponse(
                timezone = timezone,
                timezoneOffset = timezoneOffset,
                dailyForecasts = dailyForecasts,
                hourlyForecasts = hourlyForecasts
            )
        } catch (ex: Exception) {
            logging.error("Error parsing weather response", ex)
            // Return empty response as fallback
            WeatherApiResponse(
                timezone = "UTC",
                timezoneOffset = 0,
                dailyForecasts = emptyList(),
                hourlyForecasts = emptyList()
            )
        }
    }

    private fun parseWeatherAlertsResponse(responseBody: String): List<WeatherAlert> {
        return try {
            val jsonElement = Json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            val alertsArray = jsonObject["alerts"]?.jsonArray ?: return emptyList()

            alertsArray.map { alertJson ->
                val alert = alertJson.jsonObject
                val senderName = alert["sender_name"]?.jsonPrimitive?.content ?: "Weather Service"
                val event = alert["event"]?.jsonPrimitive?.content ?: "Weather Alert"
                val description = alert["description"]?.jsonPrimitive?.content ?: ""
                val start = alert["start"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) } ?: Clock.System.now()
                val end = alert["end"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) } ?: Clock.System.now()
                val tags = alert["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                // Determine severity based on event type and tags
                val severity = when {
                    tags.any { it.contains("extreme", ignoreCase = true) } ||
                            event.contains("extreme", ignoreCase = true) -> AlertSeverity.EXTREME
                    tags.any { it.contains("severe", ignoreCase = true) } ||
                            event.contains("severe", ignoreCase = true) -> AlertSeverity.SEVERE
                    tags.any { it.contains("moderate", ignoreCase = true) } ||
                            event.contains("moderate", ignoreCase = true) -> AlertSeverity.MODERATE
                    else -> AlertSeverity.MINOR
                }

                WeatherAlert(
                    title = event,
                    description = description,
                    severity = severity,
                    startTime = start,
                    endTime = end,
                    source = senderName,
                    tags = tags
                )
            }
        } catch (ex: Exception) {
            logging.error("Error parsing weather alerts response", ex)
            emptyList()
        }
    }

    private fun parseAirQualityResponse(responseBody: String): AirQualityDto {
        return try {
            val jsonElement = Json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            val list = jsonObject["list"]?.jsonArray?.firstOrNull()?.jsonObject

            if (list != null) {
                val main = list["main"]?.jsonObject
                val components = list["components"]?.jsonObject

                AirQualityDto(
                    aqi = main?.get("aqi")?.jsonPrimitive?.intOrNull ?: 1,
                    co = components?.get("co")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    no = components?.get("no")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    no2 = components?.get("no2")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    o3 = components?.get("o3")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    so2 = components?.get("so2")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    pm2_5 = components?.get("pm2_5")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    pm10 = components?.get("pm10")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    nh3 = components?.get("nh3")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    timestamp = Instant.fromEpochSeconds(list["dt"]?.jsonPrimitive?.longOrNull ?: Clock.System.now().epochSeconds)
                )
            } else {
                // Return default values if parsing fails
                AirQualityDto(
                    aqi = 1,
                    co = 0.0,
                    no = 0.0,
                    no2 = 0.0,
                    o3 = 0.0,
                    so2 = 0.0,
                    pm2_5 = 0.0,
                    pm10 = 0.0,
                    nh3 = 0.0,
                    timestamp = Clock.System.now()
                )
            }
        } catch (ex: Exception) {
            logging.error("Error parsing air quality response", ex)
            // Return default values as fallback
            AirQualityDto(
                aqi = 1,
                co = 0.0,
                no = 0.0,
                no2 = 0.0,
                o3 = 0.0,
                so2 = 0.0,
                pm2_5 = 0.0,
                pm10 = 0.0,
                nh3 = 0.0,
                timestamp = Clock.System.now()
            )
        }
    }

    private fun mapToWeatherDto(apiData: WeatherApiResponse, latitude: Double, longitude: Double): WeatherDto {
        val currentForecast = apiData.dailyForecasts.firstOrNull()
        return WeatherDto(
            id = 0,
            locationId = 0,
            latitude = latitude,
            longitude = longitude,
            timezone = apiData.timezone,
            timezoneOffset = apiData.timezoneOffset,
            lastUpdate = Clock.System.now(),
            temperature = currentForecast?.temperature ?: 0.0,
            minimumTemp = currentForecast?.minTemperature ?: 0.0,
            maximumTemp = currentForecast?.maxTemperature ?: 0.0,
            description = currentForecast?.description ?: "",
            icon = currentForecast?.icon ?: "",
            windSpeed = currentForecast?.windSpeed ?: 0.0,
            windDirection = currentForecast?.windDirection ?: 0.0,
            humidity = currentForecast?.humidity ?: 0,
            pressure = currentForecast?.pressure ?: 0,
            clouds = currentForecast?.clouds ?: 0,
            uvIndex = currentForecast?.uvIndex ?: 0.0,
            sunrise = currentForecast?.sunrise ?: Clock.System.now(),
            sunset = currentForecast?.sunset ?: Clock.System.now(),
            moonPhase = currentForecast?.moonPhase ?: 0.0
        )
    }

    private fun mapToWeatherDto(weather: Weather): WeatherDto {
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
            humidity = currentForecast?.humidity ?: 0,
            pressure = currentForecast?.pressure ?: 0,
            clouds = currentForecast?.clouds ?: 0,
            uvIndex = currentForecast?.uvIndex ?: 0.0,
            sunrise = currentForecast?.sunrise ?: Clock.System.now(),
            sunset = currentForecast?.sunset ?: Clock.System.now(),
            moonPhase = currentForecast?.moonPhase ?: 0.0
        )
    }

    private suspend fun mapToWeatherDtoAsync(weather: Weather, apiData: WeatherApiResponse): WeatherDto {
        val currentForecast = weather.forecasts.firstOrNull()
        val currentApiData = apiData.dailyForecasts.firstOrNull()

        return WeatherDto(
            id = weather.id,
            locationId = weather.locationId,
            latitude = weather.coordinate.latitude,
            longitude = weather.coordinate.longitude,
            timezone = weather.timezone,
            timezoneOffset = weather.timezoneOffset,
            lastUpdate = weather.lastUpdate,
            temperature = currentForecast?.temperature ?: currentApiData?.temperature ?: 0.0,
            minimumTemp = currentForecast?.minTemperature ?: currentApiData?.minTemperature ?: 0.0,
            maximumTemp = currentForecast?.maxTemperature ?: currentApiData?.maxTemperature ?: 0.0,
            description = currentForecast?.description ?: currentApiData?.description ?: "",
            icon = currentForecast?.icon ?: currentApiData?.icon ?: "",
            windSpeed = currentForecast?.wind?.speed ?: currentApiData?.windSpeed ?: 0.0,
            windDirection = currentForecast?.wind?.direction ?: currentApiData?.windDirection ?: 0.0,
            humidity = currentForecast?.humidity ?: currentApiData?.humidity ?: 0,
            pressure = currentForecast?.pressure ?: currentApiData?.pressure ?: 0,
            clouds = currentForecast?.clouds ?: currentApiData?.clouds ?: 0,
            uvIndex = currentForecast?.uvIndex ?: currentApiData?.uvIndex ?: 0.0,
            sunrise = currentForecast?.sunrise ?: currentApiData?.sunrise ?: Clock.System.now(),
            sunset = currentForecast?.sunset ?: currentApiData?.sunset ?: Clock.System.now(),
            moonPhase = currentForecast?.moonPhase ?: currentApiData?.moonPhase ?: 0.0
        )
    }
}

// Data classes for API responses
@Serializable
data class WeatherApiResponse(
    val timezone: String,
    val timezoneOffset: Int,
    val dailyForecasts: List<DailyForecastData>,
    val hourlyForecasts: List<HourlyForecastData>
)

@Serializable
data class DailyForecastData(
    val date: Instant,
    val sunrise: Instant,
    val sunset: Instant,
    val temperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double? = null,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val precipitation: Double? = null,
    val moonRise: Instant? = null,
    val moonSet: Instant? = null,
    val moonPhase: Double? = null
)

@Serializable
data class HourlyForecastData(
    val dateTime: Instant,
    val temperature: Double,
    val feelsLike: Double? = null,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val windDirection: Double,
    val windGust: Double? = null,
    val humidity: Int,
    val pressure: Int,
    val clouds: Int,
    val uvIndex: Double,
    val probabilityOfPrecipitation: Double,
    val visibility: Double,
    val dewPoint: Double
)
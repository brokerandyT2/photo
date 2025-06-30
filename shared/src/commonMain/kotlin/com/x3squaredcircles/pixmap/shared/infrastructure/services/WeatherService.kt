// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/services/WeatherService.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.services

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyWeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IWeatherService
import com.x3squaredcircles.pixmap.shared.domain.common.Result
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
import kotlinx.coroutines.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Weather service implementation that integrates with external weather API
 */
class WeatherService(
    private val httpClient: HttpClient,
    private val locationRepository: ILocationRepository,
    private val weatherRepository: IWeatherRepository,
    private val settingRepository: ISettingRepository,
    private val logger: Logger
) : IWeatherService {

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/3.0"
        private const val API_TIMEOUT_MS = 30000L
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun getWeatherAsync(latitude: Double, longitude: Double): Result<WeatherDto> {
        return try {
            val apiResult = getWeatherFromApiAsync(latitude, longitude)
            if (!apiResult.isSuccess) {
                return Result.failure("Failed to fetch weather: ${apiResult.exceptionOrNull()?.message}")
            }

            val apiData = apiResult.getOrThrow()
            val weatherDto = mapToWeatherDto(apiData, latitude, longitude)
            Result.success(weatherDto)
        } catch (ex: Exception) {
            logger.error("Error getting weather", ex)
            throw WeatherDomainException("Failed to get weather data", "WEATHER_API_ERROR", ex)
        }
    }

    override suspend fun updateWeatherForLocationAsync(locationId: Int): Result<WeatherDto> {
        return try {
            val locationResult = locationRepository.getByIdAsync(locationId)
            if (!locationResult.isSuccess) {
                return Result.failure("Location not found")
            }

            val location = locationResult.getOrNull()
                ?: return Result.failure("Location with ID $locationId not found")

            val existingWeatherResult = weatherRepository.getByLocationIdAsync(locationId)
            val existingWeather = if (existingWeatherResult.isSuccess) {
                existingWeatherResult.getOrNull()
            } else null

            val weatherResult = getWeatherFromApiAsync(location.coordinate.latitude, location.coordinate.longitude)
            if (!weatherResult.isSuccess) {
                return Result.failure("Failed to fetch weather")
            }

            val apiData = weatherResult.getOrThrow()
            val coordinate = Coordinate(location.coordinate.latitude, location.coordinate.longitude)

            val weather = if (existingWeather != null) {
                existingWeather
            } else {
                Weather(
                    locationId = locationId,
                    coordinate = coordinate,
                    timezone = apiData.timezone,
                    timezoneOffset = apiData.timezoneOffset
                )
            }

            // Create forecast entities from API data
            val forecasts = apiData.dailyForecasts.take(7).map { dailyForecast ->
                WeatherForecast(
                    weatherId = weather.id,
                    date = dailyForecast.date,
                    sunrise = dailyForecast.sunrise,
                    sunset = dailyForecast.sunset,
                    temperature = dailyForecast.temperature,
                    minTemperature = dailyForecast.minTemperature,
                    maxTemperature = dailyForecast.maxTemperature,
                    description = dailyForecast.description,
                    icon = dailyForecast.icon,
                    windInfo = WindInfo(
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
                    moonPhase = dailyForecast.moonPhase
                )
            }

            val hourlyForecasts = apiData.hourlyForecasts.take(48).map { hourlyForecast ->
                HourlyForecast(
                    weatherId = weather.id,
                    dateTime = hourlyForecast.dateTime,
                    temperature = hourlyForecast.temperature,
                    feelsLike = hourlyForecast.feelsLike,
                    description = hourlyForecast.description,
                    icon = hourlyForecast.icon,
                    windSpeed = hourlyForecast.windSpeed,
                    windDirection = hourlyForecast.windDirection,
                    windGust = hourlyForecast.windGust,
                    humidity = hourlyForecast.humidity,
                    pressure = hourlyForecast.pressure,
                    clouds = hourlyForecast.clouds,
                    uvIndex = hourlyForecast.uvIndex,
                    probabilityOfPrecipitation = hourlyForecast.probabilityOfPrecipitation,
                    visibility = hourlyForecast.visibility,
                    dewPoint = hourlyForecast.dewPoint
                )
            }

            // Update weather with new forecasts
            weather.updateForecasts(forecasts)
            weather.updateHourlyForecasts(hourlyForecasts)

            // Save to database
            val saveResult = if (existingWeather != null) {
                weatherRepository.updateAsync(weather)
            } else {
                weatherRepository.addAsync(weather)
            }

            if (!saveResult.isSuccess) {
                return Result.failure("Failed to save weather data")
            }

            // Map current weather data to DTO and return
            val weatherDto = mapToWeatherDtoAsync(weather, apiData)
            Result.success(weatherDto)
        } catch (ex: Exception) {
            logger.error("Error updating weather for location $locationId", ex)
            throw WeatherDomainException("Failed to update weather for location", "UPDATE_WEATHER_ERROR", ex)
        }
    }

    override suspend fun getForecastAsync(latitude: Double, longitude: Double, days: Int): Result<WeatherForecastDto> {
        val result = getWeatherFromApiAsync(latitude, longitude)
        if (!result.isSuccess) {
            return Result.failure("Failed to get forecast")
        }

        val apiData = result.getOrThrow()
        val forecastDto = WeatherForecastDto(
            timezone = apiData.timezone,
            timezoneOffset = apiData.timezoneOffset,
            lastUpdate = Clock.System.now(),
            dailyForecasts = apiData.dailyForecasts.take(days)
        )

        return Result.success(forecastDto)
    }

    override suspend fun getHourlyForecastAsync(latitude: Double, longitude: Double): Result<HourlyWeatherForecastDto> {
        val result = getWeatherFromApiAsync(latitude, longitude)
        if (!result.isSuccess) {
            return Result.failure("Failed to get hourly forecast")
        }

        val apiData = result.getOrThrow()
        val hourlyForecastDto = HourlyWeatherForecastDto(
            timezone = apiData.timezone,
            timezoneOffset = apiData.timezoneOffset,
            lastUpdate = Clock.System.now(),
            hourlyForecasts = apiData.hourlyForecasts.take(48)
        )

        return Result.success(hourlyForecastDto)
    }

    override suspend fun updateAllWeatherAsync(): Result<Int> {
        return try {
            val locationsResult = locationRepository.getActiveAsync()
            if (!locationsResult.isSuccess) {
                return Result.failure("Failed to retrieve active locations")
            }

            val locations = locationsResult.getOrNull() ?: emptyList()
            var successCount = 0

            locations.forEach { location ->
                try {
                    val result = updateWeatherForLocationAsync(location.id)
                    if (result.isSuccess) {
                        successCount++
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to update weather for location ${location.id}", ex)
                }
            }

            Result.success(successCount)
        } catch (ex: Exception) {
            throw WeatherDomainException("Failed to update all weather", "UPDATE_ALL_WEATHER_ERROR", ex)
        }
    }

    private suspend fun getWeatherFromApiAsync(latitude: Double, longitude: Double): Result<WeatherApiResponse> {
        return try {
            val apiKeyResult = getApiKeyAsync()
            val tempScaleResult = settingRepository.getByKeyAsync("TemperatureType")

            if (!apiKeyResult.isSuccess || apiKeyResult.getOrNull().isNullOrBlank()) {
                return Result.failure("Weather API key not configured")
            }

            val apiKey = apiKeyResult.getOrThrow()
            val tempScale = if (tempScaleResult.isSuccess) {
                tempScaleResult.getOrNull()?.value ?: "C"
            } else "C"

            val units = if (tempScale == "F") "imperial" else "metric"

            val response = httpClient.get("$BASE_URL/onecall") {
                parameter("lat", latitude)
                parameter("lon", longitude)
                parameter("appid", apiKey)
                parameter("units", units)
                parameter("exclude", "minutely,alerts")
            }

            if (response.status == HttpStatusCode.OK) {
                val responseText: String = response.body()
                val weatherData = json.decodeFromString<WeatherApiResponse>(responseText)
                Result.success(weatherData)
            } else {
                Result.failure("Weather API error: ${response.status}")
            }
        } catch (ex: Exception) {
            logger.error("Error calling weather API", ex)
            Result.failure("Network error: ${ex.message}")
        }
    }

    private suspend fun getApiKeyAsync(): Result<String> {
        val result = settingRepository.getByKeyAsync("WeatherApiKey")
        return if (result.isSuccess) {
            val setting = result.getOrNull()
            if (setting?.value.isNullOrBlank()) {
                Result.failure("Weather API key not configured")
            } else {
                Result.success(setting.value)
            }
        } else {
            Result.failure("Failed to retrieve API key")
        }
    }

    private fun mapToWeatherDto(apiData: WeatherApiResponse, latitude: Double, longitude: Double): WeatherDto {
        val currentForecast = apiData.dailyForecasts.firstOrNull()
        val currentHourly = apiData.hourlyForecasts.firstOrNull()

        return WeatherDto(
            id = 0, // Will be set when saved
            locationId = 0, // Will be set by caller
            latitude = latitude,
            longitude = longitude,
            timezone = apiData.timezone,
            timezoneOffset = apiData.timezoneOffset,
            lastUpdate = Clock.System.now(),
            temperature = currentHourly?.temperature ?: currentForecast?.temperature ?: 0.0,
            minimumTemp = currentForecast?.minTemperature ?: 0.0,
            maximumTemp = currentForecast?.maxTemperature ?: 0.0,
            description = currentHourly?.description ?: currentForecast?.description ?: "",
            icon = currentHourly?.icon ?: currentForecast?.icon ?: "",
            windSpeed = currentHourly?.windSpeed ?: currentForecast?.windSpeed ?: 0.0,
            windDirection = currentHourly?.windDirection ?: currentForecast?.windDirection ?: 0.0,
            windGust = currentHourly?.windGust ?: currentForecast?.windGust,
            humidity = currentHourly?.humidity ?: currentForecast?.humidity ?: 0,
            pressure = currentHourly?.pressure ?: currentForecast?.pressure ?: 0,
            clouds = currentHourly?.clouds ?: currentForecast?.clouds ?: 0,
            uvIndex = currentHourly?.uvIndex ?: currentForecast?.uvIndex ?: 0.0,
            precipitation = currentForecast?.precipitation,
            sunrise = currentForecast?.sunrise ?: Clock.System.now(),
            sunset = currentForecast?.sunset ?: Clock.System.now(),
            moonRise = currentForecast?.moonRise,
            moonSet = currentForecast?.moonSet,
            moonPhase = currentForecast?.moonPhase ?: 0.0
        )
    }

    private fun mapToWeatherDtoAsync(weather: Weather, apiData: WeatherApiResponse): WeatherDto {
        val currentForecast = weather.getCurrentForecast()

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
            windSpeed = currentForecast?.windInfo?.speed ?: 0.0,
            windDirection = currentForecast?.windInfo?.direction ?: 0.0,
            windGust = currentForecast?.windInfo?.gust,
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

@Serializable
data class WeatherApiResponse(
    val timezone: String,
    val timezoneOffset: Int,
    val dailyForecasts: List<DailyForecastData>,
    val hourlyForecasts: List<HourlyForecastData>
)

@Serializable
data class DailyForecastData(
    val date: LocalDate,
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
    val moonPhase: Double
)

@Serializable
data class HourlyForecastData(
    val dateTime: Instant,
    val temperature: Double,
    val feelsLike: Double,
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
    val visibility: Int,
    val dewPoint: Double
)
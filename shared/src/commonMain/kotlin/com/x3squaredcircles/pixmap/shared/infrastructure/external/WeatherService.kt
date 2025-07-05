// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/external/WeatherService.kt


import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.DailyForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.HourlyWeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IWeatherService

import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.entities.WeatherForecast
import com.x3squaredcircles.pixmap.shared.domain.entities.HourlyForecast
import com.x3squaredcircles.pixmap.shared.domain.exceptions.WeatherDomainException
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
//import com.x3squaredcircles.pixmap.shared.infrastructure.external.models.OpenWeatherResponse
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*

import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.days

class WeatherService(
    private val httpClient: HttpClient,
    private val locationRepository: ILocationRepository,
    private val weatherRepository: IWeatherRepository,
    private val settingRepository: ISettingRepository,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) : IWeatherService {

    companion object {
        private const val API_KEY_SETTING = "WeatherApiKey"
        private const val BASE_URL = "https://api.openweathermap.org/data/3.0/onecall"
        private const val MAX_FORECAST_DAYS = 7
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun getWeatherAsync(latitude: Double, longitude: Double): Result<WeatherDto> {
        throw IllegalStateException("Use updateWeatherForLocationAsync for offline-first persistence")
    }

    override suspend fun updateWeatherForLocationAsync(locationId: Int): Result<WeatherDto> {
        return try {
            val locationResult = locationRepository.getByIdAsync(locationId)
            if (!locationResult.isSuccess || locationResult.data == null) {
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!

            val existingWeather = weatherRepository.getByLocationIdAsync(locationId).data

            if (existingWeather != null &&
                existingWeather.lastUpdate >= Clock.System.now().minus(2.days) &&
                hasCompleteForecastData(existingWeather)) {
                val existingDto = mapToWeatherDtoAsync(existingWeather, null)
                return Result.success(existingDto)
            }

            val weatherResult = getWeatherFromApiAsync(
                location.coordinate.latitude,
                location.coordinate.longitude
            )

            if (!weatherResult.isSuccess || weatherResult.data == null) {
                if (existingWeather != null) {
                    val fallbackDto = mapToWeatherDtoAsync(existingWeather, null)
                    return Result.success(fallbackDto)
                }
                return Result.failure(weatherResult.errorMessage ?: "Failed to fetch weather")
            }

            val apiData = weatherResult.data!!

            val coordinate = Coordinate(location.coordinate.latitude, location.coordinate.longitude)
            val weather = if (existingWeather != null) {
                existingWeather
            } else {
                Weather(
                    locationId = locationId,
                    coordinate = coordinate,
                    timezone = apiData.timezone,
                    timezoneOffset = apiData.timezoneOffset,
                    lastUpdate = Instant.fromEpochSeconds(Clock.System.now().toEpochMilliseconds())
                )
            }

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
                    wind =  WindInfo(
                        speed = dailyForecast.windSpeed,
                        direction = dailyForecast.windDirection,
                        gust = dailyForecast.windGust
                    ),
                    humidity = dailyForecast.humidity,
                    pressure = dailyForecast.pressure,
                    clouds = dailyForecast.clouds,
                    uvIndex = dailyForecast.uvIndex
                ).apply {
                    setMoonData(dailyForecast.moonRise, dailyForecast.moonSet, dailyForecast.moonPhase)
                    dailyForecast.precipitation?.let { setPrecipitation(it) }
                }
            }

            val hourlyForecasts = apiData.hourlyForecasts.take(48).map { hourlyForecast ->
                HourlyForecast(
                    weatherId = weather.id,
                    dateTime = hourlyForecast.dateTime,
                    temperature = hourlyForecast.temperature,
                    feelsLike = hourlyForecast.feelsLike,
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
                    visibility = hourlyForecast.visibility,
                    dewPoint = hourlyForecast.dewPoint
                )
            }

            weather.updateForecasts(forecasts)
            weather.updateHourlyForecasts(hourlyForecasts)

            val saveResult = if (existingWeather != null) {
                weatherRepository.updateAsync(weather)
            } else {
                weatherRepository.addAsync(weather)
            }

            if (!saveResult.isSuccess) {
                return Result.failure("Failed to save weather data")
            }

            val weatherDto = mapToWeatherDtoAsync(weather, apiData)
            Result.success(weatherDto)
        } catch (ex: Exception) {
            val domainException = exceptionMapper.mapToWeatherDomainException(ex, "UpdateWeatherForLocation")
            throw domainException
        }
    }

    override suspend fun getForecastAsync(
        latitude: Double,
        longitude: Double,
        days: Int
    ): Result<WeatherForecastDto> {
        val result = getWeatherFromApiAsync(latitude, longitude)
        if (!result.isSuccess || result.data == null) {
            return Result.failure(result.errorMessage ?: "Failed to get forecast")
        }

        val forecastDto = WeatherForecastDto(
            timezone = result.data!!.timezone,
            timezoneOffset = result.data!!.timezoneOffset,
            lastUpdate = Clock.System.now(),
            dailyForecasts = result.data!!.dailyForecasts.take(days)
        )

        return Result.success(forecastDto)
    }

    override suspend fun getHourlyForecastAsync(
        latitude: Double,
        longitude: Double
    ): Result<HourlyWeatherForecastDto> {
        val result = getWeatherFromApiAsync(latitude, longitude)
        if (!result.isSuccess || result.data == null) {
            return Result.failure(result.errorMessage ?: "Failed to get hourly forecast")
        }

        val hourlyForecastDto = HourlyWeatherForecastDto(
            timezone = result.data!!.timezone,
            timezoneOffset = result.data!!.timezoneOffset,
            lastUpdate = Clock.System.now(),
            hourlyForecasts = result.data!!.hourlyForecasts.take(48)
        )

        return Result.success(hourlyForecastDto)
    }

    override suspend fun updateAllWeatherAsync(): Result<Int> {
        return try {
            val locationsResult = locationRepository.getActiveAsync()
            if (!locationsResult.isSuccess || locationsResult.data == null) {
                return Result.failure("Failed to retrieve active locations")
            }

            val locations = locationsResult.data!!
            var successCount = 0

            for (location in locations) {
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
            val domainException = exceptionMapper.mapToWeatherDomainException(ex, "UpdateAllWeather")
            throw domainException
        }
    }

    private fun hasCompleteForecastData(weather: Weather): Boolean {
        if (weather.forecasts.isEmpty()) return false

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val requiredDates = (0..4).map { today.plus(it, DateTimeUnit.DAY) }

        return requiredDates.all { requiredDate ->
            weather.forecasts.any { forecast ->
                val forecastDate = forecast.date
                forecastDate == requiredDate
            }
        }
    }

    private suspend fun getWeatherFromApiAsync(
        latitude: Double,
        longitude: Double
    ): Result<WeatherApiResponse> {
        return try {
            val apiKeyResult = getApiKeyAsync()
            val tempScaleResult = settingRepository.getByKeyAsync("TemperatureType")

            if (!apiKeyResult.isSuccess || apiKeyResult.data.isNullOrBlank()) {
                return Result.failure("Weather API key not configured")
            }

            val apiKey = apiKeyResult.data!!
            val tempS = if (tempScaleResult.data?.value == "F") "imperial" else "metric"
            val requestUrl = "$BASE_URL?lat=$latitude&lon=$longitude&appid=$apiKey&units=$tempS&exclude=minutely"

            val response = httpClient.get(requestUrl) {
                timeout {
                    requestTimeoutMillis = 30000
                }
            }

            if (!response.status.isSuccess()) {
                logger.error("Weather API request failed with status ${response.status}")
                return Result.failure("Weather API request failed: ${response.status}")
            }

            val weatherData = response.body<OpenWeatherResponse>()
            val apiResponse = mapToApiResponse(weatherData)
            Result.success(apiResponse)
        } catch (ex: Exception) {
            val domainException = exceptionMapper.mapToWeatherDomainException(ex, "GetWeatherFromApi")
            throw domainException
        }
    }

    private suspend fun getApiKeyAsync(): Result<String> {
        val settingResult = settingRepository.getByKeyAsync(API_KEY_SETTING)

        if (!settingResult.isSuccess || settingResult.data == null) {
            logger.warning("Weather API key not found in settings")
            return Result.failure("API key not found")
        }

        return Result.success(settingResult.data!!.value)
    }

    private suspend fun mapToWeatherDtoAsync(weather: Weather, apiData: WeatherApiResponse?): WeatherDto {
        val currentForecast = weather.getCurrentForecast()
        val currentApiData = apiData?.dailyForecasts?.firstOrNull()

        val rawWindDirection = currentForecast?.wind?.direction ?: currentApiData?.windDirection ?: 0.0
        val displayWindDirection = getDisplayWindDirectionAsync(rawWindDirection)

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
            windDirection = displayWindDirection,
            windGust = currentForecast?.wind?.gust ?: currentApiData?.windGust,
            humidity = currentForecast?.humidity ?: currentApiData?.humidity ?: 0,
            pressure = currentForecast?.pressure ?: currentApiData?.pressure ?: 0,
            clouds = currentForecast?.clouds ?: currentApiData?.clouds ?: 0,
            uvIndex = currentForecast?.uvIndex ?: currentApiData?.uvIndex ?: 0.0,
            precipitation = currentForecast?.precipitation ?: currentApiData?.precipitation,
            sunrise = currentForecast?.sunrise ?: currentApiData?.sunrise ?: Instant.DISTANT_PAST,
            sunset = currentForecast?.sunset ?: currentApiData?.sunset ?: Instant.DISTANT_PAST,
            moonRise = currentForecast?.moonRise ?: currentApiData?.moonRise,
            moonSet = currentForecast?.moonSet ?: currentApiData?.moonSet,
            moonPhase = currentForecast?.moonPhase ?: currentApiData?.moonPhase ?: 0.0
        )
    }

    private suspend fun getDisplayWindDirectionAsync(rawDirection: Double): Double {
        return try {
            val windDirectionSetting = settingRepository.getByKeyAsync("WindDirection")

            if (windDirectionSetting.isSuccess && windDirectionSetting.data?.value == "towardsWind") {
                (rawDirection + 180) % 360
            } else {
                rawDirection
            }
        } catch (ex: Exception) {
            logger.warning("Failed to get wind direction setting, using raw direction", ex)
            rawDirection
        }
    }

    private fun mapToApiResponse(response: OpenWeatherResponse): WeatherApiResponse {
        val apiResponse = WeatherApiResponse(
            timezone = response.timezone,
            timezoneOffset = response.timezoneOffset,
            dailyForecasts = mutableListOf(),
            hourlyForecasts = mutableListOf()
        )

        for (i in 0 until min(response.daily.size, 7)) {
            val daily = response.daily[i]
            val dailyDto = DailyForecastDto(
                date = Instant.fromEpochSeconds(daily.dt).toLocalDateTime(TimeZone.currentSystemDefault()).date,
                sunrise = Instant.fromEpochSeconds(daily.sunrise),
                sunset = Instant.fromEpochSeconds(daily.sunset),
                temperature = daily.temp.day,
                minTemperature = daily.temp.min,
                maxTemperature = daily.temp.max,
                description = daily.weather.firstOrNull()?.description ?: "",
                icon = daily.weather.firstOrNull()?.icon ?: "",
                windSpeed = daily.windSpeed,
                windDirection = daily.windDeg,
                windGust = daily.windGust,
                humidity = daily.humidity,
                pressure = daily.pressure,
                clouds = daily.clouds,
                uvIndex = daily.uvi,
                precipitation = daily.pop,
                moonRise = if (daily.moonRise > 0) Instant.fromEpochSeconds(daily.moonRise) else null,
                moonSet = if (daily.moonSet > 0) Instant.fromEpochSeconds(daily.moonSet) else null,
                moonPhase = daily.moonPhase
            )

            apiResponse.dailyForecasts.add(dailyDto)
        }

        for (i in 0 until min(response.hourly.size, 48)) {
            val hourly = response.hourly[i]
            val hourlyDto = HourlyForecastDto(
                dateTime = Instant.fromEpochSeconds(hourly.dt),
                temperature = hourly.temp,
                feelsLike = hourly.feelsLike,
                description = hourly.weather.firstOrNull()?.description ?: "",
                icon = hourly.weather.firstOrNull()?.icon ?: "",
                windSpeed = hourly.windSpeed,
                windDirection = hourly.windDeg,
                windGust = hourly.windGust,
                humidity = hourly.humidity,
                pressure = hourly.pressure,
                clouds = hourly.clouds,
                uvIndex = hourly.uvi,
                probabilityOfPrecipitation = hourly.pop,
                visibility = hourly.visibility,
                dewPoint = hourly.dewPoint
            )

            apiResponse.hourlyForecasts.add(hourlyDto)
        }

        return apiResponse
    }
}

internal data class WeatherApiResponse(
    val timezone: String = "",
    val timezoneOffset: Int,
    val dailyForecasts: MutableList<DailyForecastDto> = mutableListOf(),
    val hourlyForecasts: MutableList<HourlyForecastDto> = mutableListOf()
)
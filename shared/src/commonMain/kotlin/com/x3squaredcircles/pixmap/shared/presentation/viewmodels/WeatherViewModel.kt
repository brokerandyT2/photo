// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/presentation/viewmodels/WeatherViewModel.kt
package com.x3squaredcircles.pixmap.shared.presentation.viewmodels

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateWeatherCommand
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherForecastDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IErrorDisplayService
import com.x3squaredcircles.pixmap.shared.application.queries.GetWeatherForecastQuery
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

/**
 * View model for weather data display with optimized performance patterns
 */
class WeatherViewModel(
    private val mediator: IMediator,
    errorDisplayService: IErrorDisplayService? = null
) : BaseViewModel(errorDisplayService = errorDisplayService), INavigationAware {

    // Observable properties
    private val _locationId = MutableStateFlow(0)
    val locationId: StateFlow<Int> = _locationId.asStateFlow()

    private val _weatherForecast = MutableStateFlow<WeatherForecastDto?>(null)
    val weatherForecast: StateFlow<WeatherForecastDto?> = _weatherForecast.asStateFlow()

    private val _dailyForecasts = MutableStateFlow<List<DailyWeatherViewModel>>(emptyList())
    val dailyForecasts: StateFlow<List<DailyWeatherViewModel>> = _dailyForecasts.asStateFlow()

    // Performance optimization: Cache for icon URLs to avoid repeated string operations
    private val iconUrlCache = ConcurrentHashMap<String, String>()

    // Pre-compiled formatters
    private val temperatureFormat = "%.1f"
    private val windSpeedFormat = "%.1f"

    init {
        initializeIconCache()
    }

    /**
     * Sets the location ID for weather data
     */
    fun setLocationId(id: Int) {
        _locationId.value = id
    }

    /**
     * Loads weather data for a specific location
     */
    suspend fun loadWeather(locationId: Int) = executeSafely {
        setLocationId(locationId)

        // Update weather data with force refresh
        val updateCommand = UpdateWeatherCommand(
            locationId = locationId,
            forceUpdate = true
        )

        val updateResult = mediator.send(updateCommand)

        when {
            !updateResult.isSuccess || updateResult.data == null -> {
                onSystemError(updateResult.errorMessage ?: "Failed to load weather data")
                return@executeSafely
            }
        }

        val weatherData = updateResult.data

        // Get forecast data in parallel
        val forecastQuery = GetWeatherForecastQuery(
            latitude = weatherData.latitude,
            longitude = weatherData.longitude,
            days = 5 // Today + next 4 days for display
        )

        val forecastResult = mediator.send(forecastQuery)

        when {
            !forecastResult.isSuccess || forecastResult.data == null -> {
                onSystemError(forecastResult.errorMessage ?: "Failed to load forecast data")
                return@executeSafely
            }
        }

        // Store forecast data
        _weatherForecast.value = forecastResult.data

        // Process forecast data for UI display
        processForecastData(forecastResult.data)
    }

    /**
     * Refreshes weather data for current location
     */
    suspend fun refresh() {
        val currentLocationId = _locationId.value
        if (currentLocationId > 0) {
            loadWeather(currentLocationId)
        }
    }

    /**
     * Process forecast data and create view models for display
     */
    private fun processForecastData(forecast: WeatherForecastDto) {
        if (forecast.dailyForecasts.isEmpty()) {
            setValidationError("No forecast data available")
            return
        }

        val today = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val processedItems = forecast.dailyForecasts.take(5).mapIndexed { index, dailyForecast ->
            val isToday = index == 0 // First item is typically today

            createDailyWeatherViewModel(dailyForecast, isToday)
        }

        _dailyForecasts.value = processedItems
    }

    /**
     * Creates a DailyWeatherViewModel with optimized string formatting
     */
    private fun createDailyWeatherViewModel(
        dailyForecast: com.x3squaredcircles.pixmap.shared.application.dto.DailyForecastDto,
        isToday: Boolean
    ): DailyWeatherViewModel {

        val minTemp = temperatureFormat.format(dailyForecast.minTemperature) + "°"
        val maxTemp = temperatureFormat.format(dailyForecast.maxTemperature) + "°"
        val windSpeed = windSpeedFormat.format(dailyForecast.windSpeed) + " mph"
        val windGust = dailyForecast.windGust?.let {
            windSpeedFormat.format(it) + " mph"
        } ?: "N/A"

        val localDate = dailyForecast.date.toLocalDateTime(TimeZone.currentSystemDefault())

        return DailyWeatherViewModel(
            date = dailyForecast.date,
            dayName = if (isToday) "Today" else "${localDate.dayOfWeek}",
            description = dailyForecast.description,
            minTemperature = minTemp,
            maxTemperature = maxTemp,
            weatherIcon = getWeatherIconUrlCached(dailyForecast.icon),
            sunriseTime = formatTime(dailyForecast.sunrise),
            sunsetTime = formatTime(dailyForecast.sunset),
            windDirection = dailyForecast.windDirection,
            windSpeed = windSpeed,
            windGust = windGust,
            humidity = dailyForecast.humidity,
            pressure = dailyForecast.pressure,
            uvIndex = dailyForecast.uvIndex,
            isToday = isToday
        )
    }

    /**
     * Pre-populate common icon URLs to avoid runtime allocation
     */
    private fun initializeIconCache() {
        val commonIcons = arrayOf(
            "01d", "01n", "02d", "02n", "03d", "03n", "04d", "04n",
            "09d", "09n", "10d", "10n", "11d", "11n", "13d", "13n", "50d", "50n"
        )

        commonIcons.forEach { icon ->
            iconUrlCache[icon] = "weather_${icon}.png"
        }
    }

    /**
     * Cached icon URL resolution with performance optimization
     */
    private fun getWeatherIconUrlCached(iconCode: String): String {
        if (iconCode.isEmpty()) return "weather_unknown.png"

        return iconUrlCache.computeIfAbsent(iconCode) { code ->
            "weather_${code}.png"
        }
    }

    /**
     * Formats time from Instant to local time string
     */
    private fun formatTime(instant: Instant): String {
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    }

    /**
     * Navigation lifecycle methods
     */
    override suspend fun onNavigatedTo() {
        // Implementation as needed for automatic refresh
    }

    override suspend fun onNavigatedFrom() {
        // No cleanup needed
    }

    /**
     * Cleanup resources on dispose
     */
    override fun dispose() {
        iconUrlCache.clear()
        super.dispose()
    }
}

/**
 * View model for individual daily weather forecast items
 */
data class DailyWeatherViewModel(
    val date: Instant,
    val dayName: String,
    val description: String,
    val minTemperature: String,
    val maxTemperature: String,
    val weatherIcon: String,
    val sunriseTime: String,
    val sunsetTime: String,
    val windDirection: Double,
    val windSpeed: String,
    val windGust: String,
    val humidity: Int,
    val pressure: Int,
    val uvIndex: Double,
    val isToday: Boolean
) {
    /**
     * Formatted temperature range display
     */
    val temperatureRange: String
        get() = "$minTemperature - $maxTemperature"

    /**
     * Formatted humidity display
     */
    val formattedHumidity: String
        get() = "$humidity%"

    /**
     * Formatted pressure display
     */
    val formattedPressure: String
        get() = "${pressure} hPa"

    /**
     * Formatted UV index display
     */
    val formattedUvIndex: String
        get() = "%.1f".format(uvIndex)

    /**
     * Wind direction as compass bearing
     */
    val windDirectionText: String
        get() = when {
            windDirection < 11.25 || windDirection >= 348.75 -> "N"
            windDirection < 33.75 -> "NNE"
            windDirection < 56.25 -> "NE"
            windDirection < 78.75 -> "ENE"
            windDirection < 101.25 -> "E"
            windDirection < 123.75 -> "ESE"
            windDirection < 146.25 -> "SE"
            windDirection < 168.75 -> "SSE"
            windDirection < 191.25 -> "S"
            windDirection < 213.75 -> "SSW"
            windDirection < 236.25 -> "SW"
            windDirection < 258.75 -> "WSW"
            windDirection < 281.25 -> "W"
            windDirection < 303.75 -> "WNW"
            windDirection < 326.25 -> "NW"
            windDirection < 348.75 -> "NNW"
            else -> "N"
        }

    /**
     * Complete wind information display
     */
    val windInfo: String
        get() = "$windSpeed $windDirectionText" + if (windGust != "N/A") " (gusts $windGust)" else ""

    /**
     * Formatted date for display
     */
    val formattedDate: String
        get() {
            val localDateTime = date.toLocalDateTime(TimeZone.currentSystemDefault())
            return "${localDateTime.month} ${localDateTime.dayOfMonth}"
        }
}
// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/external/models/OpenWeatherResponse.kt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherResponse(
    @SerialName("lat") val lat: Double,
    @SerialName("lon") val lon: Double,
    @SerialName("timezone") val timezone: String = "",
    @SerialName("timezone_offset") val timezoneOffset: Int,
    @SerialName("current") val current: CurrentWeather = CurrentWeather(),
    @SerialName("hourly") val hourly: List<HourlyWeather> = emptyList(),
    @SerialName("daily") val daily: List<DailyForecast> = emptyList()
)

@Serializable
data class CurrentWeather(
    @SerialName("dt") val dt: Long = 0,
    @SerialName("sunrise") val sunrise: Long = 0,
    @SerialName("sunset") val sunset: Long = 0,
    @SerialName("temp") val temp: Double = 0.0,
    @SerialName("feels_like") val feelsLike: Double = 0.0,
    @SerialName("pressure") val pressure: Int = 0,
    @SerialName("humidity") val humidity: Int = 0,
    @SerialName("dew_point") val dewPoint: Double = 0.0,
    @SerialName("uvi") val uvi: Double = 0.0,
    @SerialName("clouds") val clouds: Int = 0,
    @SerialName("visibility") val visibility: Int = 0,
    @SerialName("wind_speed") val windSpeed: Double = 0.0,
    @SerialName("wind_deg") val windDeg: Double = 0.0,
    @SerialName("wind_gust") val windGust: Double? = null,
    @SerialName("weather") val weather: List<WeatherDescription> = emptyList()
)

@Serializable
data class HourlyWeather(
    @SerialName("dt") val dt: Long,
    @SerialName("temp") val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    @SerialName("pressure") val pressure: Int,
    @SerialName("humidity") val humidity: Int,
    @SerialName("dew_point") val dewPoint: Double,
    @SerialName("uvi") val uvi: Double,
    @SerialName("clouds") val clouds: Int,
    @SerialName("visibility") val visibility: Int,
    @SerialName("wind_speed") val windSpeed: Double,
    @SerialName("wind_deg") val windDeg: Double,
    @SerialName("wind_gust") val windGust: Double? = null,
    @SerialName("weather") val weather: List<WeatherDescription> = emptyList(),
    @SerialName("pop") val pop: Double
)

@Serializable
data class DailyForecast(
    @SerialName("dt") val dt: Long,
    @SerialName("sunrise") val sunrise: Long,
    @SerialName("sunset") val sunset: Long,
    @SerialName("moonrise") val moonRise: Long,
    @SerialName("moonset") val moonSet: Long,
    @SerialName("moon_phase") val moonPhase: Double,
    @SerialName("temp") val temp: DailyTemperature = DailyTemperature(),
    @SerialName("feels_like") val feelsLike: DailyFeelsLike = DailyFeelsLike(),
    @SerialName("pressure") val pressure: Int,
    @SerialName("humidity") val humidity: Int,
    @SerialName("dew_point") val dewPoint: Double,
    @SerialName("wind_speed") val windSpeed: Double,
    @SerialName("wind_deg") val windDeg: Double,
    @SerialName("wind_gust") val windGust: Double? = null,
    @SerialName("weather") val weather: List<WeatherDescription> = emptyList(),
    @SerialName("clouds") val clouds: Int,
    @SerialName("pop") val pop: Double,
    @SerialName("rain") val rain: Double? = null,
    @SerialName("uvi") val uvi: Double
)

@Serializable
data class DailyTemperature(
    @SerialName("day") val day: Double = 0.0,
    @SerialName("min") val min: Double = 0.0,
    @SerialName("max") val max: Double = 0.0,
    @SerialName("night") val night: Double = 0.0,
    @SerialName("eve") val eve: Double = 0.0,
    @SerialName("morn") val morn: Double = 0.0
)

@Serializable
data class DailyFeelsLike(
    @SerialName("day") val day: Double = 0.0,
    @SerialName("night") val night: Double = 0.0,
    @SerialName("eve") val eve: Double = 0.0,
    @SerialName("morn") val morn: Double = 0.0
)

@Serializable
data class WeatherDescription(
    @SerialName("id") val id: Int,
    @SerialName("main") val main: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("icon") val icon: String = ""
)
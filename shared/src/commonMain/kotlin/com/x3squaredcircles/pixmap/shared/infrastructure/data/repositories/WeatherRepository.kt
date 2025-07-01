// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/WeatherRepository.kt

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.entities.WeatherForecast
import com.x3squaredcircles.pixmap.shared.domain.entities.HourlyForecast
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.WeatherEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.WeatherForecastEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.HourlyForecastEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.coroutines.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

class WeatherRepository(
    private val context: IDatabaseContext,
    private val logger: Logger,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) {

    suspend fun getByIdAsync(id: Int): Weather? {
        return try {
            logger.info("Retrieving weather with ID $id")

            // Get weather entity
            val weatherEntity = context.getAsync(id) { primaryKey ->
                queryWeatherById(primaryKey as Int)
            } ?: run {
                logger.info("Weather with ID $id not found")
                return null
            }

            // Get related entities concurrently for better performance
            val forecastEntities = context.queryAsync(
                "SELECT * FROM WeatherForecastEntity WHERE WeatherId = ? ORDER BY Date",
                ::mapCursorToWeatherForecastEntity,
                id
            )

            val hourlyForecastEntities = context.queryAsync(
                "SELECT * FROM HourlyForecastEntity WHERE WeatherId = ? ORDER BY DateTime",
                ::mapCursorToHourlyForecastEntity,
                id
            )

            val weather = mapToDomainWithRelations(weatherEntity, forecastEntities, hourlyForecastEntities)
            logger.info("Successfully retrieved weather with ID $id")
            weather
        } catch (ex: Exception) {
            logger.error("Failed to get weather by id $id", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "GetById")
        }
    }

    suspend fun getByLocationIdAsync(locationId: Int): Weather? {
        return try {
            // Get most recent weather for location
            val weatherEntities = context.queryAsync(
                "SELECT * FROM WeatherEntity WHERE LocationId = ? ORDER BY LastUpdate DESC LIMIT 1",
                ::mapCursorToWeatherEntity,
                locationId
            )

            if (weatherEntities.isEmpty()) {
                return null
            }

            val weatherEntity = weatherEntities.first()

            // Get related entities concurrently
            val forecastEntities = context.queryAsync(
                "SELECT * FROM WeatherForecastEntity WHERE WeatherId = ? ORDER BY Date",
                ::mapCursorToWeatherForecastEntity,
                weatherEntity.id
            )

            val hourlyForecastEntities = context.queryAsync(
                "SELECT * FROM HourlyForecastEntity WHERE WeatherId = ? ORDER BY DateTime",
                ::mapCursorToHourlyForecastEntity,
                weatherEntity.id
            )

            mapToDomainWithRelations(weatherEntity, forecastEntities, hourlyForecastEntities)
        } catch (ex: Exception) {
            logger.error("Failed to get weather by location id $locationId", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "GetByLocationId")
        }
    }

    suspend fun addAsync(weather: Weather): Weather {
        return try {
            context.executeInTransactionAsync {
                // Insert main weather entity
                val weatherEntity = mapWeatherDomainToEntity(weather)
                val weatherId = insertWeather(weatherEntity)

                // Update weather object with new ID
                val weatherWithId = weather.copy(id = weatherId)

                // Bulk insert forecasts
                if (weather.forecasts.isNotEmpty()) {
                    val forecastEntities = weather.forecasts.map { forecast ->
                        mapForecastDomainToEntity(forecast).copy(weatherId = weatherId)
                    }

                    forecastEntities.chunked(50).forEach { batch ->
                        batch.forEach { entity -> insertWeatherForecast(entity) }
                    }
                }

                // Bulk insert hourly forecasts
                if (weather.hourlyForecasts.isNotEmpty()) {
                    val hourlyEntities = weather.hourlyForecasts.map { hourlyForecast ->
                        mapHourlyDomainToEntity(hourlyForecast).copy(weatherId = weatherId)
                    }

                    hourlyEntities.chunked(100).forEach { batch ->
                        batch.forEach { entity -> insertHourlyForecast(entity) }
                    }
                }

                logger.info("Created weather with ID $weatherId for location ${weather.locationId}")
                weatherWithId
            }
        } catch (ex: Exception) {
            logger.error("Failed to add weather", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "Add")
        }
    }

    suspend fun updateAsync(weather: Weather) {
        try {
            context.executeInTransactionAsync {
                // Update main weather entity
                val weatherEntity = mapWeatherDomainToEntity(weather)
                updateWeather(weatherEntity)

                // Delete existing related entities efficiently
                context.executeAsync("DELETE FROM WeatherForecastEntity WHERE WeatherId = ?", weather.id)
                context.executeAsync("DELETE FROM HourlyForecastEntity WHERE WeatherId = ?", weather.id)

                // Re-insert forecasts
                if (weather.forecasts.isNotEmpty()) {
                    val forecastEntities = weather.forecasts.map { forecast ->
                        mapForecastDomainToEntity(forecast).copy(weatherId = weather.id)
                    }

                    forecastEntities.chunked(50).forEach { batch ->
                        batch.forEach { entity -> insertWeatherForecast(entity) }
                    }
                }

                // Re-insert hourly forecasts
                if (weather.hourlyForecasts.isNotEmpty()) {
                    val hourlyEntities = weather.hourlyForecasts.map { hourlyForecast ->
                        mapHourlyDomainToEntity(hourlyForecast).copy(weatherId = weather.id)
                    }

                    hourlyEntities.chunked(100).forEach { batch ->
                        batch.forEach { entity -> insertHourlyForecast(entity) }
                    }
                }

                logger.info("Updated weather with ID ${weather.id}")
            }
        } catch (ex: Exception) {
            logger.error("Failed to update weather with id ${weather.id}", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "Update")
        }
    }

    suspend fun deleteAsync(weather: Weather) {
        try {
            context.executeInTransactionAsync {
                // Delete related entities first (due to foreign key constraints)
                context.executeAsync("DELETE FROM HourlyForecastEntity WHERE WeatherId = ?", weather.id)
                context.executeAsync("DELETE FROM WeatherForecastEntity WHERE WeatherId = ?", weather.id)
                context.executeAsync("DELETE FROM WeatherEntity WHERE Id = ?", weather.id)

                logger.info("Deleted weather with ID ${weather.id}")
            }
        } catch (ex: Exception) {
            logger.error("Failed to delete weather with id ${weather.id}", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "Delete")
        }
    }

    suspend fun getRecentAsync(count: Int): List<Weather> {
        return try {
            val weatherEntities = context.queryAsync(
                "SELECT * FROM WeatherEntity ORDER BY LastUpdate DESC LIMIT ?",
                ::mapCursorToWeatherEntity,
                count
            )

            weatherEntities.map { weatherEntity ->
                // Get related entities for each weather
                val forecastEntities = context.queryAsync(
                    "SELECT * FROM WeatherForecastEntity WHERE WeatherId = ? ORDER BY Date",
                    ::mapCursorToWeatherForecastEntity,
                    weatherEntity.id
                )

                val hourlyForecastEntities = context.queryAsync(
                    "SELECT * FROM HourlyForecastEntity WHERE WeatherId = ? ORDER BY DateTime",
                    ::mapCursorToHourlyForecastEntity,
                    weatherEntity.id
                )

                mapToDomainWithRelations(weatherEntity, forecastEntities, hourlyForecastEntities)
            }
        } catch (ex: Exception) {
            logger.error("Failed to get recent weather records", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "GetRecent")
        }
    }

    suspend fun getExpiredAsync(maxAgeHours: Int): List<Weather> {
        return try {
            val cutoffTime = Clock.System.now().minus(maxAgeHours.hours)

            val weatherEntities = context.queryAsync(
                "SELECT * FROM WeatherEntity WHERE LastUpdate < ?",
                ::mapCursorToWeatherEntity,
                cutoffTime.toString()
            )

            weatherEntities.map { weatherEntity ->
                // Get related entities for each weather
                val forecastEntities = context.queryAsync(
                    "SELECT * FROM WeatherForecastEntity WHERE WeatherId = ? ORDER BY Date",
                    ::mapCursorToWeatherForecastEntity,
                    weatherEntity.id
                )

                val hourlyForecastEntities = context.queryAsync(
                    "SELECT * FROM HourlyForecastEntity WHERE WeatherId = ? ORDER BY DateTime",
                    ::mapCursorToHourlyForecastEntity,
                    weatherEntity.id
                )

                mapToDomainWithRelations(weatherEntity, forecastEntities, hourlyForecastEntities)
            }
        } catch (ex: Exception) {
            logger.error("Failed to get expired weather records", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "GetExpired")
        }
    }

    suspend fun createBulkAsync(weatherRecords: List<Weather>): List<Weather> {
        return try {
            context.executeInTransactionAsync {
                val createdWeatherRecords = mutableListOf<Weather>()

                weatherRecords.forEach { weather ->
                    val createdWeather = addAsync(weather)
                    createdWeatherRecords.add(createdWeather)
                }

                logger.info("Bulk created ${weatherRecords.size} weather records")
                createdWeatherRecords
            }
        } catch (ex: Exception) {
            logger.error("Failed to bulk create weather records", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "CreateBulk")
        }
    }

    suspend fun deleteExpiredAsync(maxAgeHours: Int): Int {
        return try {
            val cutoffTime = Clock.System.now().minus(maxAgeHours.hours)

            context.executeInTransactionAsync {
                // Get expired weather IDs first
                val expiredWeatherIds = context.queryAsync(
                    "SELECT Id FROM WeatherEntity WHERE LastUpdate < ?",
                    { cursor -> cursor.getInt(0) ?: 0 },
                    cutoffTime.toString()
                )

                if (expiredWeatherIds.isEmpty()) {
                    return@executeInTransactionAsync 0
                }

                val placeholders = expiredWeatherIds.joinToString(",") { "?" }

                // Delete related entities first
                context.executeAsync(
                    "DELETE FROM HourlyForecastEntity WHERE WeatherId IN ($placeholders)",
                    *expiredWeatherIds.toTypedArray()
                )

                context.executeAsync(
                    "DELETE FROM WeatherForecastEntity WHERE WeatherId IN ($placeholders)",
                    *expiredWeatherIds.toTypedArray()
                )

                // Delete weather records
                val deletedCount = context.executeAsync(
                    "DELETE FROM WeatherEntity WHERE Id IN ($placeholders)",
                    *expiredWeatherIds.toTypedArray()
                )

                logger.info("Deleted $deletedCount expired weather records")
                deletedCount
            }
        } catch (ex: Exception) {
            logger.error("Failed to delete expired weather records", ex)
            throw exceptionMapper.mapToWeatherDomainException(ex, "DeleteExpired")
        }
    }

    // Helper methods for database operations
    private suspend fun queryWeatherById(id: Int): WeatherEntity? {
        val entities = context.queryAsync(
            "SELECT * FROM WeatherEntity WHERE Id = ? LIMIT 1",
            ::mapCursorToWeatherEntity,
            id
        )
        return entities.firstOrNull()
    }

    private suspend fun insertWeather(entity: WeatherEntity): Int {
        return context.executeAsync(
            """INSERT INTO WeatherEntity (LocationId, Latitude, Longitude, Timezone, TimezoneOffset, LastUpdate)
               VALUES (?, ?, ?, ?, ?, ?)""",
            entity.locationId,
            entity.latitude,
            entity.longitude,
            entity.timezone,
            entity.timezoneOffset,
            entity.lastUpdate.toString()
        )
    }

    private suspend fun updateWeather(entity: WeatherEntity): Int {
        return context.executeAsync(
            """UPDATE WeatherEntity 
               SET LocationId = ?, Latitude = ?, Longitude = ?, Timezone = ?, TimezoneOffset = ?, LastUpdate = ?
               WHERE Id = ?""",
            entity.locationId,
            entity.latitude,
            entity.longitude,
            entity.timezone,
            entity.timezoneOffset,
            entity.lastUpdate.toString(),
            entity.id
        )
    }

    private suspend fun insertWeatherForecast(entity: WeatherForecastEntity): Int {
        return context.executeAsync(
            """INSERT INTO WeatherForecastEntity 
               (WeatherId, Date, Sunrise, Sunset, Temperature, MinTemperature, MaxTemperature, Description, Icon,
                WindSpeed, WindDirection, WindGust, Humidity, Pressure, Clouds, UvIndex, Precipitation, MoonRise, MoonSet, MoonPhase)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            entity.weatherId,
            entity.date.toString(),
            entity.sunrise.toString(),
            entity.sunset.toString(),
            entity.temperature,
            entity.minTemperature,
            entity.maxTemperature,
            entity.description,
            entity.icon,
            entity.windSpeed,
            entity.windDirection,
            entity.windGust,
            entity.humidity,
            entity.pressure,
            entity.clouds,
            entity.uvIndex,
            entity.precipitation,
            entity.moonRise?.toString(),
            entity.moonSet?.toString(),
            entity.moonPhase
        )
    }

    private suspend fun insertHourlyForecast(entity: HourlyForecastEntity): Int {
        return context.executeAsync(
            """INSERT INTO HourlyForecastEntity 
               (WeatherId, DateTime, Temperature, FeelsLike, Description, Icon, WindSpeed, WindDirection, WindGust,
                Humidity, Pressure, Clouds, UvIndex, ProbabilityOfPrecipitation, Visibility, DewPoint)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            entity.weatherId,
            entity.dateTime.toString(),
            entity.temperature,
            entity.feelsLike,
            entity.description,
            entity.icon,
            entity.windSpeed,
            entity.windDirection,
            entity.windGust,
            entity.humidity,
            entity.pressure,
            entity.clouds,
            entity.uvIndex,
            entity.probabilityOfPrecipitation,
            entity.visibility,
            entity.dewPoint
        )
    }

    // Mapping functions
    private fun mapToDomainWithRelations(
        weatherEntity: WeatherEntity,
        forecastEntities: List<WeatherForecastEntity>,
        hourlyEntities: List<HourlyForecastEntity>
    ): Weather {
        val weather = mapWeatherEntityToDomain(weatherEntity)

        val forecasts = forecastEntities.map { mapForecastEntityToDomain(it) }
        val hourlyForecasts = hourlyEntities.map { mapHourlyEntityToDomain(it) }

        return weather.copy(
            forecasts = forecasts,
            hourlyForecasts = hourlyForecasts
        )
    }

    private fun mapWeatherEntityToDomain(entity: WeatherEntity): Weather {
        return Weather(
            id = entity.id,
            locationId = entity.locationId,
            coordinate = Coordinate(entity.latitude, entity.longitude),
            timezone = entity.timezone,
            timezoneOffset = entity.timezoneOffset,
            lastUpdate = entity.lastUpdate,
            forecasts = emptyList(),
            hourlyForecasts = emptyList()
        )
    }

    private fun mapWeatherDomainToEntity(weather: Weather): WeatherEntity {
        return WeatherEntity(
            id = weather.id,
            locationId = weather.locationId,
            latitude = weather.coordinate.latitude,
            longitude = weather.coordinate.longitude,
            timezone = weather.timezone,
            timezoneOffset = weather.timezoneOffset,
            lastUpdate = weather.lastUpdate
        )
    }

    private fun mapForecastEntityToDomain(entity: WeatherForecastEntity): WeatherForecast {
        return WeatherForecast(
            id = entity.id,
            weatherId = entity.weatherId,
            date = entity.date,
            sunrise = entity.sunrise,
            sunset = entity.sunset,
            temperature = entity.temperature,
            minTemperature = entity.minTemperature,
            maxTemperature = entity.maxTemperature,
            description = entity.description,
            icon = entity.icon,
            wind = WindInfo(entity.windSpeed, entity.windDirection, entity.windGust),
            humidity = entity.humidity,
            pressure = entity.pressure,
            clouds = entity.clouds,
            uvIndex = entity.uvIndex,
            precipitation = entity.precipitation,
            moonRise = entity.moonRise,
            moonSet = entity.moonSet,
            moonPhase = entity.moonPhase
        )
    }

    private fun mapForecastDomainToEntity(forecast: WeatherForecast): WeatherForecastEntity {
        return WeatherForecastEntity(
            id = forecast.id,
            weatherId = forecast.weatherId,
            date = forecast.date,
            sunrise = forecast.sunrise,
            sunset = forecast.sunset,
            temperature = forecast.temperature,
            minTemperature = forecast.minTemperature,
            maxTemperature = forecast.maxTemperature,
            description = forecast.description,
            icon = forecast.icon,
            windSpeed = forecast.wind.speed,
            windDirection = forecast.wind.direction,
            windGust = forecast.wind.gust,
            humidity = forecast.humidity,
            pressure = forecast.pressure,
            clouds = forecast.clouds,
            uvIndex = forecast.uvIndex,
            precipitation = forecast.precipitation,
            moonRise = forecast.moonRise,
            moonSet = forecast.moonSet,
            moonPhase = forecast.moonPhase
        )
    }

    private fun mapHourlyEntityToDomain(entity: HourlyForecastEntity): HourlyForecast {
        return HourlyForecast(
            id = entity.id,
            weatherId = entity.weatherId,
            dateTime = entity.dateTime,
            temperature = entity.temperature,
            feelsLike = entity.feelsLike,
            description = entity.description,
            icon = entity.icon,
            windSpeed = entity.windSpeed,
            windDirection = entity.windDirection,
            windGust = entity.windGust,
            humidity = entity.humidity,
            pressure = entity.pressure,
            clouds = entity.clouds,
            uvIndex = entity.uvIndex,
            probabilityOfPrecipitation = entity.probabilityOfPrecipitation,
            visibility = entity.visibility,
            dewPoint = entity.dewPoint
        )
    }

    private fun mapHourlyDomainToEntity(hourly: HourlyForecast): HourlyForecastEntity {
        return HourlyForecastEntity(
            id = hourly.id,
            weatherId = hourly.weatherId,
            dateTime = hourly.dateTime,
            temperature = hourly.temperature,
            feelsLike = hourly.feelsLike,
            description = hourly.description,
            icon = hourly.icon,
            windSpeed = hourly.windSpeed,
            windDirection = hourly.windDirection,
            windGust = hourly.windGust,
            humidity = hourly.humidity,
            pressure = hourly.pressure,
            clouds = hourly.clouds,
            uvIndex = hourly.uvIndex,
            probabilityOfPrecipitation = hourly.probabilityOfPrecipitation,
            visibility = hourly.visibility,
            dewPoint = hourly.dewPoint
        )
    }

    private fun mapCursorToWeatherEntity(cursor: SqlCursor): WeatherEntity {
        return WeatherEntity(
            id = cursor.getInt(0) ?: 0,
            locationId = cursor.getInt(1) ?: 0,
            latitude = cursor.getDouble(2) ?: 0.0,
            longitude = cursor.getDouble(3) ?: 0.0,
            timezone = cursor.getString(4) ?: "",
            timezoneOffset = cursor.getInt(5) ?: 0,
            lastUpdate = Instant.parse(cursor.getString(6) ?: Clock.System.now().toString())
        )
    }

    private fun mapCursorToWeatherForecastEntity(cursor: SqlCursor): WeatherForecastEntity {
        return WeatherForecastEntity(
            id = cursor.getInt(0) ?: 0,
            weatherId = cursor.getInt(1) ?: 0,
            date = Instant.parse(cursor.getString(2) ?: Clock.System.now().toString()),
            sunrise = Instant.parse(cursor.getString(3) ?: Clock.System.now().toString()),
            sunset = Instant.parse(cursor.getString(4) ?: Clock.System.now().toString()),
            temperature = cursor.getDouble(5) ?: 0.0,
            minTemperature = cursor.getDouble(6) ?: 0.0,
            maxTemperature = cursor.getDouble(7) ?: 0.0,
            description = cursor.getString(8) ?: "",
            icon = cursor.getString(9) ?: "",
            windSpeed = cursor.getDouble(10) ?: 0.0,
            windDirection = cursor.getDouble(11) ?: 0.0,
            windGust = cursor.getDouble(12),
            humidity = cursor.getInt(13) ?: 0,
            pressure = cursor.getInt(14) ?: 0,
            clouds = cursor.getInt(15) ?: 0,
            uvIndex = cursor.getDouble(16) ?: 0.0,
            precipitation = cursor.getDouble(17),
            moonRise = cursor.getString(18)?.let { Instant.parse(it) },
            moonSet = cursor.getString(19)?.let { Instant.parse(it) },
            moonPhase = cursor.getDouble(20) ?: 0.0
        )
    }

    private fun mapCursorToHourlyForecastEntity(cursor: SqlCursor): HourlyForecastEntity {
        return HourlyForecastEntity(
            id = cursor.getInt(0) ?: 0,
            weatherId = cursor.getInt(1) ?: 0,
            dateTime = Instant.parse(cursor.getString(2) ?: Clock.System.now().toString()),
            temperature = cursor.getDouble(3) ?: 0.0,
            feelsLike = cursor.getDouble(4) ?: 0.0,
            description = cursor.getString(5) ?: "",
            icon = cursor.getString(6) ?: "",
            windSpeed = cursor.getDouble(7) ?: 0.0,
            windDirection = cursor.getDouble(8) ?: 0.0,
            windGust = cursor.getDouble(9),
            humidity = cursor.getInt(10) ?: 0,
            pressure = cursor.getInt(11) ?: 0,
            clouds = cursor.getInt(12) ?: 0,
            uvIndex = cursor.getDouble(13) ?: 0.0,
            probabilityOfPrecipitation = cursor.getDouble(14) ?: 0.0,
            visibility = cursor.getInt(15) ?: 0,
            dewPoint = cursor.getDouble(16) ?: 0.0
        )
    }
}

// Entity data classes
data class WeatherEntity(
    val id: Int = 0,
    val locationId: Int,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val timezoneOffset: Int,
    val lastUpdate: Instant = Clock.System.now()
)

data class WeatherForecastEntity(
    val id: Int = 0,
    val weatherId: Int,
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
    val moonPhase: Double
)

data class HourlyForecastEntity(
    val id: Int = 0,
    val weatherId: Int,
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

// SqlCursor interface for database queries
interface SqlCursor {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
    fun getInt(index: Int): Int?
}
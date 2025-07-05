// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/WeatherPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import app.cash.sqldelight.db.SqlCursor
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.entities.WeatherForecast
import com.x3squaredcircles.pixmap.shared.domain.entities.HourlyForecast
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDate
import kotlin.time.Duration

class WeatherPersistenceRepository(
    private val context: IDatabaseContext
) : IWeatherPersistenceRepository {

    override suspend fun getByIdAsync(id: Int): Weather? {
        val weatherEntity = context.querySingleAsync(
            "SELECT * FROM WeatherEntity WHERE id = ?",
            ::mapToWeatherEntity,
            id
        ) ?: return null

        val forecasts = context.queryAsync(
            "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date",
            ::mapToWeatherForecast,
            id
        )

        val hourlyForecasts = context.queryAsync(
            "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime",
            ::mapToHourlyForecast,
            id
        )

        return Weather.create(
            id = weatherEntity.id,
            locationId = weatherEntity.locationId,
            coordinate = weatherEntity.coordinate,
            timezone = weatherEntity.timezone,
            timezoneOffset = weatherEntity.timezoneOffset,
            forecasts = forecasts,
            hourlyForecasts = hourlyForecasts,
            lastUpdate = weatherEntity.lastUpdate
        )
    }

    override suspend fun getByLocationIdAsync(locationId: Int): Weather? {
        val weatherEntity = context.querySingleAsync(
            "SELECT * FROM WeatherEntity WHERE locationId = ? ORDER BY lastUpdate DESC LIMIT 1",
            ::mapToWeatherEntity,
            locationId
        ) ?: return null

        val forecasts = context.queryAsync(
            "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date",
            ::mapToWeatherForecast,
            weatherEntity.id
        )

        val hourlyForecasts = context.queryAsync(
            "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime",
            ::mapToHourlyForecast,
            weatherEntity.id
        )

        return Weather.create(
            id = weatherEntity.id,
            locationId = weatherEntity.locationId,
            coordinate = weatherEntity.coordinate,
            timezone = weatherEntity.timezone,
            timezoneOffset = weatherEntity.timezoneOffset,
            forecasts = forecasts,
            hourlyForecasts = hourlyForecasts,
            lastUpdate = weatherEntity.lastUpdate
        )
    }

    override suspend fun addAsync(weather: Weather): Weather {
        return context.withTransactionAsync {
            val weatherId = context.executeAsync(
                """
                   INSERT INTO WeatherEntity 
                   (locationId, latitude, longitude, timezone, timezoneOffset, lastUpdate)
                   VALUES (?, ?, ?, ?, ?, ?)
               """,
                weather.locationId,
                weather.coordinate.latitude,
                weather.coordinate.longitude,
                weather.timezone,
                weather.timezoneOffset,
                weather.lastUpdate.toEpochMilliseconds()
            ).toLong()

            weather.forecasts.forEach { forecast ->
                context.executeAsync(
                    """
                       INSERT INTO WeatherForecastEntity 
                       (weatherId, date, sunrise, sunset, temperature, minTemperature, maxTemperature,
                        description, icon, windSpeed, windDirection, windGust, humidity, pressure,
                        clouds, uvIndex, precipitation, moonRise, moonSet, moonPhase)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    weatherId,
                    forecast.date.toEpochDays(),
                    forecast.sunrise.toEpochMilliseconds(),
                    forecast.sunset.toEpochMilliseconds(),
                    forecast.temperature,
                    forecast.minTemperature,
                    forecast.maxTemperature,
                    forecast.description,
                    forecast.icon,
                    forecast.wind.speed,
                    forecast.wind.direction,
                    forecast.wind.gust,
                    forecast.humidity,
                    forecast.pressure,
                    forecast.clouds,
                    forecast.uvIndex,
                    forecast.precipitation,
                    forecast.moonRise?.toEpochMilliseconds(),
                    forecast.moonSet?.toEpochMilliseconds(),
                    forecast.moonPhase
                )
            }

            weather.hourlyForecasts.forEach { hourlyForecast ->
                context.executeAsync(
                    """
                       INSERT INTO HourlyForecastEntity 
                       (weatherId, dateTime, temperature, feelsLike, description, icon, windSpeed,
                        windDirection, windGust, humidity, pressure, clouds, uvIndex,
                        probabilityOfPrecipitation, visibility, dewPoint)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    weatherId,
                    hourlyForecast.dateTime.toEpochMilliseconds(),
                    hourlyForecast.temperature,
                    hourlyForecast.feelsLike,
                    hourlyForecast.description,
                    hourlyForecast.icon,
                    hourlyForecast.wind.speed,
                    hourlyForecast.wind.direction,
                    hourlyForecast.wind.gust,
                    hourlyForecast.humidity,
                    hourlyForecast.pressure,
                    hourlyForecast.clouds,
                    hourlyForecast.uvIndex,
                    hourlyForecast.probabilityOfPrecipitation,
                    hourlyForecast.visibility,
                    hourlyForecast.dewPoint
                )
            }

            val createdWeather = Weather.create(
                id = weatherId.toInt(),
                locationId = weather.locationId,
                coordinate = weather.coordinate,
                timezone = weather.timezone,
                timezoneOffset = weather.timezoneOffset,
                forecasts = weather.forecasts,
                hourlyForecasts = weather.hourlyForecasts,
                lastUpdate = weather.lastUpdate
            )

            setIdUsingReflection(createdWeather, weatherId.toInt())
            createdWeather
        }
    }

    override suspend fun updateAsync(weather: Weather) {
        context.withTransactionAsync {
            context.executeAsync(
                """
                   UPDATE WeatherEntity 
                   SET locationId = ?, latitude = ?, longitude = ?, timezone = ?, 
                       timezoneOffset = ?, lastUpdate = ?
                   WHERE id = ?
               """,
                weather.locationId,
                weather.coordinate.latitude,
                weather.coordinate.longitude,
                weather.timezone,
                weather.timezoneOffset,
                weather.lastUpdate.toEpochMilliseconds(),
                weather.id
            )

            context.executeAsync(
                "DELETE FROM WeatherForecastEntity WHERE weatherId = ?",
                weather.id
            )

            context.executeAsync(
                "DELETE FROM HourlyForecastEntity WHERE weatherId = ?",
                weather.id
            )

            weather.forecasts.forEach { forecast ->
                context.executeAsync(
                    """
                       INSERT INTO WeatherForecastEntity 
                       (weatherId, date, sunrise, sunset, temperature, minTemperature, maxTemperature,
                        description, icon, windSpeed, windDirection, windGust, humidity, pressure,
                        clouds, uvIndex, precipitation, moonRise, moonSet, moonPhase)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    weather.id,
                    forecast.date.toEpochDays(),
                    forecast.sunrise.toEpochMilliseconds(),
                    forecast.sunset.toEpochMilliseconds(),
                    forecast.temperature,
                    forecast.minTemperature,
                    forecast.maxTemperature,
                    forecast.description,
                    forecast.icon,
                    forecast.wind.speed,
                    forecast.wind.direction,
                    forecast.wind.gust,
                    forecast.humidity,
                    forecast.pressure,
                    forecast.clouds,
                    forecast.uvIndex,
                    forecast.precipitation,
                    forecast.moonRise?.toEpochMilliseconds(),
                    forecast.moonSet?.toEpochMilliseconds(),
                    forecast.moonPhase
                )
            }

            weather.hourlyForecasts.forEach { hourlyForecast ->
                context.executeAsync(
                    """
                       INSERT INTO HourlyForecastEntity 
                       (weatherId, dateTime, temperature, feelsLike, description, icon, windSpeed,
                        windDirection, windGust, humidity, pressure, clouds, uvIndex,
                        probabilityOfPrecipitation, visibility, dewPoint)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    weather.id,
                    hourlyForecast.dateTime.toEpochMilliseconds(),
                    hourlyForecast.temperature,
                    hourlyForecast.feelsLike,
                    hourlyForecast.description,
                    hourlyForecast.icon,
                    hourlyForecast.wind.speed,
                    hourlyForecast.wind.direction,
                    hourlyForecast.wind.gust,
                    hourlyForecast.humidity,
                    hourlyForecast.pressure,
                    hourlyForecast.clouds,
                    hourlyForecast.uvIndex,
                    hourlyForecast.probabilityOfPrecipitation,
                    hourlyForecast.visibility,
                    hourlyForecast.dewPoint
                )
            }
        }
    }

    override suspend fun deleteAsync(weather: Weather) {
        context.withTransactionAsync {
            context.executeAsync(
                "DELETE FROM HourlyForecastEntity WHERE weatherId = ?",
                weather.id
            )

            context.executeAsync(
                "DELETE FROM WeatherForecastEntity WHERE weatherId = ?",
                weather.id
            )

            context.executeAsync(
                "DELETE FROM WeatherEntity WHERE id = ?",
                weather.id
            )
        }
    }

    override suspend fun getRecentAsync(count: Int): List<Weather> {
        val weatherEntities = context.queryAsync(
            "SELECT * FROM WeatherEntity ORDER BY lastUpdate DESC LIMIT ?",
            ::mapToWeatherEntity,
            count
        )

        return weatherEntities.map { weatherEntity ->
            val forecasts = context.queryAsync(
                "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date LIMIT 24",
                ::mapToWeatherForecast,
                weatherEntity.id
            )

            val hourlyForecasts = context.queryAsync(
                "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime LIMIT 24",
                ::mapToHourlyForecast,
                weatherEntity.id
            )

            Weather.create(
                id = weatherEntity.id,
                locationId = weatherEntity.locationId,
                coordinate = weatherEntity.coordinate,
                timezone = weatherEntity.timezone,
                timezoneOffset = weatherEntity.timezoneOffset,
                forecasts = forecasts,
                hourlyForecasts = hourlyForecasts,
                lastUpdate = weatherEntity.lastUpdate
            )
        }
    }

    override suspend fun getExpiredAsync(maxAge: Duration): List<Weather> {
        val cutoffTime = Clock.System.now().minus(maxAge)
        val cutoffTicks = cutoffTime.toEpochMilliseconds()

        val weatherEntities = context.queryAsync(
            "SELECT * FROM WeatherEntity WHERE lastUpdate < ?",
            ::mapToWeatherEntity,
            cutoffTicks
        )

        return weatherEntities.chunked(10).flatMap { batch ->
            batch.map { weatherEntity ->
                val forecasts = context.queryAsync(
                    "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date",
                    ::mapToWeatherForecast,
                    weatherEntity.id
                )

                val hourlyForecasts = context.queryAsync(
                    "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime",
                    ::mapToHourlyForecast,
                    weatherEntity.id
                )

                Weather.create(
                    id = weatherEntity.id,
                    locationId = weatherEntity.locationId,
                    coordinate = weatherEntity.coordinate,
                    timezone = weatherEntity.timezone,
                    timezoneOffset = weatherEntity.timezoneOffset,
                    forecasts = forecasts,
                    hourlyForecasts = hourlyForecasts,
                    lastUpdate = weatherEntity.lastUpdate
                )
            }
        }
    }

    override suspend fun createBulkAsync(weatherRecords: List<Weather>): List<Weather> {
        if (weatherRecords.isEmpty()) return weatherRecords

        return context.withTransactionAsync {
            weatherRecords.map { weather ->
                addAsync(weather)
            }
        }
    }

    override suspend fun deleteExpiredAsync(maxAge: Duration): Int {
        val cutoffTime = Clock.System.now().minus(maxAge)
        val cutoffTicks = cutoffTime.toEpochMilliseconds()

        return context.withTransactionAsync {
            val weatherIds = context.queryAsync(
                "SELECT id FROM WeatherEntity WHERE lastUpdate < ?",
                ::mapToWeatherId,
                cutoffTicks
            )

            if (weatherIds.isEmpty()) return@withTransactionAsync 0

            weatherIds.chunked(100).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }

                context.executeAsync(
                    "DELETE FROM HourlyForecastEntity WHERE weatherId IN ($placeholders)",
                    *batch.toTypedArray()
                )
                context.executeAsync(
                    "DELETE FROM WeatherForecastEntity WHERE weatherId IN ($placeholders)",
                    *batch.toTypedArray()
                )
            }

            val deletedCount = context.executeAsync(
                "DELETE FROM WeatherEntity WHERE lastUpdate < ?",
                cutoffTicks
            )

            deletedCount
        }
    }

    private fun setIdUsingReflection(entity: Any, id: Int) {
        try {
            val idField = entity::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.setInt(entity, id)
        } catch (e: Exception) {
            // Log warning if needed
        }
    }

    private fun mapToWeatherId(cursor: Any): Int {
        val sqlCursor = cursor as SqlCursor
        return sqlCursor.getLong(0)?.toInt() ?: 0
    }

    private data class WeatherEntityDto(
        val id: Int,
        val locationId: Int,
        val coordinate: Coordinate,
        val timezone: String,
        val timezoneOffset: Int,
        val lastUpdate: Instant
    )

    private fun mapToWeatherEntity(cursor: Any): WeatherEntityDto {
        val sqlCursor = cursor as SqlCursor
        return WeatherEntityDto(
            id = sqlCursor.getLong(0)?.toInt() ?: 0,
            locationId = sqlCursor.getLong(1)?.toInt() ?: 0,
            coordinate = Coordinate(
                latitude = sqlCursor.getDouble(2) ?: 0.0,
                longitude = sqlCursor.getDouble(3) ?: 0.0
            ),
            timezone = sqlCursor.getString(4) ?: "",
            timezoneOffset = sqlCursor.getLong(5)?.toInt() ?: 0,
            lastUpdate = Instant.fromEpochMilliseconds(sqlCursor.getLong(6) ?: 0L)
        )
    }

    private fun mapToWeatherForecast(cursor: Any): WeatherForecast {
        val sqlCursor = cursor as SqlCursor
        val dateDays = sqlCursor.getLong(2) ?: 0L
        val localDate = LocalDate.fromEpochDays(dateDays.toInt())

        val forecast = WeatherForecast.create(
            weatherId = sqlCursor.getLong(1)?.toInt() ?: 0,
            date = localDate,
            sunrise = Instant.fromEpochMilliseconds(sqlCursor.getLong(3) ?: 0L),
            sunset = Instant.fromEpochMilliseconds(sqlCursor.getLong(4) ?: 0L),
            temperature = sqlCursor.getDouble(5) ?: 0.0,
            minTemperature = sqlCursor.getDouble(6) ?: 0.0,
            maxTemperature = sqlCursor.getDouble(7) ?: 0.0,
            description = sqlCursor.getString(8) ?: "",
            icon = sqlCursor.getString(9) ?: "",
            wind = WindInfo(
                speed = sqlCursor.getDouble(10) ?: 0.0,
                direction = sqlCursor.getDouble(11) ?: 0.0,
                gust = sqlCursor.getDouble(12)
            ),
            humidity = sqlCursor.getLong(13)?.toInt() ?: 0,
            pressure = sqlCursor.getLong(14)?.toInt() ?: 0,
            clouds = sqlCursor.getLong(15)?.toInt() ?: 0,
            uvIndex = sqlCursor.getDouble(16) ?: 0.0
        )

        setIdUsingReflection(forecast, sqlCursor.getLong(0)?.toInt() ?: 0)

        return forecast.copy(
            precipitation = sqlCursor.getDouble(17),
            moonRise = sqlCursor.getLong(18)?.let { Instant.fromEpochMilliseconds(it) },
            moonSet = sqlCursor.getLong(19)?.let { Instant.fromEpochMilliseconds(it) },
            moonPhase = sqlCursor.getDouble(20) ?: 0.0
        )
    }

    private fun mapToHourlyForecast(cursor: Any): HourlyForecast {
        val sqlCursor = cursor as SqlCursor
        val forecast = HourlyForecast.create(
            weatherId = sqlCursor.getLong(1)?.toInt() ?: 0,
            dateTime = Instant.fromEpochMilliseconds(sqlCursor.getLong(2) ?: 0L),
            temperature = sqlCursor.getDouble(3) ?: 0.0,
            feelsLike = sqlCursor.getDouble(4) ?: 0.0,
            description = sqlCursor.getString(5) ?: "",
            icon = sqlCursor.getString(6) ?: "",
            wind = WindInfo(
                speed = sqlCursor.getDouble(7) ?: 0.0,
                direction = sqlCursor.getDouble(8) ?: 0.0,
                gust = sqlCursor.getDouble(9)
            ),
            humidity = sqlCursor.getLong(10)?.toInt() ?: 0,
            pressure = sqlCursor.getLong(11)?.toInt() ?: 0,
            clouds = sqlCursor.getLong(12)?.toInt() ?: 0,
            uvIndex = sqlCursor.getDouble(13) ?: 0.0,
            probabilityOfPrecipitation = sqlCursor.getDouble(14) ?: 0.0,
            visibility = sqlCursor.getLong(15)?.toInt() ?: 0,
            dewPoint = sqlCursor.getDouble(16) ?: 0.0
        )

        setIdUsingReflection(forecast, sqlCursor.getLong(0)?.toInt() ?: 0)
        return forecast
    }
}

fun Weather.toDto() = mapOf(
    "id" to id,
    "locationId" to locationId,
    "latitude" to coordinate.latitude,
    "longitude" to coordinate.longitude,
    "timezone" to timezone,
    "timezoneOffset" to timezoneOffset,
    "lastUpdate" to lastUpdate.toEpochMilliseconds()
)

fun Weather.toCurrentDto() = mapOf(
    "id" to id,
    "locationId" to locationId,
    "temperature" to (forecasts.firstOrNull()?.temperature ?: 0.0),
    "description" to (forecasts.firstOrNull()?.description ?: ""),
    "icon" to (forecasts.firstOrNull()?.icon ?: ""),
    "lastUpdate" to lastUpdate.toEpochMilliseconds()
)
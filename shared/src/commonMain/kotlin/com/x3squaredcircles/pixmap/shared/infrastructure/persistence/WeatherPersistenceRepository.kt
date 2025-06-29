// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/WeatherPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.entities.WeatherForecast
import com.x3squaredcircles.pixmap.shared.domain.entities.HourlyForecast
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.WindInfo
import com.x3squaredcircles.pixmap.shared.infrastructure.database.IDatabaseContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

class WeatherPersistenceRepository(
    private val context: IDatabaseContext
) : IWeatherPersistenceRepository {

    override suspend fun getByIdAsync(id: Int): Weather? {
        val weatherEntity = context.executeQuerySingle(
            sql = "SELECT * FROM WeatherEntity WHERE id = ?",
            parameters = listOf(id)
        ) { cursor ->
            mapToWeatherEntity(cursor)
        } ?: return null

        val forecasts = context.executeQuery(
            sql = "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date",
            parameters = listOf(id)
        ) { cursor ->
            mapToWeatherForecast(cursor)
        }

        val hourlyForecasts = context.executeQuery(
            sql = "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime",
            parameters = listOf(id)
        ) { cursor ->
            mapToHourlyForecast(cursor)
        }

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
        val weatherEntity = context.executeQuerySingle(
            sql = "SELECT * FROM WeatherEntity WHERE locationId = ? ORDER BY lastUpdate DESC LIMIT 1",
            parameters = listOf(locationId)
        ) { cursor ->
            mapToWeatherEntity(cursor)
        } ?: return null

        val forecasts = context.executeQuery(
            sql = "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date",
            parameters = listOf(weatherEntity.id)
        ) { cursor ->
            mapToWeatherForecast(cursor)
        }

        val hourlyForecasts = context.executeQuery(
            sql = "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime",
            parameters = listOf(weatherEntity.id)
        ) { cursor ->
            mapToHourlyForecast(cursor)
        }

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
        return context.executeInTransaction {
            val weatherId = context.executeNonQuery(
                sql = """
                   INSERT INTO WeatherEntity 
                   (locationId, latitude, longitude, timezone, timezoneOffset, lastUpdate)
                   VALUES (?, ?, ?, ?, ?, ?)
               """,
                parameters = listOf(
                    weather.locationId,
                    weather.coordinate.latitude,
                    weather.coordinate.longitude,
                    weather.timezone,
                    weather.timezoneOffset,
                    weather.lastUpdate.toEpochMilliseconds()
                )
            )

            weather.forecasts.forEach { forecast ->
                val forecastId = context.executeNonQuery(
                    sql = """
                       INSERT INTO WeatherForecastEntity 
                       (weatherId, date, sunrise, sunset, temperature, minTemperature, maxTemperature,
                        description, icon, windSpeed, windDirection, windGust, humidity, pressure,
                        clouds, uvIndex, precipitation, moonRise, moonSet, moonPhase)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    parameters = listOf(
                        weatherId,
                        forecast.date.toEpochMilliseconds(),
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
                )
            }

            weather.hourlyForecasts.forEach { hourlyForecast ->
                context.executeNonQuery(
                    sql = """
                       INSERT INTO HourlyForecastEntity 
                       (weatherId, dateTime, temperature, feelsLike, description, icon, windSpeed,
                        windDirection, windGust, humidity, pressure, clouds, uvIndex,
                        probabilityOfPrecipitation, visibility, dewPoint)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    parameters = listOf(
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
                )
            }

            Weather.create(
                id = weatherId.toInt(),
                locationId = weather.locationId,
                coordinate = weather.coordinate,
                timezone = weather.timezone,
                timezoneOffset = weather.timezoneOffset,
                forecasts = weather.forecasts,
                hourlyForecasts = weather.hourlyForecasts,
                lastUpdate = weather.lastUpdate
            )
        }
    }

    override suspend fun updateAsync(weather: Weather) {
        context.executeInTransaction {
            context.executeNonQuery(
                sql = """
                   UPDATE WeatherEntity 
                   SET locationId = ?, latitude = ?, longitude = ?, timezone = ?, 
                       timezoneOffset = ?, lastUpdate = ?
                   WHERE id = ?
               """,
                parameters = listOf(
                    weather.locationId,
                    weather.coordinate.latitude,
                    weather.coordinate.longitude,
                    weather.timezone,
                    weather.timezoneOffset,
                    weather.lastUpdate.toEpochMilliseconds(),
                    weather.id
                )
            )

            context.executeNonQuery(
                sql = "DELETE FROM WeatherForecastEntity WHERE weatherId = ?",
                parameters = listOf(weather.id)
            )

            context.executeNonQuery(
                sql = "DELETE FROM HourlyForecastEntity WHERE weatherId = ?",
                parameters = listOf(weather.id)
            )

            weather.forecasts.forEach { forecast ->
                context.executeNonQuery(
                    sql = """
                       INSERT INTO WeatherForecastEntity 
                       (weatherId, date, sunrise, sunset, temperature, minTemperature, maxTemperature,
                        description, icon, windSpeed, windDirection, windGust, humidity, pressure,
                        clouds, uvIndex, precipitation, moonRise, moonSet, moonPhase)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    parameters = listOf(
                        weather.id,
                        forecast.date.toEpochMilliseconds(),
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
                )
            }

            weather.hourlyForecasts.forEach { hourlyForecast ->
                context.executeNonQuery(
                    sql = """
                       INSERT INTO HourlyForecastEntity 
                       (weatherId, dateTime, temperature, feelsLike, description, icon, windSpeed,
                        windDirection, windGust, humidity, pressure, clouds, uvIndex,
                        probabilityOfPrecipitation, visibility, dewPoint)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   """,
                    parameters = listOf(
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
                )
            }
        }
    }

    override suspend fun deleteAsync(weather: Weather) {
        context.executeInTransaction {
            context.executeNonQuery(
                sql = "DELETE FROM HourlyForecastEntity WHERE weatherId = ?",
                parameters = listOf(weather.id)
            )

            context.executeNonQuery(
                sql = "DELETE FROM WeatherForecastEntity WHERE weatherId = ?",
                parameters = listOf(weather.id)
            )

            context.executeNonQuery(
                sql = "DELETE FROM WeatherEntity WHERE id = ?",
                parameters = listOf(weather.id)
            )
        }
    }

    override suspend fun getRecentAsync(count: Int): List<Weather> {
        val weatherEntities = context.executeQuery(
            sql = "SELECT * FROM WeatherEntity ORDER BY lastUpdate DESC LIMIT ?",
            parameters = listOf(count)
        ) { cursor ->
            mapToWeatherEntity(cursor)
        }

        return weatherEntities.map { weatherEntity ->
            val forecasts = context.executeQuery(
                sql = "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date LIMIT 24",
                parameters = listOf(weatherEntity.id)
            ) { cursor ->
                mapToWeatherForecast(cursor)
            }

            val hourlyForecasts = context.executeQuery(
                sql = "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime LIMIT 24",
                parameters = listOf(weatherEntity.id)
            ) { cursor ->
                mapToHourlyForecast(cursor)
            }

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

        val weatherEntities = context.executeQuery(
            sql = "SELECT * FROM WeatherEntity WHERE lastUpdate < ?",
            parameters = listOf(cutoffTicks)
        ) { cursor ->
            mapToWeatherEntity(cursor)
        }

        return weatherEntities.chunked(10).flatMap { batch ->
            batch.map { weatherEntity ->
                val forecasts = context.executeQuery(
                    sql = "SELECT * FROM WeatherForecastEntity WHERE weatherId = ? ORDER BY date",
                    parameters = listOf(weatherEntity.id)
                ) { cursor ->
                    mapToWeatherForecast(cursor)
                }

                val hourlyForecasts = context.executeQuery(
                    sql = "SELECT * FROM HourlyForecastEntity WHERE weatherId = ? ORDER BY dateTime",
                    parameters = listOf(weatherEntity.id)
                ) { cursor ->
                    mapToHourlyForecast(cursor)
                }

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

        return context.executeInTransaction {
            weatherRecords.map { weather ->
                addAsync(weather)
            }
        }
    }

    override suspend fun deleteExpiredAsync(maxAge: Duration): Int {
        val cutoffTime = Clock.System.now().minus(maxAge)
        val cutoffTicks = cutoffTime.toEpochMilliseconds()

        return context.executeInTransaction {
            val weatherIds = context.executeQuery(
                sql = "SELECT id FROM WeatherEntity WHERE lastUpdate < ?",
                parameters = listOf(cutoffTicks)
            ) { cursor ->
                cursor.getInt(0) ?: 0
            }

            if (weatherIds.isEmpty()) return@executeInTransaction 0

            weatherIds.chunked(100).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                val batchParams = batch.map { it }

                context.executeNonQuery(
                    sql = "DELETE FROM HourlyForecastEntity WHERE weatherId IN ($placeholders)",
                    parameters = batchParams
                )
                context.executeNonQuery(
                    sql = "DELETE FROM WeatherForecastEntity WHERE weatherId IN ($placeholders)",
                    parameters = batchParams
                )
            }

            val deletedCount = context.executeNonQuery(
                sql = "DELETE FROM WeatherEntity WHERE lastUpdate < ?",
                parameters = listOf(cutoffTicks)
            )

            deletedCount.toInt()
        }
    }

    private data class WeatherEntityDto(
        val id: Int,
        val locationId: Int,
        val coordinate: Coordinate,
        val timezone: String,
        val timezoneOffset: Int,
        val lastUpdate: Instant
    )

    private fun mapToWeatherEntity(cursor: com.x3squaredcircles.pixmap.shared.infrastructure.database.SqlCursor): WeatherEntityDto {
        return WeatherEntityDto(
            id = cursor.getInt(0) ?: 0,
            locationId = cursor.getInt(1) ?: 0,
            coordinate = Coordinate(
                latitude = cursor.getDouble(2) ?: 0.0,
                longitude = cursor.getDouble(3) ?: 0.0
            ),
            timezone = cursor.getString(4) ?: "",
            timezoneOffset = cursor.getInt(5) ?: 0,
            lastUpdate = Instant.fromEpochMilliseconds(cursor.getLong(6) ?: 0L)
        )
    }

    private fun mapToWeatherForecast(cursor: com.x3squaredcircles.pixmap.shared.infrastructure.database.SqlCursor): WeatherForecast {
        return WeatherForecast.create(
            id = cursor.getInt(0) ?: 0,
            weatherId = cursor.getInt(1) ?: 0,
            date = Instant.fromEpochMilliseconds(cursor.getLong(2) ?: 0L),
            sunrise = Instant.fromEpochMilliseconds(cursor.getLong(3) ?: 0L),
            sunset = Instant.fromEpochMilliseconds(cursor.getLong(4) ?: 0L),
            temperature = cursor.getDouble(5) ?: 0.0,
            minTemperature = cursor.getDouble(6) ?: 0.0,
            maxTemperature = cursor.getDouble(7) ?: 0.0,
            description = cursor.getString(8) ?: "",
            icon = cursor.getString(9) ?: "",
            wind = WindInfo(
                speed = cursor.getDouble(10) ?: 0.0,
                direction = cursor.getDouble(11) ?: 0.0,
                gust = cursor.getDouble(12)
            ),
            humidity = cursor.getInt(13) ?: 0,
            pressure = cursor.getInt(14) ?: 0,
            clouds = cursor.getInt(15) ?: 0,
            uvIndex = cursor.getDouble(16) ?: 0.0,
            precipitation = cursor.getDouble(17),
            moonRise = cursor.getLong(18)?.let { Instant.fromEpochMilliseconds(it) },
            moonSet = cursor.getLong(19)?.let { Instant.fromEpochMilliseconds(it) },
            moonPhase = cursor.getDouble(20) ?: 0.0
        )
    }

    private fun mapToHourlyForecast(cursor: com.x3squaredcircles.pixmap.shared.infrastructure.database.SqlCursor): HourlyForecast {
        return HourlyForecast.create(
            id = cursor.getInt(0) ?: 0,
            weatherId = cursor.getInt(1) ?: 0,
            dateTime = Instant.fromEpochMilliseconds(cursor.getLong(2) ?: 0L),
            temperature = cursor.getDouble(3) ?: 0.0,
            feelsLike = cursor.getDouble(4) ?: 0.0,
            description = cursor.getString(5) ?: "",
            icon = cursor.getString(6) ?: "",
            wind = WindInfo(
                speed = cursor.getDouble(7) ?: 0.0,
                direction = cursor.getDouble(8) ?: 0.0,
                gust = cursor.getDouble(9)
            ),
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
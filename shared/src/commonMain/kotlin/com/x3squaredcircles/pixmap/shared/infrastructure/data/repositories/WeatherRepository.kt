//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/WeatherRepository.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.WeatherEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Repository implementation for weather management
 */
class WeatherRepository(
    private val context: IDatabaseContext,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) : IWeatherRepository {

    override suspend fun getByIdAsync(id: Int): Result<Weather?> {
        return try {
            logger.logInfo("Getting weather by ID: $id")

            val entities = context.queryAsync<WeatherEntity>(
                "SELECT * FROM WeatherEntity WHERE id = ? LIMIT 1",
                ::mapCursorToWeatherEntity,
                id
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found weather: ${result?.id ?: "none"}")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get weather by ID: $id", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "GetById").message ?: "Weather operation failed")
        }
    }

    override suspend fun getByLocationIdAsync(locationId: Int): Result<Weather?> {
        return try {
            logger.logInfo("Getting weather by location ID: $locationId")

            val entities = context.queryAsync<WeatherEntity>(
                "SELECT * FROM WeatherEntity WHERE locationId = ? ORDER BY lastUpdate DESC LIMIT 1",
                ::mapCursorToWeatherEntity,
                locationId
            )

            val result = entities.firstOrNull()?.let { mapEntityToDomain(it) }
            logger.logInfo("Found weather: ${result?.id ?: "none"} for location: $locationId")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get weather by location ID: $locationId", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "GetByLocationId").message ?: "Weather operation failed")
        }
    }

    override suspend fun addAsync(weather: Weather): Result<Weather> {
        return try {
            logger.logInfo("Creating weather for location: ${weather.locationId}")

            val entity = mapDomainToEntity(weather)
            val id = context.insertAsync(entity)

            val createdWeather = createWeatherWithId(weather, id.toInt())
            logger.logInfo("Successfully created weather with ID: $id")
            Result.success(createdWeather)
        } catch (ex: Exception) {
            logger.logError("Failed to create weather for location: ${weather.locationId}", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "Add").message ?: "Weather operation failed")
        }
    }

    override suspend fun updateAsync(weather: Weather): Result<Unit> {
        return try {
            logger.logInfo("Updating weather: ${weather.id}")

            val entity = mapDomainToEntity(weather)
            val rowsAffected = context.executeAsync(
                """UPDATE WeatherEntity 
                   SET locationId = ?, latitude = ?, longitude = ?, timezone = ?, 
                       timezoneOffset = ?, lastUpdate = ?
                   WHERE id = ?""",
                entity.locationId,
                entity.latitude,
                entity.longitude,
                entity.timezone,
                entity.timezoneOffset,
                entity.lastUpdate,
                entity.id
            )

            if (rowsAffected == 0) {
                return Result.failure("Weather not found")
            }

            logger.logInfo("Successfully updated weather: ${weather.id}")
            Result.success(Unit)
        } catch (ex: Exception) {
            logger.logError("Failed to update weather: ${weather.id}", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "Update").message ?: "Weather operation failed")
        }
    }

    override suspend fun deleteAsync(id: Int): Result<Boolean> {
        return try {
            logger.logInfo("Deleting weather: $id")

            val rowsAffected = context.executeAsync(
                "DELETE FROM WeatherEntity WHERE id = ?",
                id
            )

            val deleted = rowsAffected > 0
            logger.logInfo("Weather deletion result for ID $id: $deleted")
            Result.success(deleted)
        } catch (ex: Exception) {
            logger.logError("Failed to delete weather: $id", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "Delete").message ?: "Weather operation failed")
        }
    }

    override suspend fun getRecentAsync(count: Int): Result<List<Weather>> {
        return try {
            logger.logInfo("Getting recent weather records: $count")

            val entities = context.queryAsync<WeatherEntity>(
                "SELECT * FROM WeatherEntity ORDER BY lastUpdate DESC LIMIT ?",
                ::mapCursorToWeatherEntity,
                count
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} recent weather records")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get recent weather records", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "GetRecent").message ?: "Weather operation failed")
        }
    }

    override suspend fun getExpiredAsync(maxAgeHours: Int): Result<List<Weather>> {
        return try {
            logger.logInfo("Getting expired weather records older than $maxAgeHours hours")

            val cutoffTime = Clock.System.now().epochSeconds - (maxAgeHours * 3600)
            val entities = context.queryAsync<WeatherEntity>(
                "SELECT * FROM WeatherEntity WHERE lastUpdate < ? ORDER BY lastUpdate DESC",
                ::mapCursorToWeatherEntity,
                cutoffTime
            )

            val result = entities.map { mapEntityToDomain(it) }
            logger.logInfo("Found ${result.size} expired weather records")
            Result.success(result)
        } catch (ex: Exception) {
            logger.logError("Failed to get expired weather records", ex)
            Result.failure(exceptionMapper.mapToWeatherDomainException(ex, "GetExpired").message ?: "Weather operation failed")
        }
    }

    private fun createWeatherWithId(originalWeather: Weather, id: Int): Weather {
        val newWeather = Weather(
            locationId = originalWeather.locationId,
            coordinate = originalWeather.coordinate,
            timezone = originalWeather.timezone,
            timezoneOffset = originalWeather.timezoneOffset,
            lastUpdate = originalWeather.lastUpdate
        )
        setIdUsingReflection(newWeather, id)
        return newWeather
    }

    private fun mapEntityToDomain(entity: WeatherEntity): Weather {
        val weather = Weather(
            locationId = entity.locationId,
            coordinate = Coordinate(longitude = entity.longitude, latitude = entity.latitude),
            timezone = entity.timezone,
            timezoneOffset = entity.timezoneOffset,
            lastUpdate = Instant.fromEpochSeconds(entity.lastUpdate)
        )
        setIdUsingReflection(weather, entity.id)
        return weather
    }

    private fun mapDomainToEntity(weather: Weather): WeatherEntity {
        return WeatherEntity(
            id = weather.id,
            locationId = weather.locationId,
            latitude = weather.coordinate.latitude,
            longitude = weather.coordinate.longitude,
            timezone = weather.timezone,
            timezoneOffset = weather.timezoneOffset,
            lastUpdate = weather.lastUpdate.toEpochMilliseconds()
        )
    }

    private fun mapCursorToWeatherEntity(cursor: app.cash.sqldelight.db.SqlCursor): WeatherEntity {
        return WeatherEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            locationId = cursor.getLong(1)?.toInt() ?: 0,
            latitude = cursor.getDouble(2) ?: 0.0,
            longitude = cursor.getDouble(3) ?: 0.0,
            timezone = cursor.getString(4) ?: "",
            timezoneOffset = cursor.getLong(5)?.toInt() ?: 0,
            lastUpdate = cursor.getLong(6)?:0
        )
    }

    private fun setIdUsingReflection(weather: Weather, id: Int) {
        try {
            val idField = weather::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.setInt(weather, id)
        } catch (e: Exception) {
            logger.logWarning("Could not set ID via reflection: ${e.message}")
        }
    }
}
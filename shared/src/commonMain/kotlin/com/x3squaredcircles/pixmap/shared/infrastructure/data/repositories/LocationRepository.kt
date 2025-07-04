//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/LocationRepository.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.ILoggingService
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LocationEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*

class LocationRepository(
    private val context: IDatabaseContext,
    private val logger: ILoggingService,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) : ILocationRepository {

    override suspend fun getByIdAsync(id: Int): Result<Location?> {
        return try {
            val locationEntity = context.queryAsync<LocationEntity>(
                "SELECT * FROM LocationEntity WHERE id = ?",
                ::mapCursorToLocationEntity,
                id
            ).firstOrNull()

            val location = locationEntity?.let { mapEntityToDomain(it) }
            Result.success(location)
        } catch (ex: Exception) {
            logger.logError("Failed to get location by id $id", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "GetById"))
        }
    }

    override suspend fun getAllAsync(): Result<List<Location>> {
        return try {
            val entities = context.queryAsync<LocationEntity>(
                "SELECT * FROM LocationEntity ORDER BY timestamp DESC",
                ::mapCursorToLocationEntity
            )
            val locations = entities.map { mapEntityToDomain(it) }
            Result.success(locations)
        } catch (ex: Exception) {
            logger.logError("Failed to get all locations", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "GetAll"))
        }
    }

    override suspend fun getActiveAsync(): Result<List<Location>> {
        return try {
            val entities = context.queryAsync<LocationEntity>(
                "SELECT * FROM LocationEntity WHERE isDeleted = 0 ORDER BY timestamp DESC",
                ::mapCursorToLocationEntity
            )
            val locations = entities.map { mapEntityToDomain(it) }
            Result.success(locations)
        } catch (ex: Exception) {
            logger.logError("Failed to get active locations", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "GetActive"))
        }
    }

    override suspend fun createAsync(location: Location): Result<Location> {
        return try {
            val entity = mapDomainToEntity(location)
            val id = context.insertAsync(entity)

            val createdLocation = createLocationWithId(location, id.toInt())
            Result.success(createdLocation)
        } catch (ex: Exception) {
            logger.logError("Failed to create location", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "Create"))
        }
    }

    override suspend fun updateAsync(location: Location): Result<Location> {
        return try {
            val entity = mapDomainToEntity(location)
            val rowsAffected = context.executeAsync(
                """UPDATE LocationEntity 
                   SET title = ?, description = ?, latitude = ?, longitude = ?, 
                       city = ?, state = ?, photoPath = ?, isDeleted = ?, timestamp = ?
                   WHERE id = ?""",
                entity.title,
                entity.description,
                entity.latitude,
                entity.longitude,
                entity.city,
                entity.state,
                entity.photoPath,
                if (entity.isDeleted) 1 else 0,
                entity.timestamp,
                entity.id
            )

            if (rowsAffected > 0) {
                Result.success(location)
            } else {
                Result.failure("No rows were updated")
            }
        } catch (ex: Exception) {
            logger.logError("Failed to update location with id ${location.id}", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "Update"))
        }
    }

    override suspend fun deleteAsync(id: Int): Result<Boolean> {
        return try {
            val rowsAffected = context.executeAsync(
                "UPDATE LocationEntity SET isDeleted = 1 WHERE id = ?",
                id
            )
            Result.success(rowsAffected > 0)
        } catch (ex: Exception) {
            logger.logError("Failed to delete location with id $id", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "Delete"))
        }
    }

    override suspend fun getNearbyAsync(
        latitude: Double,
        longitude: Double,
        distanceKm: Double
    ): Result<List<Location>> {
        return try {
            val entities = context.queryAsync<LocationEntity>(
                "SELECT * FROM LocationEntity WHERE isDeleted = 0",
                ::mapCursorToLocationEntity
            )

            val nearbyLocations = entities
                .filter { entity ->
                    calculateDistance(latitude, longitude, entity.latitude, entity.longitude) <= distanceKm
                }
                .map { mapEntityToDomain(it) }

            Result.success(nearbyLocations)
        } catch (ex: Exception) {
            logger.logError("Failed to get nearby locations", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "GetNearby"))
        }
    }

    override suspend fun getPagedAsync(
        pageNumber: Int,
        pageSize: Int,
        searchTerm: String?,
        includeDeleted: Boolean
    ): Result<PagedList<Location>> {
        return try {
            val offset = (pageNumber - 1) * pageSize
            val whereClause = buildString {
                if (!includeDeleted) {
                    append("isDeleted = 0")
                }
                if (!searchTerm.isNullOrBlank()) {
                    if (isNotEmpty()) append(" AND ")
                    append("(title LIKE ? OR description LIKE ?)")
                }
            }

            val parameters = mutableListOf<Any?>()
            if (!searchTerm.isNullOrBlank()) {
                val searchPattern = "%$searchTerm%"
                parameters.add(searchPattern)
                parameters.add(searchPattern)
            }

            val totalCount = context.countAsync(
                "SELECT COUNT(*) FROM LocationEntity" + if (whereClause.isNotEmpty()) " WHERE $whereClause" else "",
                *parameters.toTypedArray()
            )

            val entities = context.queryAsync<LocationEntity>(
                "SELECT * FROM LocationEntity" +
                        if (whereClause.isNotEmpty()) " WHERE $whereClause" else "" +
                                " ORDER BY timestamp DESC LIMIT ? OFFSET ?",
                ::mapCursorToLocationEntity,
                *parameters.toTypedArray(),
                pageSize,
                offset
            )

            val locations = entities.map { mapEntityToDomain(it) }
            val totalPages = ((totalCount + pageSize - 1) / pageSize).toInt()

            val pagedList = PagedList(
                 items=locations ,
                pageNumber = pageNumber,
                pageSize = pageSize,
                totalCount = totalCount.toInt(),

            )

            Result.success(pagedList)
        } catch (ex: Exception) {
            logger.logError("Failed to get paged locations", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "GetPaged"))
        }
    }

    override suspend fun getByTitleAsync(title: String): Result<Location?> {
        return try {
            val entity = context.queryAsync<LocationEntity>(
                "SELECT * FROM LocationEntity WHERE title = ? AND isDeleted = 0",
                ::mapCursorToLocationEntity,
                title
            ).firstOrNull()

            val location = entity?.let { mapEntityToDomain(it) }
            Result.success(location)
        } catch (ex: Exception) {
            logger.logError("Failed to get location by title: $title", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "GetByTitle"))
        }
    }

    suspend fun countAsync(): Result<Int> {
        return try {
            val count = context.countAsync(
                "SELECT COUNT(*) FROM LocationEntity WHERE isDeleted = 0"
            )
            Result.success(count.toInt())
        } catch (ex: Exception) {
            logger.logError("Failed to count locations", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "Count"))
        }
    }

    suspend fun existsAsync(id: Int): Result<Boolean> {
        return try {
            val count = context.countAsync(
                "SELECT COUNT(*) FROM LocationEntity WHERE id = ?",
                id
            )
            Result.success(count > 0)
        } catch (ex: Exception) {
            logger.logError("Failed to check if location exists: $id", ex)
            Result.failure(exceptionMapper.mapToLocationDomainException(ex, "Exists"))
        }
    }

    private fun createLocationWithId(location: Location, id: Int): Location {
        val newLocation = Location(location.title, location.description, location.coordinate, location.address)
        newLocation.setId(id)
        newLocation.setTimestamp(Clock.System.now())
        location.photoPath?.let { newLocation.setPhotoPath(it) }
        return newLocation
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    private fun mapEntityToDomain(entity: LocationEntity): Location {
        val coordinate = Coordinate(entity.latitude, entity.longitude)
        val address = Address(entity.city ?: "", entity.state ?: "")

        val location = Location(entity.title, entity.description ?: "", coordinate, address)

        location.setId(entity.id)
        location.setTimestamp(Instant.fromEpochSeconds(entity.timestamp))
        location.setDeleted(entity.isDeleted)
        entity.photoPath?.let { location.setPhotoPath(it) }

        return location
    }

    private fun mapDomainToEntity(location: Location): LocationEntity {
        return LocationEntity(
            id = location.id,
            title = location.title,
            description = location.description,
            latitude = location.coordinate.latitude,
            longitude = location.coordinate.longitude,
            city = location.address.city,
            state = location.address.state,
            photoPath = location.photoPath,
            isDeleted = location.isDeleted,
            timestamp = location.timestamp.epochSeconds
        )
    }

    private fun mapCursorToLocationEntity(cursor: app.cash.sqldelight.db.SqlCursor): LocationEntity {
        return LocationEntity(
            id = cursor.getLong(0)?.toInt() ?: 0,
            title = cursor.getString(1) ?: "",
            description = cursor.getString(2) ?: "",
            latitude = cursor.getDouble(3) ?: 0.0,
            longitude = cursor.getDouble(4) ?: 0.0,
            city = cursor.getString(5) ?: "",
            state = cursor.getString(6) ?: "",
            photoPath = cursor.getString(7),
            isDeleted = cursor.getLong(8) == 1L,
            timestamp = cursor.getLong(9) ?: 0L
        )
    }
    }

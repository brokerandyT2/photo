// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/LocationRepository.kt


import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import com.x3squaredcircles.pixmap.shared.infrastructure.data.entities.LocationEntity
import com.x3squaredcircles.pixmap.shared.infrastructure.services.IInfrastructureExceptionMappingService

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*

class LocationRepository(
    private val context: IDatabaseContext,
    private val logger: Logger,
    private val exceptionMapper: IInfrastructureExceptionMappingService
) {

    suspend fun getByIdAsync(id: Int): Location? {
        return try {
            val entity = context.getAsync(id) { primaryKey ->
                queryLocationById(primaryKey as Int)
            }
            entity?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get location by id $id", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "GetById")
        }
    }

    suspend fun getAllAsync(): List<Location> {
        return try {
            val entities = context.getAllAsync {
                queryAllLocations()
            }
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get all locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "GetAll")
        }
    }

    suspend fun getActiveAsync(): List<Location> {
        return try {
            val entities = context.queryAsync(
                "SELECT * FROM LocationEntity WHERE IsActive = 1 ORDER BY Timestamp DESC",
                ::mapCursorToLocationEntity
            )
            entities.map { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get active locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "GetActive")
        }
    }

    suspend fun createAsync(location: Location): Location {
        return try {
            val entity = mapDomainToEntity(location)
            val id = context.insertAsync(entity) { locationEntity ->
                insertLocation(locationEntity)
            }

            // Set the generated ID and return the created location
            val createdEntity = entity.copy(id = id)
            mapEntityToDomain(createdEntity)
        } catch (ex: Exception) {
            logger.error("Failed to create location", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "Create")
        }
    }

    suspend fun updateAsync(location: Location): Location {
        return try {
            val entity = mapDomainToEntity(location)
            context.updateAsync(entity) { locationEntity ->
                updateLocation(locationEntity)
            }
            location
        } catch (ex: Exception) {
            logger.error("Failed to update location with id ${location.id}", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "Update")
        }
    }

    suspend fun deleteAsync(id: Int): Boolean {
        return try {
            val rowsAffected = context.executeAsync(
                "UPDATE LocationEntity SET IsActive = 0 WHERE Id = ?",
                id
            )
            rowsAffected > 0
        } catch (ex: Exception) {
            logger.error("Failed to delete location with id $id", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "Delete")
        }
    }

    suspend fun getByTitleAsync(title: String): Location? {
        return try {
            val entities = context.queryAsync(
                "SELECT * FROM LocationEntity WHERE Title = ? AND IsActive = 1 LIMIT 1",
                ::mapCursorToLocationEntity,
                title
            )
            entities.firstOrNull()?.let { mapEntityToDomain(it) }
        } catch (ex: Exception) {
            logger.error("Failed to get location by title '$title'", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "GetByTitle")
        }
    }

    suspend fun getNearbyAsync(latitude: Double, longitude: Double, distanceKm: Double): List<Location> {
        return try {
            // Calculate approximate bounding box for initial filtering
            val earthRadiusKm = 6371.0
            val deltaLat = distanceKm / earthRadiusKm * (180.0 / PI)
            val deltaLon = distanceKm / (earthRadiusKm * cos(latitude * PI / 180.0)) * (180.0 / PI)

            val minLat = latitude - deltaLat
            val maxLat = latitude + deltaLat
            val minLon = longitude - deltaLon
            val maxLon = longitude + deltaLon

            val entities = context.queryAsync(
                """SELECT * FROM LocationEntity 
                   WHERE IsActive = 1 
                   AND Latitude BETWEEN ? AND ? 
                   AND Longitude BETWEEN ? AND ?""",
                ::mapCursorToLocationEntity,
                minLat, maxLat, minLon, maxLon
            )

            // Filter by exact distance using Haversine formula
            val nearbyLocations = entities.mapNotNull { entity ->
                val location = mapEntityToDomain(entity)
                val distance = calculateDistance(latitude, longitude, location.coordinate.latitude, location.coordinate.longitude)
                if (distance <= distanceKm) location else null
            }

            nearbyLocations.sortedBy {
                calculateDistance(latitude, longitude, it.coordinate.latitude, it.coordinate.longitude)
            }
        } catch (ex: Exception) {
            logger.error("Failed to get nearby locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "GetNearby")
        }
    }

    suspend fun getPagedAsync(
        pageNumber: Int,
        pageSize: Int,
        searchTerm: String? = null,
        includeDeleted: Boolean = false
    ): PagedList<Location> {
        return try {
            val offset = pageNumber * pageSize

            val whereClause = buildString {
                if (!includeDeleted) {
                    append("WHERE IsActive = 1")
                }
                if (!searchTerm.isNullOrBlank()) {
                    if (isNotEmpty()) append(" AND ")
                    else append("WHERE ")
                    append("(Title LIKE ? OR Description LIKE ?)")
                }
            }

            val orderClause = "ORDER BY Timestamp DESC"
            val limitClause = "LIMIT ? OFFSET ?"

            val countSql = "SELECT COUNT(*) FROM LocationEntity $whereClause"
            val dataSql = "SELECT * FROM LocationEntity $whereClause $orderClause $limitClause"

            // Build parameters
            val parameters = mutableListOf<Any>()
            if (!searchTerm.isNullOrBlank()) {
                val searchPattern = "%$searchTerm%"
                parameters.add(searchPattern)
                parameters.add(searchPattern)
            }

            // Get total count
            val totalCount = context.executeScalarAsync<Long>(countSql, *parameters.toTypedArray())?.toInt() ?: 0

            // Get data with limit and offset
            val dataParameters = parameters.toMutableList().apply {
                add(pageSize)
                add(offset)
            }

            val entities = context.queryAsync(
                dataSql,
                ::mapCursorToLocationEntity,
                *dataParameters.toTypedArray()
            )

            val locations = entities.map { mapEntityToDomain(it) }
            val totalPages = if (pageSize > 0) (totalCount + pageSize - 1) / pageSize else 0

            PagedList(
                items = locations,
                totalCount = totalCount,
                pageNumber = pageNumber,
                pageSize = pageSize,
                totalPages = totalPages,
                hasPreviousPage = pageNumber > 0,
                hasNextPage = pageNumber < totalPages - 1
            )
        } catch (ex: Exception) {
            logger.error("Failed to get paged locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "GetPaged")
        }
    }

    suspend fun createBulkAsync(locations: List<Location>): List<Location> {
        return try {
            val entities = locations.map { mapDomainToEntity(it) }
            context.bulkInsertAsync(entities)
            locations
        } catch (ex: Exception) {
            logger.error("Failed to bulk create locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "CreateBulk")
        }
    }

    suspend fun updateBulkAsync(locations: List<Location>): Int {
        return try {
            val entities = locations.map { mapDomainToEntity(it) }
            context.bulkUpdateAsync(entities)
        } catch (ex: Exception) {
            logger.error("Failed to bulk update locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "UpdateBulk")
        }
    }

    suspend fun countAsync(whereClause: String? = null, parameters: Map<String, Any>? = null): Int {
        return try {
            val sql = "SELECT COUNT(*) FROM LocationEntity" +
                    if (whereClause != null) " WHERE $whereClause" else ""

            val paramValues = parameters?.values?.toTypedArray() ?: emptyArray()
            context.executeScalarAsync<Long>(sql, *paramValues)?.toInt() ?: 0
        } catch (ex: Exception) {
            logger.error("Failed to count locations", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "Count")
        }
    }

    suspend fun existsAsync(id: Int): Boolean {
        return try {
            val count = context.executeScalarAsync<Long>(
                "SELECT COUNT(*) FROM LocationEntity WHERE Id = ?",
                id
            ) ?: 0
            count > 0
        } catch (ex: Exception) {
            logger.error("Failed to check if location exists with id $id", ex)
            throw exceptionMapper.mapToLocationDomainException(ex, "Exists")
        }
    }

    // Helper methods for database operations
    private suspend fun queryLocationById(id: Int): LocationEntity? {
        val entities = context.queryAsync(
            "SELECT * FROM LocationEntity WHERE Id = ? LIMIT 1",
            ::mapCursorToLocationEntity,
            id
        )
        return entities.firstOrNull()
    }

    private suspend fun queryAllLocations(): List<LocationEntity> {
        return context.queryAsync(
            "SELECT * FROM LocationEntity ORDER BY Timestamp DESC",
            ::mapCursorToLocationEntity
        )
    }

    private suspend fun insertLocation(entity: LocationEntity): Long {
        return context.executeAsync(
            """INSERT INTO LocationEntity (Title, Description, Latitude, Longitude, Photo, IsActive, Timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            entity.title,
            entity.description ?: "",
            entity.latitude,
            entity.longitude,
            entity.photo ?: "",
            if (entity.isActive) 1 else 0,
            entity.timestamp.toString()
        ).toLong()
    }

    private suspend fun updateLocation(entity: LocationEntity): Int {
        return context.executeAsync(
            """UPDATE LocationEntity 
               SET Title = ?, Description = ?, Latitude = ?, Longitude = ?, Photo = ?, IsActive = ?, Timestamp = ?
               WHERE Id = ?""",
            entity.title,
            entity.description ?: "",
            entity.latitude,
            entity.longitude,
            entity.photo ?: "",
            if (entity.isActive) 1 else 0,
            entity.timestamp.toString(),
            entity.id
        )
    }

    // Mapping functions
    private fun mapEntityToDomain(entity: LocationEntity): Location {
        return Location(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            coordinate = Coordinate(entity.latitude, entity.longitude),
            photo = entity.photo,
            isActive = entity.isActive,
            timestamp = entity.timestamp
        )
    }

    private fun mapDomainToEntity(location: Location): LocationEntity {
        return LocationEntity(
            id = location.id,
            title = location.title,
            description = location.description,
            latitude = location.coordinate.latitude,
            longitude = location.coordinate.longitude,
            photo = location.photo,
            isActive = location.isActive,
            timestamp = location.timestamp
        )
    }

    private fun mapCursorToLocationEntity(cursor: SqlCursor): LocationEntity {
        return LocationEntity(
            id = cursor.getInt(0) ?: 0,
            title = cursor.getString(1) ?: "",
            description = cursor.getString(2),
            latitude = cursor.getDouble(3) ?: 0.0,
            longitude = cursor.getDouble(4) ?: 0.0,
            photo = cursor.getString(5),
            isActive = cursor.getBoolean(6) ?: true,
            timestamp = Instant.parse(cursor.getString(7) ?: Clock.System.now().toString())
        )
    }

    // Distance calculation using Haversine formula
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
}

// Location entity data class
data class LocationEntity(
    val id: Int = 0,
    val title: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val photo: String? = null,
    val isActive: Boolean = true,
    val timestamp: Instant = Clock.System.now()
)

// SqlCursor interface for database queries
interface SqlCursor {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long?
    fun getDouble(index: Int): Double?
    fun getBoolean(index: Int): Boolean?
    fun getInt(index: Int): Int?
}
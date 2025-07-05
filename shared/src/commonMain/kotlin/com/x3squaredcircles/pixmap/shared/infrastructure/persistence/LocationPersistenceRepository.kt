// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/LocationPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import app.cash.sqldelight.db.SqlCursor
import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.rules.LocationValidationRules
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.infrastructure.data.IDatabaseContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.cos
import kotlin.math.PI

class LocationPersistenceRepository(
    private val context: IDatabaseContext
) : ILocationPersistenceRepository {

    override suspend fun getByIdAsync(id: Int): Location? {
        return context.querySingleAsync(
            "SELECT * FROM LocationEntity WHERE id = ?",
            ::mapToLocation,
            id
        )
    }

    override suspend fun getAllAsync(): List<Location> {
        return context.queryAsync(
            "SELECT * FROM LocationEntity ORDER BY timestamp DESC",
            ::mapToLocation
        )
    }

    override suspend fun getActiveAsync(): List<Location> {
        return context.queryAsync(
            "SELECT * FROM LocationEntity WHERE isDeleted = 0 ORDER BY timestamp DESC",
            ::mapToLocation
        )
    }

    override suspend fun addAsync(location: Location): Location {
        if (!LocationValidationRules.isValid(location)) {
            throw IllegalArgumentException("Location validation failed")
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val id = context.executeAsync(
            """
               INSERT INTO LocationEntity 
               (title, description, latitude, longitude, city, state, photoPath, isDeleted, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
           """,
            location.title,
            location.description,
            location.coordinate.latitude,
            location.coordinate.longitude,
            location.address.city,
            location.address.state,
            location.photoPath,
            if (location.isDeleted) 1 else 0,
            timestamp
        ).toLong()

        val createdLocation = Location(
            title = location.title,
            description = location.description,
            coordinate = location.coordinate,
            address = location.address
        )

        setLocationProperties(createdLocation, id.toInt(), timestamp, location.isDeleted, location.photoPath)
        return createdLocation
    }

    override suspend fun updateAsync(location: Location) {
        if (!LocationValidationRules.isValid(location)) {
            throw IllegalArgumentException("Location validation failed")
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val rowsAffected = context.executeAsync(
            """
               UPDATE LocationEntity 
               SET title = ?, description = ?, latitude = ?, longitude = ?, 
                   city = ?, state = ?, photoPath = ?, isDeleted = ?, timestamp = ?
               WHERE id = ?
           """,
            location.title,
            location.description,
            location.coordinate.latitude,
            location.coordinate.longitude,
            location.address.city,
            location.address.state,
            location.photoPath,
            if (location.isDeleted) 1 else 0,
            timestamp,
            location.id
        )

        if (rowsAffected == 0) {
            throw IllegalStateException("Location with id '${location.id}' not found for update")
        }
    }

    override suspend fun deleteAsync(location: Location) {
        val rowsAffected = context.executeAsync(
            "DELETE FROM LocationEntity WHERE id = ?",
            location.id
        )

        if (rowsAffected == 0) {
            throw IllegalStateException("Location with id '${location.id}' not found for deletion")
        }
    }

    override suspend fun getByTitleAsync(title: String): Location? {
        return context.querySingleAsync(
            "SELECT * FROM LocationEntity WHERE title = ? AND isDeleted = 0",
            ::mapToLocation,
            title
        )
    }

    override suspend fun getNearbyAsync(latitude: Double, longitude: Double, distanceKm: Double): List<Location> {
        val latRange = distanceKm / 111.0
        val lngRange = distanceKm / (111.0 * cos(latitude * PI / 180))

        return context.queryAsync(
            """
               SELECT * FROM LocationEntity 
               WHERE latitude BETWEEN ? AND ? 
               AND longitude BETWEEN ? AND ?
               AND isDeleted = 0
               ORDER BY timestamp DESC
           """,
            ::mapToLocation,
            latitude - latRange,
            latitude + latRange,
            longitude - lngRange,
            longitude + lngRange
        )
    }

    override suspend fun getPagedAsync(
        pageNumber: Int,
        pageSize: Int,
        searchTerm: String?,
        includeDeleted: Boolean
    ): PagedList<Location> {
        val offset = (pageNumber - 1) * pageSize
        val deletedFilter = if (includeDeleted) "" else "AND isDeleted = 0"
        val searchFilter = if (searchTerm.isNullOrBlank()) "" else "AND (title LIKE ? OR description LIKE ? OR city LIKE ? OR state LIKE ?)"

        val whereClause = "WHERE 1=1 $deletedFilter $searchFilter"
        val searchParams = if (searchTerm.isNullOrBlank()) emptyList() else {
            val searchPattern = "%$searchTerm%"
            listOf(searchPattern, searchPattern, searchPattern, searchPattern)
        }

        val totalCount = context.queryScalarAsync<Long>(
            "SELECT COUNT(*) FROM LocationEntity $whereClause",
            *searchParams.toTypedArray()
        ) ?: 0

        val items = context.queryAsync(
            "SELECT * FROM LocationEntity $whereClause ORDER BY timestamp DESC LIMIT ? OFFSET ?",
            ::mapToLocation,
            *searchParams.toTypedArray(),
            pageSize,
            offset
        )

        return PagedList.createOptimized(items, totalCount.toInt(), pageNumber, pageSize)
    }

    override suspend fun getPagedProjectedAsync(
        pageNumber: Int,
        pageSize: Int,
        selectColumns: String,
        whereClause: String?,
        parameters: Map<String, Any>?,
        orderBy: String?
    ): PagedList<Map<String, Any?>> {
        val offset = (pageNumber - 1) * pageSize
        val where = if (whereClause.isNullOrBlank()) "" else "WHERE $whereClause"
        val order = if (orderBy.isNullOrBlank()) "ORDER BY timestamp DESC" else "ORDER BY $orderBy"
        val paramList = parameters?.values?.toList() ?: emptyList()

        val totalCount = context.queryScalarAsync<Long>(
            "SELECT COUNT(*) FROM LocationEntity $where",
            *paramList.toTypedArray()
        ) ?: 0

        val items = context.queryAsync(
            "SELECT $selectColumns FROM LocationEntity $where $order LIMIT ? OFFSET ?",
            ::mapCursorToMap,
            *paramList.toTypedArray(),
            pageSize,
            offset
        )

        return PagedList.createOptimized(items, totalCount.toInt(), pageNumber, pageSize)
    }

    override suspend fun getActiveProjectedAsync(
        selectColumns: String,
        additionalWhere: String?,
        parameters: Map<String, Any>?
    ): List<Map<String, Any?>> {
        val additionalFilter = if (additionalWhere.isNullOrBlank()) "" else "AND $additionalWhere"
        val paramList = parameters?.values?.toList() ?: emptyList()

        return context.queryAsync(
            "SELECT $selectColumns FROM LocationEntity WHERE isDeleted = 0 $additionalFilter ORDER BY timestamp DESC",
            ::mapCursorToMap,
            *paramList.toTypedArray()
        )
    }

    override suspend fun getNearbyProjectedAsync(
        latitude: Double,
        longitude: Double,
        distanceKm: Double,
        selectColumns: String
    ): List<Map<String, Any?>> {
        val latRange = distanceKm / 111.0
        val lngRange = distanceKm / (111.0 * cos(latitude * PI / 180))

        return context.queryAsync(
            """
               SELECT $selectColumns FROM LocationEntity 
               WHERE latitude BETWEEN ? AND ? 
               AND longitude BETWEEN ? AND ?
               AND isDeleted = 0
               ORDER BY timestamp DESC
           """,
            ::mapCursorToMap,
            latitude - latRange,
            latitude + latRange,
            longitude - lngRange,
            longitude + lngRange
        )
    }

    override suspend fun getByIdProjectedAsync(id: Int, selectColumns: String): Map<String, Any?>? {
        return context.querySingleAsync(
            "SELECT $selectColumns FROM LocationEntity WHERE id = ?",
            ::mapCursorToMap,
            id
        )
    }

    override suspend fun getBySpecificationAsync(specification: ISqliteSpecification<Location>): List<Location> {
        val sql = buildSpecificationQuery("*", specification)
        val paramList = specification.parameters.values.toList()

        return context.queryAsync(sql, ::mapToLocation, *paramList.toTypedArray())
    }

    override suspend fun getPagedBySpecificationAsync(
        specification: ISqliteSpecification<Location>,
        pageNumber: Int,
        pageSize: Int,
        selectColumns: String
    ): PagedList<Map<String, Any?>> {
        val paramList = specification.parameters.values.toList()

        val countSql = buildSpecificationQuery("COUNT(*)", specification, includePaging = false)
        val total = context.queryScalarAsync<Long>(countSql, *paramList.toTypedArray()) ?: 0

        val dataSql = buildSpecificationQuery(selectColumns, specification, includePaging = true, pageNumber, pageSize)
        val items = context.queryAsync(dataSql, ::mapCursorToMap, *paramList.toTypedArray())

        return PagedList.createOptimized(items, total.toInt(), pageNumber, pageSize)
    }

    override suspend fun createBulkAsync(locations: List<Location>): List<Location> {
        if (locations.isEmpty()) return emptyList()

        locations.forEach { location ->
            if (!LocationValidationRules.isValid(location)) {
                throw IllegalArgumentException("Location validation failed for: ${location.title}")
            }
        }

        return context.withTransactionAsync {
            locations.map { location ->
                addAsync(location)
            }
        }
    }

    override suspend fun updateBulkAsync(locations: List<Location>): Int {
        if (locations.isEmpty()) return 0

        locations.forEach { location ->
            if (!LocationValidationRules.isValid(location)) {
                throw IllegalArgumentException("Location validation failed for: ${location.title}")
            }
        }

        return context.withTransactionAsync {
            var updated = 0
            locations.forEach { location ->
                updateAsync(location)
                updated++
            }
            updated
        }
    }

    override suspend fun countAsync(whereClause: String?, parameters: Map<String, Any>?): Int {
        val where = if (whereClause.isNullOrEmpty()) "" else "WHERE $whereClause"
        val paramList = parameters?.values?.toList() ?: emptyList()

        val count = context.queryScalarAsync<Long>(
            "SELECT COUNT(*) FROM LocationEntity $where",
            *paramList.toTypedArray()
        ) ?: 0

        return count.toInt()
    }

    override suspend fun existsAsync(whereClause: String, parameters: Map<String, Any>): Boolean {
        val paramList = parameters.values.toList()

        val result = context.queryScalarAsync<Long>(
            "SELECT EXISTS(SELECT 1 FROM LocationEntity WHERE $whereClause)",
            *paramList.toTypedArray()
        ) ?: 0

        return result > 0
    }

    override suspend fun existsByIdAsync(id: Int): Boolean {
        val result = context.queryScalarAsync<Long>(
            "SELECT EXISTS(SELECT 1 FROM LocationEntity WHERE id = ?)",
            id
        ) ?: 0

        return result > 0
    }

    override suspend fun executeQueryAsync(sql: String, parameters: Map<String, Any>?): List<Map<String, Any?>> {
        val paramList = parameters?.values?.toList() ?: emptyList()

        return context.queryAsync(sql, ::mapCursorToMap, *paramList.toTypedArray())
    }

    override suspend fun executeCommandAsync(sql: String, parameters: Map<String, Any>?): Int {
        val paramList = parameters?.values?.toList() ?: emptyList()
        return context.executeAsync(sql, *paramList.toTypedArray())
    }

    private fun setLocationProperties(location: Location, id: Int, timestamp: Long, isDeleted: Boolean, photoPath: String?) {
        try {
            // Use reflection to set internal properties
            val setIdMethod = location::class.java.getDeclaredMethod("setId", Int::class.java)
            setIdMethod.isAccessible = true
            setIdMethod.invoke(location, id)

            val setTimestampMethod = location::class.java.getDeclaredMethod("setTimestamp", Instant::class.java)
            setTimestampMethod.isAccessible = true
            setTimestampMethod.invoke(location, Instant.fromEpochMilliseconds(timestamp))

            val setDeletedMethod = location::class.java.getDeclaredMethod("setDeleted", Boolean::class.java)
            setDeletedMethod.isAccessible = true
            setDeletedMethod.invoke(location, isDeleted)

            if (photoPath != null) {
                val setPhotoPathMethod = location::class.java.getDeclaredMethod("setPhotoPath", String::class.java)
                setPhotoPathMethod.isAccessible = true
                setPhotoPathMethod.invoke(location, photoPath)
            }
        } catch (e: Exception) {
            // Log warning if needed
        }
    }

    private fun mapToLocation(cursor: Any): Location {
        val sqlCursor = cursor as SqlCursor
        val coordinate = Coordinate(
            latitude = sqlCursor.getDouble(3) ?: 0.0,
            longitude = sqlCursor.getDouble(4) ?: 0.0
        )
        val address = Address(
            city = sqlCursor.getString(5) ?: "",
            state = sqlCursor.getString(6) ?: ""
        )

        val location = Location(
            title = sqlCursor.getString(1) ?: "",
            description = sqlCursor.getString(2) ?: "",
            coordinate = coordinate,
            address = address
        )

        setLocationProperties(
            location = location,
            id = sqlCursor.getLong(0)?.toInt() ?: 0,
            timestamp = sqlCursor.getLong(8) ?: 0L,
            isDeleted = (sqlCursor.getLong(7) ?: 0) == 1L,
            photoPath = sqlCursor.getString(7)
        )

        return location
    }

    private fun mapCursorToMap(cursor: Any): Map<String, Any?> {
        val sqlCursor = cursor as SqlCursor
        val result = mutableMapOf<String, Any?>()

        // This is a simplified mapping - in real implementation, we'd need column names
        // For now, we'll use the standard LocationEntity column structure
        result["id"] = sqlCursor.getLong(0)?.toInt()
        result["title"] = sqlCursor.getString(1)
        result["description"] = sqlCursor.getString(2)
        result["latitude"] = sqlCursor.getDouble(3)
        result["longitude"] = sqlCursor.getDouble(4)
        result["city"] = sqlCursor.getString(5)
        result["state"] = sqlCursor.getString(6)
        result["photoPath"] = sqlCursor.getString(7)
        result["isDeleted"] = sqlCursor.getLong(8)?.let { it == 1L }
        result["timestamp"] = sqlCursor.getLong(9)

        return result
    }

    private fun buildSpecificationQuery(
        selectColumns: String,
        specification: ISqliteSpecification<Location>,
        includePaging: Boolean = false,
        pageNumber: Int = 1,
        pageSize: Int = 10
    ): String {
        return buildString {
            append("SELECT $selectColumns FROM LocationEntity")

            if (!specification.joins.isNullOrEmpty()) {
                append(" ${specification.joins}")
            }

            if (specification.whereClause.isNotEmpty()) {
                append(" WHERE ${specification.whereClause}")
            }

            if (!specification.orderBy.isNullOrEmpty()) {
                append(" ORDER BY ${specification.orderBy}")
            }

            if (includePaging) {
                val offset = (pageNumber - 1) * pageSize
                append(" LIMIT $pageSize OFFSET $offset")
            } else if (specification.take != null) {
                append(" LIMIT ${specification.take}")
                if (specification.skip != null) {
                    append(" OFFSET ${specification.skip}")
                }
            }
        }
    }
}

fun Location.toSummaryDto() = mapOf(
    "id" to id,
    "title" to title,
    "city" to address.city,
    "state" to address.state
)

fun Location.toCoordinateDto() = mapOf(
    "id" to id,
    "latitude" to coordinate.latitude,
    "longitude" to coordinate.longitude
)

fun Location.toListItemDto() = mapOf(
    "id" to id,
    "title" to title,
    "description" to description,
    "city" to address.city,
    "state" to address.state,
    "photoPath" to photoPath,
    "timestamp" to timestamp.toEpochMilliseconds()
)
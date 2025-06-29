// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/persistence/LocationPersistenceRepository.kt

package com.x3squaredcircles.pixmap.shared.infrastructure.persistence

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.rules.LocationValidationRules
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import com.x3squaredcircles.pixmap.shared.infrastructure.database.IDatabaseContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.cos
import kotlin.math.PI

class LocationPersistenceRepository(
    private val context: IDatabaseContext
) : ILocationPersistenceRepository {

    override suspend fun getByIdAsync(id: Int): Location? {
        return context.executeQuerySingle(
            sql = "SELECT * FROM LocationEntity WHERE id = ?",
            parameters = listOf(id)
        ) { cursor ->
            mapToLocation(cursor)
        }
    }

    override suspend fun getAllAsync(): List<Location> {
        return context.executeQuery(
            sql = "SELECT * FROM LocationEntity ORDER BY timestamp DESC"
        ) { cursor ->
            mapToLocation(cursor)
        }
    }

    override suspend fun getActiveAsync(): List<Location> {
        return context.executeQuery(
            sql = "SELECT * FROM LocationEntity WHERE isDeleted = 0 ORDER BY timestamp DESC"
        ) { cursor ->
            mapToLocation(cursor)
        }
    }

    override suspend fun addAsync(location: Location): Location {
        if (!LocationValidationRules.isValid(location)) {
            throw IllegalArgumentException("Location validation failed")
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val id = context.executeNonQuery(
            sql = """
               INSERT INTO LocationEntity 
               (title, description, latitude, longitude, city, state, photoPath, isDeleted, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
           """,
            parameters = listOf(
                location.title,
                location.description,
                location.coordinate.latitude,
                location.coordinate.longitude,
                location.address.city,
                location.address.state,
                location.photoPath,
                if (location.isDeleted) 1 else 0,
                timestamp
            )
        )

        return Location.create(
            id = id.toInt(),
            title = location.title,
            description = location.description,
            coordinate = location.coordinate,
            address = location.address,
            photoPath = location.photoPath,
            isDeleted = location.isDeleted,
            timestamp = Instant.fromEpochMilliseconds(timestamp)
        )
    }

    override suspend fun updateAsync(location: Location) {
        if (!LocationValidationRules.isValid(location)) {
            throw IllegalArgumentException("Location validation failed")
        }

        context.executeNonQuery(
            sql = """
               UPDATE LocationEntity 
               SET title = ?, description = ?, latitude = ?, longitude = ?, 
                   city = ?, state = ?, photoPath = ?, isDeleted = ?
               WHERE id = ?
           """,
            parameters = listOf(
                location.title,
                location.description,
                location.coordinate.latitude,
                location.coordinate.longitude,
                location.address.city,
                location.address.state,
                location.photoPath,
                if (location.isDeleted) 1 else 0,
                location.id
            )
        )
    }

    override suspend fun deleteAsync(location: Location) {
        context.executeNonQuery(
            sql = "DELETE FROM LocationEntity WHERE id = ?",
            parameters = listOf(location.id)
        )
    }

    override suspend fun getByTitleAsync(title: String): Location? {
        return context.executeQuerySingle(
            sql = "SELECT * FROM LocationEntity WHERE title = ?",
            parameters = listOf(title)
        ) { cursor ->
            mapToLocation(cursor)
        }
    }

    override suspend fun getNearbyAsync(latitude: Double, longitude: Double, distanceKm: Double): List<Location> {
        val latRange = distanceKm / 111.0
        val lngRange = distanceKm / (111.0 * cos(latitude * PI / 180.0))

        val locations = context.executeQuery(
            sql = """
               SELECT * FROM LocationEntity 
               WHERE isDeleted = 0 
               AND latitude BETWEEN ? AND ? 
               AND longitude BETWEEN ? AND ?
               ORDER BY timestamp DESC
           """,
            parameters = listOf(
                latitude - latRange,
                latitude + latRange,
                longitude - lngRange,
                longitude + lngRange
            )
        ) { cursor ->
            mapToLocation(cursor)
        }

        val centerCoordinate = Coordinate(latitude, longitude)
        return locations.filter { location ->
            location.coordinate.distanceTo(centerCoordinate) <= distanceKm
        }
    }

    override suspend fun getPagedAsync(
        pageNumber: Int,
        pageSize: Int,
        searchTerm: String?,
        includeDeleted: Boolean
    ): PagedList<Location> {
        val offset = (pageNumber - 1) * pageSize

        val whereClause = buildString {
            if (!includeDeleted) {
                append("isDeleted = 0")
            }

            if (!searchTerm.isNullOrWhiteSpace()) {
                if (isNotEmpty()) append(" AND ")
                append("(title LIKE ? OR description LIKE ? OR city LIKE ? OR state LIKE ?)")
            }
        }

        val parameters = mutableListOf<Any>()
        if (!searchTerm.isNullOrWhiteSpace()) {
            val pattern = "%$searchTerm%"
            parameters.addAll(listOf(pattern, pattern, pattern, pattern))
        }

        val countSql = "SELECT COUNT(*) FROM LocationEntity" +
                if (whereClause.isNotEmpty()) " WHERE $whereClause" else ""

        val total = context.executeScalar(countSql, parameters) { it.toString().toInt() }

        val dataSql = "SELECT * FROM LocationEntity" +
                (if (whereClause.isNotEmpty()) " WHERE $whereClause" else "") +
                " ORDER BY timestamp DESC LIMIT ? OFFSET ?"

        val dataParameters = parameters + listOf(pageSize, offset)

        val items = context.executeQuery(dataSql, dataParameters) { cursor ->
            mapToLocation(cursor)
        }

        return PagedList.create(items, total, pageNumber, pageSize)
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
        val paramList = parameters?.values?.toList() ?: emptyList()

        val countSql = "SELECT COUNT(*) FROM LocationEntity" +
                if (!whereClause.isNullOrEmpty()) " WHERE $whereClause" else ""

        val total = context.executeScalar(countSql, paramList) { it.toString().toInt() }

        val dataSql = "SELECT $selectColumns FROM LocationEntity" +
                (if (!whereClause.isNullOrEmpty()) " WHERE $whereClause" else "") +
                (if (!orderBy.isNullOrEmpty()) " ORDER BY $orderBy" else " ORDER BY timestamp DESC") +
                " LIMIT ? OFFSET ?"

        val dataParameters = paramList + listOf(pageSize, offset)

        val items = context.executeQuery(dataSql, dataParameters) { cursor ->
            mapCursorToMap(cursor)
        }

        return PagedList.create(items, total, pageNumber, pageSize)
    }

    override suspend fun getActiveProjectedAsync(
        selectColumns: String,
        additionalWhere: String?,
        parameters: Map<String, Any>?
    ): List<Map<String, Any?>> {
        val whereClause = "isDeleted = 0" +
                if (!additionalWhere.isNullOrEmpty()) " AND ($additionalWhere)" else ""

        val sql = "SELECT $selectColumns FROM LocationEntity WHERE $whereClause ORDER BY timestamp DESC"
        val paramList = parameters?.values?.toList() ?: emptyList()

        return context.executeQuery(sql, paramList) { cursor ->
            mapCursorToMap(cursor)
        }
    }

    override suspend fun getNearbyProjectedAsync(
        latitude: Double,
        longitude: Double,
        distanceKm: Double,
        selectColumns: String
    ): List<Map<String, Any?>> {
        val latRange = distanceKm / 111.0
        val lngRange = distanceKm / (111.0 * cos(latitude * PI / 180.0))

        return context.executeQuery(
            sql = """
               SELECT $selectColumns FROM LocationEntity 
               WHERE isDeleted = 0 
               AND latitude BETWEEN ? AND ? 
               AND longitude BETWEEN ? AND ?
               ORDER BY timestamp DESC
           """,
            parameters = listOf(
                latitude - latRange,
                latitude + latRange,
                longitude - lngRange,
                longitude + lngRange
            )
        ) { cursor ->
            mapCursorToMap(cursor)
        }
    }

    override suspend fun getByIdProjectedAsync(id: Int, selectColumns: String): Map<String, Any?>? {
        return context.executeQuerySingle(
            sql = "SELECT $selectColumns FROM LocationEntity WHERE id = ?",
            parameters = listOf(id)
        ) { cursor ->
            mapCursorToMap(cursor)
        }
    }

    override suspend fun getBySpecificationAsync(specification: ISqliteSpecification<Location>): List<Location> {
        val sql = buildSpecificationQuery("*", specification)
        val paramList = specification.parameters.values.toList()

        return context.executeQuery(sql, paramList) { cursor ->
            mapToLocation(cursor)
        }
    }

    override suspend fun getPagedBySpecificationAsync(
        specification: ISqliteSpecification<Location>,
        pageNumber: Int,
        pageSize: Int,
        selectColumns: String
    ): PagedList<Map<String, Any?>> {
        val paramList = specification.parameters.values.toList()

        val countSql = buildSpecificationQuery("COUNT(*)", specification, includePaging = false)
        val total = context.executeScalar(countSql, paramList) { it.toString().toInt() }

        val dataSql = buildSpecificationQuery(selectColumns, specification, includePaging = true, pageNumber, pageSize)
        val items = context.executeQuery(dataSql, paramList) { cursor ->
            mapCursorToMap(cursor)
        }

        return PagedList.create(items, total, pageNumber, pageSize)
    }

    override suspend fun createBulkAsync(locations: List<Location>): List<Location> {
        if (locations.isEmpty()) return locations

        locations.forEach { location ->
            if (!LocationValidationRules.isValid(location)) {
                throw IllegalArgumentException("Location validation failed for: ${location.title}")
            }
        }

        return context.executeInTransaction {
            val results = mutableListOf<Location>()
            locations.forEach { location ->
                val result = addAsync(location)
                results.add(result)
            }
            results
        }
    }

    override suspend fun updateBulkAsync(locations: List<Location>): Int {
        if (locations.isEmpty()) return 0

        locations.forEach { location ->
            if (!LocationValidationRules.isValid(location)) {
                throw IllegalArgumentException("Location validation failed for: ${location.title}")
            }
        }

        return context.executeInTransaction {
            var updated = 0
            locations.forEach { location ->
                updateAsync(location)
                updated++
            }
            updated
        }
    }

    override suspend fun countAsync(whereClause: String?, parameters: Map<String, Any>?): Int {
        val sql = "SELECT COUNT(*) FROM LocationEntity" +
                if (!whereClause.isNullOrEmpty()) " WHERE $whereClause" else ""

        val paramList = parameters?.values?.toList() ?: emptyList()

        return context.executeScalar(sql, paramList) { it.toString().toInt() }
    }

    override suspend fun existsAsync(whereClause: String, parameters: Map<String, Any>): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM LocationEntity WHERE $whereClause)"
        val paramList = parameters.values.toList()

        return context.executeScalar(sql, paramList) { it.toString().toInt() > 0 }
    }

    override suspend fun existsByIdAsync(id: Int): Boolean {
        return context.executeScalar(
            sql = "SELECT EXISTS(SELECT 1 FROM LocationEntity WHERE id = ?)",
            parameters = listOf(id)
        ) { it.toString().toInt() > 0 }
    }

    override suspend fun executeQueryAsync(sql: String, parameters: Map<String, Any>?): List<Map<String, Any?>> {
        val paramList = parameters?.values?.toList() ?: emptyList()

        return context.executeQuery(sql, paramList) { cursor ->
            mapCursorToMap(cursor)
        }
    }

    override suspend fun executeCommandAsync(sql: String, parameters: Map<String, Any>?): Int {
        val paramList = parameters?.values?.toList() ?: emptyList()
        return context.executeNonQuery(sql, paramList).toInt()
    }

    private fun mapToLocation(cursor: com.x3squaredcircles.pixmap.shared.infrastructure.database.SqlCursor): Location {
        return Location.create(
            id = cursor.getInt(0) ?: 0,
            title = cursor.getString(1) ?: "",
            description = cursor.getString(2) ?: "",
            coordinate = Coordinate(
                latitude = cursor.getDouble(3) ?: 0.0,
                longitude = cursor.getDouble(4) ?: 0.0
            ),
            address = Address(
                city = cursor.getString(5) ?: "",
                state = cursor.getString(6) ?: ""
            ),
            photoPath = cursor.getString(7),
            isDeleted = cursor.getBoolean(8) ?: false,
            timestamp = Instant.fromEpochMilliseconds(cursor.getLong(9) ?: 0L)
        )
    }

    private fun mapCursorToMap(cursor: com.x3squaredcircles.pixmap.shared.infrastructure.database.SqlCursor): Map<String, Any?> {
        val columnNames = cursor.getColumnNames()
        val result = mutableMapOf<String, Any?>()

        columnNames.forEachIndexed { index, columnName ->
            result[columnName] = when {
                cursor.getString(index) != null -> cursor.getString(index)
                cursor.getLong(index) != null -> cursor.getLong(index)
                cursor.getDouble(index) != null -> cursor.getDouble(index)
                cursor.getBoolean(index) != null -> cursor.getBoolean(index)
                else -> null
            }
        }

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
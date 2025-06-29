package com.x3squaredcircles.pixmap.shared.domain.repositories

import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Repository interface for location aggregate operations
 */
interface ILocationRepository {
    suspend fun getById(id: Int): Location?
    suspend fun getAll(): List<Location>
    suspend fun getByCoordinate(coordinate: Coordinate, radiusKm: Double): List<Location>
    suspend fun save(location: Location): Location
    suspend fun delete(id: Int)
    suspend fun exists(id: Int): Boolean
}
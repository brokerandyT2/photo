package com.x3squaredcircles.pixmap.shared.domain.repositories

import com.x3squaredcircles.pixmap.shared.domain.entities.Weather
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Repository interface for weather aggregate operations
 */
interface IWeatherRepository {
    suspend fun getByLocationId(locationId: Int): Weather?
    suspend fun getByCoordinate(coordinate: Coordinate): Weather?
    suspend fun save(weather: Weather): Weather
    suspend fun delete(locationId: Int)
    suspend fun exists(locationId: Int): Boolean
}
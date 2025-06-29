package com.x3squaredcircles.pixmap.shared.services

import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Interface for location services
 */
interface ILocationService {
    suspend fun getCurrentLocation(): Result<Coordinate>
    suspend fun getLastKnownLocation(): Result<Coordinate>
    fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
    fun isLocationEnabled(): Boolean
}
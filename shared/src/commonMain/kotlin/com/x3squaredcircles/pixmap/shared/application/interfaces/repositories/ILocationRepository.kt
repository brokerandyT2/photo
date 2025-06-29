// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/ILocationRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Repository interface for Location operations
 */
interface ILocationRepository {
    suspend fun getByIdAsync(id: Int): Result<Location?>
    suspend fun getAllAsync(): Result<List<Location>>
    suspend fun getActiveAsync(): Result<List<Location>>
    suspend fun createAsync(location: Location): Result<Location>
    suspend fun updateAsync(location: Location): Result<Location>
    suspend fun deleteAsync(id: Int): Result<Boolean>
    suspend fun getByTitleAsync(title: String): Result<Location?>
    suspend fun getNearbyAsync(latitude: Double, longitude: Double, distanceKm: Double): Result<List<Location>>
    suspend fun getPagedAsync(
        pageNumber: Int,
        pageSize: Int,
        searchTerm: String? = null,
        includeDeleted: Boolean = false
    ): Result<PagedList<Location>>
}
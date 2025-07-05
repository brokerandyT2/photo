// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/LocationRepositoryAdapter.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Adapter that bridges Application ILocationRepository to Infrastructure LocationRepository
 */
class LocationRepositoryAdapter(
    private val innerRepository: LocationRepository
) : ILocationRepository {

    override suspend fun getByIdAsync(id: Int): Result<Location?> {
        return innerRepository.getByIdAsync(id)
    }

    override suspend fun getAllAsync(): Result<List<Location>> {
        return innerRepository.getAllAsync()
    }

    override suspend fun getActiveAsync(): Result<List<Location>> {
        return innerRepository.getActiveAsync()
    }

    override suspend fun createAsync(location: Location): Result<Location> {
        return innerRepository.createAsync(location)
    }

    override suspend fun updateAsync(location: Location): Result<Location> {
        return innerRepository.updateAsync(location)
    }

    override suspend fun deleteAsync(id: Int): Result<Boolean> {
        return innerRepository.deleteAsync(id)
    }

    override suspend fun getByTitleAsync(title: String): Result<Location?> {
        return innerRepository.getByTitleAsync(title)
    }

    override suspend fun getNearbyAsync(
        latitude: Double,
        longitude: Double,
        distanceKm: Double
    ): Result<List<Location>> {
        return innerRepository.getNearbyAsync(latitude, longitude, distanceKm)
    }

    override suspend fun getPagedAsync(
        pageNumber: Int,
        pageSize: Int,
        searchTerm: String?,
        includeDeleted: Boolean
    ): Result<PagedList<Location>> {
        return innerRepository.getPagedAsync(pageNumber, pageSize, searchTerm, includeDeleted)
    }
}
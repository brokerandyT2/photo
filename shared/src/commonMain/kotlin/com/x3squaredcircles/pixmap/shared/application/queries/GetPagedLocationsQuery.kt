// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetPagedLocationsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.common.models.PagedList
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository

import com.x3squaredcircles.pixmap.shared.domain.entities.Location

data class GetPagedLocationsQuery(
    val pageNumber: Int = 1,
    val pageSize: Int = 10,
    val searchTerm: String? = null,
    val includeDeleted: Boolean = false
) : IRequest<PagedList<LocationDto>>
/**
 * Handler for GetPagedLocationsQuery
 */
class GetPagedLocationsQueryHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<GetPagedLocationsQuery, PagedList<LocationDto>> {

    override suspend fun handle(request: GetPagedLocationsQuery): PagedList<LocationDto> {
        val result = locationRepository.getPagedAsync(
            request.pageNumber,
            request.pageSize,
            request.searchTerm,
            request.includeDeleted
        )

        if (!result.isSuccess) {
            throw RuntimeException("Failed to get paged locations: ${result.errorMessage}")
        }

        val pagedLocations = result.data ?: PagedList.empty()
        return pagedLocations.map { location -> mapToDto(location) }
    }

    private fun mapToDto(location: Location): LocationDto {
        return LocationDto(
            id = location.id,
            title = location.title,
            description = location.description,
            latitude = location.coordinate.latitude,
            longitude = location.coordinate.longitude,
            city = location.address.city,
            state = location.address.state,
            photoPath = location.photoPath,
            isDeleted = location.isDeleted,
            timestamp = location.timestamp
        )
    }
}
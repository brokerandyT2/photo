//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetActiveLocationsQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Query to get active locations
 */
class GetActiveLocationsQuery : IRequest<List<LocationDto>>

/**
 * Handler for GetActiveLocationsQuery
 */
class GetActiveLocationsQueryHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<GetActiveLocationsQuery, List<LocationDto>> {

    override suspend fun handle(request: GetActiveLocationsQuery): List<LocationDto> {
        val result = locationRepository.getActiveAsync()

        return if (result.isSuccess) {
            result.data?.map { location -> mapToDto(location) } ?: emptyList()
        } else {
            throw RuntimeException(result.errorMessage ?: "Failed to get active locations")
        }
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
// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetNearbyLocationsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetNearbyLocationsQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Handler for GetNearbyLocationsQuery
 */
class GetNearbyLocationsQueryHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<GetNearbyLocationsQuery, List<LocationDto>> {

    override suspend fun handle(request: GetNearbyLocationsQuery): List<LocationDto> {
        val result = locationRepository.getNearbyAsync(
            request.latitude,
            request.longitude,
            request.distanceKm
        )

        return if (result.isSuccess) {
            result.getOrNull()?.map { location -> mapToDto(location) } ?: emptyList()
        } else {
            throw RuntimeException(result.getOrNull()?.toString() ?: "Failed to get nearby locations")
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
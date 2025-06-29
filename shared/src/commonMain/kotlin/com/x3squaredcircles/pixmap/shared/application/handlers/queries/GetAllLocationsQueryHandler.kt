// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetAllLocationsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllLocationsQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Handler for GetAllLocationsQuery
 */
class GetAllLocationsQueryHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<GetAllLocationsQuery, List<LocationDto>> {

    override suspend fun handle(request: GetAllLocationsQuery): List<LocationDto> {
        val result = locationRepository.getAllAsync()

        return if (result.isSuccess) {
            result.getOrNull()?.map { location -> mapToDto(location) } ?: emptyList()
        } else {
            throw RuntimeException(result.getOrNull()?.toString() ?: "Failed to get locations")
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
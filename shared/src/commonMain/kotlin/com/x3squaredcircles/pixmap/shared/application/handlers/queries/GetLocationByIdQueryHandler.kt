// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetLocationByIdQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationByIdQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Handler for GetLocationByIdQuery
 */
class GetLocationByIdQueryHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<GetLocationByIdQuery, LocationDto?> {

    override suspend fun handle(request: GetLocationByIdQuery): LocationDto? {
        val result = locationRepository.getByIdAsync(request.id)

        return if (result.isSuccess) {
            result.getOrNull()?.let { location -> mapToDto(location) }
        } else {
            throw RuntimeException(result.getOrNull()?.toString() ?: "Failed to get location")
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
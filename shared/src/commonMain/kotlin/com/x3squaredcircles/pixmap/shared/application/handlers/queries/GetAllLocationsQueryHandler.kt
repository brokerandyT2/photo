//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetAllLocationsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Query to get all locations
 */
class GetAllLocationsQuery : IRequest<List<LocationDto>>

/**
 * Handler for GetAllLocationsQuery
 */
class GetAllLocationsQueryHandler(
    private val locationRepository: ILocationRepository,
    private val mediator: IMediator
) : IRequestHandler<GetAllLocationsQuery, List<LocationDto>> {

    override suspend fun handle(request: GetAllLocationsQuery): List<LocationDto> {
        try {
            val result = locationRepository.getAllAsync()

            if (!result.isSuccess) {
                throw RuntimeException("Failed to retrieve locations: ${result.errorMessage}")
            }

            val locations = result.data ?: emptyList()

            if (locations.isEmpty()) {
                return emptyList()
            }

            return locations.map { location ->
                LocationDto(
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
        } catch (ex: Exception) {
            throw RuntimeException("Failed to get all locations: ${ex.message}", ex)
        }
    }
}
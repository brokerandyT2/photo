//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetNearbyLocationsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationListDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository

/**
 * Query to get nearby locations
 */
data class GetNearbyLocationsQuery(
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double = 10.0
) : IRequest<List<LocationListDto>>

/**
 * Handler for GetNearbyLocationsQuery
 */
class GetNearbyLocationsQueryHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<GetNearbyLocationsQuery, List<LocationListDto>> {

    override suspend fun handle(request: GetNearbyLocationsQuery): List<LocationListDto> {
        val result = locationRepository.getNearbyAsync(
            latitude = request.latitude,
            longitude = request.longitude,
            distanceKm = request.distanceKm
        )

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve nearby locations: ${result.errorMessage}")
        }

        val locations = result.data ?: emptyList()

        return locations.map { location ->
            LocationListDto(
                id = location.id,
                title = location.title,
                latitude = location.coordinate.latitude,
                longitude = location.coordinate.longitude,
                city = location.address.city,
                state = location.address.state,
                photoPath = location.photoPath,
                timestamp = location.timestamp,
                isDeleted = location.isDeleted
            )
        }
    }
}
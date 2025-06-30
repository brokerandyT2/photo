// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetLocationByIdQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.LocationNotFoundEvent
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetLocationByIdQuery

/**
 * Handler for GetLocationByIdQuery
 */
class GetLocationByIdQueryHandler(
    private val locationRepository: ILocationRepository,
    private val mediator: IMediator
) : IRequestHandler<GetLocationByIdQuery, LocationDto?> {

    override suspend fun handle(request: GetLocationByIdQuery): LocationDto? {
        try {
            val locationResult = locationRepository.getByIdAsync(request.id)

            if (!locationResult.isSuccess) {
                throw RuntimeException("Failed to retrieve location: ${locationResult.exceptionOrNull()?.message}")
            }

            val location = locationResult.getOrNull()

            if (location == null) {
                mediator.publish(
                    LocationNotFoundEvent(
                        locationId = request.id,
                        source = "GetLocationByIdQueryHandler"
                    )
                )
                return null
            }

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
        } catch (ex: Exception) {
            mediator.publish(
                LocationNotFoundEvent(
                    locationId = request.id,
                    source = "GetLocationByIdQueryHandler",
                    error = ex.message
                )
            )
            throw RuntimeException("Failed to get location by ID: ${ex.message}", ex)
        }
    }
}
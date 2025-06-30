// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/RestoreLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.RestoreLocationCommand
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository

/**
 * Handler for RestoreLocationCommand
 */
class RestoreLocationCommandHandler(
    private val locationRepository: ILocationRepository,
    private val mediator: IMediator
) : IRequestHandler<RestoreLocationCommand, LocationDto> {

    override suspend fun handle(request: RestoreLocationCommand): LocationDto {
        try {
            val locationResult = locationRepository.getByIdAsync(request.locationId)

            if (!locationResult.isSuccess || locationResult.getOrNull() == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        "Location ID ${request.locationId}",
                        LocationErrorType.DATABASE_ERROR,
                        "Location not found"
                    )
                )
                throw IllegalArgumentException("Location not found")
            }

            val location = locationResult.getOrThrow()
            location.restore()

            val updateResult = locationRepository.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        location.title,
                        LocationErrorType.DATABASE_ERROR,
                        updateResult.exceptionOrNull()?.message ?: "Update failed"
                    )
                )
                throw RuntimeException("Location update failed")
            }

            return mapToDto(location)
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    "Location ID ${request.locationId}",
                    LocationErrorType.NETWORK_ERROR,
                    ex.message ?: "Restore operation failed"
                )
            )
            throw RuntimeException("Failed to restore location: ${ex.message}", ex)
        }
    }

    private fun mapToDto(location: com.x3squaredcircles.pixmap.shared.domain.entities.Location): LocationDto {
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
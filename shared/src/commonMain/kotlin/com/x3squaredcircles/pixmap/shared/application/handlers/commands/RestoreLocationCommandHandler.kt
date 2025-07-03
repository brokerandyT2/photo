//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/RestoreLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.RestoreLocationCommand
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
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
) : IRequestHandler<RestoreLocationCommand, Result<LocationDto>> {

    override suspend fun handle(request: RestoreLocationCommand): Result<LocationDto> {
        return try {
            val locationResult = locationRepository.getByIdAsync(request.locationId)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        "Location ID ${request.locationId}",
                        LocationErrorType.DatabaseError,
                        "Location not found"
                    )
                )
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!
            location.restore()

            val updateResult = locationRepository.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        location.title,
                        LocationErrorType.DatabaseError,
                        updateResult.errorMessage ?: "Update failed"
                    )
                )
                return Result.failure("Failed to update location")
            }

            Result.success(mapToDto(location))
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    "Location ID ${request.locationId}",
                    LocationErrorType.NetworkError,
                    ex.message ?: "Restore operation failed"
                )
            )
            Result.failure("Failed to restore location: ${ex.message}")
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
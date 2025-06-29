// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/AttachPhotoCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.AttachPhotoCommand
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location

/**
 * Handler for AttachPhotoCommand
 */
class AttachPhotoCommandHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<AttachPhotoCommand, LocationDto> {

    override suspend fun handle(request: AttachPhotoCommand): LocationDto {
        val existingResult = locationRepository.getByIdAsync(request.locationId)

        if (!existingResult.isSuccess) {
            throw RuntimeException("Failed to get location: ${existingResult}")
        }

        val location = existingResult.getOrNull()
            ?: throw IllegalArgumentException("Location with ID ${request.locationId} not found")

        location.attachPhoto(request.photoPath)

        val result = locationRepository.updateAsync(location)

        return if (result.isSuccess) {
            val updatedLocation = result.getOrThrow()
            mapToDto(updatedLocation)
        } else {
            throw RuntimeException("Failed to attach photo: ${result}")
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
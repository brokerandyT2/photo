// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/UpdateLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateLocationCommand
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.rules.LocationValidationRules
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Handler for UpdateLocationCommand
 */
class UpdateLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<UpdateLocationCommand, LocationDto> {

    override suspend fun handle(request: UpdateLocationCommand): LocationDto {
        val existingResult = locationRepository.getByIdAsync(request.id)

        if (!existingResult.isSuccess) {
            throw RuntimeException("Failed to get location: ${existingResult}")
        }

        val location = existingResult.getOrNull()
            ?: throw IllegalArgumentException("Location with ID ${request.id} not found")

        location.updateDetails(request.title, request.description)

        val newCoordinate = Coordinate.createValidated(request.latitude, request.longitude)
        location.updateCoordinate(newCoordinate)

        if (!request.photoPath.isNullOrBlank()) {
            location.attachPhoto(request.photoPath)
        } else if (request.photoPath == null && location.photoPath != null) {
            location.removePhoto()
        }

        val errors = mutableListOf<String>()
        if (!LocationValidationRules.isValid(location, errors)) {
            throw IllegalArgumentException("Validation failed: ${errors.joinToString(", ")}")
        }

        val result = locationRepository.updateAsync(location)

        return if (result.isSuccess) {
            val updatedLocation = result.getOrThrow()
            mapToDto(updatedLocation)
        } else {
            throw RuntimeException("Failed to update location: ${result}")
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
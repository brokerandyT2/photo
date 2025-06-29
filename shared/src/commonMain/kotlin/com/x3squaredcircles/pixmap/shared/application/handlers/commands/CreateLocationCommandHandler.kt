// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateLocationCommand
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.rules.LocationValidationRules
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Handler for CreateLocationCommand
 */
class CreateLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<CreateLocationCommand, LocationDto> {

    override suspend fun handle(request: CreateLocationCommand): LocationDto {
        val coordinate = Coordinate.createValidated(request.latitude, request.longitude)
        val address = Address(request.city, request.state)

        val location = Location(
            title = request.title,
            description = request.description,
            coordinate = coordinate,
            address = address
        )

        if (!request.photoPath.isNullOrBlank()) {
            location.attachPhoto(request.photoPath)
        }

        val errors = mutableListOf<String>()
        if (!LocationValidationRules.isValid(location, errors)) {
            throw IllegalArgumentException("Validation failed: ${errors.joinToString(", ")}")
        }

        val result = locationRepository.createAsync(location)

        return if (result.isSuccess) {
            val createdLocation = result.getOrThrow()
            mapToDto(createdLocation)
        } else {
            throw RuntimeException(result.getOrNull()?.toString() ?: "Failed to create location")
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
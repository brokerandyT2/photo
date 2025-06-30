// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/SaveLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.SaveLocationCommand
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Handler for SaveLocationCommand
 */
class SaveLocationCommandHandler(
    private val locationRepository: ILocationRepository,
    private val mediator: IMediator
) : IRequestHandler<SaveLocationCommand, LocationDto> {

    override suspend fun handle(request: SaveLocationCommand): LocationDto {
        try {
            val location: Location

            if (request.id != null) {
                // Update existing location
                val existingLocationResult = locationRepository.getByIdAsync(request.id)

                if (!existingLocationResult.isSuccess || existingLocationResult.getOrNull() == null) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.DATABASE_ERROR,
                            "Location not found"
                        )
                    )
                    throw IllegalArgumentException("Location not found")
                }

                location = existingLocationResult.getOrThrow()
                location.updateDetails(request.title, request.description)

                val newCoordinate = Coordinate.createValidated(request.latitude, request.longitude)
                location.updateCoordinate(newCoordinate)

                if (!request.photoPath.isNullOrBlank()) {
                    location.attachPhoto(request.photoPath)
                }

                val updateResult = locationRepository.updateAsync(location)
                if (!updateResult.isSuccess) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.DATABASE_ERROR,
                            updateResult.exceptionOrNull()?.message ?: "Update failed"
                        )
                    )
                    throw RuntimeException("Location update failed")
                }
            } else {
                // Create new location
                val coordinate = Coordinate.createValidated(request.latitude, request.longitude)
                val address = Address(request.city, request.state)

                location = Location(
                    title = request.title,
                    description = request.description,
                    coordinate = coordinate,
                    address = address
                )

                if (!request.photoPath.isNullOrBlank()) {
                    location.attachPhoto(request.photoPath)
                }

                val createResult = locationRepository.createAsync(location)
                if (!createResult.isSuccess) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.DATABASE_ERROR,
                            createResult.exceptionOrNull()?.message ?: "Create failed"
                        )
                    )
                    throw RuntimeException("Location creation failed")
                }
            }

            return mapToDto(location)
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "DUPLICATE_TITLE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.DUPLICATE_TITLE,
                            ex.message ?: "Duplicate title"
                        )
                    )
                    throw IllegalArgumentException("Duplicate location title: ${request.title}")
                }
                "INVALID_COORDINATES" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.INVALID_COORDINATES,
                            ex.message ?: "Invalid coordinates"
                        )
                    )
                    throw IllegalArgumentException("Invalid coordinates")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    request.title,
                    LocationErrorType.NETWORK_ERROR,
                    ex.message ?: "Save operation failed"
                )
            )
            throw RuntimeException("Failed to save location: ${ex.message}", ex)
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
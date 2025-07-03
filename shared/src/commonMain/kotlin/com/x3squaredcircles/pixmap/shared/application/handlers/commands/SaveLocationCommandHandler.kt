//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/SaveLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.SaveLocationCommand
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
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
) : IRequestHandler<SaveLocationCommand, Result<LocationDto>> {

    override suspend fun handle(request: SaveLocationCommand): Result<LocationDto> {
        return try {
            val location: Location

            if (request.id != null) {
                // Update existing location
                val existingLocationResult = locationRepository.getByIdAsync(request.id)

                if (!existingLocationResult.isSuccess || existingLocationResult.data == null) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.DatabaseError,
                            "Location not found"
                        )
                    )
                    return Result.failure("Location not found")
                }

                location = existingLocationResult.data!!
                location.updateDetails(request.title, request.description)

                val newCoordinate = Coordinate(request.latitude, request.longitude)
                location.updateCoordinate(newCoordinate)

                if (!request.photoPath.isNullOrBlank()) {
                    location.attachPhoto(request.photoPath)
                }

                val updateResult = locationRepository.updateAsync(location)
                if (!updateResult.isSuccess) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            request.title,
                            LocationErrorType.DatabaseError,
                            updateResult.errorMessage ?: "Update failed"
                        )
                    )
                    return Result.failure("Failed to update location")
                }
            } else {
                // Create new location
                val coordinate = Coordinate(request.latitude, request.longitude)
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
                            LocationErrorType.DatabaseError,
                            createResult.errorMessage ?: "Create failed"
                        )
                    )
                    return Result.failure("Failed to create location")
                }
            }

            Result.success(mapToDto(location))
        } catch (ex: LocationDomainException) {
            val errorType = when (ex.code) {
                "DUPLICATE_TITLE" -> LocationErrorType.ValidationError
                "INVALID_COORDINATES" -> LocationErrorType.ValidationError
                else -> LocationErrorType.DatabaseError
            }

            mediator.publish(
                LocationSaveErrorEvent(
                    request.title,
                    errorType,
                    ex.message ?: "Domain exception"
                )
            )
            Result.failure("Failed to save location: ${ex.message}")
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    request.title,
                    LocationErrorType.NetworkError,
                    ex.message ?: "Save operation failed"
                )
            )
            Result.failure("Failed to save location: ${ex.message}")
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
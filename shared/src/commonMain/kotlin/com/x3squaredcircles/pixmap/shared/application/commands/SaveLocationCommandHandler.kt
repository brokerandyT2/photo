//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/SaveLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException
import com.x3squaredcircles.pixmap.shared.application.mappers.LocationMapper
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.coroutines.CancellationException

/**
 * Represents a command to save a location with its associated details.
 *
 * This command is used to create or update a location record. If the [id] property
 * is null, a new location will be created. If [id] is provided, the existing location with the
 * specified identifier will be updated.
 */
data class SaveLocationCommand(
    val id: Int? = null,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val photoPath: String? = null
) : IRequest<Result<LocationDto>>

/**
 * Handles the execution of the [SaveLocationCommand] to create or update a location.
 *
 * This handler processes the [SaveLocationCommand] by either creating a new
 * location or updating an existing one, based on the presence of the [id] property in the command. It
 * validates the input, updates the location's details, and persists the changes to the database. If successful, it
 * returns a [Result] containing the updated or newly created [LocationDto]. Otherwise,
 * it returns a failure result with an appropriate error message.
 */
class SaveLocationCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<SaveLocationCommand, Result<LocationDto>> {

    /**
     * Handles the process of saving a location by either creating a new location or updating an existing one.
     *
     * If the [request] contains an existing location ID, the method
     * attempts to update the corresponding location. If the ID is not provided, a new location is created. The
     * method ensures that all changes are persisted to the database.
     *
     * @param request The command containing the location data to save.
     * @return A [Result] containing a [LocationDto] if the operation succeeds; otherwise, a
     * failure result with an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: SaveLocationCommand): Result<LocationDto> {
        return try {
            val location = if (request.id != null) {
                // Update existing location
                val locationResult = unitOfWork.locations.getByIdAsync(request.id)
                if (!locationResult.isSuccess || locationResult.data == null) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            errorMessage = "Location not found"
                        )
                    )
                    return Result.failure("Location not found")
                }

                val existingLocation = locationResult.data!!
                existingLocation.updateDetails(request.title, request.description)

                val coordinate = Coordinate.createValidated(request.latitude, request.longitude)
                existingLocation.updateCoordinate(coordinate)

                if (!request.photoPath.isNullOrBlank()) {
                    existingLocation.attachPhoto(request.photoPath)
                } else {
                    existingLocation.removePhoto()
                }

                val updateResult = unitOfWork.locations.updateAsync(existingLocation)
                if (!updateResult.isSuccess) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            errorMessage = updateResult.errorMessage ?: "Update failed"
                        )
                    )
                    return Result.failure("Failed to update location")
                }

                updateResult.data!!
            } else {
                // Create new location
                val coordinate = Coordinate.createValidated(request.latitude, request.longitude)
                val address = Address(request.city, request.state)
                val newLocation = Location(request.title, request.description, coordinate, address)

                if (!request.photoPath.isNullOrBlank()) {
                    newLocation.attachPhoto(request.photoPath)
                }

                val createResult = unitOfWork.locations.createAsync(newLocation)
                if (!createResult.isSuccess) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            errorMessage = createResult.errorMessage ?: "Create failed"
                        )
                    )
                    return Result.failure("Failed to create location")
                }

                createResult.data!!
            }

            unitOfWork.saveChangesAsync()

            val locationDto = LocationMapper.run { location.toDto() }
            Result.success(locationDto)
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "DUPLICATE_TITLE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = request.title,
                            errorType = LocationErrorType.ValidationError,
                            errorMessage = ex.message ?: "Duplicate title"
                        )
                    )
                    Result.failure("A location with this title already exists")
                }
                "INVALID_COORDINATE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = request.title,
                            errorType = LocationErrorType.ValidationError,
                            errorMessage = ex.message ?: "Invalid coordinates"
                        )
                    )
                    Result.failure("Invalid coordinate values provided")
                }
                else -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            errorMessage = ex.message ?: "Domain exception"
                        )
                    )
                    Result.failure("Failed to save location: ${ex.message ?: "Unknown error"}")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    locationTitle = request.title,
                    errorType = LocationErrorType.DatabaseError,
                    errorMessage = ex.message ?: "System exception"
                )
            )
            Result.failure("System error occurred: ${ex.message ?: "Unknown error"}")
        }
    }
}
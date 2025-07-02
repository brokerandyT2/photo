// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/SaveLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException
import com.x3squaredcircles.pixmap.shared.domain.mappers.LocationMapper
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate
import kotlinx.coroutines.cancellation.CancellationException

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
)

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
    private val mapper: LocationMapper,
    private val mediator: IMediator
) : IRequestHandler<SaveLocationCommand, LocationDto> {

    /**
     * Handles the process of saving a location by either creating a new location or updating an existing one.
     *
     * If the [request] contains an existing location ID, the method
     * attempts to update the corresponding location. If the ID is not provided, a new location is created. The
     * method ensures that all changes are persisted to the database.
     *
     * @param request The command containing the details of the location to save, including its title, description, coordinates,
     * and optional photo path.
     * @return A [Result] containing a [LocationDto] representing the saved location if the
     * operation is successful; otherwise, a failure result with an appropriate error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: SaveLocationCommand): Result<LocationDto> {
        return try {
            val location: Location

            if (request.id != null) {
                // Update existing location
                val existingLocationResult = unitOfWork.locations.getByIdAsync(request.id)
                if (!existingLocationResult.isSuccess || existingLocationResult.data == null) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            details = AppResources.locationErrorNotFound
                        )
                    )
                    return Result.failure(AppResources.locationErrorNotFound)
                }

                location = existingLocationResult.data
                location.updateDetails(request.title, request.description)

                val newCoordinate = Coordinate(request.latitude, request.longitude)
                location.updateCoordinate(newCoordinate)

                if (!request.photoPath.isNullOrEmpty()) {
                    location.attachPhoto(request.photoPath)
                }

                val updateResult = unitOfWork.locations.updateAsync(location)
                if (!updateResult.isSuccess) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            details = updateResult.errorMessage ?: "Update failed"
                        )
                    )
                    return Result.failure(AppResources.locationErrorUpdateFailed)
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

                if (!request.photoPath.isNullOrEmpty()) {
                    location.attachPhoto(request.photoPath)
                }

                val createResult = unitOfWork.locations.createAsync(location)
                if (!createResult.isSuccess || createResult.data == null) {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = request.title,
                            errorType = LocationErrorType.DatabaseError,
                            details = createResult.errorMessage ?: "Create failed"
                        )
                    )
                    return Result.failure(AppResources.locationErrorCreateFailed)
                }
            }

            unitOfWork.saveChangesAsync()

            val locationDto = mapper.toDto(location)
            Result.success(locationDto)
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "DUPLICATE_TITLE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = request.title,
                            errorType = LocationErrorType.DuplicateTitle,
                            details = null
                        )
                    )
                    Result.failure(AppResources.getLocationErrorDuplicateTitle(request.title))
                }
                "INVALID_COORDINATES" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = request.title,
                            errorType = LocationErrorType.InvalidCoordinates,
                            details = null
                        )
                    )
                    Result.failure(AppResources.locationErrorInvalidCoordinates)
                }
                else -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = request.title,
                            errorType = LocationErrorType.NetworkError,
                            details = ex.message
                        )
                    )
                    Result.failure(AppResources.getLocationErrorSaveFailed(ex.message ?: "Domain exception"))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    message = request.title,
                    errorType = LocationErrorType.NetworkError,
                    details = ex.message
                )
            )
            Result.failure(AppResources.getLocationErrorSaveFailed(ex.message ?: "Unknown error"))
        }
    }
}
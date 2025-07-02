//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/RemovePhotoCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.mappers.LocationMapper
import kotlinx.coroutines.CancellationException

/**
 * Command to remove a photo from a location.
 */
data class RemovePhotoCommand(
    val locationId: Int
) : IRequest<Result<LocationDto>>

/**
 * Handles the removal of a photo from a location and updates the location's state in the data store.
 *
 * This handler processes a [RemovePhotoCommand] to remove a photo associated with a
 * specific location. It retrieves the location, removes the photo, updates the location in the data store, and
 * saves the changes. If the operation is successful, the updated location is returned as a [LocationDto].
 */
class RemovePhotoCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<RemovePhotoCommand, Result<LocationDto>> {

    /**
     * Handles the removal of a photo from a location and updates the location in the data store.
     *
     * @param request The command containing the location ID from which to remove the photo.
     * @return A [Result] containing a [LocationDto] if the operation succeeds; otherwise, a
     * failure result with an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: RemovePhotoCommand): Result<LocationDto> {
        return try {
            val locationResult = unitOfWork.locations.getByIdAsync(request.locationId)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        locationTitle = "Location ID ${request.locationId}",
                        errorType = LocationErrorType.DatabaseError,
                        errorMessage = "Location not found"
                    )
                )
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!
            location.removePhoto()

            val updateResult = unitOfWork.locations.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        locationTitle = location.title,
                        errorType = LocationErrorType.DatabaseError,
                        errorMessage = updateResult.errorMessage ?: "Update failed"
                    )
                )
                return Result.failure("Failed to update location")
            }

            unitOfWork.saveChangesAsync()

            val locationDto = LocationMapper.run { location.toDto() }
            Result.success(locationDto)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    locationTitle = "Location ID ${request.locationId}",
                    errorType = LocationErrorType.DatabaseError,
                    errorMessage = ex.message ?: "System exception"
                )
            )
            Result.failure("System error occurred: ${ex.message ?: "Unknown error"}")
        }
    }
}
// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/RemovePhotoCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.errors.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.errors.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.mappers.LocationMapper
import kotlinx.coroutines.cancellation.CancellationException

/**
 * Command to remove a photo from a location.
 */
data class RemovePhotoCommand(
    val locationId: Int
)

/**
 * Handles the removal of a photo from a location and updates the location's state in the data store.
 *
 * This handler processes a [RemovePhotoCommand] to remove a photo associated with a
 * specific location. It retrieves the location, removes the photo, updates the location in the data store, and
 * saves the changes. If the operation is successful, the updated location is returned as a [LocationDto].
 */
class RemovePhotoCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mapper: LocationMapper,
    private val mediator: IMediator
) : IRequestHandler<RemovePhotoCommand, LocationDto> {

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
                        message = AppResources.getLocationErrorNotFoundById(request.locationId),
                        errorType = LocationErrorType.DatabaseError,
                        details = AppResources.locationErrorNotFound
                    )
                )
                return Result.failure(AppResources.locationErrorNotFound)
            }

            val location = locationResult.data
            location.removePhoto()

            val updateResult = unitOfWork.locations.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        message = location.title,
                        errorType = LocationErrorType.DatabaseError,
                        details = updateResult.errorMessage ?: "Update failed"
                    )
                )
                return Result.failure(AppResources.locationErrorUpdateFailed)
            }

            unitOfWork.saveChangesAsync()

            val locationDto = mapper.toDto(location)
            Result.success(locationDto)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    message = AppResources.getLocationErrorNotFoundById(request.locationId),
                    errorType = LocationErrorType.NetworkError,
                    details = ex.message ?: "Unknown error"
                )
            )
            Result.failure(AppResources.getLocationErrorRemovePhotoFailed(ex.message ?: "Unknown error"))
        }
    }
}
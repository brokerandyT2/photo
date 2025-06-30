// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/AttachPhotoCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.errors.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.errors.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException
import com.x3squaredcircles.pixmap.shared.domain.mappers.LocationMapper
import kotlinx.coroutines.cancellation.CancellationException

/**
 * Command to attach a photo to a location.
 */
data class AttachPhotoCommand(
    val locationId: Int,
    val photoPath: String
)

/**
 * Handles the process of attaching a photo to a location and updating the location in the data store.
 *
 * This handler processes an [AttachPhotoCommand] to attach a photo to a specific location.
 * It retrieves the location by its ID, attaches the specified photo, updates the location in the data store,
 * and saves the changes. If the operation is successful, the updated location is returned as a [LocationDto].
 */
class AttachPhotoCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mapper: LocationMapper,
    private val mediator: IMediator
) : IRequestHandler<AttachPhotoCommand, LocationDto> {

    /**
     * Handles the process of attaching a photo to a location and updating the location in the data store.
     *
     * This method retrieves the location by its ID, attaches the specified photo, updates
     * the location in the data store, and saves the changes. If the location is not found or the update fails, a
     * failure result is returned.
     *
     * @param request The command containing the location ID and the path to the photo to be attached.
     * @return A [Result] containing a [LocationDto] if the operation succeeds; otherwise, a
     * failure result with an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: AttachPhotoCommand): Result<LocationDto> {
        return try {
            val locationResult = unitOfWork.locations.getByIdAsync(request.locationId)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        message = "Location ID ${request.locationId}",
                        errorType = LocationErrorType.DatabaseError,
                        details = AppResources.locationErrorNotFound
                    )
                )
                return Result.failure(AppResources.locationErrorNotFound)
            }

            val location = locationResult.data
            location.attachPhoto(request.photoPath)

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
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "INVALID_PHOTO_PATH" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = "Location ID ${request.locationId}",
                            errorType = LocationErrorType.ValidationError,
                            details = ex.message ?: "Invalid photo path"
                        )
                    )
                    Result.failure(AppResources.locationErrorPhotoPathInvalid)
                }
                else -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = "Location ID ${request.locationId}",
                            errorType = LocationErrorType.NetworkError,
                            details = ex.message ?: "Domain exception"
                        )
                    )
                    Result.failure(AppResources.getLocationErrorAttachPhotoFailed(ex.message ?: "Domain exception"))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    message = "Location ID ${request.locationId}",
                    errorType = LocationErrorType.NetworkError,
                    details = ex.message ?: "Unknown error"
                )
            )
            Result.failure(AppResources.getLocationErrorAttachPhotoFailed(ex.message ?: "Unknown error"))
        }
    }
}